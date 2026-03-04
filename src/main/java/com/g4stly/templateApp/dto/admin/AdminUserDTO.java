package com.g4stly.templateApp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only DTO returned by the admin user-management API.
 * Sensitive fields (passwordHash, salt) are never exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String phone;
    private String bio;
    private String userType;
    private Boolean isActive;
    private Boolean emailVerified;
    private Boolean adminDeactivated;
    private Integer loginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime deactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
