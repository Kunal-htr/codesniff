package backend.modules.similarity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for Tokenizer.tokenize() and Tokenizer.toSymbolStream().
 */
class TokenizerTest {

    @Test
    void tokenizesKeywords() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("if for while return");
        assertEquals(4, tokens.size());
        for (Tokenizer.Token t : tokens) {
            assertEquals(Tokenizer.Tok.KW, t.type);
        }
        assertEquals("if", tokens.get(0).text);
        assertEquals("for", tokens.get(1).text);
        assertEquals("while", tokens.get(2).text);
        assertEquals("return", tokens.get(3).text);
    }

    @Test
    void normalizesIdentifiers() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("myVar anotherName");
        assertEquals(2, tokens.size());
        for (Tokenizer.Token t : tokens) {
            assertEquals(Tokenizer.Tok.ID, t.type);
            assertEquals("ID", t.text);
        }
    }

    @Test
    void tokenizesLiterals() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("42 3.14 \"hello\"");
        assertEquals(3, tokens.size());
        assertEquals(Tokenizer.Tok.NUM, tokens.get(0).type);
        assertEquals("NUM", tokens.get(0).text);
        assertEquals(Tokenizer.Tok.NUM, tokens.get(1).type);
        assertEquals("NUM", tokens.get(1).text);
        assertEquals(Tokenizer.Tok.STR, tokens.get(2).type);
        assertEquals("STR", tokens.get(2).text);
    }

    @Test
    void tokenizesOperators() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("+ - * / = ==");
        assertEquals(6, tokens.size());
        assertEquals(Tokenizer.Tok.OP, tokens.get(0).type);
        assertEquals("+", tokens.get(0).text);
        assertEquals("==", tokens.get(5).text);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("");
        assertTrue(tokens.isEmpty());
    }

    @Test
    void nullInputReturnsEmptyList() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize(null);
        assertTrue(tokens.isEmpty());
    }

    @Test
    void sequentialIndicesAreAssigned() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("int x = 5;");
        for (int i = 0; i < tokens.size(); i++) {
            assertEquals(i, tokens.get(i).index);
        }
    }

    // --- Symbol stream tests ---

    @Test
    void symbolStreamKeywordFormat() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("if");
        List<String> stream = Tokenizer.toSymbolStream(tokens);
        assertEquals(1, stream.size());
        assertEquals("KW(if)", stream.get(0));
    }

    @Test
    void symbolStreamIdentifierFormat() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("myVar");
        List<String> stream = Tokenizer.toSymbolStream(tokens);
        assertEquals(1, stream.size());
        assertEquals("ID", stream.get(0));
    }

    @Test
    void symbolStreamOperatorFormat() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("== + ;");
        List<String> stream = Tokenizer.toSymbolStream(tokens);
        assertEquals(3, stream.size());
        assertEquals("OP(==)", stream.get(0));
        assertEquals("OP(+)", stream.get(1));
        assertEquals("OP(;)", stream.get(2));
    }

    @Test
    void symbolStreamNumberFormat() {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize("42");
        List<String> stream = Tokenizer.toSymbolStream(tokens);
        assertEquals(1, stream.size());
        assertEquals("NUM", stream.get(0));
    }

    // --- Line map tests ---

    @Test
    void buildLineMapEmptyInput() {
        List<Integer> lineMap = Tokenizer.buildLineMap("");
        assertTrue(lineMap.isEmpty());
    }

    @Test
    void buildLineMapNullInput() {
        List<Integer> lineMap = Tokenizer.buildLineMap(null);
        assertTrue(lineMap.isEmpty());
    }

    @Test
    void buildLineMapSingleLine() {
        List<Integer> lineMap = Tokenizer.buildLineMap("int x = 1;");
        assertFalse(lineMap.isEmpty());
        for (Integer line : lineMap) {
            assertEquals(1, line);
        }
    }

    @Test
    void buildLineMapMultiLine() {
        String code = "int x = 1;\nint y = 2;\nint z = 3;";
        List<Integer> lineMap = Tokenizer.buildLineMap(code);
        assertFalse(lineMap.isEmpty());
        // Should have tokens from all 3 lines
        assertTrue(lineMap.contains(1));
        assertTrue(lineMap.contains(2));
        assertTrue(lineMap.contains(3));
    }
}
