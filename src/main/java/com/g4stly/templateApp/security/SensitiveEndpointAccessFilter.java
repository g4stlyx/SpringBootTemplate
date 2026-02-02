package com.g4stly.templateApp.security;

import com.g4stly.templateApp.services.SensitiveEndpointAccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Filter to intercept and log successful access to sensitive/important endpoints.
 * This filter runs after authentication and logs access asynchronously.
 */
@Component
@Order(1)
@Slf4j
public class SensitiveEndpointAccessFilter extends OncePerRequestFilter {
    
    @Autowired
    private SensitiveEndpointAccessLogService accessLogService;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Value("${app.security.log-sensitive-access:true}")
    private boolean logSensitiveAccess;
    
    /**
     * Map of sensitive endpoint patterns to their categories.
     * Pattern -> Category name
     */
    private static final Map<String, EndpointConfig> SENSITIVE_ENDPOINTS = Map.ofEntries(
            // Database backup - CRITICAL
            Map.entry("/api/v1/admin/backup", new EndpointConfig("DATABASE_BACKUP", "CRITICAL")),
            
            // Admin management - HIGH
            Map.entry("/api/v1/admin/admins", new EndpointConfig("ADMIN_MANAGEMENT", "HIGH")),
            
            // Token management - HIGH
            Map.entry("/api/v1/admin/tokens", new EndpointConfig("TOKEN_MANAGEMENT", "HIGH")),
            
            // 2FA settings - MEDIUM
            Map.entry("/api/v1/admin/2fa/setup", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            Map.entry("/api/v1/admin/2fa/enable", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            Map.entry("/api/v1/admin/2fa/disable", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            
            // Activity logs - MEDIUM
            Map.entry("/api/v1/admin/activity-logs", new EndpointConfig("ACTIVITY_LOGS", "MEDIUM")),
            
            // Auth error logs - MEDIUM
            Map.entry("/api/v1/admin/auth-error-logs", new EndpointConfig("ERROR_LOGS", "MEDIUM")),
            
            // Profile image management - LOW
            Map.entry("/api/v1/admin/images", new EndpointConfig("IMAGE_MANAGEMENT", "LOW"))
    );
    
    /**
     * Patterns for matching dynamic endpoint paths
     */
    private static final List<PatternConfig> SENSITIVE_PATTERNS = List.of(
            // Admin management with ID
            new PatternConfig(Pattern.compile("/api/v1/admin/admins/\\d+.*"), "ADMIN_MANAGEMENT", "HIGH"),
            // Token management with ID
            new PatternConfig(Pattern.compile("/api/v1/admin/tokens/.*"), "TOKEN_MANAGEMENT", "HIGH"),
            // Database backup operations
            new PatternConfig(Pattern.compile("/api/v1/admin/backup/.*"), "DATABASE_BACKUP", "CRITICAL"),
            // Activity logs operations
            new PatternConfig(Pattern.compile("/api/v1/admin/activity-logs/.*"), "ACTIVITY_LOGS", "MEDIUM"),
            // Auth error logs operations
            new PatternConfig(Pattern.compile("/api/v1/admin/auth-error-logs/.*"), "ERROR_LOGS", "MEDIUM")
    );
    
    /**
     * Suspicious file/path patterns that attackers commonly try to access.
     * These are CRITICAL severity as successful access indicates a serious security issue.
     */
    private static final List<PatternConfig> SUSPICIOUS_PATH_PATTERNS = List.of(
            // Environment files - CRITICAL (contains secrets)
            new PatternConfig(Pattern.compile("(?i).*\\.env.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.env$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/env\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.env\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // Git repository - CRITICAL (source code exposure)
            new PatternConfig(Pattern.compile("(?i).*\\.git.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.git/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.gitignore"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // Configuration files - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*/config\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application-.*\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application-.*\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/bootstrap\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/bootstrap\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // AWS/Cloud credentials - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*\\.aws/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/credentials"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*aws.*credentials.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // SSH keys - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*\\.ssh/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/id_rsa.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/id_ed25519.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.pem$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.key$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // Database files - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*\\.sql$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.db$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.sqlite.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*dump.*\\.sql.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*backup.*\\.sql.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // Log files - HIGH (may contain sensitive info)
            new PatternConfig(Pattern.compile("(?i).*\\.log$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/logs/.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // Backup files - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*\\.bak$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.backup$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.old$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.orig$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.copy$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*~$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // Archive files - HIGH
            new PatternConfig(Pattern.compile("(?i).*\\.zip$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.tar.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.gz$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.rar$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // PHP/Server config - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*\\.htaccess.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.htpasswd.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/wp-config\\.php.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/php\\.ini.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/web\\.config.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            
            // Docker/Container files - HIGH
            new PatternConfig(Pattern.compile("(?i).*/Dockerfile.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/docker-compose.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.dockerignore.*"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            
            // CI/CD files - HIGH
            new PatternConfig(Pattern.compile("(?i).*\\.travis\\.ya?ml.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.gitlab-ci\\.ya?ml.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/\\.github/.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*Jenkinsfile.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // Package manager files - MEDIUM
            new PatternConfig(Pattern.compile("(?i).*/package\\.json$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/package-lock\\.json$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/pom\\.xml$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/build\\.gradle.*"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            
            // Sensitive paths commonly probed
            new PatternConfig(Pattern.compile("(?i).*/admin/.*"), "SUSPICIOUS_PATH_PROBE", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/phpmyadmin.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/adminer.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/wp-admin.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/wp-login.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*actuator(?!/health).*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/debug.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/trace.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/console.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/shell.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/cmd.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/exec.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            
            // Source code files - HIGH
            new PatternConfig(Pattern.compile("(?i).*\\.java$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.class$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.jar$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.war$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            
            // Secrets/Keys files - CRITICAL
            new PatternConfig(Pattern.compile("(?i).*secrets.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*password.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*api[_-]?key.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*private[_-]?key.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.p12$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.pfx$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.jks$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*keystore.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL")
    );
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!logSensitiveAccess) {
            return true;
        }
        
        String path = request.getRequestURI();
        
        // Always filter admin endpoints
        if (path.startsWith("/api/v1/admin/")) {
            return false;
        }
        
        // Check if path matches any suspicious patterns
        for (PatternConfig patternConfig : SUSPICIOUS_PATH_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return false; // Don't skip filtering - we want to log this
            }
        }
        
        // Skip filtering for other paths
        return true;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Wrap response to capture status code after processing
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        
        try {
            // Continue with the filter chain
            filterChain.doFilter(request, responseWrapper);
            
            // After the request is processed, check if we should log
            logSensitiveAccessIfNeeded(request, responseWrapper);
            
        } finally {
            // Copy the response body to the original response
            responseWrapper.copyBodyToResponse();
        }
    }
    
    /**
     * Check if the endpoint is sensitive and log access if needed
     */
    private void logSensitiveAccessIfNeeded(HttpServletRequest request, ContentCachingResponseWrapper response) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int statusCode = response.getStatus();
        
        // Only log successful requests (2xx status codes)
        if (statusCode < 200 || statusCode >= 300) {
            return;
        }
        
        // Check if this is a sensitive endpoint
        EndpointConfig config = findEndpointConfig(path);
        if (config == null) {
            return;
        }
        
        // Extract user information
        UserInfo userInfo = extractUserInfo(request);
        
        // Log the access asynchronously
        logAccessBasedOnCategory(
                config.category,
                config.severity,
                userInfo.userId,
                userInfo.userType,
                userInfo.username,
                getClientIpAddress(request),
                request.getHeader("User-Agent"),
                path,
                method,
                statusCode
        );
    }
    
    /**
     * Find the endpoint configuration for a given path
     */
    private EndpointConfig findEndpointConfig(String path) {
        // Check exact matches first (admin endpoints)
        for (Map.Entry<String, EndpointConfig> entry : SENSITIVE_ENDPOINTS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Check admin pattern matches
        for (PatternConfig patternConfig : SENSITIVE_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return new EndpointConfig(patternConfig.category, patternConfig.severity);
            }
        }
        
        // Check suspicious file/path patterns
        for (PatternConfig patternConfig : SUSPICIOUS_PATH_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return new EndpointConfig(patternConfig.category, patternConfig.severity);
            }
        }
        
        return null;
    }
    
    /**
     * Log access based on category and severity
     */
    private void logAccessBasedOnCategory(String category, String severity, Long userId, String userType,
                                          String username, String ipAddress, String userAgent,
                                          String endpoint, String httpMethod, Integer responseStatus) {
        switch (category) {
            case "DATABASE_BACKUP":
                accessLogService.logDatabaseBackupAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ADMIN_MANAGEMENT":
                accessLogService.logAdminManagementAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "TOKEN_MANAGEMENT":
                accessLogService.logTokenManagementAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "2FA_SETTINGS":
                accessLogService.log2FASettingsAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ACTIVITY_LOGS":
                accessLogService.logActivityLogsAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ERROR_LOGS":
                accessLogService.logErrorLogsAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "SUSPICIOUS_FILE_ACCESS":
                accessLogService.logSuspiciousFileAccess(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus, getSeverityLevel(severity));
                break;
            case "SUSPICIOUS_PATH_PROBE":
                accessLogService.logSuspiciousPathProbe(userId, userType, username, ipAddress, 
                        userAgent, endpoint, httpMethod, responseStatus, getSeverityLevel(severity));
                break;
            default:
                // Use generic logging for other categories
                accessLogService.logAccess(
                        getSeverityLevel(severity),
                        userId, userType, username, ipAddress, userAgent,
                        endpoint, httpMethod, category, 
                        "Access to " + category + " endpoint", responseStatus
                );
                break;
        }
    }
    
    /**
     * Convert severity string to enum
     */
    private com.g4stly.templateApp.models.SensitiveEndpointAccessLog.SeverityLevel getSeverityLevel(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> com.g4stly.templateApp.models.SensitiveEndpointAccessLog.SeverityLevel.CRITICAL;
            case "HIGH" -> com.g4stly.templateApp.models.SensitiveEndpointAccessLog.SeverityLevel.HIGH;
            case "MEDIUM" -> com.g4stly.templateApp.models.SensitiveEndpointAccessLog.SeverityLevel.MEDIUM;
            default -> com.g4stly.templateApp.models.SensitiveEndpointAccessLog.SeverityLevel.LOW;
        };
    }
    
    /**
     * Extract user information from request and security context
     */
    private UserInfo extractUserInfo(HttpServletRequest request) {
        UserInfo info = new UserInfo();
        
        // Try to get from request attributes (set by JwtAuthFilter)
        Object userIdAttr = request.getAttribute("userId");
        Object userTypeAttr = request.getAttribute("userType");
        
        if (userIdAttr != null) {
            info.userId = (Long) userIdAttr;
        }
        if (userTypeAttr != null) {
            info.userType = (String) userTypeAttr;
        }
        
        // Try to get from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            info.username = auth.getName();
            
            // Try to get userId from authentication details if not already set
            if (info.userId == null && auth.getDetails() instanceof Long) {
                info.userId = (Long) auth.getDetails();
            }
        }
        
        // Try to get from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && info.userId == null) {
            try {
                String token = authHeader.substring(7);
                info.userId = jwtUtils.extractUserIdAsLong(token);
                info.userType = jwtUtils.extractUserType(token);
                info.username = jwtUtils.extractUsername(token);
            } catch (Exception e) {
                log.debug("Could not extract user info from token: {}", e.getMessage());
            }
        }
        
        return info;
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Helper class to hold endpoint configuration
     */
    private static class EndpointConfig {
        String category;
        String severity;
        
        EndpointConfig(String category, String severity) {
            this.category = category;
            this.severity = severity;
        }
    }
    
    /**
     * Helper class for pattern-based endpoint matching
     */
    private static class PatternConfig {
        Pattern pattern;
        String category;
        String severity;
        
        PatternConfig(Pattern pattern, String category, String severity) {
            this.pattern = pattern;
            this.category = category;
            this.severity = severity;
        }
    }
    
    /**
     * Helper class to hold user information
     */
    private static class UserInfo {
        Long userId;
        String userType;
        String username;
    }
}
