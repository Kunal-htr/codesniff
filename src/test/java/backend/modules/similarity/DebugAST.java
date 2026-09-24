package backend.modules.similarity;
import backend.modules.similarity.ast.*;
import java.nio.file.*;
public class DebugAST {
    public static void main(String[] args) throws Exception {
        String srcA = Files.readString(Paths.get("benchmark_files/dataset/Type3_Modified/Pair009/Original.java"));
        String srcB = Files.readString(Paths.get("benchmark_files/dataset/Type3_Modified/Pair009/Clone.java"));
        ASTNode a = ASTBuilder.build(srcA);
        ASTNode b = ASTBuilder.build(srcB);
        ASTSimilarityResult res = ASTComparator.compare(a, b);
        System.out.println("AST Similarity: " + res.getSimilarity());
        System.out.println("Matched nodes: " + res.getMatchedNodes());
        System.out.println("Unmatched nodes: " + res.getUnmatchedNodes());
        System.out.println("Nodes A: " + res.getTotalNodesA());
        System.out.println("Nodes B: " + res.getTotalNodesB());
    }
}
