package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.profile.ChangePasswordRequest;
import com.g4stly.templateApp.dto.user.DeactivateAccountRequest;
import com.g4stly.templateApp.dto.user.UpdateUserProfileRequest;
import com.g4stly.templateApp.dto.user.UserProfileDTO;
import com.g4stly.templateApp.exception.BadRequestException;
import com.g4stly.templateApp.exception.ResourceNotFoundException;
import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;

    // ==================== GET ====================

    /**
     * Returns the profile of the currently authenticated user.
     * Ownership is enforced in the controller — userId always comes from the JWT details.
     */
    public UserProfileDTO getProfile(Long userId) {
        User user = findActiveUserById(userId);
        return mapToDTO(user);
    }

    // ==================== UPDATE ====================

    /**
     * Partial update: only non-null fields in the request are applied.
     * Email uniqueness is checked across both the users and admins tables
     * to prevent collisions at the application level.
     */
    @Transactional
    public UserProfileDTO updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = findActiveUserById(userId);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail()) ||
                adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)  user.setLastName(request.getLastName());
        if (request.getPhone() != null)     user.setPhone(request.getPhone());
        if (request.getBio() != null)       user.setBio(request.getBio());

        user = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return mapToDTO(user);
    }

    // ==================== CHANGE PASSWORD ====================

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        User user = findActiveUserById(userId);

        if (!passwordService.verifyPassword(request.getCurrentPassword(), user.getSalt(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        String newSalt = passwordService.generateSalt();
        String newHash = passwordService.hashPassword(request.getNewPassword(), newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHash);

        userRepository.save(user);
        log.info("Password changed for userId={}", userId);
    }

    // ==================== DEACTIVATE ====================

    /**
     * Soft-deletes the user's own account.
     *
     * Security checks:
     * 1. Password confirmation to prevent CSRF/accidental closure.
     * 2. All active refresh tokens are immediately revoked so existing
     *    sessions cannot continue after deactivation.
     *
     * The account enters a 30-day grace period (reactivatable on login).
     * After 30 days AccountCleanupScheduledService will anonymise PII permanently.
     */
    @Transactional
    public void deactivateAccount(Long userId, DeactivateAccountRequest request) {
        User user = findActiveUserById(userId);

        if (!passwordService.verifyPassword(request.getPassword(), user.getSalt(), user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }

        user.setIsActive(false);
        user.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all sessions immediately
        int revoked = refreshTokenService.revokeAllUserTokens(userId, "user");
        log.info("Account deactivated for userId={}; {} refresh tokens revoked", userId, revoked);
    }

    // ==================== PROFILE PICTURE ====================

    /**
     * Called by UserImageController after a successful R2 upload.
     */
    @Transactional
    public UserProfileDTO updateProfilePicture(Long userId, String imageUrl) {
        User user = findActiveUserById(userId);
        user.setProfilePicture(imageUrl);
        user = userRepository.save(user);
        log.info("Profile picture updated for userId={}", userId);
        return mapToDTO(user);
    }

    // ==================== INTERNAL ====================

    private User findActiveUserById(Long userId) {
        return userRepository.findById(userId)
                .filter(User::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    public UserProfileDTO mapToDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePicture(user.getProfilePicture())
                .phone(user.getPhone())
                .bio(user.getBio())
                .userType(user.getUserType() != null
                        ? user.getUserType().name().toLowerCase(Locale.ROOT) : null)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
