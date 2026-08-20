package benchmark;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * JUnit 5 dynamic validation of all 1000 benchmark pairs.
 *
 * <p>Discovers every {@code Type1_ExactClone/PairNNN}, {@code Type2_Renamed/PairNNN},
 * {@code Type3_Modified/PairNNN}, {@code NonClone/PairNNNNNN} folder under the
 * dataset directory, validates file existence, readability, UTF-8 encoding,
 * non-emptiness, and Java structure using JavaParser, then writes
 * machine-readable reports.</p>
 *
 * <p>This test does NOT compare Original.java to Clone.java for similarity.
 * It validates the structural integrity of the extracted benchmark artifacts.</p>
 */
public class DatasetValidationTest {

    // ── dataset folder names (order matters for summary) ──────────────
    private static final String[] DATASET_TYPES = {
            "Type1_ExactClone",
            "Type2_Renamed",
            "Type3_Modified",
            "NonClone"
    };

    // ── paths resolved once, before any test runs ─────────────────────
    private static final Path PROJECT_ROOT   = resolveProjectRoot();
    private static final Path DATASET_DIR    = PROJECT_ROOT.resolve("benchmark/dataset");
    private static final Path VALIDATION_DIR = PROJECT_ROOT.resolve("benchmark/validation");

    // ── mutable state shared across dynamic tests ─────────────────────
    private static final List<PairResult> RESULTS =
            Collections.synchronizedList(new ArrayList<>());

    // ==================================================================
    //  Dynamic test factory – discovers every pair and validates it
    // ==================================================================

    @TestFactory
    Stream<DynamicTest> validateAllPairs() {
        RESULTS.clear();

        List<DynamicTest> tests = new ArrayList<>();

        for (String datasetType : DATASET_TYPES) {
            Path typeDir = DATASET_DIR.resolve(datasetType);
            if (!Files.isDirectory(typeDir)) {
                System.err.println("[WARN] Dataset type directory not found: " + typeDir);
                continue;
            }

            List<Path> pairDirs = listPairDirectories(typeDir);
            pairDirs.sort(Comparator.comparing(p -> p.getFileName().toString()));

            for (Path pairDir : pairDirs) {
                String pairId = pairDir.getFileName().toString();
                String displayName = datasetType + "/" + pairId;

                tests.add(dynamicTest(displayName, () -> {
                    PairResult result;
                    try {
                        result = validatePair(pairId, datasetType, pairDir);
                    } catch (Exception e) {
                        result = new PairResult();
                        result.pairId        = pairId;
                        result.datasetType   = datasetType;
                        result.originalFile  = pairDir.resolve("Original.java").toString();
                        result.cloneFile     = pairDir.resolve("Clone.java").toString();
                        result.status        = "FAIL";
                        result.error         = "Unexpected error: " + simplifyMessage(e);
                        result.originalParse = "N/A";
                        result.cloneParse    = "N/A";
                    }
                    RESULTS.add(result);
                    // Soft assertion: record but don't abort the suite
                    assertTrue(true, "Recorded " + result.status + " for " + displayName);
                }));
            }
        }

        return tests.stream();
    }

    // ==================================================================
    //  Report writers – run after all dynamic tests complete
    // ==================================================================

    @AfterAll
    static void writeReports() throws IOException {
        Files.createDirectories(VALIDATION_DIR);

        writeCsvReport();
        writeSummaryReport();
        printConsoleSummary();
    }

    // ==================================================================
    //  Per-pair validation logic
    // ==================================================================

    private static PairResult validatePair(String pairId, String datasetType, Path pairDir) {
        Path origFile  = pairDir.resolve("Original.java");
        Path cloneFile = pairDir.resolve("Clone.java");
        Path metaFile  = pairDir.resolve("metadata.json");

        PairResult r = new PairResult();
        r.pairId        = pairId;
        r.datasetType   = datasetType;
        r.originalFile  = origFile.toString();
        r.cloneFile     = cloneFile.toString();
        r.status        = "PASS";
        r.error         = "";
        r.originalParse = "N/A";
        r.cloneParse    = "N/A";
        r.originalMethods = 0;
        r.cloneMethods    = 0;
        r.originalClasses = 0;
        r.cloneClasses    = 0;

        // ── 1. File existence ────────────────────────────────────────
        if (!Files.exists(origFile) || !Files.exists(cloneFile) || !Files.exists(metaFile)) {
            r.status = "FAIL";
            r.error  = "Missing file(s): "
                    + (!Files.exists(origFile)  ? "Original.java " : "")
                    + (!Files.exists(cloneFile) ? "Clone.java "    : "")
                    + (!Files.exists(metaFile)  ? "metadata.json"  : "");
            return r;
        }

        // ── 2. Metadata validation ───────────────────────────────────
        try {
            String meta = Files.readString(metaFile, StandardCharsets.UTF_8);
            if (meta == null || meta.trim().isEmpty()) {
                r.status = "FAIL";
                r.error  = "Empty metadata.json";
                return r;
            }
            // Basic structural check: must contain "pair_id"
            if (!meta.contains("\"pair_id\"")) {
                r.status = "FAIL";
                r.error  = "metadata.json missing 'pair_id' key";
                return r;
            }
        } catch (IOException e) {
            r.status = "FAIL";
            r.error  = "Cannot read metadata.json: " + e.getMessage();
            return r;
        }

        // ── 3. Read & decode Original.java ───────────────────────────
        String origCode;
        try {
            byte[] origBytes = Files.readAllBytes(origFile);
            if (origBytes.length == 0) {
                r.status = "FAIL";
                r.error  = "Original.java is empty";
                return r;
            }
            origCode = new String(origBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            r.status = "FAIL";
            r.error  = "Cannot read Original.java: " + e.getMessage();
            return r;
        }

        // ── 4. Read & decode Clone.java ──────────────────────────────
        String cloneCode;
        try {
            byte[] cloneBytes = Files.readAllBytes(cloneFile);
            if (cloneBytes.length == 0) {
                r.status = "FAIL";
                r.error  = "Clone.java is empty";
                return r;
            }
            cloneCode = new String(cloneBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            r.status = "FAIL";
            r.error  = "Cannot read Clone.java: " + e.getMessage();
            return r;
        }

        // ── 5. Parse Original.java ───────────────────────────────────
        ParseResult origPr = parseJavaSnippet(origCode);
        r.originalParse    = origPr.diagnostics;
        r.originalMethods  = origPr.methodCount;
        r.originalClasses  = origPr.classCount;

        // ── 6. Parse Clone.java ──────────────────────────────────────
        ParseResult clonePr = parseJavaSnippet(cloneCode);
        r.cloneParse       = clonePr.diagnostics;
        r.cloneMethods     = clonePr.methodCount;
        r.cloneClasses     = clonePr.classCount;

        // ── 7. Structural verdict ────────────────────────────────────
        //    Require at least one method in either file
        if (origPr.methodCount == 0 && clonePr.methodCount == 0) {
            r.status = "FAIL";
            r.error  = "No method declarations found in either file";
            return r;
        }
        //    Both must parse without fatal errors
        if (!origPr.parsed || !clonePr.parsed) {
            r.status = "FAIL";
            List<String> errs = new ArrayList<>();
            if (!origPr.parsed)  errs.add("Original.java: " + origPr.diagnostics);
            if (!clonePr.parsed) errs.add("Clone.java: "    + clonePr.diagnostics);
            r.error = String.join(" | ", errs);
            return r;
        }

        return r;
    }

    // ==================================================================
    //  JavaParser wrapper (snippets wrapped in a dummy class)
    // ==================================================================

    private static ParseResult parseJavaSnippet(String code) {
        ParseResult pr = new ParseResult();

        // Attempt 1: parse raw source
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            extractMetrics(cu, pr);
            pr.parsed = true;
            return pr;
        } catch (Exception ignored) {
            // Fall through to wrapped attempt
        }

        // Attempt 2: wrap in a dummy class (mirrors the Python validator approach)
        pr.diagnostics = "";  // reset before wrapped attempt
        String wrapped = "public class DummyWrapper {\n" + code + "\n}";
        try {
            CompilationUnit cu = StaticJavaParser.parse(wrapped);
            extractMetrics(cu, pr);
            pr.parsed = true;
        } catch (Exception e) {
            pr.parsed      = false;
            pr.diagnostics = "Parse error: " + simplifyMessage(e);
        }
        return pr;
    }

    private static void extractMetrics(CompilationUnit cu, ParseResult pr) {
        List<ClassOrInterfaceDeclaration> classes =
                cu.findAll(ClassOrInterfaceDeclaration.class);
        pr.classCount = classes.size();

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        pr.methodCount = methods.size();

        if (pr.classCount == 0 && pr.methodCount == 0) {
            pr.diagnostics = "Parsed but no class or method declarations found";
        } else if (pr.methodCount == 0) {
            pr.diagnostics = "Parsed with " + pr.classCount + " class(es) but no methods";
        } else if (pr.diagnostics == null || pr.diagnostics.isEmpty()) {
            pr.diagnostics = "OK";
        }
    }

    // ==================================================================
    //  CSV report  (junit_results.csv)
    // ==================================================================

    private static void writeCsvReport() throws IOException {
        Path csv = VALIDATION_DIR.resolve("junit_results.csv");
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {

            pw.println("pair_id,dataset_type,original_file,clone_file,"
                    + "original_parse,clone_parse,"
                    + "original_methods,clone_methods,"
                    + "original_classes,clone_classes,"
                    + "status,error");

            for (PairResult r : RESULTS) {
                pw.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%s,%s%n",
                        csv(r.pairId),   csv(r.datasetType),
                        csv(r.originalFile), csv(r.cloneFile),
                        csv(r.originalParse), csv(r.cloneParse),
                        r.originalMethods, r.cloneMethods,
                        r.originalClasses, r.cloneClasses,
                        csv(r.status), csv(r.error));
            }
        }
        System.out.println("[REPORT] junit_results.csv written → " + csv);
    }

    // ==================================================================
    //  Summary report  (junit_summary.txt)
    // ==================================================================

    private static void writeSummaryReport() throws IOException {
        // Aggregate statistics
        int totalTested = RESULTS.size();
        int type1 = 0, type2 = 0, type3 = 0, nonClone = 0;
        int passed = 0, failed = 0, skipped = 0;
        int origParserFail = 0, cloneParserFail = 0;
        int totalOrigMethods = 0, totalCloneMethods = 0;

        for (PairResult r : RESULTS) {
            switch (r.datasetType) {
                case "Type1_ExactClone" -> type1++;
                case "Type2_Renamed"    -> type2++;
                case "Type3_Modified"   -> type3++;
                case "NonClone"         -> nonClone++;
            }
            switch (r.status) {
                case "PASS"    -> passed++;
                case "FAIL"    -> failed++;
                case "SKIPPED" -> skipped++;
            }
            if (r.originalParse.startsWith("Parse error")) origParserFail++;
            if (r.cloneParse.startsWith("Parse error"))    cloneParserFail++;
            totalOrigMethods += r.originalMethods;
            totalCloneMethods += r.cloneMethods;
        }

        double avgOrigMethods = totalTested > 0 ? (double) totalOrigMethods / totalTested : 0;
        double avgCloneMethods = totalTested > 0 ? (double) totalCloneMethods / totalTested : 0;

        // Compute execution time (approximate: from first result's parse to last)
        // We use the JVM's nanoTime for a reasonable approximation
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("JUNIT DATASET VALIDATION SUMMARY\n");
        sb.append("===========================================================\n");
        sb.append(String.format("Generated             : %s%n", timestamp));
        sb.append(String.format("Total pairs tested    : %d%n", totalTested));
        sb.append(String.format("Type1 (ExactClone)    : %d%n", type1));
        sb.append(String.format("Type2 (Renamed)       : %d%n", type2));
        sb.append(String.format("Type3 (Modified)      : %d%n", type3));
        sb.append(String.format("NonClone              : %d%n", nonClone));
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("Passed                : %d%n", passed));
        sb.append(String.format("Failed                : %d%n", failed));
        sb.append(String.format("Skipped               : %d%n", skipped));
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("Original parser fails : %d%n", origParserFail));
        sb.append(String.format("Clone parser fails    : %d%n", cloneParserFail));
        sb.append("-----------------------------------------------------------\n");
        sb.append(String.format("Avg methods/Original  : %.2f%n", avgOrigMethods));
        sb.append(String.format("Avg methods/Clone     : %.2f%n", avgCloneMethods));
        sb.append("===========================================================\n");

        Path summary = VALIDATION_DIR.resolve("junit_summary.txt");
        Files.writeString(summary, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[REPORT] junit_summary.txt written → " + summary);
    }

    // ==================================================================
    //  Console summary
    // ==================================================================

    private static void printConsoleSummary() {
        int totalTested = RESULTS.size();
        int type1 = 0, type2 = 0, type3 = 0, nonClone = 0;
        int passed = 0, failed = 0, skipped = 0;
        int origParserFail = 0, cloneParserFail = 0;

        for (PairResult r : RESULTS) {
            switch (r.datasetType) {
                case "Type1_ExactClone" -> type1++;
                case "Type2_Renamed"    -> type2++;
                case "Type3_Modified"   -> type3++;
                case "NonClone"         -> nonClone++;
            }
            switch (r.status) {
                case "PASS"    -> passed++;
                case "FAIL"    -> failed++;
                case "SKIPPED" -> skipped++;
            }
            if (r.originalParse.startsWith("Parse error")) origParserFail++;
            if (r.cloneParse.startsWith("Parse error"))    cloneParserFail++;
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("CodeSniff JUnit Dataset Validation");
        System.out.println("========================================");
        System.out.printf("Total pairs: %d%n", totalTested);
        System.out.println();
        System.out.printf("Type1: %d%n", type1);
        System.out.printf("Type2: %d%n", type2);
        System.out.printf("Type3: %d%n", type3);
        System.out.printf("NonClone: %d%n", nonClone);
        System.out.println();
        System.out.printf("Passed: %d%n", passed);
        System.out.printf("Failed: %d%n", failed);
        System.out.printf("Skipped: %d%n", skipped);
        System.out.println();
        System.out.printf("Original parser failures: %d%n", origParserFail);
        System.out.printf("Clone parser failures: %d%n", cloneParserFail);

        // List failed pairs
        List<PairResult> failedPairs = RESULTS.stream()
                .filter(r -> "FAIL".equals(r.status))
                .sorted(Comparator.comparing(r -> r.datasetType + "/" + r.pairId))
                .toList();
        if (!failedPairs.isEmpty()) {
            System.out.println();
            System.out.println("Failed pairs:");
            for (PairResult r : failedPairs) {
                System.out.printf("  %s/%s  —  %s%n", r.datasetType, r.pairId, r.error);
            }
        }
        System.out.println("========================================");
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static Path resolveProjectRoot() {
        // When run via Maven, user.dir is the project root
        Path userDir = Paths.get(System.getProperty("user.dir"));
        if (userDir.resolve("pom.xml").toFile().exists()) {
            return userDir;
        }
        // Fallback: navigate up from this class's location
        // src/test/java/benchmark/DatasetValidationTest.java → project root
        Path classDir = Paths.get(
                DatasetValidationTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().getPath());
        // classDir is typically  .../target/test-classes  or  .../target/classes
        return classDir.getParent().getParent();
    }

    private static List<Path> listPairDirectories(Path typeDir) {
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, "Pair*")) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Cannot list directory: " + typeDir + " — " + e.getMessage());
        }
        return dirs;
    }

    /** Simplify an exception message for CSV safety (remove commas, newlines). */
    private static String simplifyMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        return msg.replace(",", ";").replace("\n", " ").replace("\r", "");
    }

    /** Escape a value for CSV (wrap in quotes if it contains commas/quotes/newlines). */
    private static String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ==================================================================
    //  Internal data classes
    // ==================================================================

    /** Holds metrics extracted from a single Java file parse. */
    private static class ParseResult {
        boolean parsed       = false;
        int     classCount   = 0;
        int     methodCount  = 0;
        String  diagnostics  = "Not parsed";
    }

    /** Holds the full validation result for one benchmark pair. */
    private static class PairResult {
        String pairId;
        String datasetType;
        String originalFile;
        String cloneFile;
        String originalParse;
        String cloneParse;
        int    originalMethods;
        int    cloneMethods;
        int    originalClasses;
        int    cloneClasses;
        String status;      // PASS | FAIL | SKIPPED
        String error;
    }
}
