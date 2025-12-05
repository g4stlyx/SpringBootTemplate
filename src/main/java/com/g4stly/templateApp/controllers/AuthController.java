package  com.g4stly.templateApp.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import  com.g4stly.templateApp.dto.auth.AuthResponse;
import  com.g4stly.templateApp.dto.auth.ForgotPasswordRequest;
import  com.g4stly.templateApp.dto.auth.LoginRequest;
import  com.g4stly.templateApp.dto.auth.RegisterRequest;
import  com.g4stly.templateApp.dto.auth.ResendVerificationRequest;
import  com.g4stly.templateApp.dto.auth.ResetPasswordRequest;
import  com.g4stly.templateApp.dto.auth.UserSessionDTO;
import  com.g4stly.templateApp.dto.auth.VerifyPasswordRequest;
import  com.g4stly.templateApp.dto.two_factor.TwoFactorRequiredResponse;
import  com.g4stly.templateApp.services.AuthService;
import  com.g4stly.templateApp.services.CaptchaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private CaptchaService captchaService;

    @Value("${recaptcha.enabled:false}")
    private boolean captchaEnabled;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        log.info("Registration attempt for username: {}", request.getUsername());

        AuthResponse response = authService.register(request, httpRequest);

        if (response.isSuccess()) {
            log.info("Registration successful for username: {}", request.getUsername());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Registration failed for username: {} - {}", request.getUsername(), response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Authenticate user login.
     * For admin logins:
     * - CAPTCHA verification is required when enabled
     * - Returns HTTP 202 with 2FA required response if 2FA is enabled
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login attempt for username: {}", request.getUsername());

        // Verify CAPTCHA if enabled AND user is admin
        boolean isAdminLogin = "admin".equalsIgnoreCase(request.getUserType());
        
        if (captchaEnabled && isAdminLogin) {
            // CAPTCHA is REQUIRED for admin logins
            if (request.getCaptchaToken() == null || request.getCaptchaToken().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "CAPTCHA is required for admin login");
                errorResponse.put("captchaRequired", true);
                log.warn("Admin login attempt without CAPTCHA for username: {}", request.getUsername());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            boolean captchaValid = captchaService.verifyCaptcha(
                    request.getCaptchaToken(),
                    httpRequest.getRemoteAddr()
            );
            
            if (!captchaValid) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "CAPTCHA verification failed. Please try again.");
                errorResponse.put("captchaRequired", true);
                log.warn("CAPTCHA verification failed for username: {}", request.getUsername());
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        AuthResponse response = authService.login(request, httpRequest);

        if (response.isSuccess()) {
            log.info("Login successful for username: {}", request.getUsername());
            return ResponseEntity.ok(response);
        } else if (response.isRequiresTwoFactor()) {
            // Admin login with 2FA enabled - return 202 Accepted
            log.info("2FA required for username: {}", request.getUsername());
            TwoFactorRequiredResponse twoFactorResponse = new TwoFactorRequiredResponse(
                "Two-factor authentication required. Please enter your verification code.",
                request.getUsername(),
                true,
                null // No temp token needed - we verify by username
            );
            return ResponseEntity.status(202).body(twoFactorResponse);
        } else {
            log.warn("Login failed for username: {} - {}", request.getUsername(), response.getMessage());
            return ResponseEntity.status(401).body(response);
        }
    }

    /**
     * Verify password for password change operations
     */
    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Object>> verifyPassword(
            @Valid @RequestBody VerifyPasswordRequest request,
            HttpServletRequest httpRequest) {

        log.info("Password verification attempt for username: {}", request.getUsername());

        boolean isValid = authService.verifyPassword(request, httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        response.put("message", isValid ? "Password is valid" : "Password is invalid");

        if (isValid) {
            log.info("Password verification successful for username: {}", request.getUsername());
        } else {
            log.warn("Password verification failed for username: {}", request.getUsername());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Initiate forgot password process
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        log.info("Password reset request for email: {}", request.getEmail());

        boolean success = authService.forgotPassword(request, httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", "If the email exists in our system, a password reset link has been sent.");

        return ResponseEntity.ok(response);
    }

    /**
     * Reset password using token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        log.info("Password reset attempt with token");

        boolean success = authService.resetPassword(request, httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message",
                success ? "Password has been reset successfully. You can now login with your new password."
                        : "Invalid or expired reset token. Please request a new password reset.");

        if (success) {
            log.info("Password reset successful");
        } else {
            log.warn("Password reset failed - invalid or expired token");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Verify email address using verification token
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam String token) {

        log.info("Email verification attempt with token");

        boolean success = authService.verifyEmail(token);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Email verified successfully. You can now login to your account."
                : "Invalid or expired verification token.");

        if (success) {
            log.info("Email verification successful");
        } else {
            log.warn("Email verification failed - invalid or expired token");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Resend verification email
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        log.info("Resend verification email request for email: {}", request.getEmail());

        boolean success = authService.resendVerificationEmail(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", "If the email exists and is not verified, a new verification link has been sent.");

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for authentication service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Authentication Service");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Get current user's minimal session information
     * Returns only essential data needed for UI header (name, profile picture, role)
     * This is an optimized endpoint to avoid fetching full profile data
     */
    @GetMapping("/me")
    public ResponseEntity<UserSessionDTO> getCurrentUser(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        UserSessionDTO session = authService.getCurrentUserSession(authHeader.substring(7));
        
        if (session == null) {
            return ResponseEntity.status(401).build();
        }
        
        return ResponseEntity.ok(session);
    }
}