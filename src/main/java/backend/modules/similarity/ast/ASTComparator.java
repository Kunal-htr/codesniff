package backend.modules.similarity.ast;

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

    private static final Set<ASTNode.NodeType> ALIGNABLE_TYPES = EnumSet.of(
        ASTNode.NodeType.PROGRAM, ASTNode.NodeType.CLASS, ASTNode.NodeType.METHOD,
        ASTNode.NodeType.BLOCK, ASTNode.NodeType.IF, ASTNode.NodeType.ELSE,
        ASTNode.NodeType.SWITCH, ASTNode.NodeType.CASE, ASTNode.NodeType.FOR_LOOP,
        ASTNode.NodeType.WHILE_LOOP, ASTNode.NodeType.DO_WHILE, ASTNode.NodeType.TRY,
        ASTNode.NodeType.CATCH, ASTNode.NodeType.FINALLY
    );

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
        Map<ASTNode, ASTNode> paired = new HashMap<>();
        int matchedSubtreeCount = 0;
        
        int operatorDivergenceCount = 0;
        Set<String> divergentOperators = new LinkedHashSet<>();
        Set<String> identifierRenames = new LinkedHashSet<>();

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
                if (ignoreOperators) {
                    operatorDivergenceCount += collectAndCountDivergences(nodeA, bestMatch, divergentOperators);
                }
                extractRenames(nodeA, bestMatch, identifierRenames);
                markSubtree(nodeA, bestMatch, matchedA, matchedB, paired);
                matchedSubtreeCount++;
            }
        }

        // --- Phase 1.5: LCS-style partial subtree alignment ---
        // For same-type unmatched node pairs whose hashes don't match,
        // align their children tolerant of insertion/deletion/reordering, 
        // and award weight proportional to the aligned overlap.
        for (ASTNode nodeA : sortedA) {
            if (matchedA.contains(nodeA)) continue;
            if (nodeA.subtreeSize() < 2) continue; // skip trivial leaves

            ASTNode bestMatch = null;
            double bestShared = 0;
            
            for (ASTNode nodeB : nodesB) {
                if (matchedB.contains(nodeB)) continue;
                if (nodeA.getType() == nodeB.getType()) {
                    double shared = estimateMatchScoreLCS(nodeA, nodeB, ignoreOperators);
                    if (shared > bestShared) {
                        bestShared = shared;
                        bestMatch = nodeB;
                    }
                }
            }
            
            // Only align if we found a reasonable partial match (e.g., > 20% of the subtree matches)
            if (bestMatch != null && bestShared > (nodeA.subtreeSize() * 0.2)) {
                alignAndMarkLCS(nodeA, bestMatch, matchedA, matchedB, paired, ignoreOperators);
            }
        }

        // --- Phase 2: Leaf-level matching (identifiers, literals, etc.) ---
        for (ASTNode nodeA : nodesA) {
            if (matchedA.contains(nodeA)) continue;
            
            // NARROW FIX: Require leaf matches to occur within an already-partially-matched structural context.
            ASTNode ancestorA = getNearestMatchedAncestor(nodeA, matchedA);
            if (ancestorA == null) continue;
            
            ASTNode mappedAncestorB = paired.get(ancestorA);
            if (mappedAncestorB == null) continue;

            List<ASTNode> candidates = hashIndexB.get(nodeA.structuralHash(ignoreOperators));
            if (candidates == null) continue;

            for (ASTNode candidate : candidates) {
                if (!matchedB.contains(candidate)) {
                    if (!isAncestor(mappedAncestorB, candidate)) continue;
                    
                    if (ignoreOperators) {
                        operatorDivergenceCount += collectAndCountDivergences(nodeA, candidate, divergentOperators);
                    }
                    extractRenames(nodeA, candidate, identifierRenames);
                    matchedA.add(nodeA);
                    matchedB.add(candidate);
                    paired.put(nodeA, candidate);
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
        
        // Narrow targeted fix: penalize generically small trees (trivial getters/setters)
        // by weighting by absolute matched weight rather than pure ratio.
        double damping = Math.min(1.0, matchedWeight / 15.0);
        similarity *= damping;
        
        similarity = Math.max(0.0, Math.min(1.0, similarity));

        int matchedCount = matchedA.size();
        int unmatchedCount = (sizeA - matchedA.size()) + (sizeB - matchedB.size());

        return new ASTSimilarityResult(similarity, matchedCount, unmatchedCount,
                matchedSubtreeCount, sizeA, sizeB, operatorDivergenceCount, new ArrayList<>(divergentOperators), new ArrayList<>(identifierRenames));
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

    /** Recursively mark two identical subtrees as matched. */
    private static void markSubtree(ASTNode nodeA, ASTNode nodeB, Set<ASTNode> matchedA, Set<ASTNode> matchedB, Map<ASTNode, ASTNode> paired) {
        matchedA.add(nodeA);
        matchedB.add(nodeB);
        paired.put(nodeA, nodeB);
        
        List<ASTNode> childrenA = nodeA.getChildren();
        List<ASTNode> childrenB = nodeB.getChildren();
        if (childrenA.size() == childrenB.size()) {
            for (int i = 0; i < childrenA.size(); i++) {
                markSubtree(childrenA.get(i), childrenB.get(i), matchedA, matchedB, paired);
            }
        }
    }

    /** Estimates how well two subtrees match using LCS alignment of children. */
    private static double estimateMatchScoreLCS(ASTNode a, ASTNode b, boolean ignoreOperators) {
        if (a.structuralHash(ignoreOperators) == b.structuralHash(ignoreOperators)) {
            return a.subtreeSize();
        }
        if (a.getType() != b.getType()) {
            return 0;
        }

        List<ASTNode> childrenA = a.getChildren();
        List<ASTNode> childrenB = b.getChildren();
        int m = childrenA.size();
        int n = childrenB.size();
        double[][] dp = new double[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                ASTNode ca = childrenA.get(i - 1);
                ASTNode cb = childrenB.get(j - 1);

                double score = 0;
                if (ca.structuralHash(ignoreOperators) == cb.structuralHash(ignoreOperators)) {
                    score = ca.subtreeSize();
                } else if (ca.getType() == cb.getType() && ALIGNABLE_TYPES.contains(ca.getType())) {
                    score = estimateMatchScoreLCS(ca, cb, ignoreOperators);
                }

                if (score > 0) {
                    dp[i][j] = Math.max(dp[i - 1][j - 1] + score, Math.max(dp[i - 1][j], dp[i][j - 1]));
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return 1.0 + dp[m][n]; // 1 for the root node match + LCS of children
    }

    /** Recursively aligns children using LCS and marks matches. */
    private static void alignAndMarkLCS(ASTNode a, ASTNode b, Set<ASTNode> matchedA, Set<ASTNode> matchedB, Map<ASTNode, ASTNode> paired, boolean ignoreOperators) {
        matchedA.add(a);
        matchedB.add(b);
        paired.put(a, b);

        List<ASTNode> childrenA = a.getChildren();
        List<ASTNode> childrenB = b.getChildren();
        int m = childrenA.size();
        int n = childrenB.size();
        double[][] dp = new double[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                ASTNode ca = childrenA.get(i - 1);
                ASTNode cb = childrenB.get(j - 1);

                if (matchedA.contains(ca) || matchedB.contains(cb)) {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                    continue;
                }

                double score = 0;
                if (ca.structuralHash(ignoreOperators) == cb.structuralHash(ignoreOperators)) {
                    score = ca.subtreeSize();
                } else if (ca.getType() == cb.getType() && ALIGNABLE_TYPES.contains(ca.getType())) {
                    score = estimateMatchScoreLCS(ca, cb, ignoreOperators);
                }

                if (score > 0) {
                    dp[i][j] = Math.max(dp[i - 1][j - 1] + score, Math.max(dp[i - 1][j], dp[i][j - 1]));
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (dp[i][j] == dp[i - 1][j]) {
                i--;
            } else if (dp[i][j] == dp[i][j - 1]) {
                j--;
            } else {
                ASTNode ca = childrenA.get(i - 1);
                ASTNode cb = childrenB.get(j - 1);
                if (ca.structuralHash(ignoreOperators) == cb.structuralHash(ignoreOperators)) {
                    markSubtree(ca, cb, matchedA, matchedB, paired);
                } else {
                    alignAndMarkLCS(ca, cb, matchedA, matchedB, paired, ignoreOperators);
                }
                i--;
                j--;
            }
        }
    }

    private static ASTNode getNearestMatchedAncestor(ASTNode node, Set<ASTNode> matched) {
        ASTNode p = node.getParent();
        while (p != null) {
            if (matched.contains(p)) return p;
            p = p.getParent();
        }
        return null;
    }

    private static boolean isAncestor(ASTNode ancestor, ASTNode node) {
        ASTNode p = node.getParent();
        while (p != null) {
            if (p == ancestor) return true;
            p = p.getParent();
        }
        return false;
    }

    /** Recursively traverse identical subtrees and collect mismatched operator values. */
    private static int collectAndCountDivergences(ASTNode a, ASTNode b, Set<String> divergences) {
        if (a == null || b == null || a.getType() != b.getType()) return 0;
        int count = 0;
        
        ASTNode.NodeType type = a.getType();
        if (type == ASTNode.NodeType.BINARY_OP || type == ASTNode.NodeType.UNARY_OP || type == ASTNode.NodeType.ASSIGNMENT) {
            String valA = a.getValue() == null ? "" : a.getValue();
            String valB = b.getValue() == null ? "" : b.getValue();
            if (!valA.equals(valB)) {
                divergences.add(valA + " vs " + valB);
                count++;
            }
        }
        
        List<ASTNode> childrenA = a.getChildren();
        List<ASTNode> childrenB = b.getChildren();
        if (childrenA.size() == childrenB.size()) {
            for (int i = 0; i < childrenA.size(); i++) {
                count += collectAndCountDivergences(childrenA.get(i), childrenB.get(i), divergences);
            }
        }
        return count;
    }

    /** Recursively traverse identical subtrees and collect mismatched identifiers. */
    private static void extractRenames(ASTNode a, ASTNode b, Set<String> renames) {
        if (a == null || b == null || a.getType() != b.getType()) return;
        
        if (a.getType() == ASTNode.NodeType.IDENTIFIER) {
            String valA = a.getValue() == null ? "" : a.getValue();
            String valB = b.getValue() == null ? "" : b.getValue();
            if (!valA.isEmpty() && !valB.isEmpty() && !valA.equals(valB)) {
                renames.add(valA + " -> " + valB);
            }
        }
        
        List<ASTNode> childrenA = a.getChildren();
        List<ASTNode> childrenB = b.getChildren();
        if (childrenA.size() == childrenB.size()) {
            for (int i = 0; i < childrenA.size(); i++) {
                extractRenames(childrenA.get(i), childrenB.get(i), renames);
            }
        }
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
