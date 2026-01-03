package  com.g4stly.templateApp.controllers;

import  com.g4stly.templateApp.services.DatabaseBackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/database-backup")
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class DatabaseBackupController {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    /**
     * Manually trigger database backup
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Run backup in a separate thread to avoid request timeout
            new Thread(() -> {
                databaseBackupService.createAndEmailBackup();
            }).start();
            
            response.put("success", true);
            response.put("message", "Database backup process started successfully");
            response.put("note", "The backup will be created and emailed in the background");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to start database backup process");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get backup status/configuration
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBackupStatus() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("success", true);
        response.put("status", databaseBackupService.getBackupStatus());
        response.put("scheduledTime", "Daily at 3:00 AM");
        response.put("manualTrigger", "Available via /create endpoint");
        
        return ResponseEntity.ok(response);
    }
}
