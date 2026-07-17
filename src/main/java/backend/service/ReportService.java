package backend.service;

import backend.analysis.SimilarityEngine;
import backend.dto.ReportResponse;
import backend.store.ReportStore;
import backend.store.ReportStore.ReportData;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds report responses from cached report data.
 * <p>
 * Maps internal {@link ReportData} (which retains engine-level fields like
 * fingerprint lists) into clean, public-facing {@link ReportResponse} DTOs.
 * No internal engine types are leaked to clients.
 */
@Service
public class ReportService {

    private final ReportStore reportStore;

    public ReportService(ReportStore reportStore) {
        this.reportStore = reportStore;
    }

    /**
     * Retrieve a report by ID and map it to a JSON-friendly response.
     *
     * @param id report identifier (UUID string)
     * @return the report response, or {@code null} if not found
     */
    public ReportResponse getReport(String id) {
        ReportData r = reportStore.get(id);
        if (r == null) return null;

        // Compute verdict band
        String verdict;
        String verdictDescription;
        if (r.hybrid() > 0.70) {
            verdict = "High";
            verdictDescription = "High similarity detected. There is a high probability of direct copy/paste or minimal rewriting.";
        } else if (r.hybrid() > 0.45) {
            verdict = "Suspicious";
            verdictDescription = "Suspicious similarity level. Manual review is recommended to inspect similar blocks and structures.";
        } else if (r.hybrid() > 0.25) {
            verdict = "Review";
            verdictDescription = "Moderate similarity. Code exhibits some shared components that should be reviewed.";
        } else {
            verdict = "Clean";
            verdictDescription = "Low similarity. The two files appear to be independent implementations.";
        }

        // Compute fingerprint match statistics
        Set<Long> setA = new HashSet<>();
        if (r.fpsA() != null) {
            for (SimilarityEngine.Fingerprint f : r.fpsA()) setA.add(f.hash);
        }
        Set<Long> setB = new HashSet<>();
        if (r.fpsB() != null) {
            for (SimilarityEngine.Fingerprint f : r.fpsB()) setB.add(f.hash);
        }
        int countA = setA.size();
        int countB = setB.size();
        setA.retainAll(setB);
        int matchCount = setA.size();

        return new ReportResponse(
                r.nameA(), r.nameB(),
                r.jaccard(), r.coverage(), r.lcs(), r.ast(), r.hybrid(),
                verdict, verdictDescription,
                r.operatorDivergent(),
                new ReportResponse.Metadata(
                        r.k(), r.window(), r.omitComments(),
                        matchCount, countA, countB
                )
        );
    }

    /** Number of reports currently cached. */
    public int activeReportCount() {
        return reportStore.size();
    }
}
