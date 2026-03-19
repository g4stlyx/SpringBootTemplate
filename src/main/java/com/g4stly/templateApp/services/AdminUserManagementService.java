package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.admin.*;
import com.g4stly.templateApp.exception.BadRequestException;
import com.g4stly.templateApp.exception.ResourceNotFoundException;
import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.models.VerificationToken;
import com.g4stly.templateApp.models.enums.UserType;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.repos.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the admin user-management API.
 *
 * Key design decisions:
 * - Admin-deactivated accounts are flagged with adminDeactivated=true so they
 * cannot be reactivated by the user's own login flow and are excluded from
 * the nightly PII-anonymisation cleanup job.
 * - Hard-delete permanently removes the user row and all associated tokens.
 * - Soft-deactivate (admin) sets isActive=false + adminDeactivated=true.
 * - Reactivation is an admin-only explicit action that clears both flags.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "username", "email", "firstName", "lastName",
            "isActive", "emailVerified", "userType", "createdAt", "updatedAt", "lastLoginAt");

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final AdminActivityLogger activityLogger;

    // ==================== LIST ====================

    /**
     * Returns a paginated, filterable list of all users.
     *
     * @param search        Optional free-text search against username and email.
     * @param isActive      Optional filter on the account active flag.
     * @param emailVerified Optional filter on the email-verified flag.
     * @param userType      Optional filter on application-level type.
     */
    public AdminUserListResponse getUsers(
            Long adminId,
            int page, int size,
            String sortBy, String sortDirection,
            String search,
            Boolean isActive, Boolean emailVerified, String userType,
            HttpServletRequest httpRequest) {

        String validatedSortBy = validateSortField(sortBy, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));

        UserType userTypeEnum = null;
        if (userType != null && !userType.isBlank()) {
            try {
                userTypeEnum = UserType.valueOf(userType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid userType value: " + userType);
            }
        }

        String searchTrimmed = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<User> userPage = userRepository.findWithFilters(
                searchTrimmed, isActive, emailVerified, userTypeEnum, pageable);

        List<AdminUserDTO> dtos = userPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("search", search);
        details.put("isActive", isActive);
        details.put("emailVerified", emailVerified);
        details.put("userType", userType);
        details.put("resultCount", dtos.size());
        details.put("totalItems", userPage.getTotalElements());
        activityLogger.logActivity(adminId, "READ", "User", "list", details, httpRequest);

        return AdminUserListResponse.builder()
                .users(dtos)
                .currentPage(userPage.getNumber())
                .totalPages(userPage.getTotalPages())
                .totalItems(userPage.getTotalElements())
                .pageSize(userPage.getSize())
                .build();
    }

    // ==================== GET BY ID ====================

    public AdminUserDTO getUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        Map<String, Object> details = new HashMap<>();
        details.put("targetUsername", user.getUsername());
        activityLogger.logActivity(adminId, "READ", "User", targetUserId.toString(), details, httpRequest);

        return mapToDTO(user);
    }

    // ==================== CREATE ====================

    @Transactional
    public AdminUserDTO createUser(Long adminId, AdminCreateUserRequest request, HttpServletRequest httpRequest) {
        // Uniqueness check across both tables
        if (userRepository.existsByUsername(request.getUsername())
                || adminRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())
                || adminRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(request.getPassword(), salt);

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setBio(request.getBio());
        user.setUserType(request.getUserType() != null ? request.getUserType() : UserType.APP_USER);
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setAdminDeactivated(false);

        boolean skipVerification = Boolean.TRUE.equals(request.getSkipEmailVerification());
        user.setEmailVerified(skipVerification);

        user = userRepository.save(user);

        // Send verification email unless the admin explicitly skips it
        if (!skipVerification) {
            VerificationToken token = new VerificationToken(user.getId(), "user");
            verificationTokenRepository.save(token);
            String displayName = user.getFirstName() != null ? user.getFirstName() : user.getUsername();
            emailService.sendVerificationEmail(user.getEmail(), token.getToken(), displayName);
        }

        log.info("User created by admin {} with userId={}", adminId, user.getId());

        Map<String, Object> details = new HashMap<>();
        details.put("username", user.getUsername());
        details.put("email", user.getEmail());
        details.put("userType", user.getUserType().name());
        activityLogger.logCreate(adminId, "user", user.getId().toString(), details, httpRequest);

        return mapToDTO(user);
    }

    // ==================== UPDATE ====================

    @Transactional
    public AdminUserDTO updateUser(Long adminId, Long targetUserId,
            AdminUpdateUserRequest request,
            HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        Map<String, Object> changes = new HashMap<>();

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())
                    || adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            changes.put("email", Map.of("old", user.getEmail(), "new", request.getEmail()));
            user.setEmail(request.getEmail());
            // Force re-verification when an admin directly changes the email
            user.setEmailVerified(false);
            user.setPendingEmail(null);
        }

        if (request.getFirstName() != null) {
            changes.put("firstName", Map.of("old", nullSafe(user.getFirstName()), "new", request.getFirstName()));
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            changes.put("lastName", Map.of("old", nullSafe(user.getLastName()), "new", request.getLastName()));
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getUserType() != null) {
            changes.put("userType", Map.of("old", user.getUserType().name(), "new", request.getUserType().name()));
            user.setUserType(request.getUserType());
        }
        if (request.getEmailVerified() != null) {
            changes.put("emailVerified", Map.of("old", user.getEmailVerified(), "new", request.getEmailVerified()));
            user.setEmailVerified(request.getEmailVerified());
        }

        user = userRepository.save(user);
        log.info("User {} updated by admin {}", targetUserId, adminId);

        activityLogger.logUpdate(adminId, "user", targetUserId.toString(), changes, httpRequest);
        return mapToDTO(user);
    }

    // ==================== DEACTIVATE (soft, admin-initiated) ====================

    /**
     * Admin soft-deactivates a user.
     *
     * Sets {@code adminDeactivated=true} in addition to {@code isActive=false}.
     * This prevents:
     * 1. The user from reactivating themselves via the grace-period login flow.
     * 2. The nightly cleanup job from anonymising this record (the admin may want
     * to reactivate or review the account later).
     *
     * All existing refresh tokens are revoked immediately.
     */
    @Transactional
    public AdminUserDTO deactivateUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        if (!user.getIsActive()) {
            throw new BadRequestException("User account is already inactive");
        }

        user.setIsActive(false);
        user.setAdminDeactivated(true);
        user.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllUserTokens(targetUserId, "user");
        log.info("User {} deactivated by admin {}; {} refresh tokens revoked", targetUserId, adminId, revoked);

        activityLogger.logDeactivate(adminId, "user", targetUserId.toString(), httpRequest);
        return mapToDTO(user);
    }

    // ==================== REACTIVATE ====================

    /**
     * Admin reactivates a previously deactivated user.
     * Works for both self-deactivated and admin-deactivated accounts.
     * Clears the lock and resets login attempt counter as well.
     */
    @Transactional
    public AdminUserDTO reactivateUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        if (user.getIsActive()) {
            throw new BadRequestException("User account is already active");
        }

        user.setIsActive(true);
        user.setAdminDeactivated(false);
        user.setDeactivatedAt(null);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);
        log.info("User {} reactivated by admin {}", targetUserId, adminId);

        activityLogger.logActivate(adminId, "user", targetUserId.toString(), httpRequest);
        return mapToDTO(user);
    }

    // ==================== HARD-DELETE ====================

    /**
     * Hard-deletes a user record.
     *
     * This is destructive and irreversible. All associated refresh tokens and
     * verification tokens are removed before the user row is deleted.
     */
    @Transactional
    public void hardDeleteUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        // Revoke sessions
        refreshTokenService.revokeAllUserTokens(targetUserId, "user");

        // Remove any pending verification / email-change tokens
        verificationTokenRepository.deleteByUserIdAndRole(targetUserId, "user");
        verificationTokenRepository.deleteByUserIdAndRole(targetUserId, "email_change");

        userRepository.delete(user);
        log.warn("User {} HARD-DELETED by admin {}", targetUserId, adminId);

        activityLogger.logDelete(adminId, "user", targetUserId.toString(), httpRequest);
    }

    // ==================== RESET PASSWORD ====================

    @Transactional
    public void resetUserPassword(Long adminId, Long targetUserId,
            ResetUserPasswordRequest request) {
        User user = findUserById(targetUserId);

        String newSalt = passwordService.generateSalt();
        String newHash = passwordService.hashPassword(request.getNewPassword(), newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHash);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);

        userRepository.save(user);
        log.info("Password reset for userId={} by admin {}", targetUserId, adminId);
    }

    // ==================== UNLOCK ====================

    @Transactional
    public AdminUserDTO unlockUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);
        log.info("User {} unlocked by admin {}", targetUserId, adminId);

        Map<String, Object> details = new HashMap<>();
        details.put("action", "unlocked");
        activityLogger.logActivity(adminId, "UNLOCK", "User", targetUserId.toString(), details, httpRequest);
        return mapToDTO(user);
    }

    // ==================== TOGGLE EMAIL VERIFIED ====================

    @Transactional
    public AdminUserDTO toggleEmailVerified(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        boolean newValue = !Boolean.TRUE.equals(user.getEmailVerified());
        user.setEmailVerified(newValue);
        user = userRepository.save(user);
        log.info("emailVerified toggled to {} for userId={} by admin {}", newValue, targetUserId, adminId);

        Map<String, Object> details = new HashMap<>();
        details.put("emailVerified", newValue);
        activityLogger.logActivity(adminId, "UPDATE", "User", targetUserId.toString(), details, httpRequest);
        return mapToDTO(user);
    }

    // ==================== INTERNALS ====================

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    public AdminUserDTO mapToDTO(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePicture(user.getProfilePicture())
                .phone(user.getPhone())
                .bio(user.getBio())
                .userType(user.getUserType() != null
                        ? user.getUserType().name().toLowerCase(Locale.ROOT)
                        : null)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .adminDeactivated(user.getAdminDeactivated())
                .loginAttempts(user.getLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .deactivatedAt(user.getDeactivatedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String validateSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isBlank())
            return defaultField;
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }
        return sortBy;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
