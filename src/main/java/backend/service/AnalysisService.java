package backend.service;

import backend.analysis.SimilarityEngine;
import backend.analysis.StatementGrouper;
import backend.dto.AnalysisResponse;
import backend.dto.CodePayload;
import backend.dto.CodePayload.OptionsDTO;
import backend.dto.CodePayload.Submission;
import backend.dto.PairSummaryDTO;
import backend.store.ReportStore;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates code similarity analysis across multiple submissions.
 * <p>
 * Extracted from {@code AnalyzeController} — contains all the business logic
 * for pre-analysis, corpus-wide IDF weighting, adaptive k/w scaling, pairwise
 * comparison, and operator-divergence detection.
 * <p>
 * No scoring/normalization/tokenization logic has been changed — only relocated.
 */
@Service
public class AnalysisService {

    private final ReportStore reportStore;

    public AnalysisService(ReportStore reportStore) {
        this.reportStore = reportStore;
    }

    /**
     * Run a full pairwise similarity analysis on the given submissions.
     *
     * @param payload code submissions and analysis options
     * @return analysis response with per-pair summaries and report IDs
     */
    public AnalysisResponse analyze(CodePayload payload) {
        if (payload == null || payload.submissions() == null || payload.submissions().size() < 2) {
            return new AnalysisResponse(List.of());
        }

        boolean omit = payload.options() != null && Boolean.TRUE.equals(payload.options().omitComments());
        int defaultK = payload.options() != null && payload.options().k() != null ? payload.options().k() : 6;
        int defaultW = payload.options() != null && payload.options().window() != null ? payload.options().window() : 4;
        var defaultOpt = new SimilarityEngine.Options(omit, defaultK, defaultW);

        var subs = payload.submissions();
        int N = subs.size();

        // 1. Pre-analyze all submissions exactly once using the default/neutral k/w options (O(N) pass)
        List<SimilarityEngine.Analysis> defaultAnalyses = new ArrayList<>();
        for (var sub : subs) {
            defaultAnalyses.add(SimilarityEngine.analyze(safe(sub.content()), defaultOpt));
        }

        // 2. Compute corpus-wide fingerprint document frequencies (DF) using the default (neutral) k/w pass.
        // We choose to build corpus frequency tables using a fixed neutral k/w so that the frequency count
        // baseline is consistent across all documents in the batch.
        Map<Long, Integer> fpDF = new HashMap<>();
        for (var a : defaultAnalyses) {
            for (long h : a.fpSet) {
                fpDF.put(h, fpDF.getOrDefault(h, 0) + 1);
            }
        }

        // 3. Compute corpus-wide statement document frequencies (DF) using the default pass.
        Map<Long, Integer> stmtDF = new HashMap<>();
        for (var a : defaultAnalyses) {
            var stmts = StatementGrouper.groupStatements(a.symbolStream);
            Set<Long> uniqueStmts = new HashSet<>();
            for (var s : stmts) {
                uniqueStmts.add(s.hash);
            }
            for (long h : uniqueStmts) {
                stmtDF.put(h, stmtDF.getOrDefault(h, 0) + 1);
            }
        }

        // 4. Compute N-generalized IDF weights: weight = ln(1.0 + N / df) / ln(1.0 + N)
        double logNPlus1 = Math.log(1.0 + N);
        Map<Long, Double> defaultFpWeights = new HashMap<>();
        for (var entry : fpDF.entrySet()) {
            long h = entry.getKey();
            int df = entry.getValue();
            double weight = Math.log(1.0 + (double) N / df) / logNPlus1;
            defaultFpWeights.put(h, weight);
        }

        Map<Long, Double> stmtWeights = new HashMap<>();
        for (var entry : stmtDF.entrySet()) {
            long h = entry.getKey();
            int df = entry.getValue();
            double weight = Math.log(1.0 + (double) N / df) / logNPlus1;
            stmtWeights.put(h, weight);
        }

        // 5. Compare all pairs, computing pair-specific adaptive k/w dynamically
        List<PairSummaryDTO> out = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                var si = subs.get(i);
                var sj = subs.get(j);
                var aDefault = defaultAnalyses.get(i);
                var bDefault = defaultAnalyses.get(j);

                // Compute pair-specific scaled k/w based on the minimum token count of the two compared files
                int minTokens = Math.min(aDefault.tokenCount, bDefault.tokenCount);
                int pairK = defaultK;
                int pairW = defaultW;
                if (minTokens < 50) {
                    pairK = Math.max(3, pairK - 2);
                    pairW = Math.max(2, pairW - 2);
                } else if (minTokens < 120) {
                    pairK = Math.max(4, pairK - 1);
                    pairW = Math.max(3, pairW - 1);
                }

                SimilarityEngine.Analysis aComp;
                SimilarityEngine.Analysis bComp;
                Map<Long, Double> pairFpWeights;

                if (pairK == defaultK && pairW == defaultW) {
                    // No scaling required: reuse pre-computed default analyses and default corpus weights
                    aComp = aDefault;
                    bComp = bDefault;
                    pairFpWeights = defaultFpWeights;
                } else {
                    // Sizing scaled for tiny files: re-analyze the two files for this pair using the pair-specific Options
                    var pairOpt = new SimilarityEngine.Options(omit, pairK, pairW);
                    aComp = SimilarityEngine.analyze(aDefault.rawCode, pairOpt);
                    bComp = SimilarityEngine.analyze(bDefault.rawCode, pairOpt);

                    // Since fingerprint hashes are different for scaled k, we default weights to empty (unweighted 1.0)
                    // for scaled tiny files where fingerprint rarity discounting is not necessary.
                    pairFpWeights = Map.of();
                }

                double jac = SimilarityEngine.jaccard(aComp, bComp, pairFpWeights);
                double cov = SimilarityEngine.coverage(aComp, bComp, pairK, pairFpWeights);
                double lcs = SimilarityEngine.lcsSimilarity(aComp, bComp, stmtWeights);
                double ast = SimilarityEngine.astSimilarity(aComp, bComp);
                double hyb = SimilarityEngine.hybridScore(aComp, bComp, pairK, pairFpWeights, stmtWeights);

                // Detect if the similarity is driven primarily by operator differences
                backend.ast.ASTNode treeA = backend.ast.ASTBuilder.build(aComp.rawCode);
                backend.ast.ASTNode treeB = backend.ast.ASTBuilder.build(bComp.rawCode);
                backend.ast.ASTSimilarityResult insensitiveRes = backend.ast.ASTComparator.compare(treeA, treeB, true);
                double astInsensitive = insensitiveRes.getSimilarity();
                boolean operatorDivergent = (hyb >= 0.60 && astInsensitive >= 0.90 && (astInsensitive - ast) >= 0.05);

                String id = UUID.randomUUID().toString();
                reportStore.put(id, new ReportStore.ReportData(
                        nullTo(si.name(), "A" + i), nullTo(sj.name(), "A" + j),
                        aComp.rawCode, bComp.rawCode,
                        aComp.normalizedCode, bComp.normalizedCode, aComp.symbolStream, bComp.symbolStream,
                        aComp.fps, bComp.fps, defaultK, defaultW, omit, jac, cov, lcs, ast, hyb, operatorDivergent
                ));

                out.add(new PairSummaryDTO(
                        nullTo(si.name(), "A" + i), nullTo(sj.name(), "A" + j),
                        hyb, jac, cov, id
                ));
            }
        }
        return new AnalysisResponse(out);
    }

    /* ===== Helpers ===== */

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String nullTo(String s, String def) {
        return s == null ? def : s;
    }
}
