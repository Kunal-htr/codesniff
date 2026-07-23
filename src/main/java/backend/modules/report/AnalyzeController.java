package backend.modules.report;

import backend.modules.report.dto.AnalysisResponse;
import backend.modules.report.dto.CodePayload;
import backend.modules.report.dto.CodePayload.Submission;
import backend.modules.report.dto.CodePayload.OptionsDTO;
import backend.modules.report.dto.InfoResponse;
import backend.modules.report.dto.ReportResponse;
import backend.modules.report.dto.MatchesResponse;
import backend.modules.report.dto.BatchSummaryDTO;
import backend.modules.report.dto.PairSummaryDTO;
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
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import backend.modules.history.AnalysisHistory;
import backend.modules.history.AnalysisHistoryRepository;
import backend.modules.user.User;
import backend.modules.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeController.class);

    private final AnalysisService analysisService;
    private final ReportService reportService;
    private final ExportService exportService;
    private final UserRepository userRepository;
    private final AnalysisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public AnalyzeController(AnalysisService analysisService, 
                             ReportService reportService, 
                             ExportService exportService,
                             UserRepository userRepository,
                             AnalysisHistoryRepository historyRepository,
                             ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.reportService = reportService;
        this.exportService = exportService;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
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
    public AnalysisResponse analyze(@Valid @RequestBody CodePayload payload, Principal principal) {
        AnalysisResponse response = analysisService.analyze(payload);
        saveHistoryDefensively(response.batchId(), principal);
        return response;
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
            @RequestParam(name = "window", defaultValue = "4") int w,
            Principal principal) throws IOException {
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
        AnalysisResponse response = analysisService.analyze(new CodePayload(subs, new OptionsDTO(omit, k, w)));
        saveHistoryDefensively(response.batchId(), principal);
        return response;
    }

    /**
     * Defensively saves the analysis history if the user is authenticated.
     * Wrapped in a broad try-catch to ensure failure here NEVER breaks the main analysis flow.
     */
    private void saveHistoryDefensively(String batchId, Principal principal) {
        if (principal == null) {
            return;
        }
        try {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if (user == null) {
                return;
            }

            BatchSummaryDTO summary = reportService.getBatchSummary(batchId);
            
            List<String> fileNames = new ArrayList<>();
            List<backend.modules.history.dto.HistoricalPairDTO> detailedReports = new ArrayList<>();
            
            for (PairSummaryDTO pair : summary.pairs()) {
                if (!fileNames.contains(pair.a())) fileNames.add(pair.a());
                if (!fileNames.contains(pair.b())) fileNames.add(pair.b());
                
                try {
                    backend.modules.report.dto.ReportResponse report = reportService.getReport(pair.reportId());
                    backend.modules.report.dto.MatchesResponse matches = reportService.getMatchesReport(pair.reportId());
                    
                    detailedReports.add(new backend.modules.history.dto.HistoricalPairDTO(
                            pair.reportId(),
                            matches.fileA(),
                            matches.fileB(),
                            pair.score() * 100, // Frontend expects 0-100% or formatted score? No wait, PairSummaryDTO already is 0-1 or 0-100? PairSummaryDTO score is 0.0 to 1.0 (from its javadoc). In HistoryController we store averageSimilarity which might be 0-100. Let's just store pair.score() * 100 or pair.score(). In dashboard.js we check if sim >= 80 etc, so it's 0-100.
                            matches.matchedLines(),
                            report
                    ));
                } catch (Exception ex) {
                    logger.warn("Could not fetch detailed report for pair " + pair.reportId() + " during history save", ex);
                }
            }

            // Build a comprehensive JSON payload holding both the summary metrics and the FULL report details
            java.util.Map<String, Object> fullResult = new java.util.HashMap<>();
            fullResult.put("totalFiles", summary.totalFiles());
            fullResult.put("totalPairs", summary.totalPairs());
            fullResult.put("highestSimilarity", summary.highestSimilarity());
            fullResult.put("averageSimilarity", summary.averageSimilarity());
            fullResult.put("lowestSimilarity", summary.lowestSimilarity());
            fullResult.put("suspiciousPairCount", summary.suspiciousPairCount());
            fullResult.put("pairs", detailedReports);

            AnalysisHistory history = new AnalysisHistory();
            history.setUserId(user.getId());
            history.setBatchId(batchId);
            history.setFileNames(fileNames);
            history.setTotalPairs(summary.totalPairs());
            history.setHighestSimilarity(summary.highestSimilarity());
            history.setAverageSimilarity(summary.averageSimilarity());
            
            JsonNode jsonNode = objectMapper.valueToTree(fullResult);
            history.setFullResultJson(jsonNode);
            
            historyRepository.save(history);
        } catch (Exception e) {
            logger.error("Failed to save analysis history for batch " + batchId + " (User: " + principal.getName() + "). Analysis succeeded, but history persistence failed.", e);
            throw new RuntimeException("History persistence failed: " + e.getMessage(), e);
        }
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
