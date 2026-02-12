package  com.g4stly.templateApp.config;

import  com.g4stly.templateApp.services.DatabaseBackupService;
import  com.g4stly.templateApp.services.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ScheduledTasks {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @Autowired
    private RefreshTokenService refreshTokenService;
    
    // Create database backup every day at 3:00 AM and email it
    @Scheduled(cron = "0 0 3 * * *")
    public void createDatabaseBackup() {
        databaseBackupService.createAndEmailBackup();
    }

    // Cleanup expired and revoked refresh tokens every day at 2:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredRefreshTokens() {
        int cleaned = refreshTokenService.cleanupExpiredTokens();
        System.out.println("Scheduled cleanup: removed " + cleaned + " expired/revoked refresh tokens");
    }
}