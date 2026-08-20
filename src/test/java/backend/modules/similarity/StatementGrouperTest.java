package backend.modules.similarity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for StatementGrouper.groupStatements() and hashTokens().
 */
class StatementGrouperTest {

    @Test
    void groupsBySemicolon() {
        List<String> stream = List.of("ID", "OP(;)", "NUM", "OP(;)");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        assertEquals(2, stmts.size());
        assertEquals(0, stmts.get(0).id);
        assertEquals(1, stmts.get(1).id);
    }

    @Test
    void groupsByOpenBrace() {
        List<String> stream = List.of("KW(class)", "OP({)");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        // OP({) terminates the current statement; both tokens go into one statement
        assertEquals(1, stmts.size());
        assertTrue(stmts.get(0).tokens.contains("OP({)"));
    }

    @Test
    void groupsByCloseBrace() {
        List<String> stream = List.of("OP(})", "KW(return)", "NUM", "OP(;)");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        assertEquals(2, stmts.size());
        assertEquals(List.of("OP(})"), stmts.get(0).tokens);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(List.of());
        assertTrue(stmts.isEmpty());
    }

    @Test
    void nullInputReturnsEmptyList() {
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(null);
        assertTrue(stmts.isEmpty());
    }

    @Test
    void trailingTokensWithoutDelimiter() {
        List<String> stream = List.of("ID", "OP(;)", "NUM");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        assertEquals(2, stmts.size());
        // Second statement has no delimiter
        assertEquals(1, stmts.get(1).tokens.size());
    }

    @Test
    void sequentialIds() {
        List<String> stream = List.of("A", "OP(;)", "B", "OP(;)", "C", "OP(;)");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        for (int i = 0; i < stmts.size(); i++) {
            assertEquals(i, stmts.get(i).id);
        }
    }

    @Test
    void hashDeterminism() {
        List<String> tokens = List.of("KW(if)", "OP(;)");
        long h1 = StatementGrouper.hashTokens(tokens);
        long h2 = StatementGrouper.hashTokens(tokens);
        assertEquals(h1, h2);
    }

    @Test
    void hashDiffersForDifferentTokens() {
        List<String> tokensA = List.of("KW(if)", "OP(;)");
        List<String> tokensB = List.of("KW(while)", "OP(;)");
        assertNotEquals(StatementGrouper.hashTokens(tokensA), StatementGrouper.hashTokens(tokensB));
    }

    @Test
    void hashDiffersForDifferentOrder() {
        List<String> tokensA = List.of("A", "B");
        List<String> tokensB = List.of("B", "A");
        assertNotEquals(StatementGrouper.hashTokens(tokensA), StatementGrouper.hashTokens(tokensB));
    }

    @Test
    void statementTokensAreImmutableCopy() {
        List<String> stream = List.of("A", "OP(;)");
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        List<String> originalTokens = stmts.get(0).tokens;
        assertThrows(UnsupportedOperationException.class, () -> originalTokens.add("X"));
    }

    @Test
    void realJavaSymbolStream() {
        // Simulating a simple: "int x = 5; return x;"
        List<String> stream = List.of(
            "KW(int)", "ID", "OP(=)", "NUM", "OP(;)",
            "KW(return)", "ID", "OP(;)"
        );
        List<StatementGrouper.Statement> stmts = StatementGrouper.groupStatements(stream);
        assertEquals(2, stmts.size());
        assertEquals(5, stmts.get(0).tokens.size()); // "int ID = NUM ;"
        assertEquals(3, stmts.get(1).tokens.size()); // "return ID ;"
    }
}
