package  com.g4stly.templateApp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.models.AdminActivityLog;
import com.g4stly.templateApp.repos.AdminActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to log admin activities
 * This service provides methods to automatically log admin actions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogger {
    
    private final AdminActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Log admin activity asynchronously
     * Uses separate transaction to ensure logging doesn't interfere with main operation
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(Long adminId, String action, String resourceType, 
                           String resourceId, Map<String, Object> details,
                           HttpServletRequest request) {
        try {
            AdminActivityLog log = new AdminActivityLog();
            log.setAdminId(adminId);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            
            // Convert details map to JSON string
            if (details != null && !details.isEmpty()) {
                log.setDetails(objectMapper.writeValueAsString(details));
            }
            
            // Extract IP address and User-Agent
            if (request != null) {
                log.setIpAddress(getClientIpAddress(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
            
            activityLogRepository.save(log);
            AdminActivityLogger.log.debug("Logged admin activity: {} - {} - {} - {}", adminId, action, resourceType, resourceId);
            
        } catch (Exception e) {
            AdminActivityLogger.log.error("Failed to log admin activity for adminId: {}, action: {}", adminId, action, e);
            // Don't throw exception - logging failure should not affect the main operation
        }
    }
    
    /**
     * Log activity with simple details (convenience method)
     */
    public void logActivity(Long adminId, String action, String resourceType, 
                           String resourceId, HttpServletRequest request) {
        logActivity(adminId, action, resourceType, resourceId, null, request);
    }
    
    /**
     * Log CREATE action
     */
    public void logCreate(Long adminId, String resourceType, String resourceId, 
                         Object resourceData, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "created");
        if (resourceData != null) {
            details.put("data", resourceData);
        }
        logActivity(adminId, "CREATE", resourceType, resourceId, details, request);
    }
    
    /**
     * Log UPDATE action
     */
    public void logUpdate(Long adminId, String resourceType, String resourceId, 
                         Map<String, Object> changes, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "updated");
        if (changes != null && !changes.isEmpty()) {
            details.put("changes", changes);
        }
        logActivity(adminId, "UPDATE", resourceType, resourceId, details, request);
    }
    
    /**
     * Log DELETE action
     */
    public void logDelete(Long adminId, String resourceType, String resourceId, 
                         HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "deleted");
        logActivity(adminId, "DELETE", resourceType, resourceId, details, request);
    }
    
    /**
     * Log READ action (for sensitive data access)
     */
    public void logRead(Long adminId, String resourceType, String resourceId, 
                       HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "viewed");
        logActivity(adminId, "READ", resourceType, resourceId, details, request);
    }
    
    /**
     * Log ACTIVATE action
     */
    public void logActivate(Long adminId, String resourceType, String resourceId, 
                           HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "activated");
        logActivity(adminId, "ACTIVATE", resourceType, resourceId, details, request);
    }
    
    /**
     * Log DEACTIVATE action
     */
    public void logDeactivate(Long adminId, String resourceType, String resourceId, 
                             HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "deactivated");
        logActivity(adminId, "DEACTIVATE", resourceType, resourceId, details, request);
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
