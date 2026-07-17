package backend.dto;

import java.util.List;

/**
 * Top-level response from the analysis endpoints ({@code /api/analyze}, {@code /api/analyze-files}).
 * <p>
 * Wraps a list of pairwise similarity summaries.
 */
public record AnalysisResponse(List<PairSummaryDTO> summary) {}
