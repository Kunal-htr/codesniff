package benchmark;

import backend.modules.similarity.SimilarityEngine;
import backend.modules.similarity.SimilarityEngine.Analysis;
import backend.modules.similarity.SimilarityEngine.Options;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * JUnit 5 dynamic benchmark: scores all 1000 pairs using the production SimilarityEngine.
 *
 * <p>Discovers every Type1_ExactClone, Type2_Renamed, Type3_Modified, and NonClone
 * pair folder, reads Original.java and Clone.java, runs SimilarityEngine.analyze()
 * with Options(true, 6, 4), computes hybridScore with empty weight maps, and writes
 * raw_results.csv + benchmark_summary.txt.</p>
 */
public class BenchmarkSimilarityEngineTest {

    private static final String[] DATASET_TYPES = {
            "Type1_ExactClone",
            "Type2_Renamed",
            "Type3_Modified",
            "NonClone"
    };

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path DATASET_DIR = PROJECT_ROOT.resolve("benchmark/dataset");
    private static final Path RESULTS_DIR = PROJECT_ROOT.resolve("benchmark/results");

    private static final Options ENGINE_OPTIONS = new Options(true, 6, 4);
    private static final Map<Long, Double> EMPTY_WEIGHTS = Collections.emptyMap();

    // Shared mutable state for results
    private static final List<BenchmarkResult> RESULTS =
            Collections.synchronizedList(new ArrayList<>());

    // ==================================================================
    //  Dynamic test factory
    // ==================================================================

    @TestFactory
    Stream<DynamicTest> benchmarkAllPairs() {
        RESULTS.clear();

        List<DynamicTest> tests = new ArrayList<>();

        for (String datasetType : DATASET_TYPES) {
            Path typeDir = DATASET_DIR.resolve(datasetType);
            if (!Files.isDirectory(typeDir)) {
                System.err.println("[WARN] Dataset type directory not found: " + typeDir);
                continue;
            }

            List<Path> pairDirs = listPairDirectories(typeDir);
            pairDirs.sort(Comparator.comparing(p -> p.getFileName().toString()));

            for (Path pairDir : pairDirs) {
                String pairId = pairDir.getFileName().toString();
                String displayName = datasetType + "/" + pairId;

                tests.add(dynamicTest(displayName, () -> {
                    BenchmarkResult result;
                    try {
                        result = scorePair(pairId, datasetType, pairDir);
                    } catch (Throwable e) {
                        result = new BenchmarkResult();
                        result.pairId = pairId;
                        result.datasetType = datasetType;
                        result.score = -1.0;
                        result.status = "ERROR";
                        result.error = e.getClass().getSimpleName() + ": " + simplifyMessage(e);
                        result.executionTimeMs = 0;
                    }
                    RESULTS.add(result);
                    assertTrue(true, "Recorded " + result.status + " for " + displayName);
                }));
            }
        }

        return tests.stream();
    }

    // ==================================================================
    //  Report writers
    // ==================================================================

    @AfterAll
    static void writeReports() throws IOException {
        Files.createDirectories(RESULTS_DIR);
        writeRawResultsCsv();
        writeBenchmarkSummary();
    }

    // ==================================================================
    //  Per-pair scoring logic
    // ==================================================================

    private static BenchmarkResult scorePair(String pairId, String datasetType, Path pairDir) {
        Path origFile = pairDir.resolve("Original.java");
        Path cloneFile = pairDir.resolve("Clone.java");
        Path metaFile = pairDir.resolve("metadata.json");

        BenchmarkResult r = new BenchmarkResult();
        r.pairId = pairId;
        r.datasetType = datasetType;
        r.score = -1.0;
        r.status = "OK";
        r.error = "";
        r.executionTimeMs = 0;

        // Read metadata for clone_type
        try {
            String meta = Files.readString(metaFile, StandardCharsets.UTF_8);
            int ctIdx = meta.indexOf("\"clone_type\"");
            if (ctIdx >= 0) {
                int colonIdx = meta.indexOf(':', ctIdx);
                int quoteStart = meta.indexOf('"', colonIdx + 1);
                int quoteEnd = meta.indexOf('"', quoteStart + 1);
                r.cloneType = meta.substring(quoteStart + 1, quoteEnd);
            }
        } catch (Exception e) {
            r.cloneType = datasetType;
        }

        // Read source files
        String origCode, cloneCode;
        try {
            origCode = Files.readString(origFile, StandardCharsets.UTF_8);
            if (origCode.isEmpty()) {
                r.status = "SKIP"; r.error = "Original.java empty"; return r;
            }
        } catch (IOException e) {
            r.status = "ERROR"; r.error = "Cannot read Original.java: " + e.getMessage(); return r;
        }
        try {
            cloneCode = Files.readString(cloneFile, StandardCharsets.UTF_8);
            if (cloneCode.isEmpty()) {
                r.status = "SKIP"; r.error = "Clone.java empty"; return r;
            }
        } catch (IOException e) {
            r.status = "ERROR"; r.error = "Cannot read Clone.java: " + e.getMessage(); return r;
        }

        // Run SimilarityEngine
        long start = System.nanoTime();
        try {
            Analysis a = SimilarityEngine.analyze(origCode, ENGINE_OPTIONS);
            Analysis b = SimilarityEngine.analyze(cloneCode, ENGINE_OPTIONS);
            r.score = SimilarityEngine.hybridScore(a, b, 6, EMPTY_WEIGHTS, EMPTY_WEIGHTS);
            r.status = "OK";
        } catch (Exception e) {
            r.status = "ERROR";
            r.error = e.getClass().getSimpleName() + ": " + simplifyMessage(e);
            r.score = -1.0;
        }
        long elapsed = System.nanoTime() - start;
        r.executionTimeMs = elapsed / 1_000_000;

        return r;
    }

    // ==================================================================
    //  CSV report
    // ==================================================================

    private static void writeRawResultsCsv() throws IOException {
        Path csv = RESULTS_DIR.resolve("raw_results.csv");
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {

            pw.println("pair_id,dataset_type,clone_type,score,status,error,execution_time_ms");

            for (BenchmarkResult r : RESULTS) {
                pw.printf("%s,%s,%s,%.6f,%s,%s,%d%n",
                        csv(r.pairId), csv(r.datasetType), csv(r.cloneType),
                        r.score, csv(r.status), csv(r.error), r.executionTimeMs);
            }
        }
        System.out.println("[REPORT] raw_results.csv written -> " + csv);
    }

    // ==================================================================
    //  Summary report
    // ==================================================================

    private static void writeBenchmarkSummary() throws IOException {
        int total = RESULTS.size();
        int ok = 0, errors = 0, skips = 0;
        long totalMs = 0;

        // Per-type stats
        Map<String, List<Double>> scoresByType = new LinkedHashMap<>();
        for (String t : DATASET_TYPES) scoresByType.put(t, new ArrayList<>());

        for (BenchmarkResult r : RESULTS) {
            switch (r.status) {
                case "OK" -> { ok++; totalMs += r.executionTimeMs; }
                case "ERROR" -> errors++;
                case "SKIP" -> skips++;
            }
            if (r.score >= 0) {
                scoresByType.computeIfAbsent(r.datasetType, k -> new ArrayList<>()).add(r.score);
            }
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("SIMILARENGINE BENCHMARK SUMMARY\n");
        sb.append("===========================================================\n");
        sb.append(String.format("Generated              : %s%n", timestamp));
        sb.append(String.format("Options                : omitComments=true, k=6, win=4%n"));
        sb.append(String.format("Weight maps            : empty (default 1.0 per fingerprint)%n"));
        sb.append(String.format("Total pairs discovered : %d%n", total));
        sb.append(String.format("Successful executions  : %d%n", ok));
        sb.append(String.format("Failed executions      : %d%n", errors));
        sb.append(String.format("Skipped                : %d%n", skips));
        sb.append(String.format("Total execution time   : %d ms%n", totalMs));
        sb.append("-----------------------------------------------------------\n");

        // Per-type breakdown
        for (String type : DATASET_TYPES) {
            List<Double> scores = scoresByType.getOrDefault(type, Collections.emptyList());
            sb.append(String.format("%n%s (n=%d)%n", type, scores.size()));
            if (!scores.isEmpty()) {
                double min = Collections.min(scores);
                double max = Collections.max(scores);
                double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double median = median(scores);
                sb.append(String.format("  min=%.4f  max=%.4f  mean=%.4f  median=%.4f%n", min, max, mean, median));
            } else {
                sb.append("  No scores recorded.\n");
            }
        }

        // Overall stats
        List<Double> allScores = new ArrayList<>();
        for (BenchmarkResult r : RESULTS) {
            if (r.score >= 0) allScores.add(r.score);
        }
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("%nOverall (n=%d)%n", allScores.size()));
        if (!allScores.isEmpty()) {
            double min = Collections.min(allScores);
            double max = Collections.max(allScores);
            double mean = allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double median = median(allScores);
            sb.append(String.format("  min=%.4f  max=%.4f  mean=%.4f  median=%.4f%n", min, max, mean, median));
        }

        // Known parser exceptions note
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("%nKnown JavaParser exceptions (affecting Clone.java parsing):%n"));
        sb.append("  NonClone/Pair000083 - Clone.java: wildcard syntax not parseable%n");
        sb.append("  NonClone/Pair000084 - Clone.java: wildcard syntax not parseable%n");
        sb.append("  NonClone/Pair000443 - Clone.java: '_' reserved keyword%n");
        sb.append("  These pairs still produce scores (SimilarityEngine uses its own tokenizer).%n");
        sb.append("===========================================================\n");

        Path summary = RESULTS_DIR.resolve("benchmark_summary.txt");
        Files.writeString(summary, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[REPORT] benchmark_summary.txt written -> " + summary);

        // Console summary
        System.out.println();
        System.out.println("========================================");
        System.out.println("CodeSniff SimilarityEngine Benchmark");
        System.out.println("========================================");
        System.out.printf("Total pairs: %d%n", total);
        System.out.printf("Successful: %d%n", ok);
        System.out.printf("Failed:     %d%n", errors);
        System.out.printf("Skipped:    %d%n", skips);
        System.out.printf("Total time: %d ms%n", totalMs);
        System.out.println();
        for (String type : DATASET_TYPES) {
            List<Double> scores = scoresByType.getOrDefault(type, Collections.emptyList());
            if (!scores.isEmpty()) {
                double min = Collections.min(scores);
                double max = Collections.max(scores);
                double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double median = median(scores);
                System.out.printf("%s (n=%d): min=%.4f max=%.4f mean=%.4f median=%.4f%n",
                        type, scores.size(), min, max, mean, median);
            }
        }
        if (!allScores.isEmpty()) {
            double min = Collections.min(allScores);
            double max = Collections.max(allScores);
            double mean = allScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double median = median(allScores);
            System.out.printf("OVERALL   (n=%d): min=%.4f max=%.4f mean=%.4f median=%.4f%n",
                    allScores.size(), min, max, mean, median);
        }

        // List errors
        List<BenchmarkResult> errorPairs = RESULTS.stream()
                .filter(r -> "ERROR".equals(r.status))
                .sorted(Comparator.comparing(r -> r.datasetType + "/" + r.pairId))
                .toList();
        if (!errorPairs.isEmpty()) {
            System.out.println();
            System.out.println("Failed pairs:");
            for (BenchmarkResult r : errorPairs) {
                System.out.printf("  %s/%s  --  %s%n", r.datasetType, r.pairId, r.error);
            }
        }
        System.out.println("========================================");
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static double median(List<Double> sorted) {
        List<Double> s = new ArrayList<>(sorted);
        Collections.sort(s);
        int n = s.size();
        if (n == 0) return 0;
        if (n % 2 == 1) return s.get(n / 2);
        return (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static Path resolveProjectRoot() {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        if (userDir.resolve("pom.xml").toFile().exists()) {
            return userDir;
        }
        Path classDir = Paths.get(
                BenchmarkSimilarityEngineTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().getPath());
        return classDir.getParent().getParent();
    }

    private static List<Path> listPairDirectories(Path typeDir) {
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, "Pair*")) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Cannot list directory: " + typeDir + " -- " + e.getMessage());
        }
        return dirs;
    }

    private static String simplifyMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        return msg.replace(",", ";").replace("\n", " ").replace("\r", "");
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ==================================================================
    //  Internal data class
    // ==================================================================

    private static class BenchmarkResult {
        String pairId;
        String datasetType;
        String cloneType = "";
        double score;
        String status;    // OK | ERROR | SKIP
        String error = "";
        long executionTimeMs;
    }
}
