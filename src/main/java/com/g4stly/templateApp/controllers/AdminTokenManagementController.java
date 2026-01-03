package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.admin.PasswordResetTokenDTO;
import  com.g4stly.templateApp.dto.admin.TokenListResponse;
import  com.g4stly.templateApp.dto.admin.VerificationTokenDTO;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.repos.AdminRepository;
import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.AdminTokenManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/tokens")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminTokenManagementController {
    
    private final AdminTokenManagementService tokenManagementService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    
    /**
     * Get all password reset tokens
     * Only Level 0 (Super Admin) can access
     */
    @GetMapping("/password-reset")
    public ResponseEntity<?> getAllPasswordResetTokens(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false, defaultValue = "true") Boolean includeExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
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
                        "message", "Access denied. Only Level 0 Super Admins can view password reset tokens."
                ));
            }
            
            TokenListResponse<PasswordResetTokenDTO> response = tokenManagementService.getAllPasswordResetTokens(
                    userType, includeExpired, page, size, sortBy, sortDirection, currentAdminId, httpRequest
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching password reset tokens", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch password reset tokens: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get password reset token by ID
     * Only Level 0 (Super Admin) can access
     */
    @GetMapping("/password-reset/{tokenId}")
    public ResponseEntity<?> getPasswordResetTokenById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
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
                        "message", "Access denied. Only Level 0 Super Admins can view password reset tokens."
                ));
            }
            
            PasswordResetTokenDTO tokenDTO = tokenManagementService.getPasswordResetTokenById(tokenId, currentAdminId, httpRequest);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tokenDTO
            ));
            
        } catch (RuntimeException e) {
            log.error("Error fetching password reset token by ID: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching password reset token by ID: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch password reset token: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Delete password reset token
     * Only Level 0 (Super Admin) can access
     */
    @DeleteMapping("/password-reset/{tokenId}")
    public ResponseEntity<?> deletePasswordResetToken(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
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
                        "message", "Access denied. Only Level 0 Super Admins can delete password reset tokens."
                ));
            }
            
            tokenManagementService.deletePasswordResetToken(tokenId, currentAdminId, httpRequest);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset token deleted successfully"
            ));
            
        } catch (RuntimeException e) {
            log.error("Error deleting password reset token: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting password reset token: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete password reset token: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all verification tokens
     * Only Level 0 (Super Admin) can access
     */
    @GetMapping("/verification")
    public ResponseEntity<?> getAllVerificationTokens(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false, defaultValue = "true") Boolean includeExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
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
                        "message", "Access denied. Only Level 0 Super Admins can view verification tokens."
                ));
            }
            
            TokenListResponse<VerificationTokenDTO> response = tokenManagementService.getAllVerificationTokens(
                    userType, includeExpired, page, size, sortBy, sortDirection, currentAdminId, httpRequest
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
            
        } catch (Exception e) {
            log.error("Error fetching verification tokens", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch verification tokens: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get verification token by ID
     * Only Level 0 (Super Admin) can access
     */
    @GetMapping("/verification/{tokenId}")
    public ResponseEntity<?> getVerificationTokenById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
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
                        "message", "Access denied. Only Level 0 Super Admins can view verification tokens."
                ));
            }
            
            VerificationTokenDTO tokenDTO = tokenManagementService.getVerificationTokenById(tokenId, currentAdminId, httpRequest);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tokenDTO
            ));
            
        } catch (RuntimeException e) {
            log.error("Error fetching verification token by ID: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching verification token by ID: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch verification token: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Delete verification token
     * Only Level 0 (Super Admin) can access
     */
    @DeleteMapping("/verification/{tokenId}")
    public ResponseEntity<?> deleteVerificationToken(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
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
                        "message", "Access denied. Only Level 0 Super Admins can delete verification tokens."
                ));
            }
            
            tokenManagementService.deleteVerificationToken(tokenId, currentAdminId, httpRequest);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification token deleted successfully"
            ));
            
        } catch (RuntimeException e) {
            log.error("Error deleting verification token: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting verification token: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete verification token: " + e.getMessage()
            ));
        }
    }
}
