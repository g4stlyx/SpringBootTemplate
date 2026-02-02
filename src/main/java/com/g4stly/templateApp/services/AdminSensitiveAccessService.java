package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.admin.SensitiveAccessLogListResponse;
import com.g4stly.templateApp.dto.admin.SensitiveAccessLogResponse;
import com.g4stly.templateApp.dto.admin.SensitiveAccessStatisticsResponse;
import com.g4stly.templateApp.exception.ResourceNotFoundException;
import com.g4stly.templateApp.models.SensitiveEndpointAccessLog;
import com.g4stly.templateApp.repos.SensitiveEndpointAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for admin management of sensitive endpoint access logs
 */
@Service
@Slf4j
public class AdminSensitiveAccessService {
    
    @Autowired
    private SensitiveEndpointAccessLogRepository accessLogRepository;
    
    @Autowired
    private AdminActivityLogger adminActivityLogger;
    
    /**
     * Get all sensitive access logs with pagination and filters
     */
    @Transactional(readOnly = true)
    public SensitiveAccessLogListResponse getAllLogs(
            Long adminId,
            int page, 
            int size, 
            String sortBy, 
            String sortDirection,
            Long userId,
            String userType,
            String severity,
            String category,
            String ipAddress,
            LocalDateTime startDate,
            HttpServletRequest request) {
        
        log.info("Admin {} retrieving sensitive access logs - page: {}, size: {}, sortBy: {}, filters: userId={}, userType={}, severity={}, category={}, ipAddress={}", 
                adminId, page, size, sortBy, userId, userType, severity, category, ipAddress);
        
        // Create sort
        Sort sort = sortDirection.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // Get logs with filters
        Page<SensitiveEndpointAccessLog> logsPage;
        
        if (userId != null) {
            logsPage = accessLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (userType != null && !userType.isEmpty()) {
            logsPage = accessLogRepository.findByUserTypeOrderByCreatedAtDesc(userType, pageable);
        } else if (severity != null && !severity.isEmpty()) {
            try {
                SensitiveEndpointAccessLog.SeverityLevel severityLevel = SensitiveEndpointAccessLog.SeverityLevel.valueOf(severity);
                logsPage = accessLogRepository.findBySeverityOrderByCreatedAtDesc(severityLevel, pageable);
            } catch (IllegalArgumentException e) {
                logsPage = accessLogRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else if (category != null && !category.isEmpty()) {
            logsPage = accessLogRepository.findByEndpointCategoryOrderByCreatedAtDesc(category, pageable);
        } else if (ipAddress != null && !ipAddress.isEmpty()) {
            logsPage = accessLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress, pageable);
        } else if (startDate != null) {
            logsPage = accessLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate, pageable);
        } else {
            logsPage = accessLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        
        List<SensitiveAccessLogResponse> logs = logsPage.getContent().stream()
                .map(SensitiveAccessLogResponse::from)
                .collect(Collectors.toList());
        
        // Log admin activity (READ operation)
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("resultCount", logs.size());
        details.put("totalElements", logsPage.getTotalElements());
        if (userId != null) details.put("filterUserId", userId);
        if (userType != null) details.put("filterUserType", userType);
        if (severity != null) details.put("filterSeverity", severity);
        if (category != null) details.put("filterCategory", category);
        if (ipAddress != null) details.put("filterIpAddress", ipAddress);
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "SensitiveAccessLog",
                "list",
                details,
                request
        );
        
        return SensitiveAccessLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    
    /**
     * Get a single sensitive access log by ID
     */
    @Transactional(readOnly = true)
    public SensitiveAccessLogResponse getLogById(Long adminId, Long logId, HttpServletRequest request) {
        log.info("Admin {} retrieving sensitive access log: {}", adminId, logId);
        
        SensitiveEndpointAccessLog accessLog = accessLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensitive access log not found with ID: " + logId));
        
        // Log admin activity (READ operation)
        Map<String, Object> details = new HashMap<>();
        details.put("logId", logId);
        details.put("severity", accessLog.getSeverity().name());
        details.put("category", accessLog.getEndpointCategory());
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "SensitiveAccessLog",
                logId.toString(),
                details,
                request
        );
        
        return SensitiveAccessLogResponse.from(accessLog);
    }
    
    /**
     * Get logs by user ID
     */
    @Transactional(readOnly = true)
    public SensitiveAccessLogListResponse getLogsByUserId(
            Long adminId,
            Long userId,
            int page,
            int size,
            HttpServletRequest request) {
        
        log.info("Admin {} retrieving sensitive access logs for user: {}", adminId, userId);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SensitiveEndpointAccessLog> logsPage = accessLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        List<SensitiveAccessLogResponse> logs = logsPage.getContent().stream()
                .map(SensitiveAccessLogResponse::from)
                .collect(Collectors.toList());
        
        // Log admin activity
        Map<String, Object> details = new HashMap<>();
        details.put("targetUserId", userId);
        details.put("resultCount", logs.size());
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "SensitiveAccessLog",
                "user:" + userId,
                details,
                request
        );
        
        return SensitiveAccessLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    
    /**
     * Get logs by IP address
     */
    @Transactional(readOnly = true)
    public SensitiveAccessLogListResponse getLogsByIpAddress(
            Long adminId,
            String ipAddress,
            int page,
            int size,
            HttpServletRequest request) {
        
        log.info("Admin {} retrieving sensitive access logs for IP: {}", adminId, ipAddress);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SensitiveEndpointAccessLog> logsPage = accessLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress, pageable);
        
        List<SensitiveAccessLogResponse> logs = logsPage.getContent().stream()
                .map(SensitiveAccessLogResponse::from)
                .collect(Collectors.toList());
        
        // Log admin activity
        Map<String, Object> details = new HashMap<>();
        details.put("targetIpAddress", ipAddress);
        details.put("resultCount", logs.size());
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "SensitiveAccessLog",
                "ip:" + ipAddress,
                details,
                request
        );
        
        return SensitiveAccessLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    
    /**
     * Get sensitive access statistics
     */
    @Transactional(readOnly = true)
    public SensitiveAccessStatisticsResponse getStatistics(Long adminId, HttpServletRequest request) {
        log.info("Admin {} retrieving sensitive access statistics", adminId);
        
        // Get counts by severity level
        long totalLogs = accessLogRepository.count();
        long criticalCount = accessLogRepository.countBySeverity(SensitiveEndpointAccessLog.SeverityLevel.CRITICAL);
        long highCount = accessLogRepository.countBySeverity(SensitiveEndpointAccessLog.SeverityLevel.HIGH);
        long mediumCount = accessLogRepository.countBySeverity(SensitiveEndpointAccessLog.SeverityLevel.MEDIUM);
        long lowCount = accessLogRepository.countBySeverity(SensitiveEndpointAccessLog.SeverityLevel.LOW);
        
        // Get statistics by severity
        Map<String, Long> accessBySeverity = new LinkedHashMap<>();
        List<Object[]> severityStats = accessLogRepository.getStatisticsBySeverity();
        for (Object[] stat : severityStats) {
            SensitiveEndpointAccessLog.SeverityLevel level = (SensitiveEndpointAccessLog.SeverityLevel) stat[0];
            Long count = (Long) stat[1];
            accessBySeverity.put(level.name(), count);
        }
        
        // Get statistics by category
        Map<String, Long> accessByCategory = new LinkedHashMap<>();
        List<Object[]> categoryStats = accessLogRepository.getStatisticsByCategory();
        for (Object[] stat : categoryStats) {
            String category = (String) stat[0];
            Long count = (Long) stat[1];
            if (category != null) {
                accessByCategory.put(category, count);
            }
        }
        
        // Get daily statistics for last 30 days
        Map<String, Long> dailyStatistics = new LinkedHashMap<>();
        List<Object[]> dailyStats = accessLogRepository.getDailyStatistics(LocalDateTime.now().minusDays(30));
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Object[] stat : dailyStats) {
            String date = stat[0].toString();
            Long count = (Long) stat[1];
            dailyStatistics.put(date, count);
        }
        
        // Count email alerts sent (logs where emailAlertSent = true)
        // We'll calculate this from the total counts
        long emailAlertsSent = criticalCount + highCount; // HIGH and CRITICAL trigger emails
        
        // Log admin activity
        Map<String, Object> details = new HashMap<>();
        details.put("totalLogs", totalLogs);
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "SensitiveAccessLog",
                "statistics",
                details,
                request
        );
        
        return SensitiveAccessStatisticsResponse.builder()
                .totalAccessLogs(totalLogs)
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .lowCount(lowCount)
                .emailAlertsSent(emailAlertsSent)
                .accessBySeverity(accessBySeverity)
                .accessByCategory(accessByCategory)
                .dailyStatistics(dailyStatistics)
                .build();
    }
    
    /**
     * Delete a sensitive access log (Level 0 only)
     */
    @Transactional
    public void deleteLog(Long adminId, Long logId, HttpServletRequest request) {
        log.info("Admin {} deleting sensitive access log: {}", adminId, logId);
        
        SensitiveEndpointAccessLog accessLog = accessLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Sensitive access log not found with ID: " + logId));
        
        // Collect details before deletion
        Map<String, Object> details = new HashMap<>();
        details.put("logId", logId);
        details.put("severity", accessLog.getSeverity().name());
        details.put("endpoint", accessLog.getEndpoint());
        details.put("category", accessLog.getEndpointCategory());
        
        accessLogRepository.deleteById(logId);
        
        // Log admin activity
        adminActivityLogger.logActivity(
                adminId,
                "DELETE",
                "SensitiveAccessLog",
                logId.toString(),
                details,
                request
        );
        
        log.info("Deleted sensitive access log with ID: {} by admin {}", logId, adminId);
    }
}
