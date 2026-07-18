package backend.modules.report;

import backend.modules.report.dto.AnalysisResponse;
import backend.modules.report.dto.CodePayload;
import backend.modules.report.dto.CodePayload.Submission;
import backend.modules.report.dto.CodePayload.OptionsDTO;
import backend.modules.report.dto.InfoResponse;
import backend.modules.report.dto.ReportResponse;
import backend.modules.report.dto.MatchesResponse;
import backend.modules.report.dto.BatchSummaryDTO;
import backend.modules.report.AnalysisService;
import backend.modules.report.ReportService;
import backend.modules.report.ExportService;
import org.springframework.http.HttpHeaders;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for CodeSniff analysis endpoints.
 * <p>
 * Thin controller — receives requests, validates input, delegates to services,
 * and returns responses. No business logic, no report building, no direct
 * cache access.
 */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final AnalysisService analysisService;
    private final ReportService reportService;
    private final ExportService exportService;

    public AnalyzeController(AnalysisService analysisService, ReportService reportService, ExportService exportService) {
        this.analysisService = analysisService;
        this.reportService = reportService;
        this.exportService = exportService;
    }

    /**
     * Endpoint for server health checking.
     * <p>
     * Typically polled by cron jobs or uptime monitors to keep the backend warm
     * and prevent cloud deployment instances from entering idle sleep state.
     *
     * @return plain text response confirming server status (HTTP 200)
     */
    @GetMapping(path = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("CodeSniff is alive!");
    }

    /**
     * Retrieves high-level application and runtime metadata.
     *
     * @return info response DTO containing app version, status, and cached report counts (HTTP 200)
     */
    @GetMapping(path = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InfoResponse> info() {
        InfoResponse response = new InfoResponse(
                "CodeSniff",
                "v0.6",
                "running",
                new java.util.Date().toString(),
                reportService.activeReportCount()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Executes pairwise similarity analysis on Java code submissions provided via JSON payload.
     * <p>
     * Validates options and code submissions before invoking the underlying similarity engine.
     *
     * @param payload DTO containing the submissions list and engine parameters (validated via @Valid)
     * @return analysis response DTO containing lists of evaluated pairs and their similarity metrics
     */
    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse analyze(@Valid @RequestBody CodePayload payload) {
        return analysisService.analyze(payload);
    }

    /**
     * Executes pairwise similarity analysis on Java files uploaded as multipart form data.
     *
     * @param files        list of uploaded multipart files containing source code
     * @param omitComments whether to strip comments during preprocessing
     * @param k            the token length of k-grams for fingerprinting
     * @param w            the winnowing window size
     * @return analysis response DTO containing lists of evaluated pairs and their similarity metrics
     * @throws IOException              if file read errors occur
     * @throws IllegalArgumentException if parameter bounds or validation requirements are violated
     */
    @PostMapping(path = "/analyze-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse analyzeFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(name = "omitComments", defaultValue = "true") boolean omit,
            @RequestParam(name = "k", defaultValue = "6") int k,
            @RequestParam(name = "window", defaultValue = "4") int w) throws IOException {
        if (files == null || files.size() < 2) {
            throw new IllegalArgumentException("At least 2 files are required for comparison");
        }
        if (k < 3 || k > 64) {
            throw new IllegalArgumentException("k must be between 3 and 64");
        }
        if (w < 1 || w > 128) {
            throw new IllegalArgumentException("window must be between 1 and 128");
        }
        List<Submission> subs = new ArrayList<>();
        for (var f : files) {
            subs.add(new Submission(f.getOriginalFilename(), new String(f.getBytes())));
        }
        return analysisService.analyze(new CodePayload(subs, new OptionsDTO(omit, k, w)));
    }

    /**
     * Retrieves a detailed comparison report for a specific compared pair using its unique report ID.
     *
     * @param id the unique UUID of the generated report
     * @return the report details DTO containing metrics, verdicts, and match logs (HTTP 200)
     * @throws backend.common.exception.ReportNotFoundException if the report ID does not exist in cache (HTTP 404)
     */
    @GetMapping(path = "/report/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportResponse> report(@PathVariable("id") String id) {
        ReportResponse response = reportService.getReport(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the raw code and matched line regions for a compared pair.
     *
     * @param id the unique UUID of the generated report
     * @return the MatchesResponse DTO containing raw code and line mapping blocks (HTTP 200)
     * @throws backend.common.exception.ReportNotFoundException if the report ID does not exist in cache (HTTP 404)
     */
    @GetMapping(path = "/report/{id}/matches", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchesResponse> reportMatches(@PathVariable("id") String id) {
        MatchesResponse response = reportService.getMatchesReport(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the batch-level summary statistics for a given analysis batch.
     *
     * @param id the unique UUID of the generated batch
     * @return the BatchSummaryDTO containing aggregated metrics and pair summaries (HTTP 200)
     * @throws backend.common.exception.BatchNotFoundException if the batch ID does not exist in cache (HTTP 404)
     */
    @GetMapping(path = "/batch/{id}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchSummaryDTO> batchSummary(@PathVariable("id") String id) {
        BatchSummaryDTO response = reportService.getBatchSummary(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Exports a detailed pairwise report in multiple formats.
     *
     * @param id the unique UUID of the generated report
     * @param format the desired export format (json, csv, html, pdf)
     * @return the report in the requested format
     * @throws backend.common.exception.ReportNotFoundException if the report ID does not exist in cache
     * @throws IllegalArgumentException if the format is unsupported
     */
    @GetMapping(path = "/report/{id}/export")
    public ResponseEntity<?> exportReport(@PathVariable("id") String id, @RequestParam(name = "format", defaultValue = "json") String format) {
        ReportResponse report = reportService.getReport(id);

        switch (format.toLowerCase()) {
            case "json":
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(report);
            case "csv":
                String csv = exportService.generateCsv(report);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + id + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(csv);
            case "html":
                String html = exportService.generateHtml(report);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            case "pdf":
                byte[] pdf = exportService.generatePdf(report);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + id + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}
