package backend;

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
}
