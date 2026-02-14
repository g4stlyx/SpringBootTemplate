package com.g4stly.templateApp.services.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.g4stly.templateApp.services.RefreshTokenService;

/**
 * Scheduled service for cleaning up expired and revoked refresh tokens.
 * Runs daily to keep the refresh_tokens table clean and performant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduledService {

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Cleanup expired and revoked refresh tokens every day at 2:00 AM.
     * 
     * Removes:
     * - Tokens that are both revoked and expired
     * - Tokens that expired more than 7 days ago (safety margin)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredRefreshTokens() {
        log.info("Starting scheduled refresh token cleanup task...");

        try {
            int deleted = refreshTokenService.cleanupExpiredTokens();
            log.info("Scheduled refresh token cleanup completed. Cleaned up {} tokens.", deleted);
        } catch (Exception e) {
            log.error("Error during scheduled refresh token cleanup", e);
        }
    }
}