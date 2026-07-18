package backend.modules.report.dto;

/**
 * Response DTO for the {@code GET /api/info} endpoint.
 * <p>
 * Replaces the previous untyped {@code Map<String, Object>} return,
 * giving clients a well-defined, documented JSON shape.
 *
 * @param app           application name
 * @param version       current version string (e.g. "v0.6")
 * @param status        server status (e.g. "running")
 * @param timestamp     human-readable server timestamp
 * @param activeReports number of reports currently cached in memory
 */
public record InfoResponse(
        String app,
        String version,
        String status,
        String timestamp,
        int activeReports
) {}
