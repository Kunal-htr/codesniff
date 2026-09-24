package backend.modules.similarity;
import backend.modules.similarity.ast.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
public class DebugASTTest {
    @Test
    public void test() throws Exception {
        String srcA = Files.readString(Paths.get("benchmark_files/dataset/Type3_Modified/Pair009/Original.java"));
        String srcB = Files.readString(Paths.get("benchmark_files/dataset/Type3_Modified/Pair009/Clone.java"));
        ASTNode a = ASTBuilder.build(srcA);
        ASTNode b = ASTBuilder.build(srcB);
        ASTSimilarityResult res = ASTComparator.compare(a, b);
        System.out.println("AST Similarity: " + res.getSimilarity());
        System.out.println("Matched nodes: " + res.getMatchedNodes());
        System.out.println("Unmatched nodes: " + res.getUnmatchedNodes());
        System.out.println("Nodes A: " + res.getTotalNodesA());
        System.out.println("Nodes A: " + res.getTotalNodesA());
        System.out.println("Nodes B: " + res.getTotalNodesB());
        
        System.out.println("AST A:");
        for (ASTNode n : a.getChildren()) {
            System.out.println("  " + n.getType() + " " + n.getValue());
        }
        System.out.println("AST B:");
        for (ASTNode n : b.getChildren()) {
            System.out.println("  " + n.getType() + " " + n.getValue());
        }
    }
}
