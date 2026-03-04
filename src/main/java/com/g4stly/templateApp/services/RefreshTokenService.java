package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.refresh_token.RefreshTokenResponse;
import com.g4stly.templateApp.models.RefreshToken;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.repos.RefreshTokenRepository;
import com.g4stly.templateApp.security.JwtUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    /**
     * Create a new refresh token for a user.
     */
    public RefreshToken createRefreshToken(Long userId, String role, HttpServletRequest request) {
        long expirationDays = jwtUtils.getRefreshTokenExpirationDays();
        log.info("Creating refresh token for userId={}, role={}, expirationDays={}", userId, role, expirationDays);
        
        RefreshToken refreshToken = new RefreshToken(userId, role, expirationDays);

        // Capture device info and IP
        refreshToken.setDeviceInfo(extractDeviceInfo(request));
        refreshToken.setIpAddress(getClientIpAddress(request));

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Saved refresh token - ID: {}, token: {}, expiryDate: {}", 
                saved.getId(), saved.getToken().substring(0, 8) + "...", saved.getExpiryDate());
        return saved;
    }

    /**
     * Verify a refresh token is valid (exists, not expired, not revoked).
     */
    public Optional<RefreshToken> verifyRefreshToken(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Refresh token is null or empty");
            return Optional.empty();
        }

        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        if (refreshTokenOpt.isEmpty()) {
            log.warn("Refresh token not found in database: {}", token.substring(0, Math.min(8, token.length())));
            return Optional.empty();
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        log.info("Found refresh token - ID: {}, userId: {}, role: {}, isRevoked: {}, expiryDate: {}, now: {}", 
                refreshToken.getId(), refreshToken.getUserId(), refreshToken.getRole(), 
                refreshToken.getIsRevoked(), refreshToken.getExpiryDate(), LocalDateTime.now());

        // Check if revoked - possible token reuse attack
        if (refreshToken.getIsRevoked()) {
            // Security: If a revoked token is used, revoke ALL tokens for this user
            // This indicates a potential token theft/reuse attack
            log.error("WARNING: Revoked refresh token reuse detected for userId={} role={}", 
                    refreshToken.getUserId(), refreshToken.getRole());
            refreshTokenRepository.revokeAllUserTokens(refreshToken.getUserId(), refreshToken.getRole());
            return Optional.empty();
        }

        // Check expiry
        if (refreshToken.isExpired()) {
            log.warn("Refresh token expired - expiryDate: {}, now: {}", 
                    refreshToken.getExpiryDate(), LocalDateTime.now());
            return Optional.empty();
        }

        log.info("Refresh token is valid");
        return Optional.of(refreshToken);
    }

    /**
     * Rotate refresh token: revoke old one, create a new one.
     * Returns the new refresh token.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, HttpServletRequest request) {
        // Revoke old token
        oldToken.setIsRevoked(true);
        oldToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(oldToken);

        // Create new token
        return createRefreshToken(oldToken.getUserId(), oldToken.getRole(), request);
    }

    /**
     * Revoke a specific refresh token.
     */
    @Transactional
    public boolean revokeRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            refreshToken.setIsRevoked(true);
            refreshTokenRepository.save(refreshToken);
            return true;
        }
        return false;
    }

    /**
     * Revoke all refresh tokens for a user.
     */
    @Transactional
    public int revokeAllUserTokens(Long userId, String role) {
        return refreshTokenRepository.revokeAllUserTokens(userId, role);
    }

    /**
     * Cleanup expired and revoked tokens from the database.
     */
    @Transactional
    public int cleanupExpiredTokens() {
        // Delete tokens that are both revoked and expired (old data)
        int deletedRevokedExpired = refreshTokenRepository.cleanupRevokedAndExpired(LocalDateTime.now());

        // Also delete tokens that expired more than 7 days ago (even if not revoked)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        refreshTokenRepository.deleteByExpiryDateBefore(sevenDaysAgo);

        return deletedRevokedExpired;
    }

    // ==================== Admin Management Methods ====================

    /**
     * Get all refresh tokens with optional filters.
     */
    public List<RefreshTokenResponse> getFilteredTokens(String role, Long userId, Boolean isRevoked, String ipAddress) {
        List<RefreshToken> tokens = refreshTokenRepository.findWithFilters(role, userId, isRevoked, ipAddress);
        return tokens.stream()
                .map(this::toRefreshTokenResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get refresh token by ID.
     */
    public Optional<RefreshTokenResponse> getTokenById(Long id) {
        return refreshTokenRepository.findById(id)
                .map(this::toRefreshTokenResponse);
    }

    /**
     * Get all active tokens for a specific user.
     */
    public List<RefreshTokenResponse> getActiveTokensForUser(Long userId, String role) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRoleAndIsRevokedFalse(userId, role);
        return tokens.stream()
                .filter(t -> !t.isExpired())
                .map(this::toRefreshTokenResponse)
                .collect(Collectors.toList());
    }

    /**
     * Revoke a specific token by ID (admin action).
     */
    @Transactional
    public boolean revokeTokenById(Long id) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(id);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
            return true;
        }
        return false;
    }

    /**
     * Delete a specific token by ID (admin action).
     */
    @Transactional
    public boolean deleteTokenById(Long id) {
        if (refreshTokenRepository.existsById(id)) {
            refreshTokenRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Get token statistics for admin dashboard.
     */
    public Map<String, Object> getTokenStatistics() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        stats.put("totalActiveTokens", refreshTokenRepository.countAllActiveTokens(now));
        stats.put("totalTokens", refreshTokenRepository.count());

        List<Object[]> byRole = refreshTokenRepository.countActiveTokensByRole(now);
        Map<String, Long> activeByRole = new HashMap<>();
        long userTokens = 0;
        long adminTokens = 0;
        
        for (Object[] row : byRole) {
            String role = (String) row[0];
            Long count = (Long) row[1];
            activeByRole.put(role, count);
            
            if ("user".equalsIgnoreCase(role)) {
                userTokens = count;
            } else if ("admin".equalsIgnoreCase(role)) {
                adminTokens = count;
            }
        }
        
        stats.put("activeTokensByRole", activeByRole);
        stats.put("userTokens", userTokens);
        stats.put("adminTokens", adminTokens);

        return stats;
    }

    // ==================== Helper Methods ====================

    private RefreshTokenResponse toRefreshTokenResponse(RefreshToken token) {
        String username = resolveUsername(token.getUserId(), token.getRole());
        String tokenPreview = token.getToken().length() > 8 
                ? token.getToken().substring(0, 8) + "..." 
                : token.getToken();
        
        return new RefreshTokenResponse(
                token.getId(),
                tokenPreview,
                token.getUserId(),
                token.getRole(),
                username,
                token.getExpiryDate(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getIsRevoked(),
                token.isExpired(),
                token.getDeviceInfo(),
                token.getIpAddress()
        );
    }

    private String resolveUsername(Long userId, String role) {
        try {
            switch (role.toLowerCase()) {
                case "user":
                    return userRepository.findById(userId)
                            .map(u -> u.getUsername())
                            .orElse("Unknown User #" + userId);
                case "admin":
                    return adminRepository.findById(userId)
                            .map(a -> a.getUsername())
                            .orElse("Unknown Admin #" + userId);
                default:
                    return "Unknown #" + userId;
            }
        } catch (Exception e) {
            return "Unknown #" + userId;
        }
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        // Truncate if too long
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        // Check common proxy headers
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For may contain multiple IPs; take the first
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
