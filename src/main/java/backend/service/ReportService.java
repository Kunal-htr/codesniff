package backend.service;

import backend.analysis.SimilarityEngine;
import backend.dto.ReportResponse;
import backend.dto.VerdictUtil;
import backend.dto.MatchesResponse;
import backend.dto.FileInfoDTO;
import backend.dto.MatchedRegionDTO;
import backend.dto.BatchSummaryDTO;
import backend.dto.PairSummaryDTO;
import backend.exception.BatchNotFoundException;
import backend.exception.ReportNotFoundException;
import backend.store.ReportStore;
import backend.store.ReportStore.ReportData;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
                r.divergentOperators(),
                new ReportResponse.Metadata(
                        r.k(), r.window(), r.omitComments(),
                        matchCount, countA, countB
                )
        );
    }

    /**
     * Retrieves raw code and computed matched line regions for a compared pair.
     *
     * @param id report identifier
     * @return MatchesResponse DTO containing raw code and matched regions
     * @throws ReportNotFoundException if the report ID does not exist
     */
    public MatchesResponse getMatchesReport(String id) {
        ReportData r = reportStore.get(id);
        if (r == null) {
            throw new ReportNotFoundException(id);
        }

        // Reconstruct fpSets
        Set<Long> fpSetA = new HashSet<>();
        if (r.fpsA() != null) {
            for (SimilarityEngine.Fingerprint f : r.fpsA()) {
                fpSetA.add(f.hash);
            }
        }
        Set<Long> fpSetB = new HashSet<>();
        if (r.fpsB() != null) {
            for (SimilarityEngine.Fingerprint f : r.fpsB()) {
                fpSetB.add(f.hash);
            }
        }

        // Reconstruct Analysis objects
        SimilarityEngine.Analysis a = new SimilarityEngine.Analysis(
                fpSetA, r.fpsA(), r.streamA().size(), r.streamA(), r.normA(), r.rawA()
        );
        SimilarityEngine.Analysis b = new SimilarityEngine.Analysis(
                fpSetB, r.fpsB(), r.streamB().size(), r.streamB(), r.normB(), r.rawB()
        );

        // Build line maps
        List<Integer> lineMapA = backend.analysis.Tokenizer.buildLineMap(r.rawA());
        List<Integer> lineMapB = backend.analysis.Tokenizer.buildLineMap(r.rawB());

        // Compute matched regions
        List<SimilarityEngine.MatchedRegion> regions = SimilarityEngine.computeMatchedRegions(
                a, b, r.k(), lineMapA, lineMapB
            );

        // Convert regions to DTOs
        List<MatchedRegionDTO> dtoList = new ArrayList<>();
        for (SimilarityEngine.MatchedRegion mr : regions) {
            dtoList.add(new MatchedRegionDTO(
                    mr.startLineA, mr.endLineA, mr.startLineB, mr.endLineB
            ));
        }

        FileInfoDTO fileA = new FileInfoDTO(r.nameA(), r.rawA());
        FileInfoDTO fileB = new FileInfoDTO(r.nameB(), r.rawB());

        return new MatchesResponse(fileA, fileB, dtoList);
    }

    /**
     * Aggregates and returns the batch summary statistics for a given batch ID.
     *
     * @param batchId the unique batch identifier
     * @return BatchSummaryDTO containing aggregated metrics
     * @throws BatchNotFoundException if the batch ID does not exist
     */
    public BatchSummaryDTO getBatchSummary(String batchId) {
        List<PairSummaryDTO> pairs = reportStore.getBatch(batchId);
        if (pairs == null) {
            throw new BatchNotFoundException(batchId);
        }

        int totalPairs = pairs.size();
        Set<String> uniqueFiles = new HashSet<>();
        double highest = 0.0;
        double lowest = totalPairs > 0 ? 1.0 : 0.0;
        double sum = 0.0;
        int suspiciousCount = 0;

        for (PairSummaryDTO pair : pairs) {
            uniqueFiles.add(pair.a());
            uniqueFiles.add(pair.b());
            
            double h = pair.score();
            if (h > highest) highest = h;
            if (h < lowest) lowest = h;
            sum += h;
            
            if (h > VerdictUtil.THRESHOLD_SUSPICIOUS) {
                suspiciousCount++;
            }
        }
        
        int totalFiles = uniqueFiles.size();
        double average = totalPairs > 0 ? sum / totalPairs : 0.0;
        if (totalPairs == 0) lowest = 0.0;

        return new BatchSummaryDTO(
                totalFiles,
                totalPairs,
                highest,
                average,
                lowest,
                suspiciousCount,
                pairs
        );
    }

    /** Number of reports currently cached. */
    public int activeReportCount() {
        return reportStore.size();
    }
}
