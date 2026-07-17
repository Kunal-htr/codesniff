package backend.exception;

import backend.dto.ApiErrorDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST endpoints.
 * <p>
 * Every error path returns a consistent {@link ApiErrorDTO} JSON body
 * with the appropriate HTTP status code. No raw stack traces, Whitelabel
 * HTML pages, or ambiguous 200-with-null-fields responses are ever returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean Validation failures (e.g. {@code @NotBlank}, {@code @Size}, {@code @Min}).
     * <p>
     * Summarizes all field-level errors into a single human-readable message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDTO> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + details);
    }

    /**
     * Report not found — clean 404 with a descriptive message.
     */
    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleReportNotFound(ReportNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Illegal argument — typically from {@code SimilarityEngine.Options} bounds
     * if a request somehow bypasses DTO validation (defensive catch).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Multipart/file-upload errors (corrupt upload, missing part, size exceeded).
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorDTO> handleMultipart(MultipartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "File upload error: " + ex.getMessage());
    }

    /**
     * Generic fallback — catches any unexpected exception.
     * <p>
     * Logs the real exception server-side but returns a safe generic message
     * to the client. Internal details are never leaked.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> handleGeneric(Exception ex) {
        log.error("Unhandled exception in API request", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    /* ===== Helper ===== */

    private static ResponseEntity<ApiErrorDTO> buildResponse(HttpStatus status, String message) {
        ApiErrorDTO body = new ApiErrorDTO(
                status.getReasonPhrase(),
                message,
                status.value(),
                System.currentTimeMillis()
        );
        return ResponseEntity.status(status).body(body);
    }
}
