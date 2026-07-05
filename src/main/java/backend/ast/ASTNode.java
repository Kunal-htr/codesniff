package backend.ast;

import java.util.*;

/**
 * Represents a node in an Abstract Syntax Tree for structural code comparison.
 * <p>
 * Each node has a {@link NodeType}, an optional value, and an ordered list of children.
 * Nodes support structural hashing: two subtrees with the same shape (same node types
 * in the same arrangement) produce the same hash, regardless of identifier names
 * or literal values. This enables O(1) subtree equality checks in the comparator.
 */
public class ASTNode {

    /**
     * Types of AST nodes representing Java language constructs.
     * Covers all major structural elements relevant to plagiarism detection.
     */
    public enum NodeType {
        PROGRAM,        // root node
        CLASS,          // class / interface / enum declaration
        METHOD,         // method declaration
        BLOCK,          // { ... } block
        VAR_DECL,       // variable declaration
        ASSIGNMENT,     // assignment statement
        IF,             // if statement
        ELSE,           // else branch
        SWITCH,         // switch statement
        CASE,           // case / default label
        FOR_LOOP,       // for loop (or LOOP with for-style header)
        WHILE_LOOP,     // while loop (or LOOP without for-style header)
        DO_WHILE,       // do-while loop
        RETURN,         // return statement
        BREAK,          // break statement
        CONTINUE,       // continue statement
        METHOD_CALL,    // method invocation
        EXPRESSION,     // general expression (fallback)
        BINARY_OP,      // binary operation (+, -, *, /, EQ_OP, LT_OP, etc.)
        UNARY_OP,       // unary operation (!, ~, INCR, DECR, etc.)
        LITERAL,        // literal value (NUM, STRING, BOOL, etc.)
        IDENTIFIER,     // identifier reference
        TYPE_REF,       // type reference (TYPE)
        PARAMETER,      // method parameter
        MODIFIER,       // access/non-access modifier (public, static, final, etc.)
        TRY,            // try block
        CATCH,          // catch block
        FINALLY,        // finally block
        THROW,          // throw statement
        NEW_EXPR,       // new expression
        ARRAY_ACCESS,   // array indexing
        FIELD_ACCESS,   // field / member access (a.b)
        UNKNOWN         // unrecognised construct (error recovery)
    }

    private final NodeType type;
    private final String value;       // optional: operator symbol, keyword, literal kind
    private final List<ASTNode> children;
    private ASTNode parent;           // back-reference (nullable)

    private Long cachedHash;          // lazily computed structural hash
    private Long cachedInsensitiveHash; // lazily computed operator-insensitive hash

    private static final Set<NodeType> VALUE_SIGNIFICANT_TYPES = Set.of(
        NodeType.BINARY_OP, NodeType.UNARY_OP, NodeType.ASSIGNMENT
    );

    public ASTNode(NodeType type) {
        this(type, null);
    }

    public ASTNode(NodeType type, String value) {
        this.type = type;
        this.value = value;
        this.children = new ArrayList<>();
    }

    /* ===== Tree Mutation ===== */

    /** Add a child node and set its parent reference. Returns {@code this} for chaining. */
    public ASTNode addChild(ASTNode child) {
        children.add(child);
        child.parent = this;
        cachedHash = null; // invalidate
        return this;
    }

    /** Convenience: add a leaf child with the given type and value. */
    public ASTNode addLeaf(NodeType childType, String childValue) {
        return addChild(new ASTNode(childType, childValue));
    }

    /* ===== Accessors ===== */

    public NodeType getType()              { return type; }
    public String getValue()               { return value; }
    public List<ASTNode> getChildren()     { return Collections.unmodifiableList(children); }
    public ASTNode getParent()             { return parent; }

    /* ===== Tree Metrics ===== */

    /** Distance from the root (root depth = 0). */
    public int depth() {
        int d = 0;
        ASTNode n = parent;
        while (n != null) { d++; n = n.parent; }
        return d;
    }

    /** Count of all nodes in this subtree, including this node. */
    public int subtreeSize() {
        int count = 1;
        for (ASTNode child : children) count += child.subtreeSize();
        return count;
    }

    /* ===== Structural Hashing ===== */

    /**
     * Compute a structural hash capturing tree shape while ignoring specific values.
     * Two structurally identical subtrees (same node types, same children arrangement)
     * will produce the same hash regardless of identifier names or literal values.
     * <p>
     * The hash mixes:
     * <ul>
     *   <li>Node type ordinal</li>
     *   <li>Number of children (structural feature)</li>
     *   <li>Children's hashes in order (position-sensitive)</li>
     * </ul>
     */
    public long structuralHash() {
        return structuralHash(false);
    }

    public long structuralHash(boolean ignoreOperators) {
        if (!ignoreOperators && cachedHash != null) return cachedHash;
        if (ignoreOperators && cachedInsensitiveHash != null) return cachedInsensitiveHash;

        long h = 1469598103934665603L; // FNV offset basis

        // Mix in node type
        h ^= type.ordinal();
        h *= 1099511628211L;

        // Mix in child count
        h ^= children.size();
        h *= 1099511628211L;

        // NEW: mix in value for operator-bearing node types only
        if (!ignoreOperators && VALUE_SIGNIFICANT_TYPES.contains(type) && value != null) {
            for (int i = 0; i < value.length(); i++) {
                h ^= value.charAt(i);
                h *= 1099511628211L;
            }
        }

        // Mix in children's hashes (order matters)
        for (ASTNode child : children) {
            h ^= child.structuralHash(ignoreOperators);
            h *= 1099511628211L;
        }

        if (ignoreOperators) {
            cachedInsensitiveHash = h;
        } else {
            cachedHash = h;
        }
        return h;
    }

    /* ===== Traversal ===== */

    /** Collect all nodes in this subtree via pre-order traversal. */
    public List<ASTNode> allNodes() {
        List<ASTNode> result = new ArrayList<>();
        collectNodes(result);
        return result;
    }

    private void collectNodes(List<ASTNode> result) {
        result.add(this);
        for (ASTNode child : children) child.collectNodes(result);
    }

    @Override
    public String toString() {
        return type + (value != null ? "(" + value + ")" : "") + "[" + children.size() + "]";
    }
}
