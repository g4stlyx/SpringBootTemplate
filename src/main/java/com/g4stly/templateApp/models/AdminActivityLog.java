package  com.g4stly.templateApp.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_activity_log")
public class AdminActivityLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "admin_id", nullable = false)
    private Long adminId;
    
    @Column(name = "action", nullable = false, length = 100)
    private String action;
    
    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;
    
    @Column(name = "resource_id", length = 100)
    private String resourceId;
    
    @Column(name = "details", columnDefinition = "JSON")
    private String details;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Lob
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Reference to Admin entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", insertable = false, updatable = false)
    private Admin admin;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}