package com.g4stly.templateApp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.g4stly.templateApp.models.enums.UserType;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_username", columnList = "username"),
        @Index(name = "idx_user_is_active", columnList = "is_active"),
        @Index(name = "idx_user_email_verified", columnList = "email_verified"),
        @Index(name = "idx_user_user_type", columnList = "user_type"),
        @Index(name = "idx_user_active_verified", columnList = "is_active, email_verified")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JsonIgnore
    @Column(name = "salt", nullable = false, length = 64)
    private String salt;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "profile_picture", length = 500)
    private String profilePicture;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * The application-level type of this user (e.g. APP_USER).
     * All users in this table share the same Spring Security role (ROLE_USER).
     * This field differentiates their function within the application.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 50)
    private UserType userType = UserType.APP_USER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Set when a user self-deactivates their account.
     * Null means the account has never been self-deactivated.
     * Non-null means the account may be in the 30-day grace period (still
     * re-activatable
     * on login) or may have been anonymised by AccountCleanupScheduledService.
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * True when an admin has deactivated this account.
     * Admin-deactivated accounts:
     * - Cannot be reactivated via the grace-period login flow.
     * - Are excluded from the nightly PII-anonymisation cleanup job.
     * - Can only be reactivated by an admin via the admin management API.
     */
    @Column(name = "admin_deactivated", nullable = false)
    private Boolean adminDeactivated = false;

    /**
     * Holds the new, unverified email address while the user's email-change request
     * is pending verification. Null when no change is in progress.
     */
    @Column(name = "pending_email", length = 255)
    private String pendingEmail;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
