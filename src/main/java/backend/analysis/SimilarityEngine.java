package backend.analysis;

import java.util.*;
import java.util.stream.Collectors;
import backend.ast.*;

public class SimilarityEngine {

    public static final class Options {
        public final boolean omitComments;
        public final int k;         // k-gram size
        public final int win;       // winnowing window
        public Options(boolean omitComments, int k, int win) {
            if (k < 3 || k > 64) throw new IllegalArgumentException("k out of range");
            if (win < 1 || win > 128) throw new IllegalArgumentException("window out of range");
            this.omitComments = omitComments; this.k = k; this.win = win;
        }
    }

    public static final class Fingerprint {
        public final long hash;
        public final int pos; // starting token index
        public Fingerprint(long hash, int pos){ this.hash = hash; this.pos = pos; }
    }

    public static final class Analysis {
        public final Set<Long> fpSet;
        public final List<Fingerprint> fps;
        public final int tokenCount;
        public final List<String> symbolStream;
        public final String normalizedCode;
        public final String rawCode;

        public Analysis(Set<Long> fpSet, List<Fingerprint> fps, int tokenCount, List<String> symbolStream, String normalizedCode, String rawCode) {
            this.fpSet = fpSet;
            this.fps = fps;
            this.tokenCount = tokenCount;
            this.symbolStream = symbolStream;
            this.normalizedCode = normalizedCode;
            this.rawCode = rawCode;
        }
    }

    /* ===== Pipeline ===== */

    public static Analysis analyze(String code, Options opt) {
        String norm = CodeNormalizer.normalize(code, opt.omitComments);
        var toks = Tokenizer.tokenize(norm);
        var stream = Tokenizer.toSymbolStream(toks);

        // Build k-grams
        List<Long> hashes = kgramHashes(stream, opt.k);

        // Winnow
        List<Fingerprint> fps = winnow(hashes, opt.win);

        // Set for Jaccard
        Set<Long> set = fps.stream().map(f -> f.hash).collect(Collectors.toSet());
        return new Analysis(set, fps, stream.size(), stream, norm, code);
    }

    public static double jaccard(Analysis a, Analysis b, Map<Long, Double> weights) {
        if (a.fpSet.isEmpty() && b.fpSet.isEmpty()) return 1.0;
        double interWeight = 0.0;
        for (Long h : a.fpSet) {
            if (b.fpSet.contains(h)) {
                interWeight += weights.getOrDefault(h, 1.0);
            }
        }
        double totalA = 0.0;
        for (Long h : a.fpSet) totalA += weights.getOrDefault(h, 1.0);
        double totalB = 0.0;
        for (Long h : b.fpSet) totalB += weights.getOrDefault(h, 1.0);
        
        double unionWeight = totalA + totalB - interWeight;
        return unionWeight == 0.0 ? 0.0 : interWeight / unionWeight;
    }

    public static double coverage(Analysis a, Analysis b, int k, Map<Long, Double> weights) {
        Set<Long> common = new HashSet<>(a.fpSet);
        common.retainAll(b.fpSet);
        if (common.isEmpty()) return 0.0;
        
        double coveredA = computeWeightedCoveredTokens(a.fps, common, k, weights);
        double coveredB = computeWeightedCoveredTokens(b.fps, common, k, weights);
        
        double totalWeightA = computeWeightedCoveredTokens(a.fps, a.fpSet, k, weights);
        double totalWeightB = computeWeightedCoveredTokens(b.fps, b.fpSet, k, weights);
        
        double minTotalWeight = Math.min(totalWeightA, totalWeightB);
        if (minTotalWeight <= 0.0) return 0.0;
        
        return Math.min(1.0, Math.min(coveredA, coveredB) / minTotalWeight);
    }

    private static double computeWeightedCoveredTokens(List<Fingerprint> fps, Set<Long> hashSet, int k, Map<Long, Double> weights) {
        if (fps == null || fps.isEmpty() || hashSet.isEmpty()) return 0.0;
        Map<Integer, Double> tokenWeights = new HashMap<>();
        for (Fingerprint f : fps) {
            if (hashSet.contains(f.hash)) {
                double w = weights.getOrDefault(f.hash, 1.0);
                for (int i = 0; i < k; i++) {
                    int pos = f.pos + i;
                    double currentMax = tokenWeights.getOrDefault(pos, 0.0);
                    if (w > currentMax) {
                        tokenWeights.put(pos, w);
                    }
                }
            }
        }
        double sum = 0.0;
        for (double val : tokenWeights.values()) {
            sum += val;
        }
        return sum;
    }

    /** Compute Longest Common Subsequence (LCS) similarity on statement-level token streams using custom weights. */
    public static double lcsSimilarity(Analysis a, Analysis b, Map<Long, Double> stmtWeights) {
        return LcsEngine.similarity(a.symbolStream, b.symbolStream, stmtWeights);
    }

    /** Compute Abstract Syntax Tree (AST) structural similarity. */
    public static double astSimilarity(Analysis a, Analysis b) {
        ASTNode treeA = ASTBuilder.build(a.rawCode);
        ASTNode treeB = ASTBuilder.build(b.rawCode);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        return result.getSimilarity();
    }

    /** Hybrid score: 25% Fingerprint (Jaccard) + 35% Coverage + 20% LCS + 20% AST with Jaccard-based damping. */
    public static double hybridScore(Analysis a, Analysis b, int k, Map<Long, Double> fpWeights, Map<Long, Double> stmtWeights) {
        double j = jaccard(a, b, fpWeights);
        double c = coverage(a, b, k, fpWeights);
        double lcs = lcsSimilarity(a, b, stmtWeights);
        double ast = astSimilarity(a, b);
        double s = 0.25 * j + 0.35 * c + 0.20 * lcs + 0.20 * ast;
        
        // Soft-damping factor based on Jaccard to prevent low Jaccard masking
        double damping = Math.min(1.0, j / 0.15); // fully active at 15% Jaccard
        double finalScore = s * damping;
        
        return Math.max(0.0, Math.min(1.0, finalScore));
    }

    /** Detailed similarity results for fine-grained reporting. */
    public static final class DetailedScore {
        public final double fingerprintScore;
        public final double coverageScore;
        public final double lcsScore;
        public final double astScore;
        public final double hybridScore;

        public DetailedScore(double fingerprintScore, double coverageScore, double lcsScore, double astScore, double hybridScore) {
            this.fingerprintScore = fingerprintScore;
            this.coverageScore = coverageScore;
            this.lcsScore = lcsScore;
            this.astScore = astScore;
            this.hybridScore = hybridScore;
        }

        @Override
        public String toString() {
            return String.format("DetailedScore[FP=%.1f%%, COV=%.1f%%, LCS=%.1f%%, AST=%.1f%%, HYB=%.1f%%]",
                    fingerprintScore, coverageScore, lcsScore, astScore, hybridScore);
        }
    }

    /** Helper to compute a detailed breakdown of all similarity metrics using custom weights. */
    public static DetailedScore computeDetailedScore(Analysis a, Analysis b, int k, Map<Long, Double> fpWeights, Map<Long, Double> stmtWeights) {
        double j = jaccard(a, b, fpWeights) * 100.0;
        double c = coverage(a, b, k, fpWeights) * 100.0;
        double lcs = lcsSimilarity(a, b, stmtWeights) * 100.0;
        double ast = astSimilarity(a, b) * 100.0;
        double hybrid = hybridScore(a, b, k, fpWeights, stmtWeights) * 100.0;
        return new DetailedScore(j, c, lcs, ast, hybrid);
    }

    // Simple Rabin-Karp rolling hash across k tokens (base B, mod 2^64 via overflow)
    private static List<Long> kgramHashes(List<String> sym, int k) {
        List<Long> out = new ArrayList<>();
        if (sym.size() < k) return out;
        final long B = 1_000_003L;

        // precompute bases
        long pow = 1;
        for (int i=0;i<k-1;i++) pow = pow * B;

        // initial hash
        long h = 0;
        for (int i=0;i<k;i++) h = h * B + mix(sym.get(i));

        out.add(h);
        for (int i=k;i<sym.size();i++) {
            h = h - pow * mix(sym.get(i-k));
            h = h * B + mix(sym.get(i));
            out.add(h);
        }
        return out;
    }

    // stable mixing to 64-bit-ish value
    private static long mix(String s) {
        long x = 1469598103934665603L; // FNV offset
        for (int i=0;i<s.length();i++) {
            x ^= s.charAt(i);
            x *= 1099511628211L;
        }
        return x;
    }

    // Winnowing: choose minimum hash in each window; if ties, choose rightmost minimum (stable)
    private static List<Fingerprint> winnow(List<Long> hashes, int w) {
        List<Fingerprint> out = new ArrayList<>();
        if (hashes.isEmpty()) return out;
        int win = Math.max(1, w);
        long best = Long.MAX_VALUE;
        int bestPos = -1;

        for (int i=0; i<hashes.size(); i++) {
            long h = hashes.get(i);
            // Slide window start
            int start = Math.max(0, i - win + 1);
            if (bestPos < start) { // expired, recompute min in window
                best = Long.MAX_VALUE; bestPos = -1;
                for (int j=start; j<=i; j++) {
                    long hj = hashes.get(j);
                    if (hj <= best) { best = hj; bestPos = j; } // rightmost on tie
                }
                out.add(new Fingerprint(best, bestPos));
            } else if (h <= best) {
                // new rightmost minimum
                best = h; bestPos = i;
                out.add(new Fingerprint(best, bestPos));
            }
        }
        // dedupe consecutive identical picks
        List<Fingerprint> compact = new ArrayList<>();
        Fingerprint prev = null;
        for (Fingerprint f : out) {
            if (prev == null || f.hash != prev.hash || f.pos != prev.pos) compact.add(f);
            prev = f;
        }
        return compact;
    }
}
