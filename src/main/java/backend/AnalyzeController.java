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
            double jaccard, double coverage, double hybrid
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
        int k = payload.options() != null && payload.options().k() != null ? payload.options().k() : 6;
        int w = payload.options() != null && payload.options().window() != null ? payload.options().window() : 4;

        var opt = new SimilarityEngine.Options(omit, k, w);
        var subs = payload.submissions();

        List<PairSummary> out = new ArrayList<>();
        for (int i=0;i<subs.size();i++) {
            for (int j=i+1;j<subs.size();j++) {
                var si = subs.get(i);
                var sj = subs.get(j);

                var a = SimilarityEngine.analyze(safe(si.content()), opt);
                var b = SimilarityEngine.analyze(safe(sj.content()), opt);

                double jac = SimilarityEngine.jaccard(a, b);
                double cov = SimilarityEngine.coverage(a, b, k);
                double hyb = SimilarityEngine.hybridScore(a, b, k);

                String normA = CodeNormalizer.normalize(safe(si.content()), omit);
                String normB = CodeNormalizer.normalize(safe(sj.content()), omit);
                var streamA = Tokenizer.toSymbolStream(Tokenizer.tokenize(normA));
                var streamB = Tokenizer.toSymbolStream(Tokenizer.tokenize(normB));

                String id = UUID.randomUUID().toString();
                REPORTS.put(id, new ReportData(
                        nullTo(si.name(),"A"+i), nullTo(sj.name(),"A"+j),
                        normA, normB, streamA, streamB,
                        a.fps, b.fps, k, w, omit, jac, cov, hyb
                ));

                out.add(new PairSummary(
                        nullTo(si.name(),"A"+i), nullTo(sj.name(),"A"+j),
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

            /* ===== HTML HEAD ===== */
            html.append("""
        <!doctype html>
        <html>
        <head>
            <meta charset='utf-8'>
            <meta name='viewport' content='width=device-width,initial-scale=1'/>
            <title>CodeSniff Report</title>
            <link rel='stylesheet' href='/style.css'/>
            <style>
                body {
                    background: linear-gradient(180deg, #f7fbff, #eef6ff);
                    padding: 20px;
                }
                .report-back { margin-bottom: 14px; }
                .report-back .btn {
                    padding: 8px 14px;
                    border-radius: 8px;
                    background: linear-gradient(180deg,#ffffff,#f4faff);
                    font-weight: 600;
                    border: 1px solid var(--border);
                }
            </style>
        </head>
        <body>
        <div class='container'>
        """);

            /* ===== CLOSE BUTTON ===== */
            html.append("""
        <div class='report-back'>
            <button class='btn' onclick="window.close()">Close</button>
        </div>
        """);

            /* ===== PAIRWISE REPORT CARD ===== */
            html.append("<div class='card'><h2>Pairwise Report</h2>");

            html.append("<div class='tag'>A: ").append(escape(r.nameA())).append("</div>");
            html.append("<div class='tag'>B: ").append(escape(r.nameB())).append("</div>");
            html.append("<div class='tag'>k=").append(r.k()).append("</div>");
            html.append("<div class='tag'>w=").append(r.window()).append("</div>");
            html.append("<div class='tag'>omitComments=").append(r.omitComments()).append("</div>");

            html.append("""
        <table>
            <thead>
                <tr><th>Metric</th><th>Value</th></tr>
            </thead>
            <tbody>
        """);

            html.append("<tr><td>Hybrid</td><td class='").append(cssClass).append("'>")
                    .append(String.format("%.1f%%", r.hybrid() * 100)).append(" (").append(band).append(")")
                    .append("</td></tr>");

            html.append("<tr><td>Jaccard</td><td>")
                    .append(String.format("%.1f%%", r.jaccard() * 100)).append("</td></tr>");

            html.append("<tr><td>Coverage</td><td>")
                    .append(String.format("%.1f%%", r.coverage() * 100)).append("</td></tr>");

            html.append("<tr><td>Fingerprints A</td><td>")
                    .append(r.fpsA() == null ? 0 : r.fpsA().size()).append("</td></tr>");

            html.append("<tr><td>Fingerprints B</td><td>")
                    .append(r.fpsB() == null ? 0 : r.fpsB().size()).append("</td></tr>");

            html.append("</tbody></table></div>");

            /* ===== NORMALIZED A ===== */
            html.append("<div class='card'><h3>Normalized A</h3><code>")
                    .append(escape(r.normA())).append("</code></div>");

            /* ===== NORMALIZED B ===== */
            html.append("<div class='card'><h3>Normalized B</h3><code>")
                    .append(escape(r.normB())).append("</code></div>");

            /* ===== END ===== */
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