package backend.common.exception;

/**
 * Thrown when a requested report ID does not exist in the report store.
 * <p>
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ReportNotFoundException extends RuntimeException {

    private final String reportId;

    /**
     * Constructs a new ReportNotFoundException with the missing report ID.
     *
     * @param reportId the requested report ID that could not be found
     */
    public ReportNotFoundException(String reportId) {
        super("Report not found: " + reportId);
        this.reportId = reportId;
    }

    /** The report ID that was requested but not found. */
    public String getReportId() {
        return reportId;
    }
}
