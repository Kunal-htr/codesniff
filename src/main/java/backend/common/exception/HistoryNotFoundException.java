package backend.common.exception;

public class HistoryNotFoundException extends RuntimeException {
    public HistoryNotFoundException(String id) {
        super("Analysis history not found: " + id);
    }
}
