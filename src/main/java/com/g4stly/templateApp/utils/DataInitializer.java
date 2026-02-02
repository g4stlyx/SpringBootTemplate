package com.g4stly.templateApp.utils;

import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.Client;
import com.g4stly.templateApp.models.Coach;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.ClientRepository;
import com.g4stly.templateApp.repos.CoachRepository;
import com.g4stly.templateApp.services.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Utility class to initialize default users on application startup.
 * Only runs when app.data.init-users is set to true (default: false).
 * 
 * Creates the following users with password "12345678":
 * - Admin (Level 0 Super Admin): username "g4stly", email "g4stly@g4stly.tr"
 * - Coach: username "coach", email "coach@g4stly.tr"
 * - Client: username "client", email "client@g4stly.tr"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final AdminRepository adminRepository;
    private final CoachRepository coachRepository;
    private final ClientRepository clientRepository;
    private final PasswordService passwordService;
    
    @Value("${app.data.init-users:false}")
    private boolean initUsers;
    
    private static final String DEFAULT_PASSWORD = "12345678";
    
    @Override
    @Transactional
    public void run(String... args) {
        if (!initUsers) {
            log.debug("User initialization is disabled. Set app.data.init-users=true to enable.");
            return;
        }
        
        log.info("╔════════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                     Starting Initial User Setup                                ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════════╝");
        
        createAdminUser();
        createCoachUser();
        createClientUser();
        
        log.info("╔════════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                     Initial User Setup Complete                                ║");
        log.info("╚════════════════════════════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Creates the default super admin user (g4stly)
     */
    private void createAdminUser() {
        String username = "g4stly";
        String email = "g4stly@g4stly.tr";
        
        if (adminRepository.existsByUsername(username)) {
            log.info("✓ Admin '{}' already exists, skipping creation.", username);
            return;
        }
        
        if (adminRepository.existsByEmail(email)) {
            log.info("✓ Admin with email '{}' already exists, skipping creation.", email);
            return;
        }
        
        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(DEFAULT_PASSWORD, salt);
        
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setSalt(salt);
        admin.setPasswordHash(passwordHash);
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setLevel(0); // Super Admin (Level 0)
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        admin.setTwoFactorEnabled(false);
        admin.setPermissions(List.of("ALL")); // Full permissions
        
        adminRepository.save(admin);
        
        log.info("✓ Created Admin (Level 0 Super Admin):");
        log.info("  → Username: {}", username);
        log.info("  → Email: {}", email);
        log.info("  → Password: {}", DEFAULT_PASSWORD);
    }
    
    /**
     * Creates the default coach user
     */
    private void createCoachUser() {
        String username = "coach";
        String email = "coach@g4stly.tr";
        
        if (coachRepository.existsByUsername(username)) {
            log.info("✓ Coach '{}' already exists, skipping creation.", username);
            return;
        }
        
        if (coachRepository.existsByEmail(email)) {
            log.info("✓ Coach with email '{}' already exists, skipping creation.", email);
            return;
        }
        
        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(DEFAULT_PASSWORD, salt);
        
        Coach coach = new Coach();
        coach.setUsername(username);
        coach.setEmail(email);
        coach.setSalt(salt);
        coach.setPasswordHash(passwordHash);
        coach.setFirstName("Demo");
        coach.setLastName("Coach");
        coach.setIsActive(true);
        coach.setIsVerified(true); // Pre-verified for testing
        coach.setEmailVerified(true); // Pre-verified for testing
        coach.setLoginAttempts(0);
        coach.setBio("Demo coach account for testing purposes.");
        coach.setYearsOfExperience(5);
        
        coachRepository.save(coach);
        
        log.info("✓ Created Coach:");
        log.info("  → Username: {}", username);
        log.info("  → Email: {}", email);
        log.info("  → Password: {}", DEFAULT_PASSWORD);
    }
    
    /**
     * Creates the default client user
     */
    private void createClientUser() {
        String username = "client";
        String email = "client@g4stly.tr";
        
        if (clientRepository.existsByUsername(username)) {
            log.info("✓ Client '{}' already exists, skipping creation.", username);
            return;
        }
        
        if (clientRepository.existsByEmail(email)) {
            log.info("✓ Client with email '{}' already exists, skipping creation.", email);
            return;
        }
        
        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(DEFAULT_PASSWORD, salt);
        
        Client client = new Client();
        client.setUsername(username);
        client.setEmail(email);
        client.setSalt(salt);
        client.setPasswordHash(passwordHash);
        client.setFirstName("Demo");
        client.setLastName("Client");
        client.setIsActive(true);
        client.setEmailVerified(true); // Pre-verified for testing
        client.setLoginAttempts(0);
        client.setOnboardingCompleted(true);
        client.setBio("Demo client account for testing purposes.");
        
        clientRepository.save(client);
        
        log.info("✓ Created Client:");
        log.info("  → Username: {}", username);
        log.info("  → Email: {}", email);
        log.info("  → Password: {}", DEFAULT_PASSWORD);
    }
}
