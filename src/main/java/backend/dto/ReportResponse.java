package backend.dto;

/**
 * JSON response for {@code GET /api/report/{id}}.
 * <p>
 * Maps from the internal {@code ReportStore.ReportData} to a clean public shape.
 * No internal engine types (Fingerprint, Analysis, token streams) are exposed.
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

    /** Analysis parameters and fingerprint statistics. */
    public record Metadata(
            int k,
            int window,
            boolean omitComments,
            int fingerprintMatchCount,
            int fingerprintCountA,
            int fingerprintCountB
    ) {}
}
