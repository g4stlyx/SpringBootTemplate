package com.g4stly.templateApp.dto.admin;

import com.g4stly.templateApp.models.SensitiveEndpointAccessLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveAccessLogResponse {
    private Long id;
    private String severity;
    private String severityDescription;
    private Long userId;
    private String userType;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String endpoint;
    private String httpMethod;
    private String endpointCategory;
    private String description;
    private Integer responseStatus;
    private Boolean emailAlertSent;
    private LocalDateTime createdAt;
    
    public static SensitiveAccessLogResponse from(SensitiveEndpointAccessLog log) {
        return SensitiveAccessLogResponse.builder()
                .id(log.getId())
                .severity(log.getSeverity().name())
                .severityDescription(log.getSeverity().getDescription())
                .userId(log.getUserId())
                .userType(log.getUserType())
                .username(log.getUsername())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .endpoint(log.getEndpoint())
                .httpMethod(log.getHttpMethod())
                .endpointCategory(log.getEndpointCategory())
                .description(log.getDescription())
                .responseStatus(log.getResponseStatus())
                .emailAlertSent(log.getEmailAlertSent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
