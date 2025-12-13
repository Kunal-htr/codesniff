package backend;

import java.util.regex.*;

public class CodeNormalizer {
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT  = Pattern.compile("//.*?(\\r?\\n|$)");
    private static final Pattern WHITESPACE    = Pattern.compile("[ \\t\\f\\r\\n]+");

    public static String normalize(String code, boolean omitComments) {
        if (code == null) return "";
        String s = code;
        if (omitComments) {
            s = BLOCK_COMMENT.matcher(s).replaceAll("\n");
            s = LINE_COMMENT.matcher(s).replaceAll("\n");
        }
        // normalize unicode whitespace & collapse runs
        s = s.replace('\u00A0', ' ');
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }
}
