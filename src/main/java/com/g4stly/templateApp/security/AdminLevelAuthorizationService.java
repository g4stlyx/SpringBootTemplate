package com.g4stly.templateApp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.repos.AdminRepository;

/**
 * Service for handling admin level-based authorization
 * This service provides reusable methods to check admin access levels
 * 
 * Admin Levels:
 * - Level 0: Super Admin (highest privileges)
 * - Level 1: Admin
 * - Level 2: Moderator (lowest admin privileges)
 */
@Service
@Slf4j
public class AdminLevelAuthorizationService {
    
    @Autowired
    private AdminRepository adminRepository;
    
    /**
     * Check if the current admin is level 0 (super admin)
     */
    public boolean isLevel0() {
        return hasLevel(0);
    }
    
    /**
     * Check if the current admin is level 0 or level 1
     */
    public boolean isLevel0Or1() {
        return hasMaxLevel(1);
    }
    
    /**
     * Check if the current admin is level 0, 1, or 2 (any admin)
     */
    public boolean isLevel0Or1Or2() {
        return hasMaxLevel(2);
    }
    
    /**
     * Check if the current admin has a specific level
     */
    public boolean hasLevel(int level) {
        Integer adminLevel = getCurrentAdminLevel();
        if (adminLevel == null) {
            log.warn("Could not determine admin level for authorization check");
            return false;
        }
        return adminLevel == level;
    }
    
    /**
     * Check if the current admin has a level less than or equal to the specified maximum
     * (Lower level number = higher privilege)
     */
    public boolean hasMaxLevel(int maxLevel) {
        Integer adminLevel = getCurrentAdminLevel();
        if (adminLevel == null) {
            log.warn("Could not determine admin level for authorization check");
            return false;
        }
        return adminLevel <= maxLevel;
    }
    
    /**
     * Get the current authenticated admin's level
     */
    private Integer getCurrentAdminLevel() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated user found");
                return null;
            }
            
            // Get admin ID from authentication details
            Long adminId = (Long) authentication.getDetails();
            if (adminId == null) {
                log.warn("No admin ID found in authentication details");
                return null;
            }
            
            // Fetch admin from database
            Admin admin = adminRepository.findById(adminId).orElse(null);
            if (admin == null) {
                log.warn("Admin not found with ID: {}", adminId);
                return null;
            }
            
            return admin.getLevel();
        } catch (Exception e) {
            log.error("Error getting admin level: {}", e.getMessage(), e);
            return null;
        }
    }
}
