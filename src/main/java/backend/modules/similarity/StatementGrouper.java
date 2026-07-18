package backend.modules.similarity;

import java.util.*;

/**
 * Groups a flat symbol stream into logical Java statements.
 * <p>
 * Each statement is a sequence of consecutive tokens delimited by {@code ;},
 * {@code &#123;}, or {@code &#125;}. The delimiter is included as the last token of
 * the statement it terminates. Each statement receives a deterministic FNV hash
 * of its token content for O(1) equality comparison in the LCS algorithm.
 */
public class StatementGrouper {

    /**
     * A logical statement: a group of consecutive tokens with a hash fingerprint.
     *
     * @param id     sequential statement identifier
     * @param tokens the symbol-stream tokens making up this statement (immutable)
     * @param hash   deterministic FNV-1a hash of the token sequence
     */
    public static final class Statement {
        public final int id;
        public final List<String> tokens;
        public final long hash;

        public Statement(int id, List<String> tokens, long hash) {
            this.id = id;
            this.tokens = tokens;
            this.hash = hash;
        }

        @Override
        public String toString() {
            return "Stmt#" + id + tokens;
        }
    }

    /** Tokens in the symbol stream that mark statement boundaries. */
    private static final Set<String> DELIMITERS = Set.of("OP(;)", "OP({)", "OP(})");

    /**
     * Split a symbol stream into logical statements.
     *
     * @param symbolStream flat token list produced by {@code Tokenizer.toSymbolStream()}
     * @return ordered list of statements; empty list if input is null/empty
     */
    public static List<Statement> groupStatements(List<String> symbolStream) {
        List<Statement> statements = new ArrayList<>();
        if (symbolStream == null || symbolStream.isEmpty()) return statements;

        List<String> current = new ArrayList<>();
        int stmtId = 0;

        for (String token : symbolStream) {
            current.add(token);

            if (DELIMITERS.contains(token)) {
                // Only emit if there's meaningful content (more than just the delimiter,
                // or a lone brace which is structurally significant)
                if (!current.isEmpty()) {
                    statements.add(new Statement(stmtId++, List.copyOf(current), hashTokens(current)));
                    current.clear();
                }
            }
        }

        // Emit any trailing tokens that weren't terminated by a delimiter
        if (!current.isEmpty()) {
            statements.add(new Statement(stmtId++, List.copyOf(current), hashTokens(current)));
        }

        return statements;
    }

    /**
     * Compute a deterministic FNV-1a hash of a token sequence.
     * Uses byte-level mixing with a separator between tokens to avoid
     * collisions between different token boundaries (e.g., "AB"+"C" vs "A"+"BC").
     */
    static long hashTokens(List<String> tokens) {
        long h = 1469598103934665603L; // FNV offset basis
        for (String t : tokens) {
            for (int i = 0; i < t.length(); i++) {
                h ^= t.charAt(i);
                h *= 1099511628211L; // FNV prime
            }
            h ^= 0xFF; // separator between tokens
            h *= 1099511628211L;
        }
        return h;
    }
}
