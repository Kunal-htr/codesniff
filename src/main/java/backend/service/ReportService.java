package backend.service;

import backend.analysis.SimilarityEngine;
import backend.dto.ReportResponse;
import backend.dto.VerdictUtil;
import backend.exception.ReportNotFoundException;
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
     * @return the report response
     * @throws ReportNotFoundException if the report ID does not exist
     */
    public ReportResponse getReport(String id) {
        ReportData r = reportStore.get(id);
        if (r == null) {
            throw new ReportNotFoundException(id);
        }

        String verdict = VerdictUtil.verdict(r.hybrid());
        String verdictDescription = VerdictUtil.verdictDescription(r.hybrid());

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
