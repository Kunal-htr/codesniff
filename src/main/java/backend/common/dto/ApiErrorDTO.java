package backend.common.dto;

/**
 * Standardized error response shape for all API error responses.
 * <p>
 * Provides a consistent JSON structure for client-side error handling.
 * Wired into {@link backend.common.exception.GlobalExceptionHandler} as the
 * uniform response body for all error paths.
 *
 * @param error     short error category (e.g. "Bad Request", "Not Found", "Internal Server Error")
 * @param message   human-readable description of what went wrong
 * @param status    HTTP status code (e.g. 400, 404, 500)
 * @param timestamp epoch milliseconds when the error occurred
 */
public record ApiErrorDTO(
        String error,
        String message,
        int status,
        long timestamp
) {}
