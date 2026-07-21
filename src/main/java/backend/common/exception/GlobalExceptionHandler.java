package backend.common.exception;

import backend.common.dto.ApiErrorDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * Handles Bean Validation failures (e.g. {@code @NotBlank}, {@code @Size}, {@code @Min}).
     * <p>
     * Summarizes all field-level errors into a single human-readable message.
     *
     * @param ex the binding validation exception containing field errors
     * @return response entity containing ApiErrorDTO with HTTP 400 (Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDTO> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + details);
    }

    /**
     * Handles the case when a requested report ID is not found in the cache repository.
     *
     * @param ex the report not found exception
     * @return response entity containing ApiErrorDTO with HTTP 404 (Not Found)
     */
    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleReportNotFound(ReportNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles the case when a requested batch ID is not found in the cache repository.
     *
     * @param ex the batch not found exception
     * @return response entity containing ApiErrorDTO with HTTP 404 (Not Found)
     */
    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleBatchNotFound(BatchNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles the case when a requested history ID is not found or not owned by user.
     */
    @ExceptionHandler(HistoryNotFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleHistoryNotFound(HistoryNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles duplicate user registration attempts.
     *
     * @param ex the user already exists exception
     * @return response entity containing ApiErrorDTO with HTTP 409 (Conflict)
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorDTO> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles invalid arguments passed to backend services or options checkers.
     *
     * @param ex the illegal argument exception
     * @return response entity containing ApiErrorDTO with HTTP 400 (Bad Request)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles multipart/file-upload request processing errors.
     *
     * @param ex the multipart exception
     * @return response entity containing ApiErrorDTO with HTTP 400 (Bad Request)
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorDTO> handleMultipart(MultipartException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "File upload error: " + ex.getMessage());
    }

    /**
     * Handles missing static resources or unknown paths, preventing them
     * from falling through to the 500 internal server error handler.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleNoResourceFound(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found: " + ex.getResourcePath());
    }

    /**
     * Handles invalid login credentials.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDTO> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Handles requests that require authentication but the user is not logged in.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorDTO> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Handles attempts to log in before verifying email.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiErrorDTO> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Handles rate limit exceeded scenarios.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorDTO> handleRateLimitExceeded(RateLimitExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /**
     * Fallback handler for all uncaught/generic exceptions.
     * <p>
     * Logs the real stack trace on the server side for debugging, but returns a generic
     * client-safe message to avoid leaking database schemas, file paths, or internal logic.
     *
     * @param ex the unexpected exception
     * @return response entity containing ApiErrorDTO with HTTP 500 (Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> handleGeneric(Exception ex) {
        log.error("Unhandled exception in API request", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    /* ===== Helper ===== */

    /**
     * Constructs the standard ResponseEntity wrapper around ApiErrorDTO.
     *
     * @param status  the target HTTP status
     * @param message the descriptive error message
     * @return the ResponseEntitiy containing ApiErrorDTO
     */
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
