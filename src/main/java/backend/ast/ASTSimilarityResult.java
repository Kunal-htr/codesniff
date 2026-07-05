package backend.ast;

/**
 * Holds the result of an AST structural comparison produced by
 * {@link ASTComparator#compare(ASTNode, ASTNode)}.
 * <p>
 * All counts refer to individual AST nodes. {@code matchedSubtrees} counts
 * the number of distinct subtree roots that were matched as a unit (each
 * subtree match accounts for multiple individual node matches).
 */
public class ASTSimilarityResult {

    private final double similarity;      // 0.0–1.0
    private final int matchedNodes;       // individual nodes matched
    private final int unmatchedNodes;     // individual nodes not matched (both trees combined)
    private final int matchedSubtrees;    // distinct subtree roots matched
    private final int totalNodesA;        // total nodes in tree A
    private final int totalNodesB;        // total nodes in tree B

    public ASTSimilarityResult(double similarity, int matchedNodes, int unmatchedNodes,
                                int matchedSubtrees, int totalNodesA, int totalNodesB) {
        this.similarity = similarity;
        this.matchedNodes = matchedNodes;
        this.unmatchedNodes = unmatchedNodes;
        this.matchedSubtrees = matchedSubtrees;
        this.totalNodesA = totalNodesA;
        this.totalNodesB = totalNodesB;
    }

    public double getSimilarity()    { return similarity; }
    public int getMatchedNodes()     { return matchedNodes; }
    public int getUnmatchedNodes()   { return unmatchedNodes; }
    public int getMatchedSubtrees()  { return matchedSubtrees; }
    public int getTotalNodesA()      { return totalNodesA; }
    public int getTotalNodesB()      { return totalNodesB; }

    @Override
    public String toString() {
        return String.format(
                "ASTSimilarity[%.1f%%, matched=%d, unmatched=%d, subtrees=%d, nodesA=%d, nodesB=%d]",
                similarity * 100, matchedNodes, unmatchedNodes, matchedSubtrees, totalNodesA, totalNodesB);
    }
}
