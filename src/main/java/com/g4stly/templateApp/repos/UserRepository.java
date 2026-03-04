package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.models.enums.UserType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Filtering helpers used by admin management
    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Page<User> findByEmailVerified(Boolean emailVerified, Pageable pageable);

    Page<User> findByUserType(UserType userType, Pageable pageable);

    Page<User> findByIsActiveAndEmailVerified(Boolean isActive, Boolean emailVerified, Pageable pageable);

    /**
     * Used by AccountCleanupScheduledService: find self-deactivated accounts (NOT admin-deactivated)
     * whose 30-day grace period has expired so they can be permanently anonymised.
     * Admin-deactivated accounts are intentionally excluded from automated cleanup.
     */
    List<User> findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(LocalDateTime cutoff);

    /**
     * Full-text search across username and email with optional filters.
     * Used by admin user management list endpoint.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:isActive       IS NULL OR u.isActive       = :isActive)
              AND (:emailVerified  IS NULL OR u.emailVerified  = :emailVerified)
              AND (:userType       IS NULL OR u.userType       = :userType)
            """)
    Page<User> findWithFilters(
            @Param("search")        String search,
            @Param("isActive")      Boolean isActive,
            @Param("emailVerified") Boolean emailVerified,
            @Param("userType")      UserType userType,
            Pageable pageable);
}
