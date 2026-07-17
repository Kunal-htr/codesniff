package backend.controller;

import backend.dto.AnalysisResponse;
import backend.dto.CodePayload;
import backend.dto.CodePayload.Submission;
import backend.dto.CodePayload.OptionsDTO;
import backend.dto.ReportResponse;
import backend.service.AnalysisService;
import backend.service.ReportService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

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

    public AnalyzeController(AnalysisService analysisService, ReportService reportService) {
        this.analysisService = analysisService;
        this.reportService = reportService;
    }

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
        info.put("version", "v0.6");
        info.put("status", "running");
        info.put("timestamp", new java.util.Date().toString());
        info.put("activeReports", reportService.activeReportCount());
        return ResponseEntity.ok(info);
    }

    /* ===== JSON analyze (paste or pre-read files) ===== */
    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse analyze(@RequestBody CodePayload payload) {
        return analysisService.analyze(payload);
    }

    /* ===== Multipart analyze (true file upload) ===== */
    @PostMapping(path = "/analyze-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse analyzeFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(name = "omitComments", defaultValue = "true") boolean omit,
            @RequestParam(name = "k", defaultValue = "6") int k,
            @RequestParam(name = "window", defaultValue = "4") int w) throws IOException {
        if (files == null || files.size() < 2) {
            return new AnalysisResponse(List.of());
        }
        List<Submission> subs = new ArrayList<>();
        for (var f : files) {
            subs.add(new Submission(f.getOriginalFilename(), new String(f.getBytes())));
        }
        return analysisService.analyze(new CodePayload(subs, new OptionsDTO(omit, k, w)));
    }

    /*
     * ===== JSON REPORT (v0.6: returns JSON instead of HTML — intentional change)
     * =====
     */
    @GetMapping(path = "/report/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportResponse> report(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ReportResponse response = reportService.getReport(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
