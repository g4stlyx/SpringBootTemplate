package  com.g4stly.templateApp.exception;

import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.AuthErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @Autowired
    private AuthErrorLogService authErrorLogService;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    /**
     * Handle No Handler Found Exception (404 for non-existent paths)
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("Path not found: {} {}", request.getMethod(), request.getRequestURI());
        
        // Log 404 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log404(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                "Path not found"
            );
        } catch (Exception e) {
            log.error("Failed to log 404 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "The requested path does not exist: " + request.getRequestURI(),
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle Resource Not Found Exception
     */
    @ExceptionHandler({ResourceNotFoundException.class, NotFoundException.class})
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("Resource not found: {}", ex.getMessage());
        
        // Log 404 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log404(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to log 404 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle Unauthorized and Forbidden Exception
     */
    @ExceptionHandler({UnauthorizedException.class, ForbiddenException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("Unauthorized/Forbidden access: {}", ex.getMessage());
        
        // Log 403 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log403(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage(),
                "Access to protected resource"
            );
        } catch (Exception e) {
            log.error("Failed to log 403 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle Spring Security Access Denied Exception (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        
        log.error("Access denied: {}", ex.getMessage());
        
        // Log 403 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.logAccessDenied(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to log access denied error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Access denied. You do not have permission to access this resource.",
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle Spring Security Authentication Exception (401)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        
        log.error("Authentication error: {}", ex.getMessage());
        
        // Log 401 error
        try {
            authErrorLogService.log401(
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to log 401 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication required to access this resource.",
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    /**
     * Handle Bad Request Exception
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex, 
            HttpServletRequest request) {
        
        log.error("Bad request: {}", ex.getMessage());
        
        // Log 400 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log400(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to log 400 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle Validation Exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("errors", errors);
        response.put("path", request.getRequestURI());
        response.put("timestamp", LocalDateTime.now());
        
        log.error("Validation failed: {}", errors);
        
        // Log 400 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log400(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                "Validation failed: " + errors
            );
        } catch (Exception e) {
            log.error("Failed to log validation error: {}", e.getMessage());
        }
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handle Generic Exceptions (500 errors)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("Internal server error: ", ex);
        
        // Log 500 error
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log500(
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()
            );
        } catch (Exception e) {
            log.error("Failed to log 500 error: {}", e.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                LocalDateTime.now()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Helper method to get client IP address
     */
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
    /**
     * Helper method to extract user info from request (JWT token + security context)
     */
    private UserInfo extractUserInfo(HttpServletRequest request) {
        UserInfo userInfo = new UserInfo();
        
        try {
            // Try to get from Security Context first
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication.getDetails() instanceof Long) {
                    userInfo.userId = (Long) authentication.getDetails();
                }
                if (authentication.getPrincipal() instanceof String) {
                    userInfo.username = (String) authentication.getPrincipal();
                }
            }
            
            // Try to extract from JWT token
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    if (userInfo.username == null) {
                        userInfo.username = jwtUtils.extractUsername(token);
                    }
                    if (userInfo.userId == null) {
                        userInfo.userId = jwtUtils.extractUserIdAsLong(token);
                    }
                    if (userInfo.userType == null) {
                        userInfo.userType = jwtUtils.extractUserType(token);
                    }
                } catch (Exception e) {
                    log.debug("Could not extract user info from JWT token: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting user info: {}", e.getMessage());
        }
        
        return userInfo;
    }
    
    /**
     * Inner class to hold user information
     */
    private static class UserInfo {
        Long userId;
        String userType;
        String username;
    }
}
