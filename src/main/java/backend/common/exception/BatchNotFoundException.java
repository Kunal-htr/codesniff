package backend.common.exception;

/**
 * Exception thrown when a requested batch summary ID does not exist in the store.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(String id) {
        super("Batch not found for ID: " + id);
    }
}
