package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.admin.AuthErrorLogListResponse;
import  com.g4stly.templateApp.dto.admin.AuthErrorLogResponse;
import  com.g4stly.templateApp.dto.admin.AuthErrorStatisticsResponse;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.repos.AdminRepository;
import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.AdminAuthErrorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller for admin management of authentication error logs.
 * All endpoints are restricted to admins.
 * Delete operations are restricted to Level 0 (Super Admin) only.
 */
@RestController
@RequestMapping("/api/v1/admin/auth-error-logs")
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuthErrorController {
    
    @Autowired
    private AdminAuthErrorService adminAuthErrorService;
    
    @Autowired
    private AdminRepository adminRepository;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    /**
     * GET /api/v1/admin/auth-error-logs
     * Get all authentication error logs with pagination and filtering
     */
    @GetMapping
    public ResponseEntity<?> getAllLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            
            log.info("Admin {} requesting auth error logs", adminId);
            
            AuthErrorLogListResponse response = adminAuthErrorService.getAllLogs(
                    adminId, page, size, sortBy, sortDirection, userId, userType, errorType, ipAddress, startDate, request
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching auth error logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * GET /api/v1/admin/auth-error-logs/{id}
     * Get a single authentication error log by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getLogById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            
            log.info("Admin {} requesting auth error log: {}", adminId, id);
            
            AuthErrorLogResponse response = adminAuthErrorService.getLogById(adminId, id, request);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error fetching auth error log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error log: " + e.getMessage()
            ));
        }
    }
    
    /**
     * GET /api/v1/admin/auth-error-logs/user/{userId}
     * Get authentication error logs by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getLogsByUserId(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            
            log.info("Admin {} requesting auth error logs for user: {}", adminId, userId);
            
            AuthErrorLogListResponse response = adminAuthErrorService.getLogsByUserId(
                    adminId, userId, page, size, request
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching auth error logs by user", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * GET /api/v1/admin/auth-error-logs/ip/{ipAddress}
     * Get authentication error logs by IP address
     */
    @GetMapping("/ip/{ipAddress}")
    public ResponseEntity<?> getLogsByIpAddress(
            @RequestHeader("Authorization") String token,
            @PathVariable String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            
            log.info("Admin {} requesting auth error logs for IP: {}", adminId, ipAddress);
            
            AuthErrorLogListResponse response = adminAuthErrorService.getLogsByIpAddress(
                    adminId, ipAddress, page, size, request
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching auth error logs by IP", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs: " + e.getMessage()
            ));
        }
    }
    
    /**
     * GET /api/v1/admin/auth-error-logs/statistics
     * Get authentication error statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestHeader("Authorization") String token,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            
            log.info("Admin {} requesting auth error statistics", adminId);
            
            AuthErrorStatisticsResponse response = adminAuthErrorService.getStatistics(adminId, request);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching auth error statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error statistics: " + e.getMessage()
            ));
        }
    }
    
    /**
     * DELETE /api/v1/admin/auth-error-logs/{id}
     * Delete an authentication error log (Level 0 admins only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLog(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            HttpServletRequest request) {
        
        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            
            // Only Level 0 can delete
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can delete authentication error logs."
                ));
            }
            
            log.info("Admin {} deleting auth error log: {}", adminId, id);
            
            adminAuthErrorService.deleteLog(adminId, id, request);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Authentication error log deleted successfully"
            ));
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error deleting auth error log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete authentication error log: " + e.getMessage()
            ));
        }
    }
}
