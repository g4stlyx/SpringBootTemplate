package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.admin.*;
import com.g4stly.templateApp.exception.BadRequestException;
import com.g4stly.templateApp.exception.ResourceNotFoundException;
import com.g4stly.templateApp.exception.UnauthorizedException;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.ClientRepository;
import com.g4stly.templateApp.repos.CoachRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminManagementService {
    
    // Whitelist of allowed sort fields to prevent sort field injection
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "username", "email", "firstName", "lastName", 
        "level", "isActive", "createdAt", "updatedAt", "lastLoginAt"
    );
    
    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;
    
    @Autowired
    private AdminActivityLogger activityLogger;
    
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CoachRepository coachRepository;

    /**
     * Check if requesting admin has permission to manage target admin
     * Staircase Authorization:
     * - Level 0 (Super Admin) can manage Level 1 and Level 2
     * - Level 1 (Admin) can manage Level 2 only
     * - Level 2 (Moderator) cannot manage other admins
     */
    private void validateAdminPermission(Admin requestingAdmin, Integer targetLevel, String action) {
        int requestingLevel = requestingAdmin.getLevel();
        
        log.debug("Permission check - Requesting admin level: {}, Target level: {}, Action: {}", 
            requestingLevel, targetLevel, action);
        
        // Super admin (level 0) can manage anyone
        if (requestingLevel == 0) {
            return;
        }
        
        // Level 1 can only manage level 2
        if (requestingLevel == 1 && targetLevel == 2) {
            return;
        }
        
        // Otherwise, permission denied
        throw new UnauthorizedException(
            String.format("Admin level %d cannot %s admin level %d. Insufficient permissions.", 
                requestingLevel, action, targetLevel));
    }
    
    /**
     * Validate that requesting admin can manage target admin
     */
    private void validateAdminPermission(Admin requestingAdmin, Admin targetAdmin, String action) {
        int requestingLevel = requestingAdmin.getLevel();
        int targetLevel = targetAdmin.getLevel();
        
        log.debug("Permission check - Requesting admin level: {}, Target level: {}, Action: {}", 
            requestingLevel, targetLevel, action);
        
        // Cannot manage yourself for certain actions
        if (requestingAdmin.getId().equals(targetAdmin.getId()) && 
            (action.equals("delete") || action.equals("deactivate"))) {
            throw new BadRequestException("Cannot " + action + " your own account");
        }
        
        // Super admin (level 0) can manage anyone except themselves for delete/deactivate
        if (requestingLevel == 0) {
            if (targetLevel == 0 && !requestingAdmin.getId().equals(targetAdmin.getId())) {
                // Super admins can manage other super admins (but not delete/deactivate)
                if (action.equals("delete") || action.equals("deactivate")) {
                    throw new BadRequestException("Cannot " + action + " another super admin account");
                }
            }
            return;
        }
        
        // Level 1 can only manage level 2
        if (requestingLevel == 1 && targetLevel == 2) {
            return;
        }
        
        // Otherwise, permission denied
        throw new UnauthorizedException(
            String.format("Admin level %d cannot %s admin level %d. Insufficient permissions.", 
                requestingLevel, action, targetLevel));
    }
    
    /**
     * Get all admins with pagination (only returns admins that requesting admin can see)
     */
    public AdminListResponse getAllAdmins(Long requestingAdminId, int page, int size, 
                                          String sortBy, String sortDirection, HttpServletRequest httpRequest) {
        log.info("Admin {} fetching admins - page: {}, size: {}, sortBy: {}, direction: {}", 
            requestingAdminId, page, size, sortBy, sortDirection);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + requestingAdminId));
        
        // Validate and sanitize sort field to prevent injection
        String validatedSortBy = validateSortField(sortBy, "createdAt");
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));
        Page<Admin> adminPage;
        
        // Super admin sees all admins
        if (requestingAdmin.getLevel() == 0) {
            adminPage = adminRepository.findAll(pageable);
        } 
        // Level 1 sees level 1 and 2
        else if (requestingAdmin.getLevel() == 1) {
            adminPage = adminRepository.findByLevelGreaterThanEqual(1, pageable);
        }
        // Level 2 sees only themselves
        else {
            adminPage = adminRepository.findByLevel(2, pageable);
        }
        
        List<AdminManagementDTO> admins = adminPage.getContent().stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
        
        // Log the READ activity
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        details.put("resultCount", admins.size());
        details.put("totalItems", adminPage.getTotalElements());
        activityLogger.logActivity(requestingAdminId, "READ", "Admin", "list", details, httpRequest);
        
        return AdminListResponse.builder()
            .admins(admins)
            .currentPage(adminPage.getNumber())
            .totalPages(adminPage.getTotalPages())
            .totalItems(adminPage.getTotalElements())
            .pageSize(adminPage.getSize())
            .build();
    }
    
    /**
     * Get admin by ID
     */
    public AdminManagementDTO getAdminById(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} fetching admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Check permission to view
        if (requestingAdmin.getLevel() > targetAdmin.getLevel() && 
            !requestingAdmin.getId().equals(targetAdmin.getId())) {
            throw new UnauthorizedException("You don't have permission to view this admin");
        }
        
        // Log the READ activity
        Map<String, Object> details = new HashMap<>();
        details.put("targetAdminLevel", targetAdmin.getLevel());
        details.put("targetAdminUsername", targetAdmin.getUsername());
        activityLogger.logActivity(requestingAdminId, "READ", "Admin", targetAdminId.toString(), details, httpRequest);
        
        return mapToDTO(targetAdmin);
    }
    
    /**
     * Create a new admin (with level validation)
     */
    @Transactional
    public AdminManagementDTO createAdmin(Long requestingAdminId, CreateAdminRequest request, HttpServletRequest httpRequest) {
        log.info("Admin {} creating new admin with level {}", requestingAdminId, request.getLevel());
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        // Validate permission to create admin at target level
        validateAdminPermission(requestingAdmin, request.getLevel(), "create");
        
        // Cannot create super admin (level 0) via API
        if (request.getLevel() == 0) {
            throw new BadRequestException("Cannot create super admin via API. Super admins must be created manually.");
        }
        
        // Check if username already exists (across clients, coaches, and admins)
        //TODO: Re-add cross-entity username check when needed based on the user types
        if (clientRepository.existsByUsername(request.getUsername()) || 
            coachRepository.existsByUsername(request.getUsername()) ||
            adminRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        
        // Check if email already exists (across clients, coaches, and admins)
        //TODO: Re-add cross-entity email check when needed based on the user types
        if (clientRepository.existsByEmail(request.getEmail()) || 
            coachRepository.existsByEmail(request.getEmail()) ||
            adminRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }
        
        // Create new admin
        String salt = passwordService.generateSalt();
        String hashedPassword = passwordService.hashPassword(request.getPassword(), salt);
        
        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setEmail(request.getEmail());
        admin.setPasswordHash(hashedPassword);
        admin.setSalt(salt);
        admin.setFirstName(request.getFirstName());
        admin.setLastName(request.getLastName());
        admin.setProfilePicture(request.getProfilePicture());
        admin.setLevel(request.getLevel());
        admin.setPermissions(request.getPermissions());
        admin.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        admin.setCreatedBy(requestingAdminId);
        
        admin = adminRepository.save(admin);
        log.info("Admin created successfully with ID: {}", admin.getId());
        
        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("username", admin.getUsername());
        details.put("email", admin.getEmail());
        details.put("level", admin.getLevel());
        activityLogger.logCreate(requestingAdminId, "admin", admin.getId().toString(), details, httpRequest);
        
        return mapToDTO(admin);
    }
    
    /**
     * Update admin by ID
     */
    @Transactional
    public AdminManagementDTO updateAdmin(Long requestingAdminId, Long targetAdminId, 
                                          UpdateAdminRequest request, HttpServletRequest httpRequest) {
        log.info("Admin {} updating admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission to update
        validateAdminPermission(requestingAdmin, targetAdmin, "update");
        
        // Track changes for logging
        Map<String, Object> changes = new HashMap<>();
        
        // Check if email is being changed and if it's already taken
        //TODO: Re-add cross-entity email check when needed based on the user types
        if (request.getEmail() != null && !request.getEmail().equals(targetAdmin.getEmail())) {
            if (clientRepository.existsByEmail(request.getEmail()) || 
                coachRepository.existsByEmail(request.getEmail()) ||
                adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            changes.put("email", Map.of("old", targetAdmin.getEmail(), "new", request.getEmail()));
            targetAdmin.setEmail(request.getEmail());
        }
        
        // Update fields if provided
        if (request.getFirstName() != null) {
            changes.put("firstName", Map.of("old", targetAdmin.getFirstName(), "new", request.getFirstName()));
            targetAdmin.setFirstName(request.getFirstName());
        }
        
        if (request.getLastName() != null) {
            changes.put("lastName", Map.of("old", targetAdmin.getLastName(), "new", request.getLastName()));
            targetAdmin.setLastName(request.getLastName());
        }
        
        if (request.getProfilePicture() != null) {
            targetAdmin.setProfilePicture(request.getProfilePicture());
        }
        
        // Only update level if requesting admin has permission
        if (request.getLevel() != null && !request.getLevel().equals(targetAdmin.getLevel())) {
            // Cannot change to/from level 0
            if (request.getLevel() == 0 || targetAdmin.getLevel() == 0) {
                throw new BadRequestException("Cannot change super admin level via API");
            }
            
            // Validate permission for new level
            validateAdminPermission(requestingAdmin, request.getLevel(), "assign level to");
            changes.put("level", Map.of("old", targetAdmin.getLevel(), "new", request.getLevel()));
            targetAdmin.setLevel(request.getLevel());
        }
        
        if (request.getPermissions() != null) {
            targetAdmin.setPermissions(request.getPermissions());
        }
        
        if (request.getIsActive() != null) {
            changes.put("isActive", Map.of("old", targetAdmin.getIsActive(), "new", request.getIsActive()));
            targetAdmin.setIsActive(request.getIsActive());
        }
        
        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin updated successfully: {}", targetAdminId);
        
        // Log activity
        activityLogger.logUpdate(requestingAdminId, "admin", targetAdminId.toString(), changes, httpRequest);
        
        return mapToDTO(targetAdmin);
    }
    
    /**
     * Delete admin by ID (soft delete - deactivate)
     */
    @Transactional
    public void deleteAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} deleting (deactivating) admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission to delete
        validateAdminPermission(requestingAdmin, targetAdmin, "delete");
        
        // Cannot delete super admin
        if (targetAdmin.getLevel() == 0) {
            throw new BadRequestException("Cannot delete super admin account");
        }
        
        targetAdmin.setIsActive(false);
        adminRepository.save(targetAdmin);
        
        log.info("Admin deactivated successfully: {}", targetAdminId);
        
        // Log activity
        activityLogger.logDelete(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);
    }
    
    /**
     * Activate admin
     */
    @Transactional
    public AdminManagementDTO activateAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} activating admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission
        validateAdminPermission(requestingAdmin, targetAdmin, "activate");
        
        targetAdmin.setIsActive(true);
        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);
        
        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin activated successfully: {}", targetAdminId);
        
        // Log activity
        activityLogger.logActivate(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);
        
        return mapToDTO(targetAdmin);
    }
    
    /**
     * Deactivate admin
     */
    @Transactional
    public AdminManagementDTO deactivateAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} deactivating admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission
        validateAdminPermission(requestingAdmin, targetAdmin, "deactivate");
        
        // Cannot deactivate super admin
        if (targetAdmin.getLevel() == 0) {
            throw new BadRequestException("Cannot deactivate super admin account");
        }
        
        targetAdmin.setIsActive(false);
        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin deactivated successfully: {}", targetAdminId);
        
        // Log activity
        activityLogger.logDeactivate(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);
        
        return mapToDTO(targetAdmin);
    }
    
    /**
     * Reset admin password
     */
    @Transactional
    public void resetAdminPassword(Long requestingAdminId, Long targetAdminId, 
                                   ResetUserPasswordRequest request) {
        log.info("Admin {} resetting password for admin {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission
        validateAdminPermission(requestingAdmin, targetAdmin, "reset password for");
        
        // Generate new salt and hash new password
        String newSalt = passwordService.generateSalt();
        String newHashedPassword = passwordService.hashPassword(request.getNewPassword(), newSalt);
        
        targetAdmin.setSalt(newSalt);
        targetAdmin.setPasswordHash(newHashedPassword);
        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);
        
        adminRepository.save(targetAdmin);
        log.info("Password reset successfully for admin: {}", targetAdminId);
    }
    
    /**
     * Unlock admin account
     */
    @Transactional
    public AdminManagementDTO unlockAdmin(Long requestingAdminId, Long targetAdminId) {
        log.info("Admin {} unlocking admin account {}", requestingAdminId, targetAdminId);
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        
        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        
        // Validate permission
        validateAdminPermission(requestingAdmin, targetAdmin, "unlock");
        
        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);
        
        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin account unlocked successfully: {}", targetAdminId);
        
        return mapToDTO(targetAdmin);
    }
    
    /**
     * Map Admin entity to AdminManagementDTO
     */
    private AdminManagementDTO mapToDTO(Admin admin) {
        return AdminManagementDTO.builder()
            .id(admin.getId())
            .username(admin.getUsername())
            .email(admin.getEmail())
            .firstName(admin.getFirstName())
            .lastName(admin.getLastName())
            .profilePicture(admin.getProfilePicture())
            .level(admin.getLevel())
            .permissions(admin.getPermissions())
            .isActive(admin.getIsActive())
            .loginAttempts(admin.getLoginAttempts())
            .lockedUntil(admin.getLockedUntil())
            .createdBy(admin.getCreatedBy())
            .createdAt(admin.getCreatedAt())
            .updatedAt(admin.getUpdatedAt())
            .lastLoginAt(admin.getLastLoginAt())
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
