package backend.ast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.type.*;

import java.util.*;

/**
 * Rich AST builder utilizing JavaParser to build a structured AST tree from raw
 * Java code.
 * <p>
 * This replaces the previous regex-based recursive descent builder with a true
 * compiler-level
 * parser, enabling robust and accurate Type-3 structural comparison (tolerant
 * to reordered
 * statements and nested control flow details).
 */
public class ASTBuilder {

    /**
     * Parse raw Java source code into an ASTNode tree.
     * Handles code snippets gracefully by wrapping them in a dummy class/method.
     *
     * @param rawCode the original Java source code (un-normalized)
     * @return root ASTNode (NodeType.PROGRAM)
     */
    public static ASTNode build(String rawCode) {
        ASTNode root = new ASTNode(ASTNode.NodeType.PROGRAM);
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return root;
        }

        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(rawCode);
        } catch (Exception e) {
            // Snippet fallback: wrap in a dummy class and method
            String wrapped = "public class DummyClass { public void dummyMethod() {\n" + rawCode + "\n} }";
            try {
                cu = StaticJavaParser.parse(wrapped);
            } catch (Exception ex) {
                // Return root with a parse error warning leaf
                root.addLeaf(ASTNode.NodeType.UNKNOWN, "Parse error: " + ex.getMessage());
                return root;
            }
        }

        convert(cu, root);
        return root;
    }

    /**
     * Recursively traverses the JavaParser AST and maps it to our ASTNode tree.
     */
    private static void convert(Node jpNode, ASTNode parent) {
        if (jpNode == null)
            return;

        ASTNode current = null;

        if (jpNode instanceof CompilationUnit) {
            current = parent; // Keep root as PROGRAM
        } else if (jpNode instanceof ClassOrInterfaceDeclaration) {
            current = new ASTNode(ASTNode.NodeType.CLASS, ((ClassOrInterfaceDeclaration) jpNode).getNameAsString());
        } else if (jpNode instanceof MethodDeclaration) {
            current = new ASTNode(ASTNode.NodeType.METHOD, ((MethodDeclaration) jpNode).getNameAsString());
        } else if (jpNode instanceof BlockStmt) {
            current = new ASTNode(ASTNode.NodeType.BLOCK);
        } else if (jpNode instanceof VariableDeclarationExpr) {
            current = new ASTNode(ASTNode.NodeType.VAR_DECL);
        } else if (jpNode instanceof AssignExpr) {
            AssignExpr ae = (AssignExpr) jpNode;
            current = new ASTNode(ASTNode.NodeType.ASSIGNMENT, ae.getOperator().asString());
        } else if (jpNode instanceof IfStmt) {
            current = new ASTNode(ASTNode.NodeType.IF);
        } else if (jpNode instanceof SwitchStmt) {
            current = new ASTNode(ASTNode.NodeType.SWITCH);
        } else if (jpNode instanceof SwitchEntry) {
            current = new ASTNode(ASTNode.NodeType.CASE);
        } else if (jpNode instanceof ForStmt || jpNode instanceof ForEachStmt) {
            current = new ASTNode(ASTNode.NodeType.FOR_LOOP);
        } else if (jpNode instanceof WhileStmt) {
            current = new ASTNode(ASTNode.NodeType.WHILE_LOOP);
        } else if (jpNode instanceof DoStmt) {
            current = new ASTNode(ASTNode.NodeType.DO_WHILE);
        } else if (jpNode instanceof ReturnStmt) {
            current = new ASTNode(ASTNode.NodeType.RETURN);
        } else if (jpNode instanceof BreakStmt) {
            current = new ASTNode(ASTNode.NodeType.BREAK);
        } else if (jpNode instanceof ContinueStmt) {
            current = new ASTNode(ASTNode.NodeType.CONTINUE);
        } else if (jpNode instanceof MethodCallExpr) {
            current = new ASTNode(ASTNode.NodeType.METHOD_CALL, ((MethodCallExpr) jpNode).getNameAsString());
        } else if (jpNode instanceof BinaryExpr) {
            BinaryExpr be = (BinaryExpr) jpNode;
            String op = be.getOperator().asString();
            current = new ASTNode(ASTNode.NodeType.BINARY_OP, op);
        } else if (jpNode instanceof UnaryExpr) {
            current = new ASTNode(ASTNode.NodeType.UNARY_OP, ((UnaryExpr) jpNode).getOperator().asString());
        } else if (jpNode instanceof LiteralExpr) {
            current = new ASTNode(ASTNode.NodeType.LITERAL, jpNode.getClass().getSimpleName());
        } else if (jpNode instanceof NameExpr) {
            current = new ASTNode(ASTNode.NodeType.IDENTIFIER, ((NameExpr) jpNode).getNameAsString());
        } else if (jpNode instanceof ClassOrInterfaceType) {
            current = new ASTNode(ASTNode.NodeType.TYPE_REF, ((ClassOrInterfaceType) jpNode).getNameAsString());
        } else if (jpNode instanceof Parameter) {
            current = new ASTNode(ASTNode.NodeType.PARAMETER);
        } else if (jpNode instanceof Modifier) {
            current = new ASTNode(ASTNode.NodeType.MODIFIER, ((Modifier) jpNode).getKeyword().asString());
        } else if (jpNode instanceof TryStmt) {
            current = new ASTNode(ASTNode.NodeType.TRY);
        } else if (jpNode instanceof CatchClause) {
            current = new ASTNode(ASTNode.NodeType.CATCH);
        } else if (jpNode instanceof ThrowStmt) {
            current = new ASTNode(ASTNode.NodeType.THROW);
        } else if (jpNode instanceof ObjectCreationExpr) {
            current = new ASTNode(ASTNode.NodeType.NEW_EXPR);
        } else if (jpNode instanceof ArrayAccessExpr) {
            current = new ASTNode(ASTNode.NodeType.ARRAY_ACCESS);
        } else if (jpNode instanceof FieldAccessExpr) {
            current = new ASTNode(ASTNode.NodeType.FIELD_ACCESS);
        }

        if (current != null) {
            if (current != parent) {
                parent.addChild(current);
            }
            // Recurse on children of current node
            for (Node child : jpNode.getChildNodes()) {
                convert(child, current);
            }
        } else {
            // Bypass unrecognized node and recurse directly to its children under the same
            // parent
            for (Node child : jpNode.getChildNodes()) {
                convert(child, parent);
            }
        }
    }
}
