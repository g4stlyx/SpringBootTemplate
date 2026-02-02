package com.g4stly.templateApp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.models.UserActivityLog;
import com.g4stly.templateApp.repos.UserActivityLogRepository;
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
 * Service to log user (Client/Coach) activities asynchronously.
 * This service provides methods to automatically log user actions
 * without blocking the main request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityLogger {
    
    private final UserActivityLogRepository userActivityLogRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Log user activity asynchronously.
     * Uses separate transaction to ensure logging doesn't interfere with main operation.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(Long userId, String userType, String action,
                           String resourceType, String resourceId,
                           Map<String, Object> details, boolean success,
                           String failureReason, HttpServletRequest request) {
        try {
            UserActivityLog activityLog = new UserActivityLog();
            activityLog.setUserId(userId);
            activityLog.setUserType(userType != null ? userType.toLowerCase() : "unknown");
            activityLog.setAction(action);
            activityLog.setResourceType(resourceType);
            activityLog.setResourceId(resourceId);
            activityLog.setSuccess(success);
            activityLog.setFailureReason(failureReason);
            
            // Convert details map to JSON string
            if (details != null && !details.isEmpty()) {
                activityLog.setDetails(objectMapper.writeValueAsString(details));
            }
            
            // Extract IP address and User-Agent
            if (request != null) {
                activityLog.setIpAddress(getClientIpAddress(request));
                activityLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            userActivityLogRepository.save(activityLog);
            log.debug("Logged user activity: userId={}, userType={}, action={}, success={}",
                    userId, userType, action, success);
            
        } catch (Exception e) {
            log.error("Failed to log user activity for userId: {}, action: {}", userId, action, e);
            // Don't throw exception - logging failure should not affect the main operation
        }
    }
    
    /**
     * Log successful activity (convenience method)
     */
    public void logActivity(Long userId, String userType, String action,
                           String resourceType, String resourceId,
                           Map<String, Object> details, HttpServletRequest request) {
        logActivity(userId, userType, action, resourceType, resourceId, details, true, null, request);
    }
    
    /**
     * Log simple activity without details
     */
    public void logActivity(Long userId, String userType, String action, HttpServletRequest request) {
        logActivity(userId, userType, action, null, null, null, true, null, request);
    }
    
    /**
     * Log LOGIN action
     */
    public void logLogin(Long userId, String userType, boolean success, String failureReason, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "login_attempt");
        if (!success && failureReason != null) {
            details.put("reason", failureReason);
        }
        logActivity(userId, userType, "LOGIN", "Authentication", null, details, success, failureReason, request);
    }
    
    /**
     * Log successful LOGIN
     */
    public void logLoginSuccess(Long userId, String userType, HttpServletRequest request) {
        logLogin(userId, userType, true, null, request);
    }
    
    /**
     * Log failed LOGIN
     */
    public void logLoginFailure(Long userId, String userType, String reason, HttpServletRequest request) {
        logLogin(userId, userType, false, reason, request);
    }
    
    /**
     * Log REGISTER action
     */
    public void logRegister(Long userId, String userType, boolean success, String failureReason, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "registration");
        if (!success && failureReason != null) {
            details.put("reason", failureReason);
        }
        logActivity(userId, userType, "REGISTER", "User", userId != null ? userId.toString() : null, details, success, failureReason, request);
    }
    
    /**
     * Log LOGOUT action
     */
    public void logLogout(Long userId, String userType, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "logout");
        logActivity(userId, userType, "LOGOUT", "Authentication", null, details, true, null, request);
    }
    
    /**
     * Log PASSWORD_RESET_REQUEST action
     */
    public void logPasswordResetRequest(Long userId, String userType, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_reset_requested");
        logActivity(userId, userType, "PASSWORD_RESET_REQUEST", "Security", null, details, true, null, request);
    }
    
    /**
     * Log PASSWORD_RESET_COMPLETE action
     */
    public void logPasswordResetComplete(Long userId, String userType, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_reset_completed");
        logActivity(userId, userType, "PASSWORD_RESET_COMPLETE", "Security", null, details, success, null, request);
    }
    
    /**
     * Log PASSWORD_CHANGE action
     */
    public void logPasswordChange(Long userId, String userType, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_changed");
        logActivity(userId, userType, "PASSWORD_CHANGE", "Security", null, details, success, null, request);
    }
    
    /**
     * Log EMAIL_VERIFICATION action
     */
    public void logEmailVerification(Long userId, String userType, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "email_verified");
        logActivity(userId, userType, "EMAIL_VERIFICATION", "Security", null, details, success, null, request);
    }
    
    /**
     * Log PROFILE_UPDATE action
     */
    public void logProfileUpdate(Long userId, String userType, Map<String, Object> changedFields, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_updated");
        if (changedFields != null && !changedFields.isEmpty()) {
            details.put("changed_fields", changedFields.keySet());
        }
        logActivity(userId, userType, "PROFILE_UPDATE", "Profile", userId.toString(), details, true, null, request);
    }
    
    /**
     * Log PROFILE_PICTURE_UPLOAD action
     */
    public void logProfilePictureUpload(Long userId, String userType, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_picture_uploaded");
        logActivity(userId, userType, "PROFILE_PICTURE_UPLOAD", "Profile", userId.toString(), details, success, null, request);
    }
    
    /**
     * Log PROFILE_PICTURE_DELETE action
     */
    public void logProfilePictureDelete(Long userId, String userType, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_picture_deleted");
        logActivity(userId, userType, "PROFILE_PICTURE_DELETE", "Profile", userId.toString(), details, true, null, request);
    }
    
    /**
     * Log ACCOUNT_DEACTIVATED action
     */
    public void logAccountDeactivated(Long userId, String userType, Long deactivatedBy, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "account_deactivated");
        details.put("deactivated_by", deactivatedBy);
        logActivity(userId, userType, "ACCOUNT_DEACTIVATED", "Account", userId.toString(), details, true, null, request);
    }
    
    /**
     * Log ACCOUNT_REACTIVATED action
     */
    public void logAccountReactivated(Long userId, String userType, Long reactivatedBy, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "account_reactivated");
        details.put("reactivated_by", reactivatedBy);
        logActivity(userId, userType, "ACCOUNT_REACTIVATED", "Account", userId.toString(), details, true, null, request);
    }
    
    /**
     * Log VERIFICATION_EMAIL_RESENT action
     */
    public void logVerificationEmailResent(Long userId, String userType, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "verification_email_resent");
        logActivity(userId, userType, "VERIFICATION_EMAIL_RESENT", "Email", null, details, true, null, request);
    }
    
    /**
     * Log SESSION_REFRESH action (when tokens are refreshed)
     */
    public void logSessionRefresh(Long userId, String userType, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "session_refreshed");
        logActivity(userId, userType, "SESSION_REFRESH", "Authentication", null, details, true, null, request);
    }
    
    /**
     * Log generic READ action
     */
    public void logRead(Long userId, String userType, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_viewed");
        logActivity(userId, userType, "READ", resourceType, resourceId, details, true, null, request);
    }
    
    /**
     * Log generic CREATE action
     */
    public void logCreate(Long userId, String userType, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_created");
        logActivity(userId, userType, "CREATE", resourceType, resourceId, details, true, null, request);
    }
    
    /**
     * Log generic UPDATE action
     */
    public void logUpdate(Long userId, String userType, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_updated");
        logActivity(userId, userType, "UPDATE", resourceType, resourceId, details, true, null, request);
    }
    
    /**
     * Log generic DELETE action
     */
    public void logDelete(Long userId, String userType, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_deleted");
        logActivity(userId, userType, "DELETE", resourceType, resourceId, details, true, null, request);
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
}
