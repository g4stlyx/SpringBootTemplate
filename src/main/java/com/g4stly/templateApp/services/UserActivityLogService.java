package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.admin.UserActivityLogDTO;
import com.g4stly.templateApp.dto.admin.UserActivityLogListResponse;
import com.g4stly.templateApp.models.Client;
import com.g4stly.templateApp.models.Coach;
import com.g4stly.templateApp.models.UserActivityLog;
import com.g4stly.templateApp.repos.ClientRepository;
import com.g4stly.templateApp.repos.CoachRepository;
import com.g4stly.templateApp.repos.UserActivityLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for reading and managing user activity logs.
 * Used by admins to view user activities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityLogService {
    
    // Whitelist of allowed sort fields to prevent sort field injection
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "userId", "userType", "action", "resourceType", 
        "resourceId", "ipAddress", "success", "createdAt"
    );
    
    private final UserActivityLogRepository userActivityLogRepository;
    private final ClientRepository clientRepository;
    private final CoachRepository coachRepository;
    private final AdminActivityLogger adminActivityLogger;
    
    /**
     * Get all user activity logs with pagination and filtering
     */
    @Transactional(readOnly = true)
    public UserActivityLogListResponse getAllUserActivityLogs(
            Long userId,
            String userType,
            String action,
            String resourceType,
            Boolean success,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String ipAddress,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long currentAdminId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        // Validate and sanitize sort field to prevent injection
        String validatedSortBy = validateSortField(sortBy, "createdAt");
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));
        
        // Build specification for dynamic filtering
        Specification<UserActivityLog> spec = buildSpecification(userId, userType, action, resourceType, success, startDate, endDate, ipAddress);
        
        Page<UserActivityLog> logPage = userActivityLogRepository.findAll(spec, pageable);
        
        List<UserActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        // Log admin activity for viewing user logs
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (userId != null) details.put("filterUserId", userId);
        if (userType != null) details.put("filterUserType", userType);
        if (action != null) details.put("filterAction", action);
        if (resourceType != null) details.put("filterResourceType", resourceType);
        if (success != null) details.put("filterSuccess", success);
        if (startDate != null) details.put("filterStartDate", startDate.toString());
        if (endDate != null) details.put("filterEndDate", endDate.toString());
        if (ipAddress != null) details.put("filterIpAddress", ipAddress);
        details.put("resultCount", logDTOs.size());
        details.put("totalElements", logPage.getTotalElements());
        
        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                "list",
                details,
                httpRequest
        );
        
        return UserActivityLogListResponse.builder()
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
     * Get user activity log by ID
     */
    @Transactional(readOnly = true)
    public UserActivityLogDTO getUserActivityLogById(Long logId, Long currentAdminId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserActivityLog activityLog = userActivityLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("User activity log not found with ID: " + logId));
        
        // Log admin activity
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("logId", logId);
        details.put("targetUserId", activityLog.getUserId());
        details.put("targetUserType", activityLog.getUserType());
        details.put("action", activityLog.getAction());
        
        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                logId.toString(),
                details,
                httpRequest
        );
        
        return convertToDTO(activityLog);
    }
    
    /**
     * Get activity logs for a specific user
     */
    @Transactional(readOnly = true)
    public UserActivityLogListResponse getUserActivityLogsByUser(
            Long userId,
            String userType,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long currentAdminId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        // Validate and sanitize sort field to prevent injection
        String validatedSortBy = validateSortField(sortBy, "createdAt");
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));
        
        Page<UserActivityLog> logPage = userActivityLogRepository.findByUserIdAndUserTypeOrderByCreatedAtDesc(userId, userType, pageable);
        
        List<UserActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        // Log admin activity
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("targetUserId", userId);
        details.put("targetUserType", userType);
        details.put("page", page);
        details.put("size", size);
        details.put("resultCount", logDTOs.size());
        
        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                "user:" + userId,
                details,
                httpRequest
        );
        
        return UserActivityLogListResponse.builder()
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
     * Delete old activity logs (cleanup utility)
     */
    @Transactional
    public int deleteOldActivityLogs(LocalDateTime beforeDate) {
        List<UserActivityLog> oldLogs = userActivityLogRepository.findAll(
                (root, query, cb) -> cb.lessThan(root.get("createdAt"), beforeDate)
        );
        
        int count = oldLogs.size();
        userActivityLogRepository.deleteAll(oldLogs);
        
        log.info("Deleted {} old user activity logs before date: {}", count, beforeDate);
        return count;
    }
    
    /**
     * Get activity statistics
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getActivityStatistics(LocalDateTime since) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("totalLogins", userActivityLogRepository.countByActionSince("LOGIN", since));
        stats.put("totalRegistrations", userActivityLogRepository.countByActionSince("REGISTER", since));
        stats.put("totalPasswordResets", userActivityLogRepository.countByActionSince("PASSWORD_RESET_COMPLETE", since));
        stats.put("totalProfileUpdates", userActivityLogRepository.countByActionSince("PROFILE_UPDATE", since));
        stats.put("successfulActions", userActivityLogRepository.countBySuccessAndCreatedAtAfter(true, since));
        stats.put("failedActions", userActivityLogRepository.countBySuccessAndCreatedAtAfter(false, since));
        
        return stats;
    }
    
    /**
     * Build JPA Specification for dynamic filtering
     */
    private Specification<UserActivityLog> buildSpecification(
            Long userId, String userType, String action, String resourceType,
            Boolean success, LocalDateTime startDate, LocalDateTime endDate, String ipAddress
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            
            if (userType != null && !userType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("userType")), userType.toLowerCase()));
            }
            
            if (action != null && !action.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            
            if (resourceType != null && !resourceType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("resourceType"), resourceType));
            }
            
            if (success != null) {
                predicates.add(criteriaBuilder.equal(root.get("success"), success));
            }
            
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            if (ipAddress != null && !ipAddress.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("ipAddress"), ipAddress));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Convert entity to DTO
     */
    private UserActivityLogDTO convertToDTO(UserActivityLog log) {
        String username = "Unknown";
        String email = "Unknown";
        
        // Fetch user info based on userType
        if ("client".equalsIgnoreCase(log.getUserType())) {
            Optional<Client> clientOpt = clientRepository.findById(log.getUserId());
            if (clientOpt.isPresent()) {
                Client client = clientOpt.get();
                username = client.getUsername();
                email = client.getEmail();
            }
        } else if ("coach".equalsIgnoreCase(log.getUserType())) {
            Optional<Coach> coachOpt = coachRepository.findById(log.getUserId());
            if (coachOpt.isPresent()) {
                Coach coach = coachOpt.get();
                username = coach.getUsername();
                email = coach.getEmail();
            }
        }
        
        return UserActivityLogDTO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userType(log.getUserType())
                .username(username)
                .email(email)
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .success(log.getSuccess())
                .failureReason(log.getFailureReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
    
    /**
     * Validate sort field against whitelist to prevent sort field injection attacks.
     * Returns the validated field if allowed, otherwise returns the default.
     */
    private String validateSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }
        
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }
        
        return sortBy;
    }
}
