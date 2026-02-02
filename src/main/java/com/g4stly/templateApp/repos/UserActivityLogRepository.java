package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long>, JpaSpecificationExecutor<UserActivityLog> {
    
    // Find by user
    Page<UserActivityLog> findByUserIdAndUserTypeOrderByCreatedAtDesc(Long userId, String userType, Pageable pageable);
    
    List<UserActivityLog> findByUserIdAndUserTypeOrderByCreatedAtDesc(Long userId, String userType);
    
    // Find by user type only
    Page<UserActivityLog> findByUserTypeOrderByCreatedAtDesc(String userType, Pageable pageable);
    
    // Find by action
    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    
    // Find by action and user type
    Page<UserActivityLog> findByActionAndUserTypeOrderByCreatedAtDesc(String action, String userType, Pageable pageable);
    
    // Find all ordered by created date
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    // Find by date range
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    // Find by date range and user type
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.userType = :userType AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByUserTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("userType") String userType,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    
    // Find by user and date range
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.userType = :userType AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByUserIdAndUserTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("userType") String userType,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    
    // Count by user and date
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.userType = :userType AND ual.createdAt >= :date")
    long countByUserIdAndUserTypeAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("userType") String userType,
            @Param("date") LocalDateTime date
    );
    
    // Find failed actions for a user
    Page<UserActivityLog> findByUserIdAndUserTypeAndSuccessFalseOrderByCreatedAtDesc(Long userId, String userType, Pageable pageable);
    
    // Find by IP address
    Page<UserActivityLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    
    // Delete old logs (for cleanup)
    @Query("DELETE FROM UserActivityLog ual WHERE ual.createdAt < :beforeDate")
    void deleteByCreatedAtBefore(@Param("beforeDate") LocalDateTime beforeDate);
    
    // Count by action
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.action = :action AND ual.createdAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);
    
    // Count by success status
    long countBySuccessAndCreatedAtAfter(boolean success, LocalDateTime date);
}
