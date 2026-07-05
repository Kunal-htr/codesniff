package backend;

import java.util.*;
import java.util.regex.*;

/**
 * Multi-stage code normalizer for plagiarism detection.
 * <p>
 * Transforms source code through a pipeline that strips structural boilerplate,
 * normalizes identifiers/literals/operators, and collapses equivalent constructs
 * (e.g. i++, ++i, i+=1, i=i+1) into canonical forms. This makes the downstream
 * tokenizer and fingerprint engine resilient to common obfuscation tricks such as
 * variable renaming, literal changes, loop-type swaps, and formatting changes.
 * <p>
 * Pipeline execution order (optimised for correctness):
 * <ol>
 *   <li>Remove package declarations</li>
 *   <li>Remove import statements</li>
 *   <li>Remove comments (when enabled)</li>
 *   <li>Normalize string and character literals  (early — protects string contents)</li>
 *   <li>Normalize increment/decrement expressions (before literals — needs raw '1')</li>
 *   <li>Normalize numeric literals</li>
 *   <li>Normalize boolean and null literals</li>
 *   <li>Normalize identifiers (type keywords → TYPE, user IDs → ID)</li>
 *   <li>Normalize loop keywords (for/while/do → LOOP)</li>
 *   <li>Normalize relational operators (→ ==, <, >)</li>
 *   <li>Normalize whitespace and formatting</li>
 * </ol>
 */
public class CodeNormalizer {

    /* ================================================================
     *  REGEX PATTERNS — compiled once, reused on every call
     * ================================================================ */

    // --- Comments (preserved from original implementation) ---
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT  = Pattern.compile("//.*?(\\r?\\n|$)");

    // --- Structural declarations ---
    private static final Pattern PACKAGE_DECL = Pattern.compile(
            "^\\s*package\\s+[\\w.]+\\s*;\\s*$", Pattern.MULTILINE);
    private static final Pattern IMPORT_STMT  = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?[\\w.*]+\\s*;\\s*$", Pattern.MULTILINE);

    // --- String and character literals ---
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern CHAR_LITERAL   = Pattern.compile("'(?:\\\\.|[^'\\\\])'");

    // --- Increment / Decrement patterns (order: longest match first) ---
    //  x = x + 1  (backreference ensures same variable on both sides)
    private static final Pattern SELF_ASSIGN_INCR = Pattern.compile(
            "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*\\1\\s*\\+\\s*1\\b");
    private static final Pattern SELF_ASSIGN_DECR = Pattern.compile(
            "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*\\1\\s*-\\s*1\\b");
    //  x += 1 / x -= 1
    private static final Pattern COMPOUND_INCR = Pattern.compile(
            "\\b[A-Za-z_$][A-Za-z0-9_$]*\\s*\\+=\\s*1\\b");
    private static final Pattern COMPOUND_DECR = Pattern.compile(
            "\\b[A-Za-z_$][A-Za-z0-9_$]*\\s*-=\\s*1\\b");
    //  x++ / ++x / x-- / --x
    private static final Pattern POST_INCR = Pattern.compile(
            "\\b[A-Za-z_$][A-Za-z0-9_$]*\\+\\+");
    private static final Pattern PRE_INCR  = Pattern.compile(
            "\\+\\+[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final Pattern POST_DECR = Pattern.compile(
            "\\b[A-Za-z_$][A-Za-z0-9_$]*--");
    private static final Pattern PRE_DECR  = Pattern.compile(
            "--[A-Za-z_$][A-Za-z0-9_$]*\\b");

    // --- Numeric literals (float before int to prevent partial matches) ---
    private static final Pattern FLOAT_LITERAL = Pattern.compile(
            "\\b\\d+\\.\\d+(?:[eE][+-]?\\d+)?[fFdD]?\\b"  // 3.14, 3.14f, 3.14e10
          + "|\\b\\d+[eE][+-]?\\d+[fFdD]?\\b"               // 3e10
          + "|\\b\\d+[fFdD]\\b");                             // 3f, 3d
    private static final Pattern HEX_LITERAL = Pattern.compile(
            "\\b0[xX][0-9a-fA-F]+[lL]?\\b");
    private static final Pattern BINARY_LITERAL = Pattern.compile(
            "\\b0[bB][01]+[lL]?\\b");
    private static final Pattern INT_LITERAL = Pattern.compile(
            "\\b\\d+[lL]?\\b");

    // --- Boolean and null ---
    private static final Pattern BOOLEAN_LITERAL = Pattern.compile("\\b(?:true|false)\\b");
    private static final Pattern NULL_LITERAL    = Pattern.compile("\\bnull\\b");

    // --- Identifiers ---
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");

    // --- Loop keywords ---
    private static final Pattern LOOP_KEYWORD = Pattern.compile("\\b(?:for|while|do)\\b");

    // --- Relational operators (two-char first; single-char guarded against <<, >>, ->) ---
    private static final Pattern RELATIONAL_OP = Pattern.compile(
            "(?<eq>==|!=)"
          + "|(?<lt><=|(?<![<])<(?![<=]))"
          + "|(?<gt>>=|(?<![->])>(?![>=]))"
    );

    // --- Whitespace ---
    private static final Pattern MULTI_SPACES   = Pattern.compile("[ \\t\\f]+");
    private static final Pattern BLANK_LINES    = Pattern.compile("(\\s*\\n){2,}");
    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    /* ================================================================
     *  KEYWORD & TOKEN SETS
     * ================================================================ */

    /** Primitive types and common wrapper/reference types → TYPE */
    private static final Set<String> TYPE_KEYWORDS = Set.of(
            "int", "long", "short", "byte", "float", "double", "char", "boolean", "void", "var",
            "String", "Integer", "Long", "Short", "Byte", "Float", "Double",
            "Character", "Boolean", "Object"
    );

    /** Full set of Java reserved keywords (preserved as-is during identifier normalisation) */
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "record", "var"
    );

    /** Tokens injected by earlier pipeline stages — must not be re-normalised to ID */
    private static final Set<String> NORMALIZED_TOKENS = Set.of(
            "TYPE", "ID", "NUM", "FLOAT", "STRING", "CHAR", "BOOL", "NULL",
            "LOOP", "INCR", "DECR"
    );

    /* ================================================================
     *  PUBLIC API  (signature unchanged — backward compatible)
     * ================================================================ */

    /**
     * Normalise source code through the full pipeline.
     *
     * @param code         raw source code
     * @param omitComments if true, strip all comments before normalisation
     * @return normalised, structurally abstracted text
     */
    public static String normalize(String code, boolean omitComments) {
        if (code == null) return "";
        String s = code;

        // Stage 1 — Remove package declarations
        s = removePackageDeclarations(s);

        // Stage 2 — Remove import statements
        s = removeImportStatements(s);

        // Stage 3 — Remove comments (existing behaviour)
        if (omitComments) {
            s = removeComments(s);
        }

        // Stage 4 — Normalize string and character literals
        //           (early: protects string contents from subsequent stages)
        s = normalizeStringAndCharLiterals(s);

        // Stage 5 — Normalize increment/decrement expressions
        //           (before literals: needs raw '1' for matching;
        //            before identifiers: needs original names for backreference)
        s = normalizeIncrementDecrement(s);

        // Stage 6 — Normalize numeric literals
        s = normalizeNumericLiterals(s);

        // Stage 7 — Normalize boolean and null literals
        s = normalizeBooleanAndNullLiterals(s);

        // Stage 8 — Normalize identifiers
        //           (type keywords → TYPE, user identifiers → ID, Java keywords preserved)
        s = normalizeIdentifiers(s);

        // Stage 9 — Normalize loop keywords (for/while/do → LOOP)
        s = normalizeLoopKeywords(s);

        // Stage 10 — Normalize relational operators (→ ==, <, >)
        s = normalizeRelationalOperators(s);

        // Stage 11 — Normalize whitespace and formatting (final cleanup)
        s = normalizeWhitespace(s);

        return s;
    }

    /* ================================================================
     *  STAGE IMPLEMENTATIONS  (private helpers)
     * ================================================================ */

    /** Stage 1 — Strip {@code package ...;} lines. */
    private static String removePackageDeclarations(String s) {
        return PACKAGE_DECL.matcher(s).replaceAll("");
    }

    /** Stage 2 — Strip {@code import ...;} and {@code import static ...;} lines. */
    private static String removeImportStatements(String s) {
        return IMPORT_STMT.matcher(s).replaceAll("");
    }

    /** Stage 3 — Strip block and line comments (preserves newlines for line tracking). */
    private static String removeComments(String s) {
        s = BLOCK_COMMENT.matcher(s).replaceAll("\n");
        s = LINE_COMMENT.matcher(s).replaceAll("\n");
        return s;
    }

    /** Stage 4 — Replace string literals with STRING, char literals with CHAR. */
    private static String normalizeStringAndCharLiterals(String s) {
        s = STRING_LITERAL.matcher(s).replaceAll("STRING");
        s = CHAR_LITERAL.matcher(s).replaceAll("CHAR");
        return s;
    }

    /**
     * Stage 5 — Normalize all increment/decrement forms to canonical INCR / DECR tokens.
     * <p>
     * Self-assignment forms ({@code x = x + 1}) are matched first using backreferences
     * to ensure both sides reference the same variable. Compound forms ({@code x += 1})
     * and unary forms ({@code x++}, {@code ++x}) follow.
     */
    private static String normalizeIncrementDecrement(String s) {
        // Self-assignment: x = x + 1 / x = x - 1  (backreference ensures same variable)
        s = SELF_ASSIGN_INCR.matcher(s).replaceAll("INCR");
        s = SELF_ASSIGN_DECR.matcher(s).replaceAll("DECR");

        // Compound assignment: x += 1 / x -= 1
        s = COMPOUND_INCR.matcher(s).replaceAll("INCR");
        s = COMPOUND_DECR.matcher(s).replaceAll("DECR");

        // Unary: x++ / ++x / x-- / --x
        s = POST_INCR.matcher(s).replaceAll("INCR");
        s = PRE_INCR.matcher(s).replaceAll("INCR");
        s = POST_DECR.matcher(s).replaceAll("DECR");
        s = PRE_DECR.matcher(s).replaceAll("DECR");

        return s;
    }

    /**
     * Stage 6 — Normalize numeric literals.
     * Float/scientific patterns are replaced before integer patterns to prevent
     * partial matches (e.g. {@code 3.14} must not become {@code NUM.NUM}).
     */
    private static String normalizeNumericLiterals(String s) {
        s = FLOAT_LITERAL.matcher(s).replaceAll("FLOAT");
        s = HEX_LITERAL.matcher(s).replaceAll("NUM");
        s = BINARY_LITERAL.matcher(s).replaceAll("NUM");
        s = INT_LITERAL.matcher(s).replaceAll("NUM");
        return s;
    }

    /** Stage 7 — Replace boolean literals with BOOL and null with NULL. */
    private static String normalizeBooleanAndNullLiterals(String s) {
        s = BOOLEAN_LITERAL.matcher(s).replaceAll("BOOL");
        s = NULL_LITERAL.matcher(s).replaceAll("NULL");
        return s;
    }

    /**
     * Stage 8 — Normalize identifiers.
     * <ul>
     *   <li>Already-normalised tokens (INCR, STRING, NUM …) → preserved</li>
     *   <li>Type keywords (int, String, Object …) → TYPE</li>
     *   <li>Java keywords (for, if, class …) → preserved as-is</li>
     *   <li>Everything else (user-defined names) → ID</li>
     * </ul>
     */
    private static String normalizeIdentifiers(String s) {
        return IDENTIFIER.matcher(s).replaceAll(mr -> {
            String word = mr.group();
            if (NORMALIZED_TOKENS.contains(word)) return word;   // keep injected tokens
            if (TYPE_KEYWORDS.contains(word))     return "TYPE"; // int, String, etc.
            if (JAVA_KEYWORDS.contains(word))     return word;   // for, if, class, etc.
            return "ID";                                          // user identifier
        });
    }

    /** Stage 9 — Replace loop keywords (for, while, do) with LOOP. */
    private static String normalizeLoopKeywords(String s) {
        return LOOP_KEYWORD.matcher(s).replaceAll("LOOP");
    }

    /**
     * Stage 10 — Replace relational/comparison operators with ==, <, or >.
     * <p>
     * Two-character operators ({@code <=, >=, ==, !=}) are matched first.
     * Single-character {@code <} and {@code >} are guarded by lookahead/lookbehind
     * to avoid matching shift operators ({@code <<, >>, >>>}) or lambdas ({@code ->}).
     * Spaces are padded around the operators to prevent token merging; cleaned up in Stage 11.
     */
    private static String normalizeRelationalOperators(String s) {
        return RELATIONAL_OP.matcher(s).replaceAll(mr -> " " + mr.group() + " ");
    }

    /**
     * Stage 11 — Final whitespace normalisation.
     * Normalises line endings, strips trailing whitespace, collapses blank lines
     * and multi-space runs, and trims the result.
     */
    private static String normalizeWhitespace(String s) {
        // Normalise unicode non-breaking spaces
        s = s.replace('\u00A0', ' ');
        // Normalise line endings to LF
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        // Remove trailing whitespace per line
        s = TRAILING_SPACE.matcher(s).replaceAll("");
        // Collapse consecutive blank lines into a single newline
        s = BLANK_LINES.matcher(s).replaceAll("\n");
        // Collapse inline whitespace runs to a single space
        s = MULTI_SPACES.matcher(s).replaceAll(" ");
        return s.trim();
    }
}
