package backend.store;

import backend.analysis.SimilarityEngine;
import org.springframework.stereotype.Component;

import backend.dto.PairSummaryDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory repository for caching detailed pairwise analysis reports.
 * <p>
 * This component acts as a lightweight database cache, storing full token streams, 
 * fingerprints, and individual similarity metric calculations for each compared code pair. 
 * External clients retrieve these reports asynchronously by their unique report identifier.
 */
@Component
public class ReportStore {

    private final Map<String, ReportData> reports = new ConcurrentHashMap<>();
    private final Map<String, List<PairSummaryDTO>> batchStore = new ConcurrentHashMap<>();

    /**
     * Internal data container holding the complete execution outcomes and token data 
     * for a single code-to-code comparison.
     * <p>
     * This record is not serialized directly to clients; instead, it is mapped to a 
     * clean DTO by {@code ReportService} before presentation.
     *
     * @param nameA             the name of the first submission
     * @param nameB             the name of the second submission
     * @param normA             the normalized version of code A (preprocessed)
     * @param normB             the normalized version of code B (preprocessed)
     * @param streamA           the full token symbol stream of submission A
     * @param streamB           the full token symbol stream of submission B
     * @param fpsA              the list of selected winnowed fingerprints for submission A
     * @param fpsB              the list of selected winnowed fingerprints for submission B
     * @param k                 the k-gram length parameter used during tokenization
     * @param window            the winnowing window size parameter used for fingerprint selection
     * @param omitComments      flag indicating whether source comments were excluded
     * @param jaccard           the calculated Jaccard similarity score (0.0 to 1.0)
     * @param coverage          the calculated winnowing fingerprint coverage score (0.0 to 1.0)
     * @param lcs               the calculated Longest Common Subsequence similarity score (0.0 to 1.0)
     * @param ast               the calculated AST structural similarity score (0.0 to 1.0)
     * @param hybrid            the overall aggregated similarity score (0.0 to 1.0)
     * @param operatorDivergent flag indicating if similarity is artificially inflated by operator-only matches
     */
    public record ReportData(
            String nameA, String nameB,
            String rawA, String rawB,
            String normA, String normB,
            List<String> streamA, List<String> streamB,
            List<SimilarityEngine.Fingerprint> fpsA,
            List<SimilarityEngine.Fingerprint> fpsB,
            int k, int window, boolean omitComments,
            double jaccard, double coverage, double lcs, double ast, double hybrid,
            boolean operatorDivergent
    ) {}

    /**
     * Saves a pairwise analysis report to the memory cache.
     *
     * @param id   the unique report identifier (usually a UUID string)
     * @param data the report data containing all analysis details and structures
     */
    public void put(String id, ReportData data) {
        reports.put(id, data);
    }

    /**
     * Retrieves a saved report by its identifier.
     *
     * @param id the unique report identifier
     * @return the report data, or {@code null} if no report exists with the given ID
     */
    public ReportData get(String id) {
        return reports.get(id);
    }

    /**
     * Returns the total count of reports currently stored in the cache.
     *
     * @return the size of the repository cache
     */
    public int size() {
        return reports.size();
    }

    /**
     * Saves a list of pairwise summaries representing a complete batch analysis.
     *
     * @param batchId the unique batch identifier
     * @param summaries the list of pairwise summaries
     */
    public void putBatch(String batchId, List<PairSummaryDTO> summaries) {
        batchStore.put(batchId, summaries);
    }

    /**
     * Retrieves the list of pairwise summaries for a given batch.
     *
     * @param batchId the unique batch identifier
     * @return the list of summaries, or {@code null} if not found
     */
    public List<PairSummaryDTO> getBatch(String batchId) {
        return batchStore.get(batchId);
    }
}
