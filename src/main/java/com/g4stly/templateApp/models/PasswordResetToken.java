package  com.g4stly.templateApp.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
public class PasswordResetToken {
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
    
    // For password reset tokens, we might want to track attempts
    @Column(name = "attempt_count")
    private Integer attemptCount = 0;
    
    // We might want to add IP address for security
    @Column(name = "requesting_ip")
    private String requestingIp;
    
    public PasswordResetToken() {
        // Default constructor for JPA
    }
    
    public PasswordResetToken(Long userId, String userType, String requestingIp) {
        this.token = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.userType = userType;
        this.requestingIp = requestingIp;
        this.createdDate = LocalDateTime.now();
        // Password reset tokens should expire quickly - 15 minutes
        this.expiryDate = this.createdDate.plusMinutes(15);
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
    
    public void incrementAttemptCount() {
        this.attemptCount++;
    }
    
    public boolean hasTooManyAttempts() {
        return this.attemptCount >= 3; // Block after 3 attempts
    }
}