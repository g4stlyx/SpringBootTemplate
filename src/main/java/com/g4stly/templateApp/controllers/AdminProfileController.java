package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.profile.AdminProfileDTO;
import  com.g4stly.templateApp.dto.profile.ChangePasswordRequest;
import  com.g4stly.templateApp.dto.profile.UpdateAdminProfileRequest;
import  com.g4stly.templateApp.services.AdminProfileService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/profile")
@Slf4j
public class AdminProfileController {
    
    @Autowired
    private AdminProfileService adminProfileService;
    
    /**
     * Get current admin's profile
     * GET /api/v1/admin/profile
     */
    @GetMapping
    public ResponseEntity<AdminProfileDTO> getAdminProfile(Authentication authentication) {
        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching their profile", adminId);
        
        AdminProfileDTO profile = adminProfileService.getAdminProfile(adminId);
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Get any admin's profile by ID (for super admins and higher level admins)
     * GET /api/v1/admin/profile/{adminId}
     */
    @GetMapping("/{adminId}")
    public ResponseEntity<AdminProfileDTO> getAdminProfileById(
            @PathVariable Long adminId,
            Authentication authentication) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching profile of admin {}", requestingAdminId, adminId);
        
        AdminProfileDTO profile = adminProfileService.getAdminProfile(adminId);
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Update current admin's profile
     * PUT /api/v1/admin/profile
     */
    @PutMapping
    public ResponseEntity<AdminProfileDTO> updateAdminProfile(
            @Valid @RequestBody UpdateAdminProfileRequest request,
            Authentication authentication) {
        
        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} updating their profile", adminId);
        
        AdminProfileDTO profile = adminProfileService.updateAdminProfile(adminId, request);
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Change current admin's password
     * POST /api/v1/admin/profile/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        
        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} changing their password", adminId);
        
        adminProfileService.changePassword(adminId, request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Password changed successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deactivate an admin account (only for super admins or higher level admins)
     * POST /api/v1/admin/profile/{adminId}/deactivate
     */
    @PostMapping("/{adminId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateAccount(
            @PathVariable Long adminId,
            Authentication authentication) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} deactivating account of admin {}", requestingAdminId, adminId);
        
        adminProfileService.deactivateAccount(adminId, requestingAdminId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin account deactivated successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reactivate an admin account (only for super admins or higher level admins)
     * POST /api/v1/admin/profile/{adminId}/reactivate
     */
    @PostMapping("/{adminId}/reactivate")
    public ResponseEntity<Map<String, Object>> reactivateAccount(
            @PathVariable Long adminId,
            Authentication authentication) {
        
        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} reactivating account of admin {}", requestingAdminId, adminId);
        
        adminProfileService.reactivateAccount(adminId, requestingAdminId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin account reactivated successfully");
        
        return ResponseEntity.ok(response);
    }
}
