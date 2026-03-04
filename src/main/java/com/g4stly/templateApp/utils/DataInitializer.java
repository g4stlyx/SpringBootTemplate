package com.g4stly.templateApp.utils;

import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.models.enums.UserType;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.services.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Utility class to initialise default users on application startup.
 * Only runs when app.data.init-users is set to true (default: false).
 *
 * Creates the following users with password "12345678":
 * - Admin (Level 0 Super Admin): username "g4stly", email "g4stly@g4stly.tr"
 * - User (Waiter):               username "waiter",  email "waiter@g4stly.tr"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
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

        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║                  Starting Initial User Setup                   ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");

        createAdminUser();
        createUser();

        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║                  Initial User Setup Complete                   ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }

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
        admin.setLevel(0);
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        admin.setTwoFactorEnabled(false);
        admin.setPermissions(List.of("ALL"));

        adminRepository.save(admin);

        log.info("✓ Created Admin (Level 0 Super Admin):");
        log.info("  → Username: {}", username);
        log.info("  → Email: {}", email);
        log.info("  → Password: {}", DEFAULT_PASSWORD);
    }

    private void createUser() {
        String username = "user";
        String email = "user@g4stly.tr";

        if (userRepository.existsByUsername(username)) {
            log.info("✓ User '{}' already exists, skipping creation.", username);
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.info("✓ User with email '{}' already exists, skipping creation.", email);
            return;
        }

        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(DEFAULT_PASSWORD, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setSalt(salt);
        user.setPasswordHash(passwordHash);
        user.setFirstName("Demo");
        user.setLastName("User");
        user.setUserType(UserType.WAITER);
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setLoginAttempts(0);
        user.setBio("Demo waiter account for testing purposes.");

        userRepository.save(user);

        log.info("✓ Created User (Waiter):");
        log.info("  → Username: {}", username);
        log.info("  → Email: {}", email);
        log.info("  → Password: {}", DEFAULT_PASSWORD);
    }
}
