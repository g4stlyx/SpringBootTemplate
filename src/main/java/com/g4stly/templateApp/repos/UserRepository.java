package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.models.enums.UserType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
