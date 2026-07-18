package backend.modules.report.dto;

import java.util.List;

/**
 * Batch-level summary statistics for an entire analyzed submission set.
 * <p>
 * Aggregates pairwise results into high-level metrics suitable for
 * dashboards and batch-level reporting (v0.8 visual analytics).
 * <p>
 * All similarity scores use the <strong>0.0–1.0 decimal</strong> convention,
 * consistent with {@link PairSummaryDTO} and {@link ReportResponse}.
 *
 * @param totalFiles          number of files in the analyzed batch
 * @param totalPairs          number of pairwise comparisons performed
 * @param highestSimilarity   maximum hybrid score across all pairs (0.0–1.0)
 * @param averageSimilarity   mean hybrid score across all pairs (0.0–1.0)
 * @param lowestSimilarity    minimum hybrid score across all pairs (0.0–1.0)
 * @param suspiciousPairCount number of pairs exceeding the "Suspicious" threshold (hybrid &gt; 0.45)
 * @param pairs               per-pair summaries included in this batch
 */
public record BatchSummaryDTO(
        int totalFiles,
        int totalPairs,
        double highestSimilarity,
        double averageSimilarity,
        double lowestSimilarity,
        int suspiciousPairCount,
        List<PairSummaryDTO> pairs
) {}
