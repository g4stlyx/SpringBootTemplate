package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.SensitiveEndpointAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensitiveEndpointAccessLogRepository extends JpaRepository<SensitiveEndpointAccessLog, Long> {
    
    /**
     * Find all logs with pagination ordered by createdAt desc
     */
    Page<SensitiveEndpointAccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Find logs by severity level
     */
    Page<SensitiveEndpointAccessLog> findBySeverityOrderByCreatedAtDesc(
            SensitiveEndpointAccessLog.SeverityLevel severity, Pageable pageable);
    
    /**
     * Find logs by user ID
     */
    Page<SensitiveEndpointAccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find logs by user type (admin, coach, client)
     */
    Page<SensitiveEndpointAccessLog> findByUserTypeOrderByCreatedAtDesc(String userType, Pageable pageable);
    
    /**
     * Find logs by IP address
     */
    Page<SensitiveEndpointAccessLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    
    /**
     * Find logs by endpoint category
     */
    Page<SensitiveEndpointAccessLog> findByEndpointCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    
    /**
     * Find logs by endpoint pattern (contains)
     */
    Page<SensitiveEndpointAccessLog> findByEndpointContainingOrderByCreatedAtDesc(String endpointPattern, Pageable pageable);
    
    /**
     * Find logs within date range
     */
    @Query("SELECT s FROM SensitiveEndpointAccessLog s WHERE s.createdAt BETWEEN :startDate AND :endDate ORDER BY s.createdAt DESC")
    Page<SensitiveEndpointAccessLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    /**
     * Find logs after a specific date
     */
    Page<SensitiveEndpointAccessLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    
    /**
     * Count access by IP address within time window
     */
    @Query("SELECT COUNT(s) FROM SensitiveEndpointAccessLog s WHERE s.ipAddress = :ipAddress AND s.createdAt >= :since")
    Long countByIpAddressSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    
    /**
     * Count access by user ID within time window
     */
    @Query("SELECT COUNT(s) FROM SensitiveEndpointAccessLog s WHERE s.userId = :userId AND s.createdAt >= :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    /**
     * Get statistics by severity level
     */
    @Query("SELECT s.severity, COUNT(s) FROM SensitiveEndpointAccessLog s GROUP BY s.severity")
    List<Object[]> getStatisticsBySeverity();
    
    /**
     * Get statistics by endpoint category
     */
    @Query("SELECT s.endpointCategory, COUNT(s) FROM SensitiveEndpointAccessLog s GROUP BY s.endpointCategory")
    List<Object[]> getStatisticsByCategory();
    
    /**
     * Get daily statistics for last N days
     */
    @Query("SELECT DATE(s.createdAt) as date, COUNT(s) as count FROM SensitiveEndpointAccessLog s " +
           "WHERE s.createdAt >= :since GROUP BY DATE(s.createdAt) ORDER BY date DESC")
    List<Object[]> getDailyStatistics(@Param("since") LocalDateTime since);
    
    /**
     * Count total logs
     */
    long count();
    
    /**
     * Count by severity level
     */
    long countBySeverity(SensitiveEndpointAccessLog.SeverityLevel severity);
    
    /**
     * Count by endpoint category
     */
    long countByEndpointCategory(String category);
    
    /**
     * Find critical severity logs in last N hours
     */
    @Query("SELECT s FROM SensitiveEndpointAccessLog s WHERE s.severity = 'CRITICAL' AND s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<SensitiveEndpointAccessLog> findRecentCriticalLogs(@Param("since") LocalDateTime since);
    
    /**
     * Find logs where email alert was not sent
     */
    List<SensitiveEndpointAccessLog> findByEmailAlertSentFalseOrderByCreatedAtDesc();
}
