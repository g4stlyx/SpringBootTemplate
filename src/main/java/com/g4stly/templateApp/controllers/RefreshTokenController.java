package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.refresh_token.RefreshTokenRequest;
import com.g4stly.templateApp.models.RefreshToken;
import com.g4stly.templateApp.security.JwtUtils;
import com.g4stly.templateApp.services.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for refresh token operations.
 * Handles token refresh and logout for all user types (client, coach, admin).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class RefreshTokenController {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${app.refresh-token.cookie-name:refreshToken}")
    private String cookieName;

    @Value("${app.refresh-token.cookie-max-age:2592000}") // 30 days in seconds
    private int cookieMaxAge;

    @Value("${app.refresh-token.use-cookies:true}")
    private boolean useCookies;

    /**
     * Refresh access token using refresh token.
     * Accepts refresh token from cookies (web) or request body (mobile).
     * Returns: new access token and new refresh token (token rotation).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            // Extract refresh token from cookie or request body
            String refreshTokenStr = extractRefreshToken(httpRequest, request);

            if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
                log.warn("Refresh token missing from request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token is required"));
            }

            log.info("Received refresh token: {}...", refreshTokenStr.substring(0, Math.min(8, refreshTokenStr.length())));

            // Verify refresh token
            Optional<RefreshToken> refreshTokenOpt = refreshTokenService.verifyRefreshToken(refreshTokenStr);
            if (refreshTokenOpt.isEmpty()) {
                log.warn("Invalid or expired refresh token");
                clearRefreshTokenCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token. Please login again."));
            }

            RefreshToken oldRefreshToken = refreshTokenOpt.get();
            log.info("Refreshing token for userId={}, userType={}", 
                    oldRefreshToken.getUserId(), oldRefreshToken.getUserType());

            // Rotate refresh token (revoke old, create new)
            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldRefreshToken, httpRequest);

            // Generate new access token
            String username = resolveUsername(oldRefreshToken.getUserId(), oldRefreshToken.getUserType());
            String accessToken = jwtUtils.generateToken(
                    username, 
                    oldRefreshToken.getUserId(), 
                    oldRefreshToken.getUserType()
            );

            // Set new refresh token in cookie if using cookies
            if (useCookies) {
                setRefreshTokenCookie(httpResponse, newRefreshToken.getToken());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", jwtUtils.getAccessTokenExpiration());
            
            // Only include refresh token in response for mobile clients
            if (!useCookies || request != null) {
                response.put("refreshToken", newRefreshToken.getToken());
            }

            log.info("Token refresh successful for userId={}", oldRefreshToken.getUserId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during token refresh: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to refresh token"));
        }
    }

    /**
     * Logout - revoke refresh token.
     * Accepts refresh token from cookies or request body.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            // Extract refresh token from cookie or request body
            String refreshTokenStr = extractRefreshToken(httpRequest, request);

            if (refreshTokenStr != null && !refreshTokenStr.isEmpty()) {
                boolean revoked = refreshTokenService.revokeRefreshToken(refreshTokenStr);
                if (revoked) {
                    log.info("Refresh token revoked successfully");
                } else {
                    log.warn("Refresh token not found for revocation");
                }
            }

            // Clear cookie regardless
            clearRefreshTokenCookie(httpResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage(), e);
            // Still clear cookie and return success - don't fail logout
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully", "success", true));
        }
    }

    /**
     * Logout from all devices - revoke all refresh tokens for the user.
     * Requires authentication (accessed by logged-in users).
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            // Extract user info from JWT access token
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            String accessToken = authHeader.substring(7);
            Long userId = jwtUtils.extractUserIdAsLong(accessToken);
            String userType = jwtUtils.extractUserType(accessToken);

            if (userId == null || userType == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid access token"));
            }

            // Revoke all tokens for this user
            int revokedCount = refreshTokenService.revokeAllUserTokens(userId, userType);

            // Clear current cookie
            clearRefreshTokenCookie(httpResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logged out from all devices successfully");
            response.put("revokedTokens", revokedCount);
            response.put("success", true);

            log.info("User userId={}, userType={} logged out from all devices. {} tokens revoked.", 
                    userId, userType, revokedCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during logout-all: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to logout from all devices"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Extract refresh token from cookie or request body.
     * Priority: cookie (for web) > request body (for mobile).
     */
    private String extractRefreshToken(HttpServletRequest httpRequest, RefreshTokenRequest request) {
        // Try cookie first (web clients)
        if (httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Fallback to request body (mobile clients)
        if (request != null && request.getRefreshToken() != null) {
            return request.getRefreshToken();
        }

        return null;
    }

    /**
     * Set refresh token in HttpOnly secure cookie.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(cookieName, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxAge);
        response.addCookie(cookie);
    }

    /**
     * Clear refresh token cookie.
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * Resolve username by userId and userType.
     * This is a simple fallback - in production, you might want to inject repositories.
     */
    private String resolveUsername(Long userId, String userType) {
        // For simplicity, we return a generic username
        // In a real implementation, you'd query the appropriate repository
        return userType + "_" + userId;
    }
}
