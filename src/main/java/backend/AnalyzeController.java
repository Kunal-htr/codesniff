package backend;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    // In-memory store for per-pair report data
    private static final Map<String, ReportData> REPORTS = new ConcurrentHashMap<>();

    /* ===== DTOs ===== */
    public static record Submission(String name, String content) {}
    public static record OptionsDTO(Boolean omitComments, Integer k, Integer window) {}
    public static record CodePayload(List<Submission> submissions, OptionsDTO options) {}

    public static record PairSummary(
            String a, String b,
            double score,        // hybrid
            double jaccard,
            double coverage,
            String reportId
    ) {}
    public static record ResponseDTO(List<PairSummary> summary) {}

    public static record ReportData(
            String nameA, String nameB,
            String normA, String normB,
            List<String> streamA, List<String> streamB,
            List<SimilarityEngine.Fingerprint> fpsA,
            List<SimilarityEngine.Fingerprint> fpsB,
            int k, int window, boolean omitComments,
            double jaccard, double coverage, double lcs, double ast, double hybrid,
            boolean operatorDivergent
    ) {}

    /* ===== HEALTH CHECK (used by cron + UptimeRobot to keep server warm) ===== */
    @GetMapping(path = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("CodeSniff is alive!");
    }

    /* ===== SERVER INFO (optional - shows version + uptime) ===== */
    @GetMapping(path = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "CodeSniff");
        info.put("version", "v0.5");
        info.put("status", "running");
        info.put("timestamp", new java.util.Date().toString());
        info.put("activeReports", REPORTS.size());
        return ResponseEntity.ok(info);
    }

    /* ===== JSON analyze (paste or pre-read files) ===== */
    @PostMapping(path="/analyze", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO analyze(@RequestBody CodePayload payload) {
        if (payload == null || payload.submissions() == null || payload.submissions().size() < 2) {
            return new ResponseDTO(List.of());
        }
        boolean omit = payload.options() != null && Boolean.TRUE.equals(payload.options().omitComments());
        int defaultK = payload.options() != null && payload.options().k() != null ? payload.options().k() : 6;
        int defaultW = payload.options() != null && payload.options().window() != null ? payload.options().window() : 4;
        var defaultOpt = new SimilarityEngine.Options(omit, defaultK, defaultW);

        var subs = payload.submissions();
        int N = subs.size();

        // 1. Pre-analyze all submissions exactly once using the default/neutral k/w options (O(N) pass)
        List<SimilarityEngine.Analysis> defaultAnalyses = new ArrayList<>();
        for (var sub : subs) {
            defaultAnalyses.add(SimilarityEngine.analyze(safe(sub.content()), defaultOpt));
        }

        // 2. Compute corpus-wide fingerprint document frequencies (DF) using the default (neutral) k/w pass.
        // We choose to build corpus frequency tables using a fixed neutral k/w so that the frequency count
        // baseline is consistent across all documents in the batch.
        Map<Long, Integer> fpDF = new HashMap<>();
        for (var a : defaultAnalyses) {
            for (long h : a.fpSet) {
                fpDF.put(h, fpDF.getOrDefault(h, 0) + 1);
            }
        }

        // 3. Compute corpus-wide statement document frequencies (DF) using the default pass.
        Map<Long, Integer> stmtDF = new HashMap<>();
        for (var a : defaultAnalyses) {
            var stmts = StatementGrouper.groupStatements(a.symbolStream);
            Set<Long> uniqueStmts = new HashSet<>();
            for (var s : stmts) {
                uniqueStmts.add(s.hash);
            }
            for (long h : uniqueStmts) {
                stmtDF.put(h, stmtDF.getOrDefault(h, 0) + 1);
            }
        }

        // 4. Compute N-generalized IDF weights: weight = ln(1.0 + N / df) / ln(1.0 + N)
        double logNPlus1 = Math.log(1.0 + N);
        Map<Long, Double> defaultFpWeights = new HashMap<>();
        for (var entry : fpDF.entrySet()) {
            long h = entry.getKey();
            int df = entry.getValue();
            double weight = Math.log(1.0 + (double) N / df) / logNPlus1;
            defaultFpWeights.put(h, weight);
        }

        Map<Long, Double> stmtWeights = new HashMap<>();
        for (var entry : stmtDF.entrySet()) {
            long h = entry.getKey();
            int df = entry.getValue();
            double weight = Math.log(1.0 + (double) N / df) / logNPlus1;
            stmtWeights.put(h, weight);
        }

        // 5. Compare all pairs, computing pair-specific adaptive k/w dynamically
        List<PairSummary> out = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                var si = subs.get(i);
                var sj = subs.get(j);
                var aDefault = defaultAnalyses.get(i);
                var bDefault = defaultAnalyses.get(j);

                // Compute pair-specific scaled k/w based on the minimum token count of the two compared files
                int minTokens = Math.min(aDefault.tokenCount, bDefault.tokenCount);
                int pairK = defaultK;
                int pairW = defaultW;
                if (minTokens < 50) {
                    pairK = Math.max(3, pairK - 2);
                    pairW = Math.max(2, pairW - 2);
                } else if (minTokens < 120) {
                    pairK = Math.max(4, pairK - 1);
                    pairW = Math.max(3, pairW - 1);
                }

                SimilarityEngine.Analysis aComp;
                SimilarityEngine.Analysis bComp;
                Map<Long, Double> pairFpWeights;

                if (pairK == defaultK && pairW == defaultW) {
                    // No scaling required: reuse pre-computed default analyses and default corpus weights
                    aComp = aDefault;
                    bComp = bDefault;
                    pairFpWeights = defaultFpWeights;
                } else {
                    // Sizing scaled for tiny files: re-analyze the two files for this pair using the pair-specific Options
                    var pairOpt = new SimilarityEngine.Options(omit, pairK, pairW);
                    aComp = SimilarityEngine.analyze(aDefault.rawCode, pairOpt);
                    bComp = SimilarityEngine.analyze(bDefault.rawCode, pairOpt);
                    
                    // Since fingerprint hashes are different for scaled k, we default weights to empty (unweighted 1.0)
                    // for scaled tiny files where fingerprint rarity discounting is not necessary.
                    pairFpWeights = Map.of();
                }

                double jac = SimilarityEngine.jaccard(aComp, bComp, pairFpWeights);
                double cov = SimilarityEngine.coverage(aComp, bComp, pairK, pairFpWeights);
                double lcs = SimilarityEngine.lcsSimilarity(aComp, bComp, stmtWeights);
                double ast = SimilarityEngine.astSimilarity(aComp, bComp);
                double hyb = SimilarityEngine.hybridScore(aComp, bComp, pairK, pairFpWeights, stmtWeights);

                // Detect if the similarity is driven primarily by operator differences
                backend.ast.ASTNode treeA = backend.ast.ASTBuilder.build(aComp.rawCode);
                backend.ast.ASTNode treeB = backend.ast.ASTBuilder.build(bComp.rawCode);
                backend.ast.ASTSimilarityResult insensitiveRes = backend.ast.ASTComparator.compare(treeA, treeB, true);
                double astInsensitive = insensitiveRes.getSimilarity();
                boolean operatorDivergent = (hyb >= 0.60 && astInsensitive >= 0.90 && (astInsensitive - ast) >= 0.05);

                String id = UUID.randomUUID().toString();
                REPORTS.put(id, new ReportData(
                        nullTo(si.name(), "A" + i), nullTo(sj.name(), "A" + j),
                        aComp.normalizedCode, bComp.normalizedCode, aComp.symbolStream, bComp.symbolStream,
                        aComp.fps, bComp.fps, defaultK, defaultW, omit, jac, cov, lcs, ast, hyb, operatorDivergent
                ));

                out.add(new PairSummary(
                        nullTo(si.name(), "A" + i), nullTo(sj.name(), "A" + j),
                        hyb, jac, cov, id
                ));
            }
        }
        return new ResponseDTO(out);
    }

    /* ===== Multipart analyze (true file upload) ===== */
    @PostMapping(path="/analyze-files", consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO analyzeFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(name="omitComments", defaultValue="true") boolean omit,
            @RequestParam(name="k", defaultValue="6") int k,
            @RequestParam(name="window", defaultValue="4") int w
    ) throws IOException {
        if (files == null || files.size() < 2) return new ResponseDTO(List.of());
        List<Submission> subs = new ArrayList<>();
        for (var f : files) subs.add(new Submission(f.getOriginalFilename(), new String(f.getBytes())));
        return analyze(new CodePayload(subs, new OptionsDTO(omit, k, w)));
    }

    /* ===== HTML REPORT PAGE (matches site UI theme) ===== */
    @GetMapping(path="/report/{id}", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report(@PathVariable("id") String id) {
        try {
            if (id == null || id.isBlank()) {
                return ResponseEntity.badRequest().body(htmlError("Bad report id."));
            }

            ReportData r = REPORTS.get(id);
            if (r == null) {
                return ResponseEntity.status(404).body(htmlError(
                        "Report not found. The report store resets on restart. Run a new analysis and click View again."
                ));
            }

            String band = r.hybrid() > 0.70 ? "High"
                    : r.hybrid() > 0.45 ? "Suspicious"
                    : r.hybrid() > 0.25 ? "Review" : "Clean";
            String cssClass = r.hybrid() > 0.70 ? "bad" : r.hybrid() > 0.45 ? "warn" : "ok";

            StringBuilder html = new StringBuilder();

            /* ===== HTML HEAD & STYLE ===== */
            html.append("""
        <!doctype html>
        <html>
        <head>
            <meta charset='utf-8'>
            <meta name='viewport' content='width=device-width,initial-scale=1'/>
            <title>CodeSniff Detailed Report</title>
            <link rel='stylesheet' href='/style.css'/>
            <style>
                :root {
                    --bg-report: #f8fafc;
                    --card-report: #ffffff;
                    --text-primary: #0f172a;
                    --text-secondary: #475569;
                    --primary-color: #3b82f6;
                    --success-color: #10b981;
                    --warning-color: #f59e0b;
                    --danger-color: #ef4444;
                    --border-light: #e2e8f0;
                }
                body {
                    background-color: var(--bg-report);
                    color: var(--text-primary);
                    font-family: 'Inter', system-ui, -apple-system, sans-serif;
                    padding: 40px 20px;
                    margin: 0;
                }
                .container {
                    max-width: 900px;
                    margin: 0 auto;
                }
                .header-bar {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 24px;
                }
                .header-title {
                    font-size: 24px;
                    font-weight: 800;
                    color: var(--text-primary);
                    margin: 0;
                }
                .btn-close {
                    padding: 10px 20px;
                    border-radius: 8px;
                    background: #ffffff;
                    border: 1px solid var(--border-light);
                    font-weight: 600;
                    color: var(--text-secondary);
                    cursor: pointer;
                    transition: all 0.2s ease;
                }
                .btn-close:hover {
                    background: #f1f5f9;
                    color: var(--text-primary);
                }
                
                /* File Comparison Banner */
                .comparison-card {
                    background: var(--card-report);
                    border-radius: 16px;
                    border: 1px solid var(--border-light);
                    padding: 24px;
                    margin-bottom: 24px;
                    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
                }
                .comparison-flex {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    gap: 16px;
                }
                .file-node {
                    flex: 1;
                    padding: 16px;
                    background: #f8fafc;
                    border-radius: 12px;
                    border: 1px dashed #cbd5e1;
                    text-align: center;
                }
                .file-node h3 {
                    margin: 0 0 4px 0;
                    font-size: 11px;
                    color: var(--text-secondary);
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }
                .file-node p {
                    margin: 0;
                    font-weight: 700;
                    color: var(--text-primary);
                    font-size: 16px;
                    word-break: break-all;
                }
                .vs-badge {
                    font-weight: 800;
                    color: var(--text-secondary);
                    font-size: 14px;
                    background: #e2e8f0;
                    padding: 8px 12px;
                    border-radius: 50%;
                }

                /* Hybrid Dial Card */
                .dial-card {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    background: var(--card-report);
                    border-radius: 16px;
                    border: 1px solid var(--border-light);
                    padding: 32px;
                    margin-bottom: 24px;
                    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
                    gap: 24px;
                }
                .dial-left {
                    flex: 1;
                }
                .dial-left h2 {
                    margin: 0 0 8px 0;
                    font-size: 22px;
                    font-weight: 800;
                }
                .dial-left p {
                    margin: 0;
                    color: var(--text-secondary);
                    font-size: 14px;
                    line-height: 1.5;
                }
                .dial-right {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    width: 140px;
                    height: 140px;
                    border-radius: 50%;
                    position: relative;
                }
                .dial-score {
                    font-size: 32px;
                    font-weight: 900;
                    margin: 0;
                }
                .dial-label {
                    font-size: 11px;
                    font-weight: 700;
                    text-transform: uppercase;
                    margin-top: 2px;
                }

                /* Verdict Colors */
                .verdict-high {
                    color: var(--danger-color);
                    border: 8px solid #fecaca;
                    background: #fef2f2;
                }
                .verdict-suspicious {
                    color: var(--warning-color);
                    border: 8px solid #fde68a;
                    background: #fffbeb;
                }
                .verdict-review {
                    color: var(--primary-color);
                    border: 8px solid #bfdbfe;
                    background: #eff6ff;
                }
                .verdict-clean {
                    color: var(--success-color);
                    border: 8px solid #a7f3d0;
                    background: #ecfdf5;
                }

                /* Grid of Metrics */
                .metrics-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 16px;
                    margin-bottom: 24px;
                }
                .metric-card {
                    background: var(--card-report);
                    border-radius: 12px;
                    border: 1px solid var(--border-light);
                    padding: 20px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.01);
                }
                .metric-name {
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--text-secondary);
                    margin-bottom: 8px;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }
                .metric-value {
                    font-size: 26px;
                    font-weight: 800;
                    color: var(--text-primary);
                    margin-bottom: 12px;
                }
                .metric-progress {
                    height: 6px;
                    background: #e2e8f0;
                    border-radius: 3px;
                    overflow: hidden;
                }
                .metric-progress-bar {
                    height: 100%;
                    background: var(--primary-color);
                    border-radius: 3px;
                }
                .metric-desc {
                    margin-top: 8px;
                    font-size: 11px;
                    color: var(--text-secondary);
                }

                /* Metadata Card */
                .meta-card {
                    background: var(--card-report);
                    border-radius: 12px;
                    border: 1px solid var(--border-light);
                    padding: 20px;
                }
                .meta-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                    gap: 16px;
                }
                .meta-item {
                    display: flex;
                    flex-direction: column;
                }
                .meta-label {
                    font-size: 11px;
                    font-weight: 700;
                    color: var(--text-secondary);
                    text-transform: uppercase;
                    margin-bottom: 4px;
                }
                .meta-val {
                    font-weight: 700;
                    font-size: 14px;
                    color: var(--text-primary);
                }
            </style>
        </head>
        <body>
        <div class='container'>
        """);

            /* ===== HEADER BAR ===== */
            html.append("<div class='header-bar'>")
                .append("<h1 class='header-title'>Detailed Analysis Report</h1>")
                .append("<button class='btn-close' onclick=\"window.close()\">Close</button>")
                .append("</div>");

            /* ===== OPERATOR DIVERGENT WARNING ===== */
            if (r.operatorDivergent()) {
                html.append("""
                    <div class='card warn-banner' style='background: #fffbeb; border: 1px solid #fef3c7; border-left: 5px solid #d97706; padding: 14px; margin-bottom: 24px; border-radius: 12px;'>
                        <p style='margin: 0; color: #b45309; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 8px;'>
                            <span>⚠️</span> High similarity driven primarily by operator differences (e.g. comparison direction) — may indicate independent implementations of complementary logic (max/min, ascending/descending). Recommend manual review before flagging as plagiarism.
                        </p>
                    </div>
                """);
            }

            /* ===== FILE COMPARISON CARD ===== */
            html.append("<div class='comparison-card'>")
                .append("<div class='comparison-flex'>")
                .append("<div class='file-node'><h3>File A</h3><p>").append(escape(r.nameA())).append("</p></div>")
                .append("<div class='vs-badge'>VS</div>")
                .append("<div class='file-node'><h3>File B</h3><p>").append(escape(r.nameB())).append("</p></div>")
                .append("</div>")
                .append("</div>");

            /* ===== HYBRID VERDICT CARD ===== */
            String verdictStyle = cssClass.equals("bad") ? "verdict-high"
                                : cssClass.equals("warn") ? "verdict-suspicious"
                                : cssClass.equals("ok") && r.hybrid() > 0.25 ? "verdict-review" : "verdict-clean";
            String verdictDesc = cssClass.equals("bad") ? "High similarity detected. There is a high probability of direct copy/paste or minimal rewriting."
                               : cssClass.equals("warn") ? "Suspicious similarity level. Manual review is recommended to inspect similar blocks and structures."
                               : cssClass.equals("ok") && r.hybrid() > 0.25 ? "Moderate similarity. Code exhibits some shared components that should be reviewed."
                               : "Low similarity. The two files appear to be independent implementations.";

            html.append("<div class='dial-card'>")
                .append("<div class='dial-left'>")
                .append("<h2>Similarity Verdict: ").append(band).append("</h2>")
                .append("<p>").append(verdictDesc).append("</p>")
                .append("</div>")
                .append("<div class='dial-right ").append(verdictStyle).append("'>")
                .append("<span class='dial-score'>").append(String.format("%.1f%%", r.hybrid() * 100)).append("</span>")
                .append("<span class='dial-label'>Hybrid</span>")
                .append("</div>")
                .append("</div>");

            /* ===== METRICS GRID ===== */
            html.append("<div class='metrics-grid'>");

            // 1. Jaccard
            html.append("<div class='metric-card'>")
                .append("<div class='metric-name'>Jaccard Similarity</div>")
                .append("<div class='metric-value'>").append(String.format("%.1f%%", r.jaccard() * 100)).append("</div>")
                .append("<div class='metric-progress'><div class='metric-progress-bar' style='width: ").append(r.jaccard() * 100).append("%'></div></div>")
                .append("<div class='metric-desc'>Measures the ratio of matching unique token fingerprints between the two files.</div>")
                .append("</div>");

            // 2. Coverage
            html.append("<div class='metric-card'>")
                .append("<div class='metric-name'>Coverage</div>")
                .append("<div class='metric-value'>").append(String.format("%.1f%%", r.coverage() * 100)).append("</div>")
                .append("<div class='metric-progress'><div class='metric-progress-bar' style='width: ").append(r.coverage() * 100).append("%'></div></div>")
                .append("<div class='metric-desc'>Percentage of unique token positions in the smaller file that are covered in the larger file.</div>")
                .append("</div>");

            // 3. LCS
            html.append("<div class='metric-card'>")
                .append("<div class='metric-name'>LCS Alignment</div>")
                .append("<div class='metric-value'>").append(String.format("%.1f%%", r.lcs() * 100)).append("</div>")
                .append("<div class='metric-progress'><div class='metric-progress-bar' style='width: ").append(r.lcs() * 100).append("%'></div></div>")
                .append("<div class='metric-desc'>Longest Common Subsequence of logical statements, reflecting program flow similarity.</div>")
                .append("</div>");

            // 4. AST
            html.append("<div class='metric-card'>")
                .append("<div class='metric-name'>AST Structural Match</div>")
                .append("<div class='metric-value'>").append(String.format("%.1f%%", r.ast() * 100)).append("</div>")
                .append("<div class='metric-progress'><div class='metric-progress-bar' style='width: ").append(r.ast() * 100).append("%'></div></div>")
                .append("<div class='metric-desc'>Measures the similarity of Abstract Syntax Trees constructed via JavaParser.</div>")
                .append("</div>");

            html.append("</div>"); // metrics-grid

            /* ===== METADATA & PARAMS ===== */
            Set<Long> setA = new HashSet<>();
            if (r.fpsA() != null) {
                for (var f : r.fpsA()) setA.add(f.hash);
            }
            Set<Long> setB = new HashSet<>();
            if (r.fpsB() != null) {
                for (var f : r.fpsB()) setB.add(f.hash);
            }
            int sizeA = setA.size();
            int sizeB = setB.size();
            setA.retainAll(setB);
            int matchedCount = setA.size();

            html.append("<div class='meta-card'>")
                .append("<div class='meta-grid'>")
                .append("<div class='meta-item'><span class='meta-label'>K-Gram Size (k)</span><span class='meta-val'>").append(r.k()).append("</span></div>")
                .append("<div class='meta-item'><span class='meta-label'>Window Size (w)</span><span class='meta-val'>").append(r.window()).append("</span></div>")
                .append("<div class='meta-item'><span class='meta-label'>Omit Comments</span><span class='meta-val'>").append(r.omitComments() ? "Yes" : "No").append("</span></div>")
                .append("<div class='meta-item'><span class='meta-label'>Fingerprint Matches</span><span class='meta-val'>").append(String.format("%d / %d & %d", matchedCount, sizeA, sizeB)).append("</span></div>")
                .append("</div>")
                .append("</div>");

            html.append("""
        </div> <!-- container -->
        </body>
        </html>
        """);

            return ResponseEntity.ok(html.toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().body(htmlError("Internal error: " + escape(ex.toString())));
        }
    }

    /* ===== Helpers ===== */
    private static String safe(String s){ return s == null ? "" : s; }
    private static String nullTo(String s, String def){ return s == null ? def : s; }
    private static String escape(String s){
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private static String htmlError(String msg){
        return "<!doctype html><html><body style='font-family:sans-serif;background:#0b0f17;color:#e8eefc;padding:24px'>"
                + "<div style='background:#121826;border:1px solid #1f2a44;border-radius:10px;padding:16px'>"
                + "<h2>Error</h2><p>"+ escape(msg) +"</p></div></body></html>";
    }
}