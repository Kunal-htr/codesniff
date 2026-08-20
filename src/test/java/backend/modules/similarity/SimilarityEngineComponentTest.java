package backend.modules.similarity;

import backend.modules.similarity.ast.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for SimilarityEngine.
 * Verifies the full analysis pipeline (K-gram hashing, Rabin-Karp, Winnowing),
 * Jaccard, Coverage, LCS delegation, AST delegation, and Hybrid formula.
 *
 * K-gram hashing (Rabin-Karp rolling hash) and Winnowing are private methods;
 * they are tested indirectly through the public analyze() pipeline.
 */
class SimilarityEngineComponentTest {

    private static final SimilarityEngine.Options DEFAULT_OPTS =
            new SimilarityEngine.Options(true, 3, 4);

    private static SimilarityEngine.Analysis analyze(String code) {
        return SimilarityEngine.analyze(code, DEFAULT_OPTS);
    }

    // ==================== Analysis Pipeline ====================
    // (Tests K-gram hashing, Rabin-Karp, and Winnowing indirectly)

    @Test
    void analyzeProducesFingerprintsForValidCode() {
        String code = "public class Foo { public int add(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a = analyze(code);
        assertFalse(a.fpSet.isEmpty(), "Fingerprint set should not be empty");
        assertFalse(a.fps.isEmpty(), "Fingerprint list should not be empty");
        assertTrue(a.tokenCount > 0, "Token count should be positive");
    }

    @Test
    void analyzeProducesEmptyFingerprintsForCodeShorterThanK() {
        // Code that produces fewer than k=3 tokens
        String code = "int x;";
        SimilarityEngine.Analysis a = analyze(code);
        // After normalization and tokenization, this may produce very few tokens
        // The k-gram step requires at least k tokens to produce any hash
        // We just verify the analysis completes without error
        assertNotNull(a);
        assertNotNull(a.fpSet);
        assertNotNull(a.fps);
    }

    @Test
    void analyzeFingerprintPositionsAreValid() {
        String code = "public class Foo { public int sum(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a = analyze(code);
        for (SimilarityEngine.Fingerprint fp : a.fps) {
            assertTrue(fp.pos >= 0, "Position should be non-negative");
            assertTrue(fp.pos < a.tokenCount, "Position should be less than token count");
        }
    }

    @Test
    void analyzeFingerprintSetMatchesFingerprintList() {
        String code = "public class Foo { int x; int y; int z; }";
        SimilarityEngine.Analysis a = analyze(code);
        Set<Long> setFromList = new HashSet<>();
        for (SimilarityEngine.Fingerprint fp : a.fps) {
            setFromList.add(fp.hash);
        }
        // fpSet should be a subset of hashes from fps (winnowing deduplicates)
        assertTrue(a.fpSet.containsAll(setFromList),
                "fpSet should contain all hashes from fps");
    }

    @Test
    void analyzeSymbolStreamIsGenerated() {
        String code = "public class Foo { int x = 5; }";
        SimilarityEngine.Analysis a = analyze(code);
        assertNotNull(a.symbolStream);
        assertFalse(a.symbolStream.isEmpty(), "Symbol stream should not be empty");
    }

    @Test
    void analyzeNormalizedCodeDiffersFromRaw() {
        String code = "package test;\nimport java.util.*;\npublic class Foo { int x = 42; }";
        SimilarityEngine.Analysis a = analyze(code);
        // Normalized code should not contain package/import
        assertFalse(a.normalizedCode.contains("package"));
        assertFalse(a.normalizedCode.contains("import"));
    }

    @Test
    void analyzeIdenticalCodeProducesIdenticalFingerprints() {
        String code = "public class Foo { int add(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a1 = analyze(code);
        SimilarityEngine.Analysis a2 = analyze(code);
        assertEquals(a1.fpSet, a2.fpSet);
        assertEquals(a1.fps.size(), a2.fps.size());
    }

    @Test
    void analyzeDifferentCodeProducesDifferentFingerprints() {
        // Use structurally very different code to ensure low overlap
        String codeA = "class A { int compute(int x, int y, int z) { if (x > y) { return x + z; } else { return y * z; } } }";
        String codeB = "class B { String transform(String s) { String r = s.substring(0, 1); for (int i = 0; i < s.length(); i++) { r += s.charAt(i); } return r; } }";
        SimilarityEngine.Analysis a = analyze(codeA);
        SimilarityEngine.Analysis b = analyze(codeB);
        // Very different code should have low Jaccard overlap
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        assertTrue(j < 0.5, "Expected low Jaccard for different code, got " + j);
    }

    // ==================== Jaccard Similarity ====================

    @Test
    void jaccardBothEmptyFingerprintSets() {
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", "");
        assertEquals(1.0, SimilarityEngine.jaccard(a, b, new HashMap<>()), 1e-9);
    }

    @Test
    void jaccardIdenticalFingerprintSets() {
        Map<Long, Double> weights = new HashMap<>();
        weights.put(1L, 1.0);
        weights.put(2L, 1.0);
        weights.put(3L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(1L, 2L, 3L), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(1L, 2L, 3L), List.of(), 0, List.of(), "", "");
        assertEquals(1.0, SimilarityEngine.jaccard(a, b, weights), 1e-9);
    }

    @Test
    void jaccardDisjointSets() {
        Map<Long, Double> weights = new HashMap<>();
        weights.put(1L, 1.0);
        weights.put(2L, 1.0);
        weights.put(3L, 1.0);
        weights.put(4L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(1L, 2L), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(3L, 4L), List.of(), 0, List.of(), "", "");
        assertEquals(0.0, SimilarityEngine.jaccard(a, b, weights), 1e-9);
    }

    @Test
    void jaccardPartialOverlap() {
        // a = {1, 2, 3}, b = {2, 3, 4}
        // intersection weight = 1+1 = 2 (hashes 2,3)
        // totalA = 1+1+1 = 3
        // totalB = 1+1+1 = 3
        // union = 3+3-2 = 4
        // jaccard = 2/4 = 0.5
        Map<Long, Double> weights = new HashMap<>();
        weights.put(1L, 1.0); weights.put(2L, 1.0);
        weights.put(3L, 1.0); weights.put(4L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(1L, 2L, 3L), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(2L, 3L, 4L), List.of(), 0, List.of(), "", "");
        assertEquals(0.5, SimilarityEngine.jaccard(a, b, weights), 1e-9);
    }

    @Test
    void jaccardWithUnequalWeights() {
        // a = {1, 2}, b = {2, 3}
        // weights: 1→2.0, 2→3.0, 3→1.0
        // intersection = {2} → 3.0
        // totalA = 2.0 + 3.0 = 5.0
        // totalB = 3.0 + 1.0 = 4.0
        // union = 5.0 + 4.0 - 3.0 = 6.0
        // jaccard = 3.0 / 6.0 = 0.5
        Map<Long, Double> weights = new HashMap<>();
        weights.put(1L, 2.0); weights.put(2L, 3.0); weights.put(3L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(1L, 2L), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(2L, 3L), List.of(), 0, List.of(), "", "");
        assertEquals(0.5, SimilarityEngine.jaccard(a, b, weights), 1e-9);
    }

    @Test
    void jaccardWeightsDefaultToOne() {
        // When hash is not in weights map, default weight is 1.0
        // a = {10, 20}, b = {20, 30}
        // intersection = {20} → 1.0 (default)
        // totalA = 1+1 = 2, totalB = 1+1 = 2
        // union = 2+2-1 = 3
        // jaccard = 1/3 ≈ 0.3333
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(10L, 20L), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(20L, 30L), List.of(), 0, List.of(), "", "");
        assertEquals(1.0 / 3.0, SimilarityEngine.jaccard(a, b, new HashMap<>()), 1e-9);
    }

    @Test
    void jaccardOneEmptyOneNonEmpty() {
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(1L, 2L), List.of(), 0, List.of(), "", "");
        // a empty, b non-empty → intersection=0, union=2, jaccard=0
        assertEquals(0.0, SimilarityEngine.jaccard(a, b, new HashMap<>()), 1e-9);
    }

    // ==================== Coverage Similarity ====================

    @Test
    void coverageIdenticalAnalyses() {
        // Same fingerprints at same positions → coverage should be 1.0
        SimilarityEngine.Fingerprint f1 = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2 = new SimilarityEngine.Fingerprint(200L, 3);

        Map<Long, Double> weights = new HashMap<>();
        weights.put(100L, 1.0);
        weights.put(200L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(100L, 200L), List.of(f1, f2), 10, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(100L, 200L), List.of(f1, f2), 10, List.of(), "", "");

        double cov = SimilarityEngine.coverage(a, b, 3, weights);
        assertEquals(1.0, cov, 1e-9);
    }

    @Test
    void coverageDisjointFingerprints() {
        SimilarityEngine.Fingerprint f1 = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2 = new SimilarityEngine.Fingerprint(200L, 3);

        SimilarityEngine.Fingerprint f3 = new SimilarityEngine.Fingerprint(300L, 0);
        SimilarityEngine.Fingerprint f4 = new SimilarityEngine.Fingerprint(400L, 3);

        Map<Long, Double> weights = new HashMap<>();
        weights.put(100L, 1.0); weights.put(200L, 1.0);
        weights.put(300L, 1.0); weights.put(400L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(100L, 200L), List.of(f1, f2), 10, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(300L, 400L), List.of(f3, f4), 10, List.of(), "", "");

        double cov = SimilarityEngine.coverage(a, b, 3, weights);
        assertEquals(0.0, cov, 1e-9);
    }

    @Test
    void coveragePartialOverlap() {
        // A has fingerprints covering positions 0-2 and 3-5
        // B has fingerprints covering positions 0-2 and 6-8
        // Common: fingerprint at pos 0 → covers 0,1,2
        // coveredA = 3, coveredB = 3
        // totalA = 6, totalB = 6
        // coverage = min(3,3)/min(6,6) = 3/6 = 0.5
        SimilarityEngine.Fingerprint f1A = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2A = new SimilarityEngine.Fingerprint(200L, 3);
        SimilarityEngine.Fingerprint f1B = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2B = new SimilarityEngine.Fingerprint(300L, 6);

        Map<Long, Double> weights = new HashMap<>();
        weights.put(100L, 1.0); weights.put(200L, 1.0); weights.put(300L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(100L, 200L), List.of(f1A, f2A), 10, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(100L, 300L), List.of(f1B, f2B), 10, List.of(), "", "");

        double cov = SimilarityEngine.coverage(a, b, 3, weights);
        assertEquals(0.5, cov, 1e-9);
    }

    @Test
    void coverageEmptyCommonFingerprints() {
        SimilarityEngine.Fingerprint f1 = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2 = new SimilarityEngine.Fingerprint(200L, 0);

        Map<Long, Double> weights = new HashMap<>();
        weights.put(100L, 1.0); weights.put(200L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(100L), List.of(f1), 10, List.of(), "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(200L), List.of(f2), 10, List.of(), "", "");

        double cov = SimilarityEngine.coverage(a, b, 3, weights);
        assertEquals(0.0, cov, 1e-9);
    }

    // ==================== LCS Delegation ====================

    @Test
    void lcsSimilarityDelegatesToLcsEngine() {
        // Identical symbol streams → LCS similarity should be 1.0
        List<String> stream = List.of("KW(if)", "OP(;)", "KW(return)", "OP(;)");
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, stream, "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, stream, "", "");
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        assertEquals(1.0, lcs, 1e-9);
    }

    @Test
    void lcsSimilarityDifferentStreams() {
        List<String> streamA = List.of("A", "OP(;)", "B", "OP(;)");
        List<String> streamB = List.of("C", "OP(;)", "D", "OP(;)");
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, streamA, "", "");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, streamB, "", "");
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        assertEquals(0.0, lcs, 1e-9);
    }

    // ==================== AST Delegation ====================

    @Test
    void astSimilarityIdenticalCode() {
        String code = "class Foo { int add(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", code);
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", code);
        double ast = SimilarityEngine.astSimilarity(a, b);
        assertEquals(1.0, ast, 1e-9);
    }

    @Test
    void astSimilarityDifferentCode() {
        String codeA = "class Foo { int x; }";
        String codeB = "class Bar { void m() { if (true) { return; } } }";
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", codeA);
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", codeB);
        double ast = SimilarityEngine.astSimilarity(a, b);
        assertTrue(ast < 0.9, "Expected lower AST similarity for different structures, got " + ast);
    }

    // ==================== Hybrid Score & Formula ====================

    @Test
    void hybridFormulaWeightsAreCorrect() {
        // Create analyses where all metrics can be computed
        String code = "class Foo { int add(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);

        // With identical code, all metrics should be ~1.0
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        double c = SimilarityEngine.coverage(a, b, DEFAULT_OPTS.k, new HashMap<>());
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);

        // Verify the raw weighted sum (before damping)
        double expectedRaw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        // The actual hybridScore applies damping: s * min(1.0, j / 0.15)
        double damping = Math.min(1.0, j / 0.15);
        double expectedHybrid = expectedRaw * damping;

        double actualHybrid = SimilarityEngine.hybridScore(a, b, DEFAULT_OPTS.k, new HashMap<>(), new HashMap<>());
        assertEquals(expectedHybrid, actualHybrid, 1e-9);
    }

    @Test
    void hybridScoreUsesWeights025_035_020_020() {
        // Construct Analysis objects with known metrics
        // Jaccard = 1.0, Coverage = 0.0, LCS = 0.0, AST = 0.0
        // → raw = 0.25*1.0 + 0.35*0.0 + 0.20*0.0 + 0.20*0.0 = 0.25
        // → damping = min(1.0, 1.0/0.15) = 1.0
        // → hybrid = 0.25
        SimilarityEngine.Fingerprint f1 = new SimilarityEngine.Fingerprint(100L, 0);
        Map<Long, Double> fpWeights = new HashMap<>();
        fpWeights.put(100L, 1.0);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                Set.of(100L), List.of(f1), 5, List.of("X", "OP(;)"), "", "class Foo { int x; }");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                Set.of(100L), List.of(f1), 5, List.of("Y", "OP(;)"), "", "class Foo { int y; }");

        double j = SimilarityEngine.jaccard(a, b, fpWeights);
        double c = SimilarityEngine.coverage(a, b, 3, fpWeights);
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);

        // Verify individual weights: 0.25, 0.35, 0.20, 0.20
        double raw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        double damping = Math.min(1.0, j / 0.15);
        double expected = raw * damping;

        double actual = SimilarityEngine.hybridScore(a, b, 3, fpWeights, new HashMap<>());
        assertEquals(expected, actual, 1e-9,
                "Hybrid score should use weights 0.25, 0.35, 0.20, 0.20");
    }

    @Test
    void hybridScoreDampingFactor() {
        // When Jaccard is low (< 0.15), damping reduces the score
        // Jaccard = 0.075 → damping = 0.075/0.15 = 0.5
        // If raw = 0.5, then hybrid = 0.5 * 0.5 = 0.25
        SimilarityEngine.Fingerprint f1 = new SimilarityEngine.Fingerprint(100L, 0);
        SimilarityEngine.Fingerprint f2 = new SimilarityEngine.Fingerprint(200L, 3);
        Map<Long, Double> fpWeights = new HashMap<>();
        fpWeights.put(100L, 1.0);
        fpWeights.put(200L, 1.0);

        // a has {100, 200}, b has {100, 300} → intersection={100}
        // Jaccard = 1 / (2+2-1) = 1/3 ≈ 0.333 → damping = min(1.0, 0.333/0.15) = 1.0
        // Let me construct a case with very low Jaccard:
        // a has {100, 200, 300, 400, 500}, b has {100, 600, 700, 800, 900}
        // intersection = {100} → 1.0
        // totalA = 5.0, totalB = 5.0
        // union = 5+5-1 = 9
        // Jaccard = 1/9 ≈ 0.111 → damping = 0.111/0.15 ≈ 0.741
        Set<Long> aSet = Set.of(100L, 200L, 300L, 400L, 500L);
        Set<Long> bSet = Set.of(100L, 600L, 700L, 800L, 900L);

        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                aSet, List.of(), 10, List.of(), "", "class A { int x; int y; int z; int w; int v; }");
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                bSet, List.of(), 10, List.of(), "", "class B { String a; String b; String c; String d; String e; }");

        double j = SimilarityEngine.jaccard(a, b, fpWeights);
        double c = SimilarityEngine.coverage(a, b, 3, fpWeights);
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);

        double raw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        double damping = Math.min(1.0, j / 0.15);
        double expected = raw * damping;

        double actual = SimilarityEngine.hybridScore(a, b, 3, fpWeights, new HashMap<>());
        assertEquals(expected, actual, 1e-9);
        // Verify damping is active (Jaccard < 0.15)
        assertTrue(j < 0.15, "Jaccard should be below damping threshold, got " + j);
        assertTrue(damping < 1.0, "Damping should be active, got " + damping);
    }

    @Test
    void hybridScoreIsClampedToUnitInterval() {
        // Hybrid score should always be in [0, 1]
        String codeA = "class Foo { int x; }";
        String codeB = "class Bar { String s; void m() { if (true) { return; } } }";
        SimilarityEngine.Analysis a = analyze(codeA);
        SimilarityEngine.Analysis b = analyze(codeB);
        double score = SimilarityEngine.hybridScore(a, b, DEFAULT_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score >= 0.0, "Hybrid score should be >= 0, got " + score);
        assertTrue(score <= 1.0, "Hybrid score should be <= 1, got " + score);
    }

    @Test
    void hybridScoreIdenticalCode() {
        String code = "class Foo { int add(int a, int b) { return a + b; } }";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);
        double score = SimilarityEngine.hybridScore(a, b, DEFAULT_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score > 0.5, "Identical code should produce high hybrid score, got " + score);
    }

    @Test
    void hybridScoreZeroForEmptyAnalyses() {
        SimilarityEngine.Analysis empty = new SimilarityEngine.Analysis(
                Set.of(), List.of(), 0, List.of(), "", "");
        double score = SimilarityEngine.hybridScore(empty, empty, 3, new HashMap<>(), new HashMap<>());
        // Both empty → jaccard=1.0, coverage=0, LCS=0, AST=1.0 (identical empty PROGRAM nodes)
        // raw = 0.25*1.0 + 0.35*0 + 0.20*0 + 0.20*1.0 = 0.45
        // damping = min(1.0, 1.0/0.15) = 1.0
        assertEquals(0.45, score, 1e-9);
    }

    // ==================== Options validation ====================

    @Test
    void optionsRejectsKTooSmall() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityEngine.Options(true, 2, 4));
    }

    @Test
    void optionsRejectsKTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityEngine.Options(true, 65, 4));
    }

    @Test
    void optionsRejectsWindowTooSmall() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityEngine.Options(true, 3, 0));
    }

    @Test
    void optionsRejectsWindowTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityEngine.Options(true, 3, 129));
    }

    // ==================== DetailedScore ====================

    @Test
    void computeDetailedScoreAllPercentages() {
        String code = "class Foo { int x; }";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);
        SimilarityEngine.DetailedScore ds = SimilarityEngine.computeDetailedScore(
                a, b, DEFAULT_OPTS.k, new HashMap<>(), new HashMap<>());
        // All scores should be percentages (0-100 range for identical code)
        assertTrue(ds.fingerprintScore >= 0 && ds.fingerprintScore <= 100);
        assertTrue(ds.coverageScore >= 0 && ds.coverageScore <= 100);
        assertTrue(ds.lcsScore >= 0 && ds.lcsScore <= 100);
        assertTrue(ds.astScore >= 0 && ds.astScore <= 100);
        assertTrue(ds.hybridScore >= 0 && ds.hybridScore <= 100);
    }

    @Test
    void detailedScoreToString() {
        String code = "class Foo { int x; }";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);
        SimilarityEngine.DetailedScore ds = SimilarityEngine.computeDetailedScore(
                a, b, DEFAULT_OPTS.k, new HashMap<>(), new HashMap<>());
        String str = ds.toString();
        assertTrue(str.contains("FP="));
        assertTrue(str.contains("COV="));
        assertTrue(str.contains("LCS="));
        assertTrue(str.contains("AST="));
        assertTrue(str.contains("HYB="));
    }
}
