package  com.g4stly.templateApp.services;

import  com.g4stly.templateApp.dto.admin.AdminActivityLogDTO;
import  com.g4stly.templateApp.dto.admin.AdminActivityLogListResponse;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.models.AdminActivityLog;
import  com.g4stly.templateApp.repos.AdminActivityLogRepository;
import  com.g4stly.templateApp.repos.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogService {
    
    private final AdminActivityLogRepository activityLogRepository;
    private final AdminRepository adminRepository;
    private final AdminActivityLogger adminActivityLogger;
    
    /**
     * Get all activity logs with pagination and optional filtering
     */
    @Transactional(readOnly = true)
    public AdminActivityLogListResponse getAllActivityLogs(
            Long adminId,
            String action,
            String resourceType,
            LocalDateTime startDate,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long currentAdminId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<AdminActivityLog> logPage;
        
        // Apply filters
        if (adminId != null) {
            logPage = activityLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
        } else {
            logPage = activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        
        List<AdminActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        // Log activity (Note: This creates self-referential logs - reading activity logs generates new activity logs)
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (adminId != null) details.put("filterAdminId", adminId);
        if (action != null) details.put("filterAction", action);
        if (resourceType != null) details.put("filterResourceType", resourceType);
        if (startDate != null) details.put("filterStartDate", startDate.toString());
        details.put("resultCount", logDTOs.size());
        details.put("totalElements", logPage.getTotalElements());
        
        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "AdminActivityLog",
                "list",
                details,
                httpRequest
        );
        
        return AdminActivityLogListResponse.builder()
                .logs(logDTOs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .pageSize(logPage.getSize())
                .hasNext(logPage.hasNext())
                .hasPrevious(logPage.hasPrevious())
                .build();
    }
    
    /**
     * Get activity log by ID
     */
    @Transactional(readOnly = true)
    public AdminActivityLogDTO getActivityLogById(Long logId, Long currentAdminId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        AdminActivityLog log = activityLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Activity log not found with ID: " + logId));
        
        // Log activity (Note: This creates self-referential logs - reading activity logs generates new activity logs)
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("logId", logId);
        details.put("targetAdminId", log.getAdminId());
        details.put("action", log.getAction());
        details.put("resourceType", log.getResourceType());
        
        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "AdminActivityLog",
                logId.toString(),
                details,
                httpRequest
        );
        
        return convertToDTO(log);
    }
    
    /**
     * Delete activity log (for cleanup purposes)
     */
    @Transactional
    public void deleteActivityLog(Long logId) {
        if (!activityLogRepository.existsById(logId)) {
            throw new RuntimeException("Activity log not found with ID: " + logId);
        }
        
        activityLogRepository.deleteById(logId);
        log.info("Deleted activity log with ID: {}", logId);
    }
    
    /**
     * Delete old activity logs (cleanup utility)
     */
    @Transactional
    public int deleteOldActivityLogs(LocalDateTime beforeDate) {
        List<AdminActivityLog> oldLogs = activityLogRepository
                .findByCreatedAtAfterOrderByCreatedAtDesc(beforeDate);
        
        int count = oldLogs.size();
        activityLogRepository.deleteAll(oldLogs);
        
        log.info("Deleted {} old activity logs before date: {}", count, beforeDate);
        return count;
    }
    
    /**
     * Get activity statistics for an admin
     */
    @Transactional(readOnly = true)
    public long getActivityCountForAdmin(Long adminId, LocalDateTime since) {
        return activityLogRepository.countByAdminIdAndCreatedAtAfter(adminId, since);
    }
    
    /**
     * Convert entity to DTO
     */
    private AdminActivityLogDTO convertToDTO(AdminActivityLog log) {
        Admin admin = adminRepository.findById(log.getAdminId()).orElse(null);
        
        return AdminActivityLogDTO.builder()
                .id(log.getId())
                .adminId(log.getAdminId())
                .adminUsername(admin != null ? admin.getUsername() : "Unknown")
                .adminEmail(admin != null ? admin.getEmail() : "Unknown")
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
