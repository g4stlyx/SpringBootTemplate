package  com.g4stly.templateApp.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens", indexes = {
    @Index(name = "idx_verification_token", columnList = "token"),
    @Index(name = "idx_verification_user", columnList = "user_id, user_type"),
    @Index(name = "idx_verification_user_type", columnList = "user_type"),
    @Index(name = "idx_verification_expiry_date", columnList = "expiry_date"),
    @Index(name = "idx_verification_created_date", columnList = "created_date")
})
@Data
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "user_type", nullable = false)
    private String userType; // "user" or "admin"
    
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;
    
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
    public VerificationToken() {
        // Default constructor for JPA
    }
    
    public VerificationToken(Long userId, String userType) {
        this.token = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.userType = userType;
        this.createdDate = LocalDateTime.now();
        // Set expiry to 24 hours from now
        this.expiryDate = this.createdDate.plusHours(24);
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}