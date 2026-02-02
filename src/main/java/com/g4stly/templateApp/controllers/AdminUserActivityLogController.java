package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.admin.UserActivityLogDTO;
import com.g4stly.templateApp.dto.admin.UserActivityLogListResponse;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.security.JwtUtils;
import com.g4stly.templateApp.services.UserActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller for managing user activity logs.
 * All endpoints are restricted to Level 0 (Super Admin) only.
 */
@RestController
@RequestMapping("/api/v1/admin/user-activity-logs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminUserActivityLogController {
    
    private final UserActivityLogService userActivityLogService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    
    /**
     * Get all user activity logs with pagination and optional filtering.
     * Only Level 0 (Super Admin) can access.
     *
     * @param token Authorization header with JWT token
     * @param userId Optional filter by user ID
     * @param userType Optional filter by user type (client/coach)
     * @param action Optional filter by action (LOGIN, REGISTER, LOGOUT, etc.)
     * @param resourceType Optional filter by resource type
     * @param success Optional filter by success status
     * @param startDate Optional filter by start date
     * @param endDate Optional filter by end date
     * @param ipAddress Optional filter by IP address
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDirection Sort direction (default: desc)
     * @param httpRequest HTTP request for logging
     * @return Paginated list of user activity logs
     */
    @GetMapping
    public ResponseEntity<?> getAllUserActivityLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            // Extract admin ID and verify Level 0
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }
            
            UserActivityLogListResponse response = userActivityLogService.getAllUserActivityLogs(
                    userId, userType, action, resourceType, success,
                    startDate, endDate, ipAddress,
                    page, size, sortBy, sortDirection,
                    currentAdminId, httpRequest
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching user activity logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get user activity log by ID.
     * Only Level 0 (Super Admin) can access.
     *
     * @param token Authorization header with JWT token
     * @param logId Activity log ID
     * @param httpRequest HTTP request for logging
     * @return Activity log details
     */
    @GetMapping("/{logId}")
    public ResponseEntity<?> getUserActivityLogById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long logId,
            HttpServletRequest httpRequest
    ) {
        try {
            // Extract admin ID and verify Level 0
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }
            
            UserActivityLogDTO logDTO = userActivityLogService.getUserActivityLogById(logId, currentAdminId, httpRequest);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", logDTO
            ));
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error fetching user activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity log: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching user activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity log: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get activity logs for a specific user.
     * Only Level 0 (Super Admin) can access.
     *
     * @param token Authorization header with JWT token
     * @param userId User ID
     * @param userType User type (client/coach)
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDirection Sort direction (default: desc)
     * @param httpRequest HTTP request for logging
     * @return Paginated list of user's activity logs
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserActivityLogsByUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "client") String userType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            // Extract admin ID and verify Level 0
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }
            
            UserActivityLogListResponse response = userActivityLogService.getUserActivityLogsByUser(
                    userId, userType,
                    page, size, sortBy, sortDirection,
                    currentAdminId, httpRequest
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching user activity logs for user: {}", userId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get activity statistics.
     * Only Level 0 (Super Admin) can access.
     *
     * @param token Authorization header with JWT token
     * @param since Time period to get statistics from (default: 30 days ago)
     * @return Activity statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getActivityStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        try {
            // Extract admin ID and verify Level 0
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view activity statistics."
                ));
            }
            
            // Default to 30 days ago if not specified
            if (since == null) {
                since = LocalDateTime.now().minusDays(30);
            }
            
            Map<String, Object> stats = userActivityLogService.getActivityStatistics(since);
            stats.put("periodStart", since);
            stats.put("periodEnd", LocalDateTime.now());
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", stats
            ));
            
        } catch (Exception e) {
            log.error("Error fetching activity statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity statistics: " + e.getMessage()
            ));
        }
    }
}
