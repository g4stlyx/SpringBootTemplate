package  com.g4stly.templateApp.exception;

/**
 * Exception thrown when a user doesn't have permission to access a resource (403)
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
