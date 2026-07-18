package backend.analysis;

import java.util.*;
import java.util.regex.*;

public class Tokenizer {

    public enum Tok {
        KW,        // keyword
        ID,        // identifier
        NUM,       // number literal
        STR,       // string/char literal
        OP,        // operator or punct
    }

    private static final Set<String> JAVA_KW = Set.of(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const","continue",
        "default","do","double","else","enum","extends","final","finally","float","for","goto",
        "if","implements","import","instanceof","int","interface","long","native","new","package",
        "private","protected","public","return","short", "static","strictfp","super","switch","synchronized",
        "this","throw","throws","transient","try","void","volatile","while","record","var"
    );

    private static final Pattern TOKENS = Pattern.compile(
        "(?<id>[A-Za-z_$][A-Za-z0-9_$]*)"
      + "|(?<num>\\d+(?:_\\d+)*(?:\\.\\d+(?:_\\d+)*)?(?:[eE][+-]?\\d+)?[fFdDlL]?)"
      + "|(?<str>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
      + "|(?<op>==|!=|<=|>=|&&|\\|\\||\\+\\+|--|<<|>>>|>>|::|[+\\-*/%&|^~!?<>=.:;(),\\[\\]{}])"
      + "|(?<ws>\\s+)"
      , Pattern.DOTALL);

    public static final class Token {
        public final Tok type;
        public final String text;   // normalized text
        public final int index;     // sequential index
        public Token(Tok type, String text, int index) {
            this.type = type; this.text = text; this.index = index;
        }
        @Override public String toString(){ return type+":"+text; }
    }

    public static List<Token> tokenize(String normalized) {
        List<Token> out = new ArrayList<>();
        if (normalized == null || normalized.isEmpty()) return out;
        Matcher m = TOKENS.matcher(normalized);
        int idx = 0;
        while (m.find()) {
            if (m.group("ws") != null) continue;
            if (m.group("id") != null) {
                String raw = m.group("id");
                if (JAVA_KW.contains(raw)) out.add(new Token(Tok.KW, raw, idx++));
                else out.add(new Token(Tok.ID, "ID", idx++));
            } else if (m.group("num") != null) {
                out.add(new Token(Tok.NUM, "NUM", idx++));
            } else if (m.group("str") != null) {
                out.add(new Token(Tok.STR, "STR", idx++));
            } else if (m.group("op") != null) {
                out.add(new Token(Tok.OP, m.group("op"), idx++));
            }
        }
        return out;
    }

    /** Convert tokens to a compact string for k-gramming, e.g., "KW class ID { ..." */
    public static List<String> toSymbolStream(List<Token> toks) {
        List<String> s = new ArrayList<>(toks.size());
        for (Token t : toks) {
            switch (t.type) {
                case KW -> s.add("KW(" + t.text + ")");
                case OP -> s.add("OP(" + t.text + ")");
                case ID -> s.add("ID");
                case NUM -> s.add("NUM");
                case STR -> s.add("STR");
            }
        }
        return s;
    }

    private static final Pattern RAW_TOKENS = Pattern.compile(
        "(?<comment>//.*?(\\r?\\n|$)|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)"
      + "|(?<pkg>package\\s+[\\w.]+\\s*;)"
      + "|(?<imp>import\\s+(?:static\\s+)?[\\w.*]+\\s*;)"
      + "|(?<id>[A-Za-z_$][A-Za-z0-9_$]*)"
      + "|(?<num>\\d+(?:_\\d+)*(?:\\.\\d+(?:_\\d+)*)?(?:[eE][+-]?\\d+)?[fFdDlL]?)"
      + "|(?<str>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
      + "|(?<op>==|!=|<=|>=|&&|\\|\\||\\+\\+|--|<<|>>>|>>|::|[+\\-*/%&|^~!?<>=.:;(),\\[\\]{}])"
      + "|(?<ws>\\s+)"
      , Pattern.DOTALL);

    /**
     * Builds a list mapping each token index in rawCode to its 1-indexed line number in the original source.
     * Skips comments, packages, imports, and whitespace to align closely with the normalized token index.
     * <p>
     * Note: Because normalisation alters certain token sequences (e.g. collapsing variable assignments),
     * the token index count in the normalised stream may differ slightly. Proportional position mapping
     * should be used to map normalized indices back to raw indices.
     *
     * @param rawCode the original raw code
     * @return a List of integers where list.get(i) is the line number of token i in rawCode
     */
    public static List<Integer> buildLineMap(String rawCode) {
        List<Integer> lineMap = new ArrayList<>();
        if (rawCode == null || rawCode.isEmpty()) return lineMap;
        
        Matcher m = RAW_TOKENS.matcher(rawCode);
        while (m.find()) {
            if (m.group("ws") != null || m.group("comment") != null || m.group("pkg") != null || m.group("imp") != null) {
                continue;
            }
            
            // It is a token that exists in normalized form (id, num, str, op)
            int start = m.start();
            int line = 1;
            for (int i = 0; i < start; i++) {
                if (rawCode.charAt(i) == '\n') {
                    line++;
                }
            }
            lineMap.add(line);
        }
        return lineMap;
    }
}
