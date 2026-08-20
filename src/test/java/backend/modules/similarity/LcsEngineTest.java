package backend.modules.similarity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for LcsEngine.
 * Verifies the LCS DP algorithm, similarity normalization, and weight handling.
 */
class LcsEngineTest {

    private static List<String> makeStream(String... tokens) {
        return Arrays.asList(tokens);
    }

    private static StatementGrouper.Statement makeStmt(int id, String... tokens) {
        List<String> toks = Arrays.asList(tokens);
        return new StatementGrouper.Statement(id, toks, StatementGrouper.hashTokens(toks));
    }

    // --- Convenience method: similarity(List<String>, List<String>) ---

    @Test
    void identicalStreamsProduceSimilarityOne() {
        List<String> stream = makeStream("KW(if)", "OP(;)", "KW(return)", "OP(;)");
        double sim = LcsEngine.similarity(stream, stream);
        assertEquals(1.0, sim, 1e-9);
    }

    @Test
    void emptyStreamsProduceZeroSimilarity() {
        double sim = LcsEngine.similarity(List.of(), List.of());
        assertEquals(0.0, sim, 1e-9);
    }

    @Test
    void oneEmptyStreamProducesZeroSimilarity() {
        List<String> stream = makeStream("KW(if)", "OP(;)");
        assertEquals(0.0, LcsEngine.similarity(stream, List.of()), 1e-9);
        assertEquals(0.0, LcsEngine.similarity(List.of(), stream), 1e-9);
    }

    @Test
    void nullStreamsProduceZeroSimilarity() {
        assertEquals(0.0, LcsEngine.similarity(null, List.of()), 1e-9);
        assertEquals(0.0, LcsEngine.similarity(List.of(), null), 1e-9);
    }

    @Test
    void completelyDifferentStreamsProduceLowSimilarity() {
        // Stream A: ["A", "OP(;)"]  → 1 statement
        // Stream B: ["B", "OP(;)"]  → 1 statement (different hash)
        List<String> streamA = makeStream("A", "OP(;)");
        List<String> streamB = makeStream("B", "OP(;)");
        double sim = LcsEngine.similarity(streamA, streamB);
        assertEquals(0.0, sim, 1e-9);
    }

    @Test
    void partialOverlapProducesExpectedSimilarity() {
        // Stream A: ["A", "OP(;)", "B", "OP(;)", "C", "OP(;)" ]  → 3 statements: A;, B;, C;
        // Stream B: ["A", "OP(;)", "X", "OP(;)", "C", "OP(;)" ]  → 3 statements: A;, X;, C;
        // LCS: A;, C; → length 2
        List<String> streamA = makeStream("A", "OP(;)", "B", "OP(;)", "C", "OP(;)");
        List<String> streamB = makeStream("A", "OP(;)", "X", "OP(;)", "C", "OP(;)");
        double sim = LcsEngine.similarity(streamA, streamB);
        // Auto-weights: both streams share A and C hashes
        // With 2 statements each matching and df=2 for A and C:
        //   weight(A) = log(1+2/2)/log(3) ≈ 0.6309
        //   weight(C) = log(1+2/2)/log(3) ≈ 0.6309
        //   weight(B) = log(3)/log(3) = 1.0 (not in B)
        //   weight(X) = log(3)/log(3) = 1.0 (not in A)
        // lcsWeight = weight(A) + weight(C) ≈ 1.2619
        // totalA = 0.6309 + 1.0 + 0.6309 = 2.2619
        // totalB = 0.6309 + 1.0 + 0.6309 = 2.2619
        // sim = 2 * 1.2619 / (2.2619 + 2.2619) ≈ 0.558
        assertTrue(sim > 0.4 && sim < 0.7,
                "Expected partial similarity between 0.4 and 0.7, got " + sim);
    }

    // --- compute() with manual weights ---

    @Test
    void computeWithUniformWeights() {
        // All statements have weight 1.0
        // A: [stmt0, stmt1]  B: [stmt0, stmt2]
        // LCS: stmt0 only → lcsWeight = 1
        // totalA = 2, totalB = 2
        // similarity = 2*1/(2+2) = 0.5
        Map<Long, Double> weights = new HashMap<>();

        StatementGrouper.Statement s0 = makeStmt(0, "A", "OP(;)");
        StatementGrouper.Statement s1 = makeStmt(1, "B", "OP(;)");
        StatementGrouper.Statement s2 = makeStmt(2, "C", "OP(;)");

        weights.put(s0.hash, 1.0);
        weights.put(s1.hash, 1.0);
        weights.put(s2.hash, 1.0);

        LcsEngine.LcsResult result = LcsEngine.compute(
                List.of(s0, s1),
                List.of(s0, s2),
                weights);

        assertEquals(1, result.lcsLength);
        assertEquals(0.5, result.similarity, 1e-9);
        assertEquals(1, result.matchedPairs.size());
        assertEquals(0, result.matchedPairs.get(0)[0]);
        assertEquals(0, result.matchedPairs.get(0)[1]);
    }

    @Test
    void computeAllStatementsMatch() {
        Map<Long, Double> weights = new HashMap<>();

        StatementGrouper.Statement s0 = makeStmt(0, "A", "OP(;)");
        StatementGrouper.Statement s1 = makeStmt(1, "B", "OP(;)");

        weights.put(s0.hash, 1.0);
        weights.put(s1.hash, 1.0);

        LcsEngine.LcsResult result = LcsEngine.compute(
                List.of(s0, s1),
                List.of(s0, s1),
                weights);

        assertEquals(2, result.lcsLength);
        assertEquals(1.0, result.similarity, 1e-9);
        assertEquals(2, result.matchedPairs.size());
    }

    @Test
    void computeNoStatementsMatch() {
        Map<Long, Double> weights = new HashMap<>();

        StatementGrouper.Statement s0 = makeStmt(0, "A", "OP(;)");
        StatementGrouper.Statement s1 = makeStmt(1, "B", "OP(;)");
        StatementGrouper.Statement s2 = makeStmt(2, "C", "OP(;)");
        StatementGrouper.Statement s3 = makeStmt(3, "D", "OP(;)");

        weights.put(s0.hash, 1.0);
        weights.put(s1.hash, 1.0);
        weights.put(s2.hash, 1.0);
        weights.put(s3.hash, 1.0);

        LcsEngine.LcsResult result = LcsEngine.compute(
                List.of(s0, s1),
                List.of(s2, s3),
                weights);

        assertEquals(0, result.lcsLength);
        assertEquals(0.0, result.similarity, 1e-9);
        assertTrue(result.matchedPairs.isEmpty());
    }

    @Test
    void computeEmptyStatements() {
        LcsEngine.LcsResult result = LcsEngine.compute(List.of(), List.of());
        assertEquals(0, result.lcsLength);
        assertEquals(0.0, result.similarity, 1e-9);
    }

    @Test
    void computeWithDifferentWeights() {
        // Verify that higher-weight statements contribute more to LCS
        Map<Long, Double> weights = new HashMap<>();

        StatementGrouper.Statement s0 = makeStmt(0, "COMMON", "OP(;)");
        StatementGrouper.Statement s1 = makeStmt(1, "UNIQUE_A", "OP(;)");
        StatementGrouper.Statement s2 = makeStmt(2, "UNIQUE_B", "OP(;)");

        weights.put(s0.hash, 2.0);
        weights.put(s1.hash, 1.0);
        weights.put(s2.hash, 1.0);

        LcsEngine.LcsResult result = LcsEngine.compute(
                List.of(s0, s1),
                List.of(s0, s2),
                weights);

        // LCS: s0 → lcsWeight = 2.0
        // totalA = 2.0 + 1.0 = 3.0
        // totalB = 2.0 + 1.0 = 3.0
        // similarity = 2*2.0 / (3.0+3.0) = 4/6 ≈ 0.6667
        assertEquals(2.0 / 3.0, result.similarity, 1e-9);
    }

    @Test
    void computeWeightedWithMismatch() {
        // A: [s0, s1]  B: [s0, s1]  but different weights
        // Verify that identical statements with weight 0 contribute nothing
        Map<Long, Double> weights = new HashMap<>();

        StatementGrouper.Statement s0 = makeStmt(0, "A", "OP(;)");
        StatementGrouper.Statement s1 = makeStmt(1, "B", "OP(;)");

        weights.put(s0.hash, 0.0);
        weights.put(s1.hash, 5.0);

        LcsEngine.LcsResult result = LcsEngine.compute(
                List.of(s0, s1),
                List.of(s0, s1),
                weights);

        // lcsWeight = 0.0 + 5.0 = 5.0
        // totalA = 0.0 + 5.0 = 5.0
        // totalB = 0.0 + 5.0 = 5.0
        // similarity = 2*5.0 / (5.0+5.0) = 1.0
        assertEquals(1.0, result.similarity, 1e-9);
    }
}
