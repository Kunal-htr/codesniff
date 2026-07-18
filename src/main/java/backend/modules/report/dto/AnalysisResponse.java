package backend.modules.report.dto;

import java.util.List;

/**
 * Top-level response from the analysis endpoints ({@code /api/analyze}, {@code /api/analyze-files}).
 * <p>
 * Wraps a list of pairwise similarity summaries for all evaluated file pairs.
 *
 * @param batchId unique identifier for this analysis batch
 * @param summary list of pairwise summaries
 */
public record AnalysisResponse(String batchId, List<PairSummaryDTO> summary) {}
