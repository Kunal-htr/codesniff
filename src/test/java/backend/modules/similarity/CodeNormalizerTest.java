package backend.modules.similarity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for CodeNormalizer.normalize().
 * Verifies each normalization stage independently and the full pipeline.
 */
class CodeNormalizerTest {

    // --- Stage 1: Package declarations ---

    @Test
    void removesPackageDeclarations() {
        String code = "package backend.modules;\nint x = 1;";
        String result = CodeNormalizer.normalize(code, false);
        assertFalse(result.contains("package"));
        // x gets normalized to ID by the identifier stage
        assertTrue(result.contains("ID") || result.contains("TYPE"));
    }

    @Test
    void removesNestedPackage() {
        String code = "package com.example.deep.nested;\nclass Foo {}";
        String result = CodeNormalizer.normalize(code, false);
        assertFalse(result.contains("package"));
        assertTrue(result.contains("class"));
    }

    // --- Stage 2: Import statements ---

    @Test
    void removesImportStatements() {
        String code = "import java.util.*;\nimport static java.lang.Math.abs;\nint x = 1;";
        String result = CodeNormalizer.normalize(code, false);
        assertFalse(result.contains("import"));
        // x gets normalized to ID by the identifier stage
        assertTrue(result.contains("ID") || result.contains("TYPE"));
    }

    // --- Stage 3: Comments ---

    @Test
    void removesBlockAndLineComments() {
        String code = "int x = 1; /* block comment */\nint y = 2; // line comment\n";
        String result = CodeNormalizer.normalize(code, true);
        assertFalse(result.contains("block comment"));
        assertFalse(result.contains("line comment"));
    }

    @Test
    void preservesCodeWhenCommentsNotOmitted() {
        String code = "int x = 1; // keep this";
        String result = CodeNormalizer.normalize(code, false);
        // Comments are NOT stripped when omitComments=false
        assertTrue(result.contains("//"));
    }

    // --- Stage 4: String and char literals ---

    @Test
    void normalizesStringLiterals() {
        String code = "String s = \"hello world\";";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("STRING"));
        assertFalse(result.contains("hello"));
    }

    @Test
    void normalizesCharLiterals() {
        String code = "char c = 'a';";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("CHAR"));
        assertFalse(result.contains("'a'"));
    }

    // --- Stage 5: Increment/Decrement normalization ---

    @Test
    void normalizesPostIncrement() {
        String code = "int i = 0; i++;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("INCR"));
        assertFalse(result.contains("++"));
    }

    @Test
    void normalizesPreIncrement() {
        String code = "int i = 0; ++i;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("INCR"));
    }

    @Test
    void normalizesCompoundAssignmentIncrement() {
        String code = "int i = 0; i += 1;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("INCR"));
        assertFalse(result.contains("+="));
    }

    @Test
    void normalizesSelfAssignmentIncrement() {
        String code = "int i = 0; i = i + 1;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("INCR"));
    }

    @Test
    void normalizesPostDecrement() {
        String code = "int i = 5; i--;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("DECR"));
        assertFalse(result.contains("--"));
    }

    @Test
    void normalizesCompoundAssignmentDecrement() {
        String code = "int i = 5; i -= 1;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("DECR"));
    }

    // --- Stage 6: Numeric literals ---

    @Test
    void normalizesIntegerLiterals() {
        String code = "int x = 42;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("NUM"));
        assertFalse(result.contains("42"));
    }

    @Test
    void normalizesFloatLiterals() {
        String code = "double pi = 3.14;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("FLOAT"));
        assertFalse(result.contains("3.14"));
    }

    @Test
    void normalizesHexLiterals() {
        String code = "int hex = 0xFF;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("NUM"));
        assertFalse(result.contains("0xFF"));
    }

    // --- Stage 7: Boolean and null literals ---

    @Test
    void normalizesBooleanLiterals() {
        String code = "boolean t = true; boolean f = false;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("BOOL"));
        assertFalse(result.contains("true"));
        assertFalse(result.contains("false"));
    }

    @Test
    void normalizesNullLiteral() {
        String code = "Object o = null;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("NULL"));
        assertFalse(result.contains("null"));
    }

    // --- Stage 8: Identifier normalization ---

    @Test
    void normalizesTypeKeywords() {
        String code = "int x; String s; double d;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("TYPE"));
    }

    @Test
    void normalizesUserIdentifiersToID() {
        String code = "int myVariable = 0;";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("ID"));
        assertFalse(result.contains("myVariable"));
    }

    @Test
    void preservesJavaKeywords() {
        String code = "if (x > 0) { return x; }";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("if"));
        assertTrue(result.contains("return"));
    }

    // --- Stage 9: Loop keywords ---

    @Test
    void normalizesLoopKeywords() {
        String code = "for (int i=0;i<10;i++) {} while(true) {} do {} while(false);";
        String result = CodeNormalizer.normalize(code, false);
        assertTrue(result.contains("LOOP"));
        assertFalse(result.contains("for"));
        assertFalse(result.contains("while"));
        assertFalse(result.contains("do"));
    }

    // --- Stage 10: Relational operators ---

    @Test
    void normalizesRelationalOperators() {
        String code = "if (a == b) {} if (a != b) {} if (a <= b) {} if (a >= b) {}";
        String result = CodeNormalizer.normalize(code, false);
        // == and != are preserved; <= and >= are normalized
        assertTrue(result.contains("=="));
        assertTrue(result.contains("!="));
    }

    // --- Full pipeline ---

    @Test
    void nullInputReturnsEmpty() {
        assertEquals("", CodeNormalizer.normalize(null, false));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals("", CodeNormalizer.normalize("", false));
    }

    @Test
    void fullPipelineIdempotent() {
        String code = "package test;\nimport java.util.*;\npublic class Foo {\n  // comment\n  int x = 42;\n  String s = \"hello\";\n}";
        String first = CodeNormalizer.normalize(code, true);
        String second = CodeNormalizer.normalize(first, true);
        assertEquals(first, second);
    }

    @Test
    void fullPipelineOnRealJavaCode() {
        String code = "public class ArraySum {\n" +
                "    public static int sumArray(int[] arr) {\n" +
                "        int total = 0;\n" +
                "        for (int i = 0; i < arr.length; i++) {\n" +
                "            total += arr[i];\n" +
                "        }\n" +
                "        return total;\n" +
                "    }\n" +
                "}";
        String result = CodeNormalizer.normalize(code, true);
        // Should contain structural tokens
        assertTrue(result.contains("TYPE"));
        assertTrue(result.contains("ID"));
        assertTrue(result.contains("LOOP"));
        // Should NOT contain original identifiers
        assertFalse(result.contains("arr"));
        assertFalse(result.contains("total"));
    }
}
