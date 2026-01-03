package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.admin.*;
import  com.g4stly.templateApp.services.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/admins")
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1()")
public class AdminManagementController {
    
    @Autowired
    private AdminManagementService adminManagementService;
    
    /**
     * Get all admins with pagination (filtered by permission)
     * GET /api/v1/admin/admins?page=0&size=10&sortBy=createdAt&sortDirection=desc
     */
    @GetMapping
    public ResponseEntity<AdminListResponse> getAllAdmins(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching all admins - page: {}, size: {}", requestingAdminId, page, size);
        
        AdminListResponse response = adminManagementService.getAllAdmins(
            requestingAdminId, page, size, sortBy, sortDirection, httpRequest);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get admin by ID
     * GET /api/v1/admin/admins/{adminId}
     */
    @GetMapping("/{adminId}")
    public ResponseEntity<AdminManagementDTO> getAdminById(
            @PathVariable Long adminId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching admin {}", requestingAdminId, adminId);
        
        AdminManagementDTO admin = adminManagementService.getAdminById(requestingAdminId, adminId, httpRequest);
        return ResponseEntity.ok(admin);
    }
    
    /**
     * Create new admin
     * POST /api/v1/admin/admins
     */
    @PostMapping
    public ResponseEntity<AdminManagementDTO> createAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} creating new admin with level {}", requestingAdminId, request.getLevel());
        
        AdminManagementDTO admin = adminManagementService.createAdmin(requestingAdminId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(admin);
    }
    
    /**
     * Update admin
     * PUT /api/v1/admin/admins/{adminId}
     */
    @PutMapping("/{adminId}")
    public ResponseEntity<AdminManagementDTO> updateAdmin(
            @PathVariable Long adminId,
            @Valid @RequestBody UpdateAdminRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} updating admin {}", requestingAdminId, adminId);
        
        AdminManagementDTO admin = adminManagementService.updateAdmin(
            requestingAdminId, adminId, request, httpRequest);
        return ResponseEntity.ok(admin);
    }
    
    /**
     * Delete admin (soft delete)
     * DELETE /api/v1/admin/admins/{adminId}
     */
    @DeleteMapping("/{adminId}")
    public ResponseEntity<Map<String, Object>> deleteAdmin(
            @PathVariable Long adminId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} deleting admin {}", requestingAdminId, adminId);
        
        adminManagementService.deleteAdmin(requestingAdminId, adminId, httpRequest);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin deactivated successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Activate admin
     * POST /api/v1/admin/admins/{adminId}/activate
     */
    @PostMapping("/{adminId}/activate")
    public ResponseEntity<AdminManagementDTO> activateAdmin(
            @PathVariable Long adminId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} activating admin {}", requestingAdminId, adminId);
        
        AdminManagementDTO admin = adminManagementService.activateAdmin(requestingAdminId, adminId, httpRequest);
        return ResponseEntity.ok(admin);
    }
    
    /**
     * Deactivate admin
     * POST /api/v1/admin/admins/{adminId}/deactivate
     */
    @PostMapping("/{adminId}/deactivate")
    public ResponseEntity<AdminManagementDTO> deactivateAdmin(
            @PathVariable Long adminId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} deactivating admin {}", requestingAdminId, adminId);
        
        AdminManagementDTO admin = adminManagementService.deactivateAdmin(requestingAdminId, adminId, httpRequest);
        return ResponseEntity.ok(admin);
    }
    
    /**
     * Reset admin password
     * POST /api/v1/admin/admins/{adminId}/reset-password
     */
    @PostMapping("/{adminId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetAdminPassword(
            @PathVariable Long adminId,
            @Valid @RequestBody ResetUserPasswordRequest request,
            Authentication authentication) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} resetting password for admin {}", requestingAdminId, adminId);
        
        adminManagementService.resetAdminPassword(requestingAdminId, adminId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin password reset successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Unlock admin account
     * POST /api/v1/admin/admins/{adminId}/unlock
     */
    @PostMapping("/{adminId}/unlock")
    public ResponseEntity<AdminManagementDTO> unlockAdmin(
            @PathVariable Long adminId,
            Authentication authentication) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} unlocking admin {}", requestingAdminId, adminId);
        
        AdminManagementDTO admin = adminManagementService.unlockAdmin(requestingAdminId, adminId);
        return ResponseEntity.ok(admin);
    }
}
