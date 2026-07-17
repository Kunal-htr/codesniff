package backend.analysis;

import java.util.*;

/**
 * Longest Common Subsequence engine operating on statement-level sequences.
 * <p>
 * Uses dynamic programming to find the longest subsequence of matching statements
 * between two code fragments. Statement equality is determined by hash comparison
 * (two statements are "equal" if their normalized token sequences hash identically).
 * <p>
 * Statement-level LCS is preferred over token-level because a single inserted
 * statement costs exactly 1 mismatch instead of corrupting dozens of token-level
 * matches. Typical Java methods have 10–30 statements, so the DP table is small
 * (≈ 300–900 cells).
 */
public class LcsEngine {

    /**
     * Result of an LCS computation.
     *
     * @param lcsLength    number of statements in the LCS
     * @param similarity   normalised similarity (0.0–1.0), computed as
     *                     {@code 2 * lcsLength / (lenA + lenB)}
     * @param matchedPairs list of {@code [indexA, indexB]} pairs identifying
     *                     which statements in A align with which statements in B
     */
    public static final class LcsResult {
        public final int lcsLength;
        public final double similarity;
        public final List<int[]> matchedPairs;

        public LcsResult(int lcsLength, double similarity, List<int[]> matchedPairs) {
            this.lcsLength = lcsLength;
            this.similarity = similarity;
            this.matchedPairs = matchedPairs;
        }
    }

    /**
     * Compute the LCS between two statement sequences.
     * <p>
     * Statements are considered equal if their hash fingerprints match
     * (see {@link StatementGrouper.Statement#hash}).
     *
     * @param stmtsA statements from code A
     * @param stmtsB statements from code B
     * @return LCS result with length, normalised similarity, and matched index pairs
     */
    public static LcsResult compute(List<StatementGrouper.Statement> stmtsA,
                                     List<StatementGrouper.Statement> stmtsB) {
        if (stmtsA == null || stmtsB == null || stmtsA.isEmpty() || stmtsB.isEmpty()) {
            return new LcsResult(0, 0.0, List.of());
        }

        // Fallback for pairwise use case: compute pairwise weights (N=2)
        Map<Long, Double> weights = new HashMap<>();
        double log3 = Math.log(3.0);
        Set<Long> setB = new HashSet<>();
        for (var s : stmtsB) setB.add(s.hash);
        for (var s : stmtsA) {
            int df = setB.contains(s.hash) ? 2 : 1;
            weights.put(s.hash, Math.log(1.0 + 2.0 / df) / log3);
        }
        for (var s : stmtsB) {
            if (!weights.containsKey(s.hash)) {
                weights.put(s.hash, Math.log(1.0 + 2.0 / 1) / log3);
            }
        }
        return compute(stmtsA, stmtsB, weights);
    }

    /**
     * Compute the LCS between two statement sequences using externally supplied weights.
     */
    public static LcsResult compute(List<StatementGrouper.Statement> stmtsA,
                                     List<StatementGrouper.Statement> stmtsB,
                                     Map<Long, Double> weights) {
        if (stmtsA == null || stmtsB == null || stmtsA.isEmpty() || stmtsB.isEmpty()) {
            return new LcsResult(0, 0.0, List.of());
        }

        int n = stmtsA.size();
        int m = stmtsB.size();

        // DP table: dp[i][j] = LCS weight sum of matching statements
        double[][] dp = new double[n + 1][m + 1];

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (stmtsA.get(i - 1).hash == stmtsB.get(j - 1).hash) {
                    double w = weights.getOrDefault(stmtsA.get(i - 1).hash, 1.0);
                    dp[i][j] = dp[i - 1][j - 1] + w;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack to recover the matched pairs
        List<int[]> pairs = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (stmtsA.get(i - 1).hash == stmtsB.get(j - 1).hash) {
                pairs.add(new int[]{i - 1, j - 1});
                i--;
                j--;
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        Collections.reverse(pairs); // restore chronological order

        double lcsWeight = dp[n][m];

        // Sum weights of all statements in A and B
        double totalWeightA = 0.0;
        for (var s : stmtsA) totalWeightA += weights.getOrDefault(s.hash, 1.0);
        double totalWeightB = 0.0;
        for (var s : stmtsB) totalWeightB += weights.getOrDefault(s.hash, 1.0);

        double sim = (totalWeightA + totalWeightB) == 0.0 ? 0.0 : (2.0 * lcsWeight) / (totalWeightA + totalWeightB);

        return new LcsResult((int) Math.round(lcsWeight), sim, pairs);
    }

    /**
     * Convenience method: compute LCS similarity directly from symbol streams.
     * Internally groups each stream into statements, then runs the DP algorithm.
     *
     * @param streamA symbol stream from code A
     * @param streamB symbol stream from code B
     * @return normalised similarity 0.0–1.0
     */
    public static double similarity(List<String> streamA, List<String> streamB) {
        var stmtsA = StatementGrouper.groupStatements(streamA);
        var stmtsB = StatementGrouper.groupStatements(streamB);
        return compute(stmtsA, stmtsB).similarity;
    }

    /**
     * Convenience method: compute LCS similarity directly from symbol streams using custom weights.
     */
    public static double similarity(List<String> streamA, List<String> streamB, Map<Long, Double> weights) {
        var stmtsA = StatementGrouper.groupStatements(streamA);
        var stmtsB = StatementGrouper.groupStatements(streamB);
        return compute(stmtsA, stmtsB, weights).similarity;
    }
}
