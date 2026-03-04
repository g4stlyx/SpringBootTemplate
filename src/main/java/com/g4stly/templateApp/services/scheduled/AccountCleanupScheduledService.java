package com.g4stly.templateApp.services.scheduled;

import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.repos.RefreshTokenRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nightly job that permanently anonymises user accounts whose 30-day
 * self-deactivation grace period has expired.
 *
 * Anonymisation strategy (GDPR-friendly):
 * - username  → "deleted_{id}"
 * - email     → "deleted_{id}@deleted.invalid"
 * - firstName, lastName, phone, bio → null
 * - profilePicture → null (the R2 object is also deleted)
 * - All active refresh tokens revoked (belt-and-suspenders; they should
 *   have been revoked at deactivation time, but re-revoke to be safe).
 * - deactivatedAt is kept as an audit marker so the record is not re-processed.
 *
 * Runs every night at 02:30 local time (between the DB backup at 03:00 and
 * the activity-log cleanup at 04:00 to avoid I/O contention).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountCleanupScheduledService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ImageUploadService imageUploadService;

    @Value("${app.account.deactivation.grace-period-days:30}")
    private int gracePeriodDays;

    @Scheduled(cron = "0 30 2 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void anonymiseExpiredAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(gracePeriodDays);
        log.info("Starting account cleanup. Anonymising accounts deactivated before {}", cutoff);

        List<User> expired = userRepository.findByIsActiveFalseAndDeactivatedAtBefore(cutoff);

        if (expired.isEmpty()) {
            log.info("Account cleanup: no expired accounts found.");
            return;
        }

        int count = 0;
        for (User user : expired) {
            try {
                anonymise(user);
                count++;
            } catch (Exception e) {
                // Log and continue — don't let one bad record abort the whole batch
                log.error("Failed to anonymise userId={}: {}", user.getId(), e.getMessage(), e);
            }
        }

        log.info("Account cleanup completed. Anonymised {} account(s).", count);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void anonymise(User user) {
        log.info("Anonymising expired account userId={}", user.getId());

        // Delete profile picture from R2 storage before nulling the reference
        if (user.getProfilePicture() != null && !user.getProfilePicture().isBlank()) {
            try {
                imageUploadService.deleteImage(user.getProfilePicture());
            } catch (Exception e) {
                log.warn("Could not delete profile picture for userId={}: {}", user.getId(), e.getMessage());
                // Non-fatal — continue with DB anonymisation
            }
        }

        // Revoke all remaining sessions (safety net)
        refreshTokenRepository.revokeAllUserTokens(user.getId(), "user");

        // Overwrite PII
        user.setUsername("deleted_" + user.getId());
        user.setEmail("deleted_" + user.getId() + "@deleted.invalid");
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setBio(null);
        user.setProfilePicture(null);
        // isActive stays false; deactivatedAt is kept as an audit trail marker
        // so this record is not re-processed on the next run
        user.setPasswordHash("ANONYMISED");
        user.setSalt("ANONYMISED");

        userRepository.save(user);
        log.info("Anonymisation complete for userId={}", user.getId());
    }
}
