package backend;

import java.util.*;
import java.util.stream.Collectors;

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
        public Analysis(Set<Long> fpSet, List<Fingerprint> fps, int tokenCount) {
            this.fpSet = fpSet; this.fps = fps; this.tokenCount = tokenCount;
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
        return new Analysis(set, fps, stream.size());
    }

    public static double jaccard(Analysis a, Analysis b) {
        if (a.fpSet.isEmpty() && b.fpSet.isEmpty()) return 1.0;
        int inter = 0;
        for (Long h : a.fpSet) if (b.fpSet.contains(h)) inter++;
        int union = a.fpSet.size() + b.fpSet.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    public static double coverage(Analysis a, Analysis b, int k) {
        // Coverage = matched k-gram tokens / min(totalTokensA, totalTokensB)
        Set<Long> common = new HashSet<>(a.fpSet);
        common.retainAll(b.fpSet);
        long matchedKTokens = (long) common.size() * k;
        int denom = Math.max(1, Math.min(a.tokenCount, b.tokenCount));
        double cov = Math.min(1.0, matchedKTokens / (double) denom);
        return cov;
    }

    /** Hybrid score: weighted mean with diminishing returns. Tunable. */
    public static double hybridScore(Analysis a, Analysis b, int k) {
        double j = jaccard(a, b);
        double c = coverage(a, b, k);
        // Slightly reward coverage more; clamp
        double s = 0.45 * j + 0.55 * c;
        return Math.max(0.0, Math.min(1.0, s));
    }

    /* ===== Internals ===== */

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
        int n = hashes.size();
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

    /* Convenience: compare two source strings with options */
    public static double compare(String aCode, String bCode, Options opt) {
        Analysis a = analyze(aCode, opt);
        Analysis b = analyze(bCode, opt);
        return hybridScore(a, b, opt.k);
    }
}
