package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.Coach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CoachRepository extends JpaRepository<Coach, Long> {
    
    Optional<Coach> findByUsername(String username);
    
    Optional<Coach> findByEmail(String email);
    
    Optional<Coach> findByUsernameOrEmail(String username, String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    List<Coach> findByIsVerifiedTrue();
    
    List<Coach> findByIsActiveTrueAndIsVerifiedTrue();
    
    // Admin management queries
    Page<Coach> findByIsVerified(Boolean isVerified, Pageable pageable);
    
    Page<Coach> findByIsActive(Boolean isActive, Pageable pageable);
    
    Page<Coach> findByIsVerifiedAndIsActive(Boolean isVerified, Boolean isActive, Pageable pageable);
}
