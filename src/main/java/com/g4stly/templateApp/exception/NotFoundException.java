package  com.g4stly.templateApp.exception;

/**
 * Exception thrown when a requested resource is not found (404)
 */
public class NotFoundException extends RuntimeException {
    
    public NotFoundException(String message) {
        super(message);
    }
    
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
