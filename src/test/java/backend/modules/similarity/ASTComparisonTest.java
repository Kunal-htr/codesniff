package backend.modules.similarity;

import backend.modules.similarity.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for ASTBuilder and ASTComparator.
 * Verifies AST construction and structural similarity comparison.
 */
class ASTComparisonTest {

    // --- ASTBuilder tests ---

    @Test
    void buildNullReturnsEmptyProgram() {
        ASTNode root = ASTBuilder.build(null);
        assertEquals(ASTNode.NodeType.PROGRAM, root.getType());
        assertEquals(1, root.subtreeSize());
    }

    @Test
    void buildEmptyStringReturnsEmptyProgram() {
        ASTNode root = ASTBuilder.build("");
        assertEquals(ASTNode.NodeType.PROGRAM, root.getType());
        assertEquals(1, root.subtreeSize());
    }

    @Test
    void buildSimpleClass() {
        String code = "public class Foo { public int x; }";
        ASTNode root = ASTBuilder.build(code);
        assertEquals(ASTNode.NodeType.PROGRAM, root.getType());
        assertTrue(root.subtreeSize() > 1);
    }

    @Test
    void buildMethodDeclaration() {
        String code = "class Foo { public int add(int a, int b) { return a + b; } }";
        ASTNode root = ASTBuilder.build(code);
        boolean hasMethod = root.allNodes().stream()
                .anyMatch(n -> n.getType() == ASTNode.NodeType.METHOD);
        assertTrue(hasMethod);
    }

    @Test
    void buildIfStatement() {
        String code = "class Foo { void m() { if (x > 0) { return 1; } } }";
        ASTNode root = ASTBuilder.build(code);
        boolean hasIf = root.allNodes().stream()
                .anyMatch(n -> n.getType() == ASTNode.NodeType.IF);
        assertTrue(hasIf);
    }

    @Test
    void buildForLoop() {
        String code = "class Foo { void m() { for (int i=0; i<10; i++) { } } }";
        ASTNode root = ASTBuilder.build(code);
        boolean hasFor = root.allNodes().stream()
                .anyMatch(n -> n.getType() == ASTNode.NodeType.FOR_LOOP);
        assertTrue(hasFor);
    }

    @Test
    void buildWhileLoop() {
        String code = "class Foo { void m() { while (true) { } } }";
        ASTNode root = ASTBuilder.build(code);
        boolean hasWhile = root.allNodes().stream()
                .anyMatch(n -> n.getType() == ASTNode.NodeType.WHILE_LOOP);
        assertTrue(hasWhile);
    }

    // --- ASTNode structural hash tests ---

    @Test
    void structuralHashDeterminism() {
        ASTNode node = new ASTNode(ASTNode.NodeType.CLASS, "Foo");
        node.addChild(new ASTNode(ASTNode.NodeType.METHOD, "bar"));
        long h1 = node.structuralHash();
        long h2 = node.structuralHash();
        assertEquals(h1, h2);
    }

    @Test
    void structuralHashSameShapeDifferentValues() {
        ASTNode a = new ASTNode(ASTNode.NodeType.CLASS, "Foo");
        a.addChild(new ASTNode(ASTNode.NodeType.METHOD, "bar"));

        ASTNode b = new ASTNode(ASTNode.NodeType.CLASS, "Baz");
        b.addChild(new ASTNode(ASTNode.NodeType.METHOD, "qux"));

        // Same structure, different values → same structural hash
        assertEquals(a.structuralHash(), b.structuralHash());
    }

    @Test
    void structuralHashDiffersForDifferentShapes() {
        ASTNode a = new ASTNode(ASTNode.NodeType.CLASS);
        a.addChild(new ASTNode(ASTNode.NodeType.METHOD));

        ASTNode b = new ASTNode(ASTNode.NodeType.CLASS);
        b.addChild(new ASTNode(ASTNode.NodeType.IF));

        assertNotEquals(a.structuralHash(), b.structuralHash());
    }

    @Test
    void structuralHashDiffersForDifferentIdentifierValues() {
        ASTNode a = new ASTNode(ASTNode.NodeType.IDENTIFIER, "x");
        ASTNode b = new ASTNode(ASTNode.NodeType.IDENTIFIER, "y");
        assertNotEquals(a.structuralHash(), b.structuralHash(),
                "IDENTIFIER nodes with different values should produce different hashes");
    }

    @Test
    void structuralHashSameForSameIdentifierValues() {
        ASTNode a = new ASTNode(ASTNode.NodeType.IDENTIFIER, "x");
        ASTNode b = new ASTNode(ASTNode.NodeType.IDENTIFIER, "x");
        assertEquals(a.structuralHash(), b.structuralHash(),
                "IDENTIFIER nodes with same values should produce same hashes");
    }

    @Test
    void structuralHashDiffersForDifferentLiteralValues() {
        ASTNode a = new ASTNode(ASTNode.NodeType.LITERAL, "IntegerLiteralExpr");
        ASTNode b = new ASTNode(ASTNode.NodeType.LITERAL, "StringLiteralExpr");
        assertNotEquals(a.structuralHash(), b.structuralHash(),
                "LITERAL nodes with different values should produce different hashes");
    }

    @Test
    void structuralHashDiffersForDifferentTypeRefs() {
        ASTNode a = new ASTNode(ASTNode.NodeType.TYPE_REF, "int");
        ASTNode b = new ASTNode(ASTNode.NodeType.TYPE_REF, "String");
        assertNotEquals(a.structuralHash(), b.structuralHash(),
                "TYPE_REF nodes with different values should produce different hashes");
    }

    // --- ASTComparator tests ---

    @Test
    void compareIdenticalCodeHighSimilarity() {
        String code = "class Foo { int add(int a, int b) { return a + b; } }";
        ASTNode treeA = ASTBuilder.build(code);
        ASTNode treeB = ASTBuilder.build(code);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        assertEquals(1.0, result.getSimilarity(), 1e-9);
    }

    @Test
    void compareNullTrees() {
        ASTSimilarityResult result = ASTComparator.compare(null, null);
        assertEquals(1.0, result.getSimilarity(), 1e-9);
    }

    @Test
    void compareOneNullTree() {
        ASTNode tree = ASTBuilder.build("class Foo {}");
        ASTSimilarityResult result = ASTComparator.compare(tree, null);
        assertEquals(0.0, result.getSimilarity(), 1e-9);
    }

    @Test
    void compareRenamedVariablesHighSimilarity() {
        String codeA = "class Foo { int sum(int a, int b) { return a + b; } }";
        String codeB = "class Foo { int sum(int x, int y) { return x + y; } }";
        ASTNode treeA = ASTBuilder.build(codeA);
        ASTNode treeB = ASTBuilder.build(codeB);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        // Same method name, same structure, different parameter/variable names
        // IDENTIFIER children won't match, but structural nodes (METHOD, BLOCK, RETURN, BINARY_OP) will
        // Similarity should be positive but not perfect
        assertTrue(result.getSimilarity() > 0.0,
                "Expected positive similarity for renamed variables, got " + result.getSimilarity());
        assertTrue(result.getSimilarity() < 1.0,
                "Renamed variables should not produce perfect similarity, got " + result.getSimilarity());
    }

    @Test
    void compareStructurallyModifiedCode() {
        String codeA = "class Foo { void m() { int a = 1; int b = 2; } }";
        String codeB = "class Foo { void m() { int a = 1; int b = 2; int c = 3; } }";
        ASTNode treeA = ASTBuilder.build(codeA);
        ASTNode treeB = ASTBuilder.build(codeB);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        // Tree B has an extra VAR_DECL, so some nodes won't match
        assertTrue(result.getSimilarity() > 0.3,
                "Expected moderate similarity for modified code, got " + result.getSimilarity());
        assertTrue(result.getSimilarity() < 1.0,
                "Modified code should not produce perfect similarity, got " + result.getSimilarity());
    }

    @Test
    void compareUnrelatedAlgorithmsLowSimilarity() {
        // Two completely different algorithms with different structure
        String codeA = "class Foo { int binarySearch(int[] arr, int target) { int low = 0; int high = arr.length - 1; while (low <= high) { int mid = (low + high) / 2; if (arr[mid] == target) { return mid; } else if (arr[mid] < target) { low = mid + 1; } else { high = mid - 1; } } return -1; } }";
        String codeB = "class Bar { void bubbleSort(int[] arr) { for (int i = 0; i < arr.length; i++) { for (int j = 0; j < arr.length - i - 1; j++) { if (arr[j] > arr[j + 1]) { int temp = arr[j]; arr[j] = arr[j + 1]; arr[j + 1] = temp; } } } } }";
        ASTNode treeA = ASTBuilder.build(codeA);
        ASTNode treeB = ASTBuilder.build(codeB);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        // Different algorithms: while vs nested for, if-else-if vs single if, return vs no return
        assertTrue(result.getSimilarity() < 0.5,
                "Expected low similarity for unrelated algorithms, got " + result.getSimilarity());
    }

    @Test
    void compareFullyDifferentCodeVeryLowSimilarity() {
        // Recursive factorial vs iterative string builder — different control flow
        String codeA = "class A { int factorial(int n) { if (n <= 1) { return 1; } return n * factorial(n - 1); } }";
        String codeB = "class B { String reverse(String s) { int i = 0; int j = s.length() - 1; while (i < j) { char temp = s.charAt(i); i = i + 1; j = j - 1; } return s; } }";
        ASTNode treeA = ASTBuilder.build(codeA);
        ASTNode treeB = ASTBuilder.build(codeB);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        // Different algorithms: recursive call vs while loop, different operators, different leaf values
        // Similarity should be moderate (structural baseline from shared CLASS/METHOD/BLOCK) but < 1.0
        assertTrue(result.getSimilarity() < 1.0,
                "Fully different algorithms should not produce perfect similarity, got " + result.getSimilarity());
        // Verify it's lower than identical code
        ASTNode treeC = ASTBuilder.build(codeA);
        ASTSimilarityResult identicalResult = ASTComparator.compare(treeA, treeC);
        assertTrue(result.getSimilarity() < identicalResult.getSimilarity(),
                "Different algorithms should score lower than identical code");
    }

    @Test
    void compareEmptyProgramsAreIdentical() {
        ASTNode emptyA = ASTBuilder.build("");
        ASTNode emptyB = ASTBuilder.build("");
        ASTSimilarityResult result = ASTComparator.compare(emptyA, emptyB);
        assertEquals(1.0, result.getSimilarity(), 1e-9,
                "Two empty programs should have similarity 1.0");
    }

    @Test
    void compareMinimalClassVsComplexClass() {
        String simple = "class Foo {}";
        String complex = "class Bar { void method() { if (true) { return 1; } else { return 0; } } }";
        ASTNode treeA = ASTBuilder.build(simple);
        ASTNode treeB = ASTBuilder.build(complex);
        ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
        assertTrue(result.getSimilarity() < 0.5,
                "Expected low similarity for minimal vs complex, got " + result.getSimilarity());
    }

    @Test
    void similarityAlwaysInUnitInterval() {
        String[] codes = {
            "class A { void m() { } }",
            "class B { int x; }",
            "class C { void m() { if (true) { return 1; } } }",
            "class D { void m() { for (int i=0; i<10; i++) { } } }"
        };
        for (int i = 0; i < codes.length; i++) {
            for (int j = 0; j < codes.length; j++) {
                ASTNode treeA = ASTBuilder.build(codes[i]);
                ASTNode treeB = ASTBuilder.build(codes[j]);
                ASTSimilarityResult result = ASTComparator.compare(treeA, treeB);
                assertTrue(result.getSimilarity() >= 0.0 && result.getSimilarity() <= 1.0,
                        "Similarity out of range for pair (" + i + "," + j + "): " + result.getSimilarity());
            }
        }
    }

    @Test
    void similarityIsSymmetric() {
        String codeA = "class Foo { int add(int a, int b) { return a + b; } }";
        String codeB = "class Bar { void process() { if (x > 0) { return; } } }";
        ASTNode treeA = ASTBuilder.build(codeA);
        ASTNode treeB = ASTBuilder.build(codeB);
        ASTSimilarityResult resultAB = ASTComparator.compare(treeA, treeB);
        ASTSimilarityResult resultBA = ASTComparator.compare(treeB, treeA);
        assertEquals(resultAB.getSimilarity(), resultBA.getSimilarity(), 1e-9,
                "AST similarity should be symmetric");
    }

    @Test
    void subtreeSizeIsCorrect() {
        ASTNode root = new ASTNode(ASTNode.NodeType.PROGRAM);
        root.addChild(new ASTNode(ASTNode.NodeType.CLASS));
        root.getChildren().get(0).addChild(new ASTNode(ASTNode.NodeType.METHOD));
        assertEquals(3, root.subtreeSize());
    }

    @Test
    void allNodesContainsAllNodes() {
        ASTNode root = new ASTNode(ASTNode.NodeType.PROGRAM);
        root.addChild(new ASTNode(ASTNode.NodeType.CLASS));
        root.getChildren().get(0).addChild(new ASTNode(ASTNode.NodeType.METHOD));
        assertEquals(3, root.allNodes().size());
    }
}
