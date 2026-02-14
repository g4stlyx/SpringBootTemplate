package com.g4stly.templateApp.services.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.g4stly.templateApp.services.DatabaseBackupService;

/**
 * Scheduled service for creating daily database backups.
 * Runs every day at 3:00 AM to create a backup and email it to the configured recipient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupScheduledService {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    /**
     * Create a database backup and email it.
     * Runs every day at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void createDatabaseBackup() {
        log.info("Starting scheduled database backup");
        databaseBackupService.createAndEmailBackup();
    }
}
