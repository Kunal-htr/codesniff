package backend.store;

import backend.analysis.SimilarityEngine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for per-pair report data.
 * <p>
 * Owns the {@code ConcurrentHashMap} that was previously embedded in
 * {@code AnalyzeController}. Thread-safe for concurrent reads/writes.
 */
@Component
public class ReportStore {

    private final Map<String, ReportData> reports = new ConcurrentHashMap<>();

    /**
     * Internal report data record. Retains all fields needed for report
     * generation and CSV export. Not exposed directly to clients — the
     * {@code ReportService} maps this to public DTOs.
     */
    public record ReportData(
            String nameA, String nameB,
            String normA, String normB,
            List<String> streamA, List<String> streamB,
            List<SimilarityEngine.Fingerprint> fpsA,
            List<SimilarityEngine.Fingerprint> fpsB,
            int k, int window, boolean omitComments,
            double jaccard, double coverage, double lcs, double ast, double hybrid,
            boolean operatorDivergent
    ) {}

    /** Store a report under the given ID. */
    public void put(String id, ReportData data) {
        reports.put(id, data);
    }

    /** Retrieve a report by ID, or {@code null} if not found. */
    public ReportData get(String id) {
        return reports.get(id);
    }

    /** Number of reports currently cached. */
    public int size() {
        return reports.size();
    }
}
