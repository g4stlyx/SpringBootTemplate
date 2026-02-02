package com.g4stly.templateApp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for User Activity Log - used when viewing user activity logs as an admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogDTO {
    private Long id;
    private Long userId;
    private String userType;
    private String username;
    private String email;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String failureReason;
    private LocalDateTime createdAt;
}
