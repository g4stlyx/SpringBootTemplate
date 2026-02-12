package com.g4stly.templateApp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Client entity - represents a "danışan" (client) in the coaching platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_client_email", columnList = "email"),
    @Index(name = "idx_client_username", columnList = "username"),
    @Index(name = "idx_client_is_active", columnList = "is_active"),
    @Index(name = "idx_client_email_verified", columnList = "email_verified"),
    @Index(name = "idx_client_active_verified", columnList = "is_active, email_verified")
})
public class Client {
    
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
    
    @Column(name = "date_of_birth")
    private LocalDateTime dateOfBirth;
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;
    
    @Column(name = "occupation", length = 100)
    private String occupation;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;
    
    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
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
