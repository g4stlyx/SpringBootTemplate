package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.refresh_token.RefreshTokenResponse;
import com.g4stly.templateApp.services.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin controller for managing refresh tokens.
 * Provides endpoints for viewing, filtering, searching, revoking,
 * and deleting refresh tokens. Restricted to admin level 0 and 1.
 */
@RestController
@RequestMapping("/api/v1/admin/refresh-tokens")
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminRefreshTokenController {

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Get all refresh tokens with optional filters.
     * Supports filtering by userType, userId, isRevoked, ipAddress.
     */
    @GetMapping
    public ResponseEntity<?> getAllTokens(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean isRevoked,
            @RequestParam(required = false) String ipAddress) {
        try {
            List<RefreshTokenResponse> tokens = refreshTokenService.getFilteredTokens(
                    userType, userId, isRevoked, ipAddress);

            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve refresh tokens: " + e.getMessage());
        }
    }

    /**
     * Get a specific refresh token by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTokenById(
            @PathVariable Long id) {
        try {
            Optional<RefreshTokenResponse> token = refreshTokenService.getTokenById(id);
            if (token.isPresent()) {
                return ResponseEntity.ok(token.get());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve refresh token: " + e.getMessage());
        }
    }

    /**
     * Get active refresh tokens for a specific user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getActiveTokensForUser(
            @PathVariable Long userId,
            @RequestParam String userType) {
        try {
            List<RefreshTokenResponse> tokens = refreshTokenService.getActiveTokensForUser(userId, userType);
            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve user tokens: " + e.getMessage());
        }
    }

    /**
     * Get token statistics for the admin dashboard.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getTokenStatistics() {
        try {
            Map<String, Object> stats = refreshTokenService.getTokenStatistics();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve token statistics: " + e.getMessage());
        }
    }

    /**
     * Revoke a specific refresh token by ID.
     */
    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revokeToken(
            @PathVariable Long id) {
        try {
            boolean revoked = refreshTokenService.revokeTokenById(id);
            if (revoked) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Refresh token revoked successfully");
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to revoke refresh token: " + e.getMessage());
        }
    }

    /**
     * Revoke all refresh tokens for a specific user.
     */
    @PutMapping("/revoke-all")
    public ResponseEntity<?> revokeAllUserTokens(
            @RequestParam Long userId,
            @RequestParam String userType) {
        try {
            int count = refreshTokenService.revokeAllUserTokens(userId, userType);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All tokens revoked for user");
            response.put("revokedCount", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to revoke user tokens: " + e.getMessage());
        }
    }

    /**
     * Delete a specific refresh token by ID (permanent removal).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteToken(
            @PathVariable Long id) {
        try {
            boolean deleted = refreshTokenService.deleteTokenById(id);
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Refresh token deleted successfully");
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete refresh token: " + e.getMessage());
        }
    }

    /**
     * Trigger manual cleanup of expired and revoked tokens (Level 0 only).
     */
    @PostMapping("/cleanup")
    public ResponseEntity<?> triggerCleanup() {
        try {
            int cleaned = refreshTokenService.cleanupExpiredTokens();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Token cleanup completed");
            response.put("tokensRemoved", cleaned);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to cleanup tokens: " + e.getMessage());
        }
    }
}
