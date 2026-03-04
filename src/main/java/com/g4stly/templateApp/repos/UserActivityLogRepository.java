package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long>, JpaSpecificationExecutor<UserActivityLog> {
    
    // Find by user
    Page<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);
    
    List<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role);
    
    // Find by role only
    Page<UserActivityLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    
    // Find by action
    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    
    // Find by action and role
    Page<UserActivityLog> findByActionAndRoleOrderByCreatedAtDesc(String action, String role, Pageable pageable);
    
    // Find all ordered by created date
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    // Find by date range
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    // Find by date range and role
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.role = :role AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    
    // Find by user and date range
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.role = :role AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    
    // Count by user and date
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.role = :role AND ual.createdAt >= :date")
    long countByUserIdAndRoleAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("date") LocalDateTime date
    );
    
    // Find failed actions for a user
    Page<UserActivityLog> findByUserIdAndRoleAndSuccessFalseOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);
    
    // Find by IP address
    Page<UserActivityLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
      
    // Count by action
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.action = :action AND ual.createdAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);
    
    // Count by success status
    long countBySuccessAndCreatedAtAfter(boolean success, LocalDateTime date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserActivityLog u WHERE u.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
