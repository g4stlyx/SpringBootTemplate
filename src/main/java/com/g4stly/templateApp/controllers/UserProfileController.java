package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.profile.ChangePasswordRequest;
import com.g4stly.templateApp.dto.user.ChangeEmailRequest;
import com.g4stly.templateApp.dto.user.DeactivateAccountRequest;
import com.g4stly.templateApp.dto.user.UpdateUserProfileRequest;
import com.g4stly.templateApp.dto.user.UserProfileDTO;
import com.g4stly.templateApp.services.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User profile management endpoints.
 *
 * All endpoints extract the authenticated user's ID from {@code authentication.getDetails()},
 * meaning a user can only ever read or modify their own data — no userId path/query
 * parameters are accepted to prevent IDOR attacks.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
public class UserProfileController {

    private final UserProfileService userProfileService;

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/profile
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getDetails();
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/profile
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping
    public ResponseEntity<UserProfileDTO> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/profile/change-password
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        userProfileService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/profile/change-email
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Initiates an email change.
     *
     * Requires current-password confirmation.  A verification link is sent to
     * the new address; the email is only updated once that link is clicked via
     * {@code GET /api/v1/auth/verify-email-change?token=…}.
     */
    @PostMapping("/change-email")
    public ResponseEntity<Map<String, Object>> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        userProfileService.requestEmailChange(userId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification email sent to " + request.getNewEmail()
                        + ". Please click the link to confirm the change."));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/profile
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Soft-delete the caller's own account.
     *
     * Requires password confirmation in the request body to prevent accidental
     * or CSRF-driven account closure.  The account enters a 30-day grace period:
     * logging in during that window reactivates it automatically.
     * After 30 days the nightly AccountCleanupScheduledService permanently anonymises PII.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deactivateAccount(
            @Valid @RequestBody DeactivateAccountRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        log.info("User {} requested self-deactivation", userId);
        userProfileService.deactivateAccount(userId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account deactivated. You may reactivate it by logging in within 30 days."
        ));
    }
}
