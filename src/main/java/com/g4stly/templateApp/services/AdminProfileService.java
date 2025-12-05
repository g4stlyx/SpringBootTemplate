package  com.g4stly.templateApp.services;

import  com.g4stly.templateApp.dto.profile.AdminProfileDTO;
import  com.g4stly.templateApp.dto.profile.ChangePasswordRequest;
import  com.g4stly.templateApp.dto.profile.UpdateAdminProfileRequest;
import  com.g4stly.templateApp.exception.BadRequestException;
import  com.g4stly.templateApp.exception.ResourceNotFoundException;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.repos.AdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AdminProfileService {
    
    @Autowired
    private AdminRepository adminRepository;
    
    @Autowired
    private PasswordService passwordService;
    
    /**
     * Get admin profile by admin ID
     */
    public AdminProfileDTO getAdminProfile(Long adminId) {
        log.info("Fetching profile for admin ID: {}", adminId);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        return mapToDTO(admin);
    }
    
    /**
     * Update admin profile
     */
    @Transactional
    public AdminProfileDTO updateAdminProfile(Long adminId, UpdateAdminProfileRequest request) {
        log.info("Updating profile for admin ID: {}", adminId);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        // Check if email is being changed and if it's already taken
        if (request.getEmail() != null && !request.getEmail().equals(admin.getEmail())) {
            if (adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            admin.setEmail(request.getEmail());
            log.info("Email changed for admin ID: {}", adminId);
        }
        
        // Update other fields if provided
        if (request.getFirstName() != null) {
            admin.setFirstName(request.getFirstName());
        }
        
        if (request.getLastName() != null) {
            admin.setLastName(request.getLastName());
        }
        
        if (request.getProfilePicture() != null) {
            admin.setProfilePicture(request.getProfilePicture());
        }
        
        admin = adminRepository.save(admin);
        log.info("Profile updated successfully for admin ID: {}", adminId);
        
        return mapToDTO(admin);
    }
    
    /**
     * Update admin profile picture
     */
    @Transactional
    public AdminProfileDTO updateProfilePicture(Long adminId, String imageUrl) {
        log.info("Updating profile picture for admin ID: {}", adminId);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        admin.setProfilePicture(imageUrl);
        admin = adminRepository.save(admin);
        
        log.info("Profile picture updated successfully for admin ID: {}", adminId);
        return mapToDTO(admin);
    }
    
    /**
     * Change admin password
     */
    @Transactional
    public void changePassword(Long adminId, ChangePasswordRequest request) {
        log.info("Changing password for admin ID: {}", adminId);
        
        // Validate new password and confirm password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        // Verify current password
        boolean isValidPassword = passwordService.verifyPassword(
            request.getCurrentPassword(), admin.getSalt(), admin.getPasswordHash());
        
        if (!isValidPassword) {
            throw new BadRequestException("Current password is incorrect");
        }
        
        // Check if new password is same as current password
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }
        
        // Generate new salt and hash new password
        String newSalt = passwordService.generateSalt();
        String newHashedPassword = passwordService.hashPassword(request.getNewPassword(), newSalt);
        
        admin.setSalt(newSalt);
        admin.setPasswordHash(newHashedPassword);
        
        adminRepository.save(admin);
        log.info("Password changed successfully for admin ID: {}", adminId);
    }
    
    /**
     * Deactivate admin account (only for super admins or higher level admins)
     */
    @Transactional
    public void deactivateAccount(Long adminId, Long requestingAdminId) {
        log.info("Deactivating account for admin ID: {} by admin ID: {}", adminId, requestingAdminId);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found with ID: " + requestingAdminId));
        
        // Check if requesting admin has permission (must be super admin or higher level)
        if (requestingAdmin.getLevel() > admin.getLevel() && requestingAdmin.getLevel() != 0) {
            throw new BadRequestException("You don't have permission to deactivate this admin account");
        }
        
        // Cannot deactivate super admin
        if (admin.getLevel() == 0) {
            throw new BadRequestException("Cannot deactivate super admin account");
        }
        
        admin.setIsActive(false);
        adminRepository.save(admin);
        
        log.info("Account deactivated successfully for admin ID: {}", adminId);
    }
    
    /**
     * Reactivate admin account (only for super admins or higher level admins)
     */
    @Transactional
    public void reactivateAccount(Long adminId, Long requestingAdminId) {
        log.info("Reactivating account for admin ID: {} by admin ID: {}", adminId, requestingAdminId);
        
        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        
        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found with ID: " + requestingAdminId));
        
        // Check if requesting admin has permission (must be super admin or higher level)
        if (requestingAdmin.getLevel() > admin.getLevel() && requestingAdmin.getLevel() != 0) {
            throw new BadRequestException("You don't have permission to reactivate this admin account");
        }
        
        admin.setIsActive(true);
        adminRepository.save(admin);
        
        log.info("Account reactivated successfully for admin ID: {}", adminId);
    }
    
    /**
     * Map Admin entity to AdminProfileDTO
     */
    private AdminProfileDTO mapToDTO(Admin admin) {
        return AdminProfileDTO.builder()
            .id(admin.getId())
            .username(admin.getUsername())
            .email(admin.getEmail())
            .firstName(admin.getFirstName())
            .lastName(admin.getLastName())
            .profilePicture(admin.getProfilePicture())
            .level(admin.getLevel())
            .permissions(admin.getPermissions())
            .isActive(admin.getIsActive())
            .createdBy(admin.getCreatedBy())
            .createdAt(admin.getCreatedAt())
            .updatedAt(admin.getUpdatedAt())
            .lastLoginAt(admin.getLastLoginAt())
            .build();
    }
}
