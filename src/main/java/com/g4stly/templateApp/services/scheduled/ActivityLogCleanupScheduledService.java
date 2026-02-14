package com.g4stly.templateApp.services.scheduled;

import com.g4stly.templateApp.repos.UserActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled service for cleaning up old user activity logs.
 * Runs daily at 4:00 AM to delete logs older than the configured retention period.
 *
 * Note: Admin activity logs and authentication error logs are intentionally
 * retained indefinitely for audit/compliance purposes.
 *
 * Tables cleaned:
 * - user_activity_logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogCleanupScheduledService {

    private final UserActivityLogRepository userActivityLogRepository;

    @Value("${app.activity-log.retention-days:30}")
    private int retentionDays;

    /**
     * Clean up user activity logs older than the configured retention period.
     * Runs every day at 4:00 AM Turkey time (offset from 3:00 AM database backup to avoid contention).
     * Uses a bulk DELETE query for performance — does not load entities into memory.
     * 
     * Transaction will roll back automatically if any exception occurs.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void cleanupOldActivityLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Starting scheduled activity log cleanup. Deleting logs older than {} days (before {})",
                retentionDays, cutoff);

        int deleted = userActivityLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Activity log cleanup completed successfully. Deleted {} user activity logs", deleted);
    }
}