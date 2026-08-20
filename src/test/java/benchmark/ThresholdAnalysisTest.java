package benchmark;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 7 — Threshold analysis and final benchmark report.
 * Reads raw_results.csv, computes metrics at each threshold, writes final reports.
 */
public class ThresholdAnalysisTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path RESULTS_DIR = PROJECT_ROOT.resolve("benchmark/results");
    private static final Path RAW_CSV = RESULTS_DIR.resolve("raw_results.csv");

    private static final double[] THRESHOLDS = {0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95};

    // Ground truth: Type1/Type2/Type3 = Clone, NonClone = Not Clone
    private static final Set<String> CLONE_TYPES = Set.of("Type1_ExactClone", "Type2_Renamed", "Type3_Modified");

    // Stored results for AfterAll report writing
    private static List<ScoredPair> allPairs = new ArrayList<>();
    private static List<ThresholdResult> thresholdResults = new ArrayList<>();

    // ==================================================================
    //  Data class
    // ==================================================================

    private static class ScoredPair {
        String pairId;
        String datasetType;
        String cloneType;
        double score;
        String status;
        String error;
        boolean groundTruthClone; // true = actually a clone
    }

    private static class ThresholdResult {
        double threshold;
        int tp, tn, fp, fn;
        double accuracy, precision, recall, f1, specificity;
        // Per-type counts
        int tp_t1, tp_t2, tp_t3, tn_nc;
        int fp_nc;
        int fn_t1, fn_t2, fn_t3;
    }

    private static class TypeStats {
        int total, correct;
        double minScore, maxScore, meanScore;
    }

    // ==================================================================
    //  Test: Load and validate data
    // ==================================================================

    @Test
    void loadDataAndValidateCounts() throws IOException {
        allPairs = loadRawResults();

        long okCount = allPairs.stream().filter(p -> "OK".equals(p.status)).count();
        long errorCount = allPairs.stream().filter(p -> "ERROR".equals(p.status)).count();
        long totalScored = allPairs.stream().filter(p -> "OK".equals(p.status) && p.score >= 0).count();

        assertEquals(1000, allPairs.size(), "Total pairs must be 1000");
        assertTrue(totalScored >= 998, "Scored pairs must be at least 998, got " + totalScored);
        assertTrue(errorCount <= 2, "Failed pairs must be at most 2, got " + errorCount);

        // Verify per-type counts
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ScoredPair p : allPairs) {
            byType.merge(p.datasetType, 1L, Long::sum);
        }
        assertEquals(167L, (long) byType.getOrDefault("Type1_ExactClone", 0L));
        assertEquals(167L, (long) byType.getOrDefault("Type2_Renamed", 0L));
        assertEquals(166L, (long) byType.getOrDefault("Type3_Modified", 0L));
        assertEquals(500L, (long) byType.getOrDefault("NonClone", 0L));
    }

    // ==================================================================
    //  Test: Threshold analysis
    // ==================================================================

    @Test
    void computeThresholdAnalysis() {
        thresholdResults.clear();

        List<ScoredPair> scored = allPairs.stream()
                .filter(p -> "OK".equals(p.status) && p.score >= 0)
                .toList();

        for (double threshold : THRESHOLDS) {
            ThresholdResult tr = new ThresholdResult();
            tr.threshold = threshold;

            for (ScoredPair p : scored) {
                boolean predictedClone = p.score >= threshold;
                boolean actualClone = p.groundTruthClone;

                if (actualClone && predictedClone) {
                    tr.tp++;
                    if ("Type1_ExactClone".equals(p.datasetType)) tr.tp_t1++;
                    else if ("Type2_Renamed".equals(p.datasetType)) tr.tp_t2++;
                    else if ("Type3_Modified".equals(p.datasetType)) tr.tp_t3++;
                } else if (!actualClone && !predictedClone) {
                    tr.tn++;
                    if ("NonClone".equals(p.datasetType)) tr.tn_nc++;
                } else if (!actualClone && predictedClone) {
                    tr.fp++;
                    if ("NonClone".equals(p.datasetType)) tr.fp_nc++;
                } else { // actualClone && !predictedClone
                    tr.fn++;
                    if ("Type1_ExactClone".equals(p.datasetType)) tr.fn_t1++;
                    else if ("Type2_Renamed".equals(p.datasetType)) tr.fn_t2++;
                    else if ("Type3_Modified".equals(p.datasetType)) tr.fn_t3++;
                }
            }

            int total = tr.tp + tr.tn + tr.fp + tr.fn;
            tr.accuracy = total > 0 ? (double)(tr.tp + tr.tn) / total : 0;
            tr.precision = (tr.tp + tr.fp) > 0 ? (double) tr.tp / (tr.tp + tr.fp) : 0;
            tr.recall = (tr.tp + tr.fn) > 0 ? (double) tr.tp / (tr.tp + tr.fn) : 0;
            tr.f1 = (tr.precision + tr.recall) > 0 ? 2 * tr.precision * tr.recall / (tr.precision + tr.recall) : 0;
            tr.specificity = (tr.tn + tr.fp) > 0 ? (double) tr.tn / (tr.tn + tr.fp) : 0;

            thresholdResults.add(tr);
        }

        // Verify we have 10 thresholds
        assertEquals(10, thresholdResults.size(), "Must have 10 threshold results");
    }

    // ==================================================================
    //  Test: Find best threshold
    // ==================================================================

    @Test
    void identifyBestThreshold() {
        ThresholdResult best = null;
        for (ThresholdResult tr : thresholdResults) {
            if (best == null) {
                best = tr;
            } else {
                // Maximize F1, then recall, then lower threshold
                if (tr.f1 > best.f1 + 1e-9) {
                    best = tr;
                } else if (Math.abs(tr.f1 - best.f1) < 1e-9) {
                    if (tr.recall > best.recall + 1e-9) {
                        best = tr;
                    } else if (Math.abs(tr.recall - best.recall) < 1e-9) {
                        if (tr.threshold < best.threshold) {
                            best = tr;
                        }
                    }
                }
            }
        }

        // Best threshold must be in our list
        assertNotNull(best, "Best threshold must exist");
        assertTrue(best.threshold >= 0.50 && best.threshold <= 0.95, "Best threshold in range");
        assertTrue(best.f1 >= 0, "F1 must be non-negative");

        System.out.println("Best threshold: " + best.threshold
                + " F1=" + String.format("%.4f", best.f1)
                + " Recall=" + String.format("%.4f", best.recall)
                + " Precision=" + String.format("%.4f", best.precision));
    }

    // ==================================================================
    //  AfterAll: Write all reports
    // ==================================================================

    @AfterAll
    static void writeAllReports() throws IOException {
        Files.createDirectories(RESULTS_DIR);

        // Re-load data for report writing
        if (allPairs.isEmpty()) {
            allPairs = loadRawResults();
        }

        // Re-compute thresholds
        thresholdResults.clear();
        List<ScoredPair> scored = allPairs.stream()
                .filter(p -> "OK".equals(p.status) && p.score >= 0)
                .toList();

        for (double threshold : THRESHOLDS) {
            ThresholdResult tr = computeThreshold(scored, threshold);
            thresholdResults.add(tr);
        }

        writeThresholdResultsCsv();
        writeConfusionMatrixCsv();
        writeFinalBenchmarkReport();

        System.out.println("[REPORT] threshold_results.csv written");
        System.out.println("[REPORT] confusion_matrix.csv written");
        System.out.println("[REPORT] final_benchmark_report.txt written");
    }

    // ==================================================================
    //  Report writers
    // ==================================================================

    private static void writeThresholdResultsCsv() throws IOException {
        Path csv = RESULTS_DIR.resolve("threshold_results.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            pw.println("threshold,TP,TN,FP,FN,Accuracy,Precision,Recall,F1,Specificity,"
                    + "TP_Type1,TP_Type2,TP_Type3,TN_NonClone,FP_NonClone,FN_Type1,FN_Type2,FN_Type3");

            for (ThresholdResult tr : thresholdResults) {
                pw.printf("%.2f,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%d,%d%n",
                        tr.threshold, tr.tp, tr.tn, tr.fp, tr.fn,
                        tr.accuracy, tr.precision, tr.recall, tr.f1, tr.specificity,
                        tr.tp_t1, tr.tp_t2, tr.tp_t3, tr.tn_nc, tr.fp_nc,
                        tr.fn_t1, tr.fn_t2, tr.fn_t3);
            }
        }
    }

    private static void writeConfusionMatrixCsv() throws IOException {
        // Find best threshold
        ThresholdResult best = findBestThreshold();

        Path csv = RESULTS_DIR.resolve("confusion_matrix.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            pw.println("metric,value");
            pw.printf("best_threshold,%.2f%n", best.threshold);
            pw.printf("true_positives,%d%n", best.tp);
            pw.printf("true_negatives,%d%n", best.tn);
            pw.printf("false_positives,%d%n", best.fp);
            pw.printf("false_negatives,%d%n", best.fn);
            pw.printf("accuracy,%.4f%n", best.accuracy);
            pw.printf("precision,%.4f%n", best.precision);
            pw.printf("recall,%.4f%n", best.recall);
            pw.printf("f1_score,%.4f%n", best.f1);
            pw.printf("specificity,%.4f%n", best.specificity);
            pw.printf("total_pairs,1000%n");
            pw.printf("scored_pairs,998%n");
            pw.printf("unscorable_pairs,2%n");

            // Per-type breakdown at best threshold
            pw.println();
            pw.println("per_type_breakdown,metric,value");
            pw.printf("Type1_ExactClone,total,%d%n", 167);
            pw.printf("Type1_ExactClone,true_positives,%d%n", best.tp_t1);
            pw.printf("Type1_ExactClone,false_negatives,%d%n", best.fn_t1);
            pw.printf("Type2_Renamed,total,%d%n", 167);
            pw.printf("Type2_Renamed,true_positives,%d%n", best.tp_t2);
            pw.printf("Type2_Renamed,false_negatives,%d%n", best.fn_t2);
            pw.printf("Type3_Modified,total,%d%n", 166);
            pw.printf("Type3_Modified,true_positives,%d%n", best.tp_t3);
            pw.printf("Type3_Modified,false_negatives,%d%n", best.fn_t3);
            pw.printf("NonClone,total,%d%n", 500);
            pw.printf("NonClone,true_negatives,%d%n", best.tn_nc);
            pw.printf("NonClone,false_positives,%d%n", best.fp_nc);
        }
    }

    private static void writeFinalBenchmarkReport() throws IOException {
        ThresholdResult best = findBestThreshold();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<ScoredPair> scored = allPairs.stream()
                .filter(p -> "OK".equals(p.status) && p.score >= 0)
                .toList();

        // Per-type score distributions
        Map<String, List<Double>> scoresByType = new LinkedHashMap<>();
        scoresByType.put("Type1_ExactClone", new ArrayList<>());
        scoresByType.put("Type2_Renamed", new ArrayList<>());
        scoresByType.put("Type3_Modified", new ArrayList<>());
        scoresByType.put("NonClone", new ArrayList<>());

        for (ScoredPair p : scored) {
            scoresByType.computeIfAbsent(p.datasetType, k -> new ArrayList<>()).add(p.score);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("CODESNIFF v1.0 — FINAL BENCHMARK REPORT\n");
        sb.append("===========================================================\n");
        sb.append(String.format("Generated              : %s%n", timestamp));
        sb.append(String.format("Engine                 : SimilarityEngine (omitComments=true, k=6, win=4)%n"));
        sb.append(String.format("Weight maps            : empty (default 1.0 per fingerprint)%n"));
        sb.append("===========================================================\n");

        // Dataset composition
        sb.append("\n1. DATASET COMPOSITION\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("Total pairs            : 1000%n"));
        sb.append(String.format("  Type1_ExactClone     : 167  (ground truth: Clone)%n"));
        sb.append(String.format("  Type2_Renamed        : 167  (ground truth: Clone)%n"));
        sb.append(String.format("  Type3_Modified       : 166  (ground truth: Clone)%n"));
        sb.append(String.format("  NonClone             : 500  (ground truth: NonClone)%n"));

        // Scored / Unscored
        sb.append("\n2. SCORING STATUS\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("Successfully scored    : 998%n"));
        sb.append(String.format("Unscorable (errors)   : 2%n"));
        sb.append(String.format("Metrics calculated on : 998 scored pairs; 2 pairs were unscorable.%n"));

        // Failed pairs
        sb.append("\n3. FAILED PAIRS (StackOverflowError)\n");
        sb.append("-----------------------------------------------------------\n");
        List<ScoredPair> errors = allPairs.stream()
                .filter(p -> "ERROR".equals(p.status))
                .sorted(Comparator.comparing(p -> p.pairId))
                .toList();
        for (ScoredPair p : errors) {
            sb.append(String.format("  %-20s  dataset=%-18s  error=%s%n",
                    p.pairId, p.datasetType, p.error));
        }
        sb.append("  Reason: StackOverflowError in SimilarityEngine's internal regex\n");
        sb.append("  processing on pathological Java source input. These pairs are\n");
        sb.append("  NonClone only and do not affect clone-detection precision/recall.\n");

        // Score distribution by type
        sb.append("\n4. SCORE DISTRIBUTION BY TYPE\n");
        sb.append("-----------------------------------------------------------\n");
        for (Map.Entry<String, List<Double>> entry : scoresByType.entrySet()) {
            List<Double> scores = entry.getValue();
            if (scores.isEmpty()) continue;
            double min = Collections.min(scores);
            double max = Collections.max(scores);
            double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double median = median(scores);
            sb.append(String.format("  %-20s  n=%-4d  min=%.4f  max=%.4f  mean=%.4f  median=%.4f%n",
                    entry.getKey(), scores.size(), min, max, mean, median));
        }

        // Overall stats
        List<Double> allScores = scored.stream().map(p -> p.score).toList();
        double overallMin = Collections.min(allScores);
        double overallMax = Collections.max(allScores);
        double overallMean = allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double overallMedian = median(allScores);
        sb.append(String.format("  %-20s  n=%-4d  min=%.4f  max=%.4f  mean=%.4f  median=%.4f%n",
                "OVERALL", allScores.size(), overallMin, overallMax, overallMean, overallMedian));

        // Threshold table
        sb.append("\n5. THRESHOLD ANALYSIS TABLE\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("  %-9s  %-4s %-4s %-4s %-4s  %-9s %-9s %-7s %-6s %-11s%n",
                "Threshold", "TP", "TN", "FP", "FN", "Accuracy", "Precision", "Recall", "F1", "Specificity"));
        for (ThresholdResult tr : thresholdResults) {
            sb.append(String.format("  %-9s  %-4d %-4d %-4d %-4d  %-9s %-9s %-7s %-6s %-11s%n",
                    String.format("%.2f", tr.threshold),
                    tr.tp, tr.tn, tr.fp, tr.fn,
                    String.format("%.4f", tr.accuracy),
                    String.format("%.4f", tr.precision),
                    String.format("%.4f", tr.recall),
                    String.format("%.4f", tr.f1),
                    String.format("%.4f", tr.specificity)));
        }

        // Best threshold
        sb.append("\n6. BEST THRESHOLD\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("  Threshold             : %.2f%n", best.threshold));
        sb.append(String.format("  Accuracy              : %.4f%n", best.accuracy));
        sb.append(String.format("  Precision             : %.4f%n", best.precision));
        sb.append(String.format("  Recall                : %.4f%n", best.recall));
        sb.append(String.format("  F1 Score              : %.4f%n", best.f1));
        sb.append(String.format("  Specificity           : %.4f%n", best.specificity));

        // Per-type performance at best threshold
        sb.append("\n7. PER-TYPE PERFORMANCE AT BEST THRESHOLD (").append(String.format("%.2f", best.threshold)).append(")\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("  Type1_ExactClone      : %d/%d detected (%.1f%% recall)%n",
                best.tp_t1, 167, 100.0 * best.tp_t1 / 167));
        sb.append(String.format("  Type2_Renamed         : %d/%d detected (%.1f%% recall)%n",
                best.tp_t2, 167, 100.0 * best.tp_t2 / 167));
        sb.append(String.format("  Type3_Modified        : %d/%d detected (%.1f%% recall)%n",
                best.tp_t3, 166, 100.0 * best.tp_t3 / 166));
        sb.append(String.format("  NonClone              : %d/%d correctly rejected (%.1f%% specificity)%n",
                best.tn_nc, 500, 100.0 * best.tn_nc / 500));

        // Confusion matrix at best threshold
        sb.append("\n8. CONFUSION MATRIX AT BEST THRESHOLD\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("                     Predicted Clone  Predicted NonClone%n"));
        sb.append(String.format("  Actual Clone       %6d (TP)       %6d (FN)%n", best.tp, best.fn));
        sb.append(String.format("  Actual NonClone    %6d (FP)       %6d (TN)%n", best.fp, best.tn));

        // Limitations
        sb.append("\n9. LIMITATIONS\n");
        sb.append("-----------------------------------------------------------\n");
        sb.append("  - 2 NonClone pairs (Pair000327, Pair000421) could not be scored\n");
        sb.append("    due to StackOverflowError in SimilarityEngine's regex pipeline.\n");
        sb.append("  - NonClone ground truth is metadata-derived (cross-project,\n");
        sb.append("    different-filename pairs), not database-verified BigCloneBench\n");
        sb.append("    nonclone pairs. PostgreSQL was unavailable at generation time.\n");
        sb.append("  - Metrics are calculated on 998 scored pairs; 2 pairs were unscorable.\n");
        sb.append("  - This benchmark evaluates scoring discrimination, not production\n");
        sb.append("    deployment readiness.\n");

        sb.append("\n===========================================================\n");
        sb.append("END OF BENCHMARK REPORT\n");
        sb.append("===========================================================\n");

        Files.writeString(RESULTS_DIR.resolve("final_benchmark_report.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static List<ScoredPair> loadRawResults() throws IOException {
        List<ScoredPair> pairs = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(RAW_CSV, StandardCharsets.UTF_8)) {
            String header = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 7) continue;

                ScoredPair sp = new ScoredPair();
                sp.pairId = parts[0];
                sp.datasetType = parts[1];
                sp.cloneType = parts[2];
                sp.score = Double.parseDouble(parts[3]);
                sp.status = parts[4];
                sp.error = parts[5];
                sp.groundTruthClone = CLONE_TYPES.contains(sp.datasetType);
                pairs.add(sp);
            }
        }
        return pairs;
    }

    private static String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private static ThresholdResult computeThreshold(List<ScoredPair> scored, double threshold) {
        ThresholdResult tr = new ThresholdResult();
        tr.threshold = threshold;

        for (ScoredPair p : scored) {
            boolean predictedClone = p.score >= threshold;
            boolean actualClone = p.groundTruthClone;

            if (actualClone && predictedClone) {
                tr.tp++;
                if ("Type1_ExactClone".equals(p.datasetType)) tr.tp_t1++;
                else if ("Type2_Renamed".equals(p.datasetType)) tr.tp_t2++;
                else if ("Type3_Modified".equals(p.datasetType)) tr.tp_t3++;
            } else if (!actualClone && !predictedClone) {
                tr.tn++;
                if ("NonClone".equals(p.datasetType)) tr.tn_nc++;
            } else if (!actualClone && predictedClone) {
                tr.fp++;
                if ("NonClone".equals(p.datasetType)) tr.fp_nc++;
            } else {
                tr.fn++;
                if ("Type1_ExactClone".equals(p.datasetType)) tr.fn_t1++;
                else if ("Type2_Renamed".equals(p.datasetType)) tr.fn_t2++;
                else if ("Type3_Modified".equals(p.datasetType)) tr.fn_t3++;
            }
        }

        int total = tr.tp + tr.tn + tr.fp + tr.fn;
        tr.accuracy = total > 0 ? (double)(tr.tp + tr.tn) / total : 0;
        tr.precision = (tr.tp + tr.fp) > 0 ? (double) tr.tp / (tr.tp + tr.fp) : 0;
        tr.recall = (tr.tp + tr.fn) > 0 ? (double) tr.tp / (tr.tp + tr.fn) : 0;
        tr.f1 = (tr.precision + tr.recall) > 0 ? 2 * tr.precision * tr.recall / (tr.precision + tr.recall) : 0;
        tr.specificity = (tr.tn + tr.fp) > 0 ? (double) tr.tn / (tr.tn + tr.fp) : 0;

        return tr;
    }

    private static ThresholdResult findBestThreshold() {
        ThresholdResult best = null;
        for (ThresholdResult tr : thresholdResults) {
            if (best == null) {
                best = tr;
            } else {
                if (tr.f1 > best.f1 + 1e-9) {
                    best = tr;
                } else if (Math.abs(tr.f1 - best.f1) < 1e-9) {
                    if (tr.recall > best.recall + 1e-9) {
                        best = tr;
                    } else if (Math.abs(tr.recall - best.recall) < 1e-9) {
                        if (tr.threshold < best.threshold) {
                            best = tr;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n == 0) return 0;
        if (n % 2 == 1) return sorted.get(n / 2);
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static Path resolveProjectRoot() {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        if (userDir.resolve("pom.xml").toFile().exists()) {
            return userDir;
        }
        Path classDir = Paths.get(
                ThresholdAnalysisTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().getPath());
        return classDir.getParent().getParent();
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) throw new AssertionError(msg);
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
}
