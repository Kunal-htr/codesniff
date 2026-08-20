package backend.modules.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9B — Integration tests for SimilarityEngine.
 *
 * Verifies that analyze() produces Analysis objects that correctly flow
 * through jaccard(), coverage(), lcsSimilarity(), astSimilarity(), and
 * hybridScore() as a complete pipeline on real Java code examples.
 *
 * Production config under test: Options(true, 6, 4)
 */
class SimilarityEngineIntegrationTest {

    // ==================== Production Configuration ====================

    private static final SimilarityEngine.Options PROD_OPTS =
            new SimilarityEngine.Options(true, 6, 4);

    // ==================== Test Code Samples ====================

    /** Identical code — both files are the same array sum method. */
    private static final String ARRAY_SUM_A =
            "public class ArraySum {\n" +
            "    public static int sumArray(int[] arr) {\n" +
            "        int total = 0;\n" +
            "        for (int i = 0; i < arr.length; i++) {\n" +
            "            total += arr[i];\n" +
            "        }\n" +
            "        return total;\n" +
            "    }\n" +
            "}";

    private static final String ARRAY_SUM_B = ARRAY_SUM_A;

    /** Type-2 clone: same logic, renamed variables. */
    private static final String FIND_MAX_ORIGINAL =
            "public class FindMax {\n" +
            "    public static int find(int[] arr) {\n" +
            "        if (arr == null || arr.length == 0) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        int maxVal = arr[0];\n" +
            "        for (int i = 1; i < arr.length; i++) {\n" +
            "            if (arr[i] > maxVal) {\n" +
            "                maxVal = arr[i];\n" +
            "            }\n" +
            "        }\n" +
            "        return maxVal;\n" +
            "    }\n" +
            "}";

    private static final String FIND_MAX_RENAMED =
            "public class FindMaxRenamed {\n" +
            "    public static int locateLargest(int[] numbers) {\n" +
            "        if (numbers == null || numbers.length == 0) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        int largest = numbers[0];\n" +
            "        for (int idx = 1; idx < numbers.length; idx++) {\n" +
            "            if (numbers[idx] > largest) {\n" +
            "                largest = numbers[idx];\n" +
            "            }\n" +
            "        }\n" +
            "        return largest;\n" +
            "    }\n" +
            "}";

    /** Type-3 clone: structural modifications — extra statements, different loop type. */
    private static final String FIND_MIN_ORIGINAL =
            "public class FindMin {\n" +
            "    public static int find(int[] arr) {\n" +
            "        if (arr == null || arr.length == 0) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        int minVal = arr[0];\n" +
            "        for (int i = 1; i < arr.length; i++) {\n" +
            "            if (arr[i] < minVal) {\n" +
            "                minVal = arr[i];\n" +
            "            }\n" +
            "        }\n" +
            "        return minVal;\n" +
            "    }\n" +
            "}";

    private static final String FIND_MIN_MODIFIED =
            "public class FindMinModified {\n" +
            "    public static int find(int[] arr) {\n" +
            "        int minVal;\n" +
            "        boolean isEmpty = (arr == null || arr.length == 0);\n" +
            "        if (isEmpty) {\n" +
            "            return -1;\n" +
            "        }\n" +
            "        minVal = arr[0];\n" +
            "        int i = 1;\n" +
            "        while (i < arr.length) {\n" +
            "            int current = arr[i];\n" +
            "            if (current < minVal) {\n" +
            "                minVal = current;\n" +
            "            }\n" +
            "            i++;\n" +
            "        }\n" +
            "        System.out.println(\"Minimum computed.\");\n" +
            "        return minVal;\n" +
            "    }\n" +
            "}";

    /** Completely unrelated code — a string concatenation method. */
    private static final String STRING_CONCAT =
            "public class StringConcat {\n" +
            "    public static String concat(String a, String b) {\n" +
            "        if (a == null) a = \"\";\n" +
            "        if (b == null) b = \"\";\n" +
            "        return a + b;\n" +
            "    }\n" +
            "}";

    /** A second unrelated method — binary search. */
    private static final String BINARY_SEARCH =
            "public class BinarySearch {\n" +
            "    public static int search(int[] arr, int target) {\n" +
            "        int low = 0;\n" +
            "        int high = arr.length - 1;\n" +
            "        while (low <= high) {\n" +
            "            int mid = (low + high) / 2;\n" +
            "            if (arr[mid] == target) return mid;\n" +
            "            else if (arr[mid] < target) low = mid + 1;\n" +
            "            else high = mid - 1;\n" +
            "        }\n" +
            "        return -1;\n" +
            "    }\n" +
            "}";

    // ==================== Helper ====================

    private static SimilarityEngine.Analysis analyze(String code) {
        return SimilarityEngine.analyze(code, PROD_OPTS);
    }

    // ==================== 1. Identical Code — Perfect/Very High Similarity ====================

    @Test
    void identicalMethodsProduceVeryHighHybridScore() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score > 0.8,
                "Identical code should produce very high hybrid score, got " + score);
    }

    @Test
    void identicalMethodsHaveMatchingFingerprintSets() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        // Identical code → identical normalization → identical k-grams → identical fingerprints
        assertEquals(a.fpSet, b.fpSet,
                "Identical code should produce identical fingerprint sets");
    }

    @Test
    void identicalMethodsHaveMatchingSymbolStreams() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        assertEquals(a.symbolStream, b.symbolStream,
                "Identical code should produce identical symbol streams");
    }

    @Test
    void identicalMethodsJaccardIsOne() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        assertEquals(1.0, j, 1e-9,
                "Identical code should have Jaccard = 1.0");
    }

    @Test
    void identicalMethodsCoverageIsOne() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        assertEquals(1.0, c, 1e-9,
                "Identical code should have Coverage = 1.0");
    }

    // ==================== 2. Type-2 Clone — Renamed Variables ====================

    @Test
    void renamedVariablesProduceHighCloneSimilarity() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        // Type-2 clones (renamed variables) should be detected as high similarity
        assertTrue(score > 0.5,
                "Type-2 clone (renamed variables) should produce high hybrid score, got " + score);
    }

    @Test
    void renamedVariablesJaccardIsHigh() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        // After normalization, identifiers become ID → renamed vars should have high Jaccard
        assertTrue(j > 0.5,
                "Type-2 clone should have high Jaccard, got " + j);
    }

    @Test
    void renamedVariablesLCSIsHigh() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        // Statement-level structure is identical after normalization
        assertTrue(lcs > 0.5,
                "Type-2 clone should have high LCS similarity, got " + lcs);
    }

    @Test
    void renamedVariablesASTIsHigh() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);
        double ast = SimilarityEngine.astSimilarity(a, b);
        // With leaf-value-aware hashing, renamed variables produce moderate AST similarity
        // because IDENTIFIER children no longer match (names differ), but structural nodes
        // (METHOD, BLOCK, RETURN, BINARY_OP, IF, FOR_LOOP) still match.
        assertTrue(ast > 0.2,
                "Type-2 clone should have moderate AST similarity, got " + ast);
        assertTrue(ast < 1.0,
                "Renamed variables should not produce perfect AST similarity, got " + ast);
    }

    // ==================== 3. Type-3 Clone — Structural Modifications ====================

    @Test
    void modifiedCodeProduceReducedButMeaningfulSimilarity() {
        SimilarityEngine.Analysis a = analyze(FIND_MIN_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MIN_MODIFIED);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        // Type-3 clones have structural changes → lower score than Type-2 but still meaningful
        assertTrue(score > 0.2,
                "Type-3 clone should produce meaningful similarity, got " + score);
        assertTrue(score < 0.95,
                "Type-3 clone should be lower than identical, got " + score);
    }

    @Test
    void modifiedCodeLCSIsReduced() {
        SimilarityEngine.Analysis a = analyze(FIND_MIN_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MIN_MODIFIED);
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        // Structural changes (extra var decl, while instead of for, extra println)
        // should reduce LCS compared to identical code
        assertTrue(lcs > 0.0 && lcs < 1.0,
                "Type-3 clone should have intermediate LCS, got " + lcs);
    }

    @Test
    void modifiedCodeCoverageIsReduced() {
        SimilarityEngine.Analysis a = analyze(FIND_MIN_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MIN_MODIFIED);
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        // Extra statements change token coverage
        assertTrue(c > 0.0 && c < 1.0,
                "Type-3 clone should have intermediate coverage, got " + c);
    }

    // ==================== 4. Unrelated Code — Low Similarity ====================

    @Test
    void unrelatedMethodsProduceLowSimilarity() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(STRING_CONCAT);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        // Completely different logic → low similarity
        assertTrue(score < 0.5,
                "Unrelated methods should produce low hybrid score, got " + score);
    }

    @Test
    void unrelatedMethodsJaccardIsLow() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(STRING_CONCAT);
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        assertTrue(j < 0.5,
                "Unrelated methods should have low Jaccard, got " + j);
    }

    @Test
    void unrelatedMethodsLCSIsLow() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(STRING_CONCAT);
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        assertTrue(lcs < 0.5,
                "Unrelated methods should have low LCS, got " + lcs);
    }

    @Test
    void twoDifferentAlgorithmsAreLowSimilarity() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(BINARY_SEARCH);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        // Both are search algorithms but structurally very different
        assertTrue(score < 0.6,
                "Different algorithms should produce low-moderate similarity, got " + score);
    }

    // ==================== 5. Edge Cases ====================

    @Test
    void emptyCodeHandledGracefully() {
        SimilarityEngine.Analysis a = analyze("");
        SimilarityEngine.Analysis b = analyze("");
        // Should not throw — produces Analysis with empty fpSet
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score >= 0.0 && score <= 1.0,
                "Empty code hybrid score should be in [0,1], got " + score);
    }

    @Test
    void singleStatementCodeHandledGracefully() {
        String code = "class Foo { int x = 5; }";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score > 0.5,
                "Single-statement identical code should score high, got " + score);
    }

    @Test
    void minimalClassHandledGracefully() {
        String code = "class Empty {}";
        SimilarityEngine.Analysis a = analyze(code);
        SimilarityEngine.Analysis b = analyze(code);
        // Should produce valid Analysis even for minimal code
        assertNotNull(a.fpSet);
        assertNotNull(b.fpSet);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score >= 0.0 && score <= 1.0,
                "Minimal class should produce valid score, got " + score);
    }

    @Test
    void codeWithCommentsHandledGracefully() {
        String codeA = "class Foo {\n// TODO: fix this\nint x = 5;\n/* block comment */\nint y = 10;\n}";
        String codeB = "class Foo {\nint x = 5;\nint y = 10;\n}";
        SimilarityEngine.Analysis a = analyze(codeA);
        SimilarityEngine.Analysis b = analyze(codeB);
        // Comments are omitted (omitComments=true) → normalized forms should be similar
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(score > 0.5,
                "Code with/without comments should score high when comments omitted, got " + score);
    }

    @Test
    void codeWithDifferentFormattingHandledGracefully() {
        String codeA = "class Foo {\n    int x = 5;\n    int y = 10;\n}";
        String codeB = "class Foo { int x = 5; int y = 10; }";
        SimilarityEngine.Analysis a = analyze(codeA);
        SimilarityEngine.Analysis b = analyze(codeB);
        double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        // Whitespace normalization should make these equivalent
        assertTrue(score > 0.8,
                "Different formatting should not reduce similarity, got " + score);
    }

    // ==================== 6. Pipeline Integration — analyze() feeds hybridScore() ====================

    @Test
    void analyzeOutputFeedsJaccard() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        // Verify that analyze() produces fpSet that jaccard() consumes
        assertFalse(a.fpSet.isEmpty(), "Analysis A should have fingerprints");
        assertFalse(b.fpSet.isEmpty(), "Analysis B should have fingerprints");
        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        assertEquals(1.0, j, 1e-9, "Jaccard should be 1.0 for identical fingerprints");
    }

    @Test
    void analyzeOutputFeedsCoverage() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        // Verify that analyze() produces fps that coverage() consumes
        assertFalse(a.fps.isEmpty(), "Analysis A should have fingerprint list");
        assertFalse(b.fps.isEmpty(), "Analysis B should have fingerprint list");
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        assertEquals(1.0, c, 1e-9, "Coverage should be 1.0 for identical code");
    }

    @Test
    void analyzeOutputFeedsLCS() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        // Verify that analyze() produces symbolStream that lcsSimilarity() consumes
        assertFalse(a.symbolStream.isEmpty(), "Analysis A should have symbol stream");
        assertFalse(b.symbolStream.isEmpty(), "Analysis B should have symbol stream");
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        assertEquals(1.0, lcs, 1e-9, "LCS should be 1.0 for identical symbol streams");
    }

    @Test
    void analyzeOutputFeedsAST() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        // Verify that analyze() stores rawCode that astSimilarity() consumes
        assertNotNull(a.rawCode, "Analysis A should have rawCode");
        assertNotNull(b.rawCode, "Analysis B should have rawCode");
        double ast = SimilarityEngine.astSimilarity(a, b);
        assertEquals(1.0, ast, 1e-9, "AST should be 1.0 for identical raw code");
    }

    @Test
    void fullPipelineConsistencyForIdenticalCode() {
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);

        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);
        double hybrid = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());

        // All component scores should be 1.0 for identical code
        assertEquals(1.0, j, 1e-9, "Jaccard should be 1.0");
        assertEquals(1.0, c, 1e-9, "Coverage should be 1.0");
        assertEquals(1.0, lcs, 1e-9, "LCS should be 1.0");
        assertEquals(1.0, ast, 1e-9, "AST should be 1.0");

        // Hybrid: raw = 0.25*1 + 0.35*1 + 0.20*1 + 0.20*1 = 1.0
        // damping = min(1.0, 1.0/0.15) = 1.0
        // hybrid = 1.0
        assertEquals(1.0, hybrid, 1e-9, "Hybrid should be 1.0 for identical code");
    }

    // ==================== 7. Hybrid Score Consistency with Component Scores ====================

    @Test
    void hybridScoreConsistentWithComponentScores() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);

        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);
        double hybrid = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());

        // Manually compute expected hybrid
        double expectedRaw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        double damping = Math.min(1.0, j / 0.15);
        double expectedHybrid = expectedRaw * damping;

        assertEquals(expectedHybrid, hybrid, 1e-9,
                "Hybrid score should match manual computation from components");
    }

    @Test
    void hybridScoreConsistentForModifiedCode() {
        SimilarityEngine.Analysis a = analyze(FIND_MIN_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MIN_MODIFIED);

        double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
        double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
        double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
        double ast = SimilarityEngine.astSimilarity(a, b);
        double hybrid = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());

        double expectedRaw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        double damping = Math.min(1.0, j / 0.15);
        double expectedHybrid = expectedRaw * damping;

        assertEquals(expectedHybrid, hybrid, 1e-9,
                "Hybrid score should match manual computation for modified code");
    }

    @Test
    void hybridScoreClampedToUnitInterval() {
        // Test with all four code pairs — score must always be in [0, 1]
        String[][] pairs = {
            {ARRAY_SUM_A, ARRAY_SUM_B},
            {FIND_MAX_ORIGINAL, FIND_MAX_RENAMED},
            {FIND_MIN_ORIGINAL, FIND_MIN_MODIFIED},
            {ARRAY_SUM_A, STRING_CONCAT}
        };
        for (String[] pair : pairs) {
            SimilarityEngine.Analysis a = analyze(pair[0]);
            SimilarityEngine.Analysis b = analyze(pair[1]);
            double score = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
            assertTrue(score >= 0.0 && score <= 1.0,
                    "Hybrid score must be in [0,1], got " + score + " for pair: " +
                    pair[0].substring(0, 30) + " vs " + pair[1].substring(0, 30));
        }
    }

    @Test
    void dampingDoesNotAmplifyScore() {
        // For any pair, hybrid <= raw (damping can only reduce or maintain)
        String[][] pairs = {
            {ARRAY_SUM_A, ARRAY_SUM_B},
            {FIND_MAX_ORIGINAL, FIND_MAX_RENAMED},
            {FIND_MIN_ORIGINAL, FIND_MIN_MODIFIED},
            {ARRAY_SUM_A, STRING_CONCAT}
        };
        for (String[] pair : pairs) {
            SimilarityEngine.Analysis a = analyze(pair[0]);
            SimilarityEngine.Analysis b = analyze(pair[1]);
            double j = SimilarityEngine.jaccard(a, b, new HashMap<>());
            double c = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>());
            double lcs = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>());
            double ast = SimilarityEngine.astSimilarity(a, b);
            double raw = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
            double hybrid = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());
            assertTrue(hybrid <= raw + 1e-9,
                    "Hybrid should not exceed raw weighted sum (damping reduces), " +
                    "hybrid=" + hybrid + " > raw=" + raw);
        }
    }

    // ==================== 8. Production Configuration Verification ====================

    @Test
    void productionConfigOptionsValues() {
        assertEquals(true, PROD_OPTS.omitComments,
                "Production config should omit comments");
        assertEquals(6, PROD_OPTS.k,
                "Production k-gram size should be 6");
        assertEquals(4, PROD_OPTS.win,
                "Production winnowing window should be 4");
    }

    @Test
    void productionConfigKIsSix() {
        // Verify that k=6 is used in the pipeline
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        // With k=6, we need at least 6 tokens to produce any k-gram hashes
        // The array sum method has many more than 6 tokens
        assertFalse(a.fpSet.isEmpty(),
                "k=6 should produce fingerprints for a multi-statement method");
    }

    @Test
    void emptyWeightMapsAreUsedInProduction() {
        // The production hybridScore() call uses empty maps for fpWeights and stmtWeights
        // Verify this works correctly — empty maps → default weight 1.0 everywhere
        SimilarityEngine.Analysis a = analyze(ARRAY_SUM_A);
        SimilarityEngine.Analysis b = analyze(ARRAY_SUM_B);
        Map<Long, Double> emptyMap = new HashMap<>();

        // With empty maps, all weights default to 1.0
        double jWithEmpty = SimilarityEngine.jaccard(a, b, emptyMap);
        double jWithExplicit = SimilarityEngine.jaccard(a, b, Map.of());
        assertEquals(jWithEmpty, jWithExplicit, 1e-9,
                "Empty map and Map.of() should produce same Jaccard");

        double cWithEmpty = SimilarityEngine.coverage(a, b, PROD_OPTS.k, emptyMap);
        double cWithExplicit = SimilarityEngine.coverage(a, b, PROD_OPTS.k, Map.of());
        assertEquals(cWithEmpty, cWithExplicit, 1e-9,
                "Empty map and Map.of() should produce same Coverage");
    }

    // ==================== 9. Score Ordering Invariants ====================

    @Test
    void identicalCodeScoresHigherThanRenamedVariables() {
        double identicalScore = SimilarityEngine.hybridScore(
                analyze(ARRAY_SUM_A), analyze(ARRAY_SUM_B),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        double renamedScore = SimilarityEngine.hybridScore(
                analyze(FIND_MAX_ORIGINAL), analyze(FIND_MAX_RENAMED),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(identicalScore >= renamedScore,
                "Identical code (" + identicalScore + ") should score >= renamed variables (" + renamedScore + ")");
    }

    @Test
    void renamedVariablesScoreHigherThanModifiedCode() {
        double renamedScore = SimilarityEngine.hybridScore(
                analyze(FIND_MAX_ORIGINAL), analyze(FIND_MAX_RENAMED),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        double modifiedScore = SimilarityEngine.hybridScore(
                analyze(FIND_MIN_ORIGINAL), analyze(FIND_MIN_MODIFIED),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(renamedScore >= modifiedScore,
                "Renamed variables (" + renamedScore + ") should score >= modified code (" + modifiedScore + ")");
    }

    @Test
    void modifiedCodeScoresHigherThanUnrelatedCode() {
        double modifiedScore = SimilarityEngine.hybridScore(
                analyze(FIND_MIN_ORIGINAL), analyze(FIND_MIN_MODIFIED),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        double unrelatedScore = SimilarityEngine.hybridScore(
                analyze(ARRAY_SUM_A), analyze(STRING_CONCAT),
                PROD_OPTS.k, new HashMap<>(), new HashMap<>());
        assertTrue(modifiedScore >= unrelatedScore,
                "Modified code (" + modifiedScore + ") should score >= unrelated code (" + unrelatedScore + ")");
    }

    // ==================== 10. DetailedScore Integration ====================

    @Test
    void detailedScoreMatchesIndividualComponents() {
        SimilarityEngine.Analysis a = analyze(FIND_MAX_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MAX_RENAMED);
        SimilarityEngine.DetailedScore ds = SimilarityEngine.computeDetailedScore(
                a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());

        double expectedJ = SimilarityEngine.jaccard(a, b, new HashMap<>()) * 100.0;
        double expectedC = SimilarityEngine.coverage(a, b, PROD_OPTS.k, new HashMap<>()) * 100.0;
        double expectedL = SimilarityEngine.lcsSimilarity(a, b, new HashMap<>()) * 100.0;
        double expectedAst = SimilarityEngine.astSimilarity(a, b) * 100.0;
        double expectedH = SimilarityEngine.hybridScore(a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>()) * 100.0;

        assertEquals(expectedJ, ds.fingerprintScore, 1e-9);
        assertEquals(expectedC, ds.coverageScore, 1e-9);
        assertEquals(expectedL, ds.lcsScore, 1e-9);
        assertEquals(expectedAst, ds.astScore, 1e-9);
        assertEquals(expectedH, ds.hybridScore, 1e-9);
    }

    @Test
    void detailedScoreHybridIsWeightedAverage() {
        SimilarityEngine.Analysis a = analyze(FIND_MIN_ORIGINAL);
        SimilarityEngine.Analysis b = analyze(FIND_MIN_MODIFIED);
        SimilarityEngine.DetailedScore ds = SimilarityEngine.computeDetailedScore(
                a, b, PROD_OPTS.k, new HashMap<>(), new HashMap<>());

        // ds values are in percentage (0-100). Hybrid formula in percentage space:
        // raw = 0.25 * fpScore + 0.35 * covScore + 0.20 * lcsScore + 0.20 * astScore
        // damping = min(1.0, (fpScore/100) / 0.15)  — ratio-space damping
        // hybridScore = raw * damping
        double expectedRaw = 0.25 * ds.fingerprintScore + 0.35 * ds.coverageScore
                + 0.20 * ds.lcsScore + 0.20 * ds.astScore;
        double damping = Math.min(1.0, (ds.fingerprintScore / 100.0) / 0.15);
        double expectedHybrid = expectedRaw * damping;

        assertEquals(ds.hybridScore, expectedHybrid, 1e-6,
                "DetailedScore hybrid should match weighted formula");
    }
}
