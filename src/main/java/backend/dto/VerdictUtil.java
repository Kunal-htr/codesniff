package backend.dto;

/**
 * Centralized verdict computation for similarity scores.
 * <p>
 * Defines the threshold bands and description strings used across all services
 * that need to classify a hybrid similarity score into a verdict category.
 * This ensures {@code ReportService}, {@code AnalysisService}, and any future
 * consumers produce identical verdicts for the same score — no risk of
 * threshold drift between independently maintained copies.
 * <p>
 * <strong>Threshold bands (all scores on the 0.0–1.0 decimal scale):</strong>
 * <ul>
 *   <li>{@code > 0.70} → <strong>High</strong></li>
 *   <li>{@code > 0.45} → <strong>Suspicious</strong></li>
 *   <li>{@code > 0.25} → <strong>Review</strong></li>
 *   <li>{@code ≤ 0.25} → <strong>Clean</strong></li>
 * </ul>
 */
public final class VerdictUtil {

    private VerdictUtil() {} // utility class — no instances

    /** High similarity threshold (hybrid &gt; 0.70). */
    public static final double THRESHOLD_HIGH = 0.70;

    /** Suspicious similarity threshold (hybrid &gt; 0.45). */
    public static final double THRESHOLD_SUSPICIOUS = 0.45;

    /** Review similarity threshold (hybrid &gt; 0.25). */
    public static final double THRESHOLD_REVIEW = 0.25;

    /**
     * Compute the verdict label for the given hybrid similarity score.
     *
     * @param hybridScore similarity score on the 0.0–1.0 scale
     * @return one of "High", "Suspicious", "Review", or "Clean"
     */
    public static String verdict(double hybridScore) {
        if (hybridScore > THRESHOLD_HIGH) return "High";
        if (hybridScore > THRESHOLD_SUSPICIOUS) return "Suspicious";
        if (hybridScore > THRESHOLD_REVIEW) return "Review";
        return "Clean";
    }

    /**
     * Compute the verdict description for the given hybrid similarity score.
     *
     * @param hybridScore similarity score on the 0.0–1.0 scale
     * @return human-readable explanation of the verdict
     */
    public static String verdictDescription(double hybridScore) {
        if (hybridScore > THRESHOLD_HIGH) {
            return "High similarity detected. There is a high probability of direct copy/paste or minimal rewriting.";
        }
        if (hybridScore > THRESHOLD_SUSPICIOUS) {
            return "Suspicious similarity level. Manual review is recommended to inspect similar blocks and structures.";
        }
        if (hybridScore > THRESHOLD_REVIEW) {
            return "Moderate similarity. Code exhibits some shared components that should be reviewed.";
        }
        return "Low similarity. The two files appear to be independent implementations.";
    }
}
