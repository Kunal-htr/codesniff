package backend.dto;

/**
 * Per-pair similarity summary returned in the analysis response.
 * <p>
 * This is the public-facing shape — no internal engine types are exposed.
 */
public record PairSummaryDTO(
        String a,
        String b,
        double score,       // hybrid score
        double jaccard,
        double coverage,
        String reportId
) {}
