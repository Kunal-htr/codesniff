package backend.ast;

import java.util.*;

/**
 * Compares two AST trees using greedy top-down subtree matching.
 * <p>
 * <b>Algorithm:</b>
 * <ol>
 *   <li>Compute {@link ASTNode#structuralHash() structural hashes} for every node
 *       in both trees (cached, so O(n + m) total).</li>
 *   <li>Build a hash → node-list index for tree B.</li>
 *   <li>Iterate over tree A's nodes from <em>largest subtree first</em>.
 *       If an unmatched node in A has the same structural hash and subtree size
 *       as an unmatched node in B, mark the entire subtrees as matched.</li>
 *   <li>After subtree matching, do a second pass for leaf-level (single-node) matches.</li>
 *   <li>Similarity = {@code 2 * matchedNodes / (totalA + totalB)}.</li>
 * </ol>
 * <p>
 * Complexity: O(n + m) hashing + O(min(n,m) · log n) matching for typical inputs.
 * <p>
 * This greedy approach is a well-known approximation for tree similarity that
 * works well when structural overlap is high (common in plagiarism scenarios).
 * The architecture is designed so a full tree-edit-distance algorithm can
 * replace this comparator in a future version.
 */
public class ASTComparator {

    /**
     * Compare two AST trees and compute structural similarity.
     *
     * @param treeA first AST (from {@link ASTBuilder})
     * @param treeB second AST (from {@link ASTBuilder})
     * @return similarity result with score and match statistics
     */
    public static ASTSimilarityResult compare(ASTNode treeA, ASTNode treeB) {
        return compare(treeA, treeB, false);
    }

    public static ASTSimilarityResult compare(ASTNode treeA, ASTNode treeB, boolean ignoreOperators) {
        if (treeA == null && treeB == null) {
            return new ASTSimilarityResult(1.0, 0, 0, 0, 0, 0);
        }
        int sizeA = treeA != null ? treeA.subtreeSize() : 0;
        int sizeB = treeB != null ? treeB.subtreeSize() : 0;
        if (treeA == null || treeB == null) {
            return new ASTSimilarityResult(0.0, 0, sizeA + sizeB, 0, sizeA, sizeB);
        }
        if (sizeA == 0 && sizeB == 0) {
            return new ASTSimilarityResult(1.0, 0, 0, 0, 0, 0);
        }

        // Collect all nodes from both trees
        List<ASTNode> nodesA = treeA.allNodes();
        List<ASTNode> nodesB = treeB.allNodes();

        // Build hash → nodes index for tree B
        Map<Long, List<ASTNode>> hashIndexB = buildHashIndex(nodesB, ignoreOperators);

        // Track matched nodes
        Set<ASTNode> matchedA = new HashSet<>();
        Set<ASTNode> matchedB = new HashSet<>();
        int matchedSubtreeCount = 0;

        // --- Phase 1: Greedy subtree matching (largest first) ---
        List<ASTNode> sortedA = new ArrayList<>(nodesA);
        sortedA.sort((a, b) -> Integer.compare(b.subtreeSize(), a.subtreeSize()));

        for (ASTNode nodeA : sortedA) {
            if (matchedA.contains(nodeA)) continue;
            if (nodeA.subtreeSize() < 2) continue; // skip trivial leaves

            long hash = nodeA.structuralHash(ignoreOperators);
            List<ASTNode> candidates = hashIndexB.get(hash);
            if (candidates == null) continue;

            // Find best unmatched candidate with matching subtree size
            ASTNode bestMatch = findBestCandidate(nodeA, candidates, matchedB);
            if (bestMatch != null) {
                markSubtree(nodeA, matchedA);
                markSubtree(bestMatch, matchedB);
                matchedSubtreeCount++;
            }
        }

        // --- Phase 2: Leaf-level matching for remaining unmatched nodes ---
        for (ASTNode nodeA : nodesA) {
            if (matchedA.contains(nodeA)) continue;

            List<ASTNode> candidates = hashIndexB.get(nodeA.structuralHash(ignoreOperators));
            if (candidates == null) continue;

            for (ASTNode candidate : candidates) {
                if (!matchedB.contains(candidate)) {
                    matchedA.add(nodeA);
                    matchedB.add(candidate);
                    break;
                }
            }
        }

        // --- Compute result ---
        double matchedWeight = 0.0;
        for (ASTNode nodeA : matchedA) {
            matchedWeight += getNodeWeight(nodeA.getType());
        }

        double totalWeightA = 0.0;
        for (ASTNode nodeA : nodesA) {
            totalWeightA += getNodeWeight(nodeA.getType());
        }

        double totalWeightB = 0.0;
        for (ASTNode nodeB : nodesB) {
            totalWeightB += getNodeWeight(nodeB.getType());
        }

        double similarity = (totalWeightA + totalWeightB) == 0.0 ? 0.0 : (2.0 * matchedWeight) / (totalWeightA + totalWeightB);
        similarity = Math.max(0.0, Math.min(1.0, similarity));

        int matchedCount = matchedA.size();
        int unmatchedCount = (sizeA - matchedA.size()) + (sizeB - matchedB.size());

        return new ASTSimilarityResult(similarity, matchedCount, unmatchedCount,
                matchedSubtreeCount, sizeA, sizeB);
    }

    /* ===== Internal Helpers ===== */

    /** Build an index mapping structural hash → list of nodes. */
    private static Map<Long, List<ASTNode>> buildHashIndex(List<ASTNode> nodes, boolean ignoreOperators) {
        Map<Long, List<ASTNode>> index = new HashMap<>();
        for (ASTNode node : nodes) {
            index.computeIfAbsent(node.structuralHash(ignoreOperators), k -> new ArrayList<>()).add(node);
        }
        return index;
    }

    /** Find the first unmatched candidate with the same subtree size. */
    private static ASTNode findBestCandidate(ASTNode target, List<ASTNode> candidates,
                                              Set<ASTNode> matchedB) {
        int targetSize = target.subtreeSize();
        for (ASTNode candidate : candidates) {
            if (!matchedB.contains(candidate) && candidate.subtreeSize() == targetSize) {
                return candidate;
            }
        }
        return null;
    }

    /** Recursively mark all nodes in a subtree as matched. */
    private static void markSubtree(ASTNode node, Set<ASTNode> matched) {
        matched.add(node);
        for (ASTNode child : node.getChildren()) markSubtree(child, matched);
    }

    /** Compute node-specific weights to de-emphasize boilerplate constructs. */
    private static double getNodeWeight(ASTNode.NodeType type) {
        if (type == null) return 1.0;
        switch (type) {
            case MODIFIER:
            case BLOCK:
            case PROGRAM:
            case UNKNOWN:
                return 0.1;
            case CLASS:
            case METHOD:
            case PARAMETER:
                return 0.3;
            case IF:
            case ELSE:
            case SWITCH:
            case CASE:
            case FOR_LOOP:
            case WHILE_LOOP:
            case DO_WHILE:
            case TRY:
            case CATCH:
            case FINALLY:
            case THROW:
            case RETURN:
            case BREAK:
            case CONTINUE:
                return 1.0; // Control structures
            case ASSIGNMENT:
            case VAR_DECL:
            case METHOD_CALL:
            case BINARY_OP:
            case UNARY_OP:
            case ARRAY_ACCESS:
            case FIELD_ACCESS:
            case NEW_EXPR:
                return 1.0; // Operations
            case LITERAL:
            case IDENTIFIER:
            case TYPE_REF:
                return 0.5; // Leaf details
            default:
                return 1.0;
        }
    }
}
