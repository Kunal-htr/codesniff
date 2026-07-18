package backend.modules.report.dto;

/**
 * Per-pair similarity summary returned in the top-level analysis response.
 * <p>
 * This record provides the public-facing summary for a single compared code pair,
 * hiding internal AST details and selected fingerprint data.
 *
 * @param a        the name of code file A
 * @param b        the name of code file B
 * @param score    the overall hybrid similarity score (0.0 to 1.0)
 * @param jaccard  the Jaccard similarity score (0.0 to 1.0)
 * @param coverage the winnowing fingerprint coverage similarity score (0.0 to 1.0)
 * @param reportId the unique UUID string of the cached report, used to fetch detailed results later
 */
public record PairSummaryDTO(
        String a,
        String b,
        double score,       // hybrid score
        double jaccard,
        double coverage,
        String reportId
) {}
