package backend.modules.report.dto;

/**
 * Tunable validation constants for request DTOs.
 * <p>
 * Centralizes magic numbers so they can be adjusted in one place
 * without hunting through annotation attributes across multiple records.
 */
public final class ValidationConstants {

    private ValidationConstants() {} // utility class — no instances

    /**
     * Maximum number of submissions allowed in a single analysis batch.
     * <p>
     * Guards against abuse/DoS from excessively large payloads.
     * Pairwise comparison is O(N²), so this keeps the upper bound practical.
     */
    public static final int MAX_SUBMISSIONS = 200;
}
