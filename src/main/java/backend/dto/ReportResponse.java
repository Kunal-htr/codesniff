package backend.dto;

/**
 * JSON response for the report details retrieved via {@code GET /api/report/{id}}.
 * <p>
 * Exposes a complete similarity analysis report for a compared file pair.
 * Translates internal engine cache formats into clean DTO structures without
 * leaking internal type references.
 *
 * @param nameA             the name of submission A
 * @param nameB             the name of submission B
 * @param jaccard           Jaccard similarity score based on fingerprint sets (0.0 to 1.0)
 * @param coverage          winnowing fingerprint coverage score (0.0 to 1.0)
 * @param lcs               Longest Common Subsequence statement-level score (0.0 to 1.0)
 * @param ast               AST structural similarity score (0.0 to 1.0)
 * @param hybrid            the aggregated, weighted overall similarity score (0.0 to 1.0)
 * @param verdict           similarity level classification ("Clean", "Review", "Suspicious", "High")
 * @param verdictDescription human-readable explanation of the verdict
 * @param operatorDivergent flag indicating if similarity is driven primarily by operator matching
 * @param metadata          parameters and metadata details for this comparison
 */
public record ReportResponse(
        String nameA,
        String nameB,
        double jaccard,
        double coverage,
        double lcs,
        double ast,
        double hybrid,
        String verdict,
        String verdictDescription,
        boolean operatorDivergent,
        Metadata metadata
) {

    /**
     * Comparison metadata detailing analysis parameters and fingerprint stats.
     *
     * @param k                     the token length of k-grams used
     * @param window                the winnowing window size used
     * @param omitComments          whether comments were stripped from input
     * @param fingerprintMatchCount the number of matched fingerprints shared between files
     * @param fingerprintCountA     the total fingerprint count of file A
     * @param fingerprintCountB     the total fingerprint count of file B
     */
    public record Metadata(
            int k,
            int window,
            boolean omitComments,
            int fingerprintMatchCount,
            int fingerprintCountA,
            int fingerprintCountB
    ) {}
}
