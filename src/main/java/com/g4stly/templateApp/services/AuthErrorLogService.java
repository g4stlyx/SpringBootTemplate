package com.g4stly.templateApp.services;

import com.g4stly.templateApp.models.AuthenticationErrorLog;
import com.g4stly.templateApp.repos.AuthenticationErrorLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for logging authentication and authorization errors asynchronously
 */
@Service
@Slf4j
public class AuthErrorLogService {
    
    @Autowired
    private AuthenticationErrorLogRepository authErrorLogRepository;
    
    @Value("${app.security.log-auth-errors:true}")
    private boolean logAuthErrors;
    
    /**
     * Log authentication error asynchronously with separate transaction
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthError(
            AuthenticationErrorLog.ErrorType errorType,
            Long userId,
            String userType,
            String username,
            String ipAddress,
            String userAgent,
            String endpoint,
            String httpMethod,
            String errorMessage,
            String attemptedAction
    ) {
        if (!logAuthErrors) {
            return;
        }
        
        try {
            AuthenticationErrorLog errorLog = AuthenticationErrorLog.builder()
                    .errorType(errorType)
                    .userId(userId)
                    .userType(userType)
                    .username(username)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .errorMessage(errorMessage)
                    .attemptedAction(attemptedAction)
                    .build();
            
            authErrorLogRepository.save(errorLog);
            
            // Log to console with formatted output
            logToConsole(errorType, userId, username, ipAddress, endpoint, errorMessage);
            
        } catch (Exception e) {
            // Don't let logging errors affect the main flow
            log.error("Failed to log authentication error: {}", e.getMessage());
        }
    }
    
    /**
     * Log 401 Unauthorized error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log401(String ipAddress, String userAgent, String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.UNAUTHORIZED_401,
                null,
                null,
                null,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Authentication required"
        );
    }
    
    /**
     * Log 403 Forbidden error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log403(Long userId, String userType, String username, String ipAddress, String userAgent, 
                       String endpoint, String httpMethod, String errorMessage, String attemptedAction) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.FORBIDDEN_403,
                userId,
                userType,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                attemptedAction
        );
    }
    
    /**
     * Log 404 Not Found error (resource access attempt)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log404(Long userId, String userType, String username, String ipAddress, String userAgent, 
                       String endpoint, String httpMethod, String resourceType) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.NOT_FOUND_404,
                userId,
                userType,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "Resource not found: " + resourceType,
                "Attempted to access non-existent resource"
        );
    }
    
    /**
     * Log 400 Bad Request error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log400(Long userId, String userType, String username, String ipAddress, String userAgent, 
                       String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.BAD_REQUEST_400,
                userId,
                userType,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Bad request"
        );
    }
    
    /**
     * Log 500 Internal Server Error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log500(Long userId, String userType, String username, String ipAddress, String userAgent, 
                       String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.INTERNAL_SERVER_ERROR_500,
                userId,
                userType,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Internal server error"
        );
    }
    
    /**
     * Log invalid token error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvalidToken(String ipAddress, String userAgent, String endpoint, String httpMethod, String tokenError) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.INVALID_TOKEN,
                null,
                null,
                null,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                tokenError,
                "Invalid or expired authentication token"
        );
    }
    
    /**
     * Log access denied error
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(Long userId, String userType, String username, String ipAddress, String userAgent, 
                                String endpoint, String httpMethod, String reason) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.ACCESS_DENIED,
                userId,
                userType,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                reason,
                "Access denied to protected resource"
        );
    }
    
    /**
     * Log formatted message to console
     */
    private void logToConsole(
            AuthenticationErrorLog.ErrorType errorType,
            Long userId,
            String username,
            String ipAddress,
            String endpoint,
            String errorMessage
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║ Authentication Error Detected                                                  ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Error Type: %-66s ║\n", errorType.name() + " - " + errorType.getDescription()));
        
        if (userId != null) {
            sb.append(String.format("║ User ID: %-69s ║\n", userId));
        }
        if (username != null && !username.isEmpty()) {
            sb.append(String.format("║ Username: %-68s ║\n", username));
        }
        if (ipAddress != null && !ipAddress.isEmpty()) {
            sb.append(String.format("║ IP Address: %-66s ║\n", ipAddress));
        }
        if (endpoint != null && !endpoint.isEmpty()) {
            String truncatedEndpoint = endpoint.length() > 66 ? endpoint.substring(0, 63) + "..." : endpoint;
            sb.append(String.format("║ Endpoint: %-68s ║\n", truncatedEndpoint));
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String truncatedMessage = errorMessage.length() > 66 ? errorMessage.substring(0, 63) + "..." : errorMessage;
            sb.append(String.format("║ Message: %-69s ║\n", truncatedMessage));
        }
        
        sb.append("╚════════════════════════════════════════════════════════════════════════════════╝");
        
        log.warn(sb.toString());
    }
}
