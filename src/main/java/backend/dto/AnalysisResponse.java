package backend.dto;

import java.util.List;

/**
 * Top-level response from the analysis endpoints ({@code /api/analyze}, {@code /api/analyze-files}).
 * <p>
 * Wraps a list of pairwise similarity summaries for all evaluated file pairs.
 *
 * @param summary list of pairwise summaries
 */
public record AnalysisResponse(List<PairSummaryDTO> summary) {}
