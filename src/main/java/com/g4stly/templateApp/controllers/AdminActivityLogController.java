package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.admin.AdminActivityLogDTO;
import  com.g4stly.templateApp.dto.admin.AdminActivityLogListResponse;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.repos.AdminRepository;
import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.AdminActivityLogService;
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
 * Controller for managing admin activity logs.
 * All endpoints are restricted to Level 0 (Super Admin) only.
 */
@RestController
@RequestMapping("/api/v1/admin/activity-logs")
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogController {
    
    private final AdminActivityLogService activityLogService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    
    /**
     * Get all admin activity logs with pagination and optional filtering.
     * Only Level 0 (Super Admin) can access.
     * 
     * @param token Authorization header with JWT token
     * @param adminId Optional filter by admin ID
     * @param action Optional filter by action (READ, CREATE, UPDATE, DELETE)
     * @param resourceType Optional filter by resource type
     * @param startDate Optional filter by start date
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDirection Sort direction (default: desc)
     * @param httpRequest HTTP request for logging
     * @return Paginated list of activity logs
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllActivityLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
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
                        "message", "Access denied. Only Level 0 Super Admins can view activity logs."
                ));
            }
            
            AdminActivityLogListResponse response = activityLogService.getAllActivityLogs(
                    adminId, action, resourceType, startDate, 
                    page, size, sortBy, sortDirection, 
                    currentAdminId, httpRequest
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching activity logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get activity log by ID.
     * Only Level 0 (Super Admin) can access.
     * 
     * @param token Authorization header with JWT token
     * @param logId Activity log ID
     * @param httpRequest HTTP request for logging
     * @return Activity log details
     */
    @GetMapping("/{logId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getActivityLogById(
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
                        "message", "Access denied. Only Level 0 Super Admins can view activity logs."
                ));
            }
            
            AdminActivityLogDTO logDTO = activityLogService.getActivityLogById(logId, currentAdminId, httpRequest);
            
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
            log.error("Error fetching activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity log: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity log: " + e.getMessage()
            ));
        }
    }
}
