import com.github.javaparser.StaticJavaParser;
import java.nio.file.*;
public class TestParse {
    public static void main(String[] args) throws Exception {
        String code = Files.readString(Paths.get("benchmark_files/dataset/Type3_Modified/Pair009/Clone.java"));
        String wrapped = "public class __Wrapper__ {\n" + code + "\n}";
        try {
            StaticJavaParser.parse(wrapped);
            System.out.println("Parsed successfully!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
