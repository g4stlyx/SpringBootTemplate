package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.dto.two_factor.TwoFactorLoginRequest;
import  com.g4stly.templateApp.dto.two_factor.TwoFactorSetupResponse;
import  com.g4stly.templateApp.dto.two_factor.TwoFactorVerifyRequest;
import  com.g4stly.templateApp.dto.auth.AuthResponse;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.models.RefreshToken;
import  com.g4stly.templateApp.security.JwtUtils;
import  com.g4stly.templateApp.services.RefreshTokenService;
import  com.g4stly.templateApp.services.TwoFactorAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for managing Two-Factor Authentication for admin users.
 * Provides endpoints for setup, verification, and status checking.
 */
@RestController
@RequestMapping("/api/v1/admin/2fa")
@Slf4j
public class TwoFactorAuthController {

    @Autowired
    private TwoFactorAuthService twoFactorAuthService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Setup/Generate 2FA secret and QR code for an admin.
     * Admin must be authenticated.
     * 
     * @param token Authorization header containing JWT token
     * @return TwoFactorSetupResponse with secret and QR code
     */
    @PostMapping("/setup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TwoFactorSetupResponse> setup(@RequestHeader("Authorization") String token) {
        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA setup requested for admin ID: {}", adminId);
        
        TwoFactorSetupResponse response = twoFactorAuthService.generateSecret(adminId);
        
        log.info("2FA setup completed for admin ID: {}", adminId);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify the 2FA code and enable 2FA for the admin.
     * 
     * @param token Authorization header containing JWT token
     * @param request Request containing the 6-digit verification code
     * @return Success/failure response
     */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        
        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA verification requested for admin ID: {}", adminId);
        
        boolean isValid = twoFactorAuthService.verifyAndEnable(adminId, request.getCode());

        Map<String, Object> response = new HashMap<>();
        if (isValid) {
            response.put("success", true);
            response.put("message", "2FA has been enabled successfully");
            log.info("2FA enabled successfully for admin ID: {}", adminId);
        } else {
            response.put("success", false);
            response.put("message", "Invalid verification code");
            log.warn("Invalid 2FA verification code for admin ID: {}", adminId);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Disable 2FA for an admin (requires verification code).
     * 
     * @param token Authorization header containing JWT token
     * @param request Request containing the 6-digit verification code
     * @return Success/failure response
     */
    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> disable(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        
        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA disable requested for admin ID: {}", adminId);
        
        try {
            twoFactorAuthService.disable(adminId, request.getCode());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "2FA has been disabled successfully");
            log.info("2FA disabled successfully for admin ID: {}", adminId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            log.warn("Failed to disable 2FA for admin ID: {} - {}", adminId, e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Check if 2FA is enabled for the current admin.
     * 
     * @param token Authorization header containing JWT token
     * @return Response with enabled status
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> status(@RequestHeader("Authorization") String token) {
        Long adminId = extractAdminIdFromToken(token);
        boolean enabled = twoFactorAuthService.isTwoFactorEnabled(adminId);

        Map<String, Object> response = new HashMap<>();
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }

    /**
     * Complete 2FA login - verify code and return full auth response.
     * This endpoint is public (no JWT required) as it's part of the login flow.
     * SECURITY: Requires a valid challenge token issued during password verification.
     * 
     * @param request Request containing username, 6-digit code, and challenge token
     * @return AuthResponse with JWT tokens on success
     */
    @PostMapping("/verify-login")
    public ResponseEntity<?> verifyLogin(@Valid @RequestBody TwoFactorLoginRequest request, HttpServletRequest httpRequest) {
        log.info("2FA login verification for username: {}", request.getUsername());
        
        try {
            boolean isValid = twoFactorAuthService.verifyCodeByUsername(
                request.getUsername(), 
                request.getCode(),
                request.getChallengeToken()
            );
            
            if (!isValid) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid verification code or challenge token. Please try logging in again.");
                log.warn("Invalid 2FA code or challenge token for username: {}", request.getUsername());
                return ResponseEntity.status(401).body(errorResponse);
            }
            
            // Get admin and generate tokens
            Admin admin = twoFactorAuthService.getAdminByUsername(request.getUsername());
            
            // Update last login
            admin.setLastLoginAt(LocalDateTime.now());
            
            // Generate access token with admin level
            String accessToken = jwtUtils.generateToken(admin.getUsername(), admin.getId(), "admin", admin.getLevel());
            
            // Create and save refresh token in database
            RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
                admin.getId(), "admin", httpRequest);
            
            log.info("Returning refresh token: {}...", refreshTokenEntity.getToken().substring(0, 8));
            
            AuthResponse authResponse = AuthResponse.builder()
                .success(true)
                .message("Admin login successful")
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getToken())
                .expiresIn(jwtUtils.getAccessTokenExpiration())
                .user(AuthResponse.UserInfo.builder()
                    .id(admin.getId())
                    .username(admin.getUsername())
                    .email(admin.getEmail())
                    .firstName(admin.getFirstName())
                    .lastName(admin.getLastName())
                    .profilePicture(admin.getProfilePicture())
                    .isActive(admin.getIsActive())
                    .emailVerified(true) // Admins are auto-verified
                    .userType("admin")
                    .level(admin.getLevel())
                    .lastLoginAt(admin.getLastLoginAt())
                    .build())
                .build();
            
            log.info("2FA login successful for username: {}", request.getUsername());
            return ResponseEntity.ok(authResponse);
            
        } catch (Exception e) {
            log.error("2FA login failed for username: {} - {}", request.getUsername(), e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(401).body(errorResponse);
        }
    }

    /**
     * Extract admin ID from JWT token
     * @param token The Authorization header value
     * @return Admin ID as Long
     */
    private Long extractAdminIdFromToken(String token) {
        String jwtToken = token.replace("Bearer ", "");
        return jwtUtils.extractUserIdAsLong(jwtToken);
    }
}
