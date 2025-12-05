package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    
    Optional<Client> findByUsername(String username);
    
    Optional<Client> findByEmail(String email);
    
    Optional<Client> findByUsernameOrEmail(String username, String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    // Admin filtering methods
    Page<Client> findByIsActive(Boolean isActive, Pageable pageable);
    
    Page<Client> findByEmailVerified(Boolean emailVerified, Pageable pageable);
    
    Page<Client> findByIsActiveAndEmailVerified(Boolean isActive, Boolean emailVerified, Pageable pageable);
}

