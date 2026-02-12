package com.g4stly.templateApp.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token", columnList = "token"),
    @Index(name = "idx_refresh_user_composite", columnList = "user_id, user_type"),
    @Index(name = "idx_refresh_expiry_date", columnList = "expiry_date"),
    @Index(name = "idx_refresh_is_revoked", columnList = "is_revoked")
})
@Data
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public RefreshToken() {
        this.token = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public RefreshToken(Long userId, String userType, long expirationDays) {
        this.token = UUID.randomUUID().toString();
        this.userId = userId;
        this.userType = userType;
        this.createdAt = LocalDateTime.now();
        this.expiryDate = this.createdAt.plusDays(expirationDays);
        this.isRevoked = false;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    public boolean isValid() {
        return !isExpired() && !isRevoked;
    }
}
