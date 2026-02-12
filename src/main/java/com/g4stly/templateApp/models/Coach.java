package com.g4stly.templateApp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Coach entity - represents a "koç" (coach) in the coaching platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "coaches", indexes = {
    @Index(name = "idx_coach_email", columnList = "email"),
    @Index(name = "idx_coach_username", columnList = "username"),
    @Index(name = "idx_coach_is_active", columnList = "is_active"),
    @Index(name = "idx_coach_is_verified", columnList = "is_verified"),
    @Index(name = "idx_coach_active_verified", columnList = "is_active, is_verified")
})
public class Coach {
    
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
    
    @Column(name = "specializations", columnDefinition = "TEXT")
    private String specializations; // JSON array of specialization areas
    
    @Column(name = "certifications", columnDefinition = "TEXT")
    private String certifications; // JSON array of certifications
    
    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;
    
    @Column(name = "hourly_rate")
    private Double hourlyRate;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false; // Admin verification for coaches
    
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
