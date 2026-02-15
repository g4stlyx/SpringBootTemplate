package  com.g4stly.templateApp.services;

import  com.g4stly.templateApp.dto.admin.PasswordResetTokenDTO;
import  com.g4stly.templateApp.dto.admin.TokenListResponse;
import  com.g4stly.templateApp.dto.admin.VerificationTokenDTO;
import  com.g4stly.templateApp.models.Admin;
import  com.g4stly.templateApp.models.Client;
import  com.g4stly.templateApp.models.Coach;
import  com.g4stly.templateApp.models.PasswordResetToken;
import  com.g4stly.templateApp.models.VerificationToken;
import  com.g4stly.templateApp.repos.AdminRepository;
import  com.g4stly.templateApp.repos.ClientRepository;
import  com.g4stly.templateApp.repos.CoachRepository;
import  com.g4stly.templateApp.repos.PasswordResetTokenRepository;
import  com.g4stly.templateApp.repos.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTokenManagementService {
    
    // Whitelist of allowed sort fields to prevent sort field injection
    private static final Set<String> ALLOWED_PASSWORD_RESET_SORT_FIELDS = Set.of(
        "id", "token", "userId", "userType", "expiryDate", 
        "createdDate", "attemptCount", "requestingIp"
    );
    
    private static final Set<String> ALLOWED_VERIFICATION_SORT_FIELDS = Set.of(
        "id", "token", "userId", "userType", "expiryDate", 
        "createdDate", "usedDate", "used"
    );
    
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final ClientRepository clientRepository;
    private final CoachRepository coachRepository;
    private final AdminRepository adminRepository;
    
    @Autowired
    private AdminActivityLogger adminActivityLogger;
    
    /**
     * Get all password reset tokens with pagination and filtering
     */
    @Transactional(readOnly = true)
    public TokenListResponse<PasswordResetTokenDTO> getAllPasswordResetTokens(
            String userType,
            Boolean includeExpired,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long adminId,
            HttpServletRequest httpRequest
    ) {
        // Validate and sanitize sort field to prevent injection
        String validatedSortBy = validatePasswordResetSortField(sortBy, "createdDate");
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));
        
        Page<PasswordResetToken> tokenPage;
        
        if (userType != null && !userType.isEmpty()) {
            tokenPage = passwordResetTokenRepository.findByUserTypeOrderByCreatedDateDesc(userType, pageable);
        } else if (Boolean.FALSE.equals(includeExpired)) {
            tokenPage = passwordResetTokenRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(
                    LocalDateTime.now(), pageable);
        } else {
            tokenPage = passwordResetTokenRepository.findAllByOrderByCreatedDateDesc(pageable);
        }
        
        List<PasswordResetTokenDTO> tokenDTOs = tokenPage.getContent().stream()
                .map(this::convertPasswordResetTokenToDTO)
                .collect(Collectors.toList());
        
        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (userType != null) details.put("userType", userType);
        if (includeExpired != null) details.put("includeExpired", includeExpired);
        details.put("resultCount", tokenDTOs.size());
        details.put("totalElements", tokenPage.getTotalElements());
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "PasswordResetToken",
                "list",
                details,
                httpRequest
        );
        
        return TokenListResponse.<PasswordResetTokenDTO>builder()
                .tokens(tokenDTOs)
                .currentPage(tokenPage.getNumber())
                .totalPages(tokenPage.getTotalPages())
                .totalElements(tokenPage.getTotalElements())
                .pageSize(tokenPage.getSize())
                .hasNext(tokenPage.hasNext())
                .hasPrevious(tokenPage.hasPrevious())
                .build();
    }
    
    /**
     * Get password reset token by ID
     */
    @Transactional(readOnly = true)
    public PasswordResetTokenDTO getPasswordResetTokenById(Long tokenId, Long adminId, HttpServletRequest httpRequest) {
        PasswordResetToken token = passwordResetTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Password reset token not found with ID: " + tokenId));
        
        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("tokenId", tokenId);
        details.put("userId", token.getUserId());
        details.put("userType", token.getUserType());
        details.put("isExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "PasswordResetToken",
                tokenId.toString(),
                details,
                httpRequest
        );
        
        return convertPasswordResetTokenToDTO(token);
    }
    
    /**
     * Get all verification tokens with pagination and filtering
     */
    @Transactional(readOnly = true)
    public TokenListResponse<VerificationTokenDTO> getAllVerificationTokens(
            String userType,
            Boolean includeExpired,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long adminId,
            HttpServletRequest httpRequest
    ) {
        // Validate and sanitize sort field to prevent injection
        String validatedSortBy = validateVerificationSortField(sortBy, "createdDate");
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validatedSortBy));
        
        Page<VerificationToken> tokenPage;
        
        if (userType != null && !userType.isEmpty()) {
            tokenPage = verificationTokenRepository.findByUserTypeOrderByCreatedDateDesc(userType, pageable);
        } else if (Boolean.FALSE.equals(includeExpired)) {
            tokenPage = verificationTokenRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(
                    LocalDateTime.now(), pageable);
        } else {
            tokenPage = verificationTokenRepository.findAllByOrderByCreatedDateDesc(pageable);
        }
        
        List<VerificationTokenDTO> tokenDTOs = tokenPage.getContent().stream()
                .map(this::convertVerificationTokenToDTO)
                .collect(Collectors.toList());
        
        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (userType != null) details.put("userType", userType);
        if (includeExpired != null) details.put("includeExpired", includeExpired);
        details.put("resultCount", tokenDTOs.size());
        details.put("totalElements", tokenPage.getTotalElements());
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "VerificationToken",
                "list",
                details,
                httpRequest
        );
        
        return TokenListResponse.<VerificationTokenDTO>builder()
                .tokens(tokenDTOs)
                .currentPage(tokenPage.getNumber())
                .totalPages(tokenPage.getTotalPages())
                .totalElements(tokenPage.getTotalElements())
                .pageSize(tokenPage.getSize())
                .hasNext(tokenPage.hasNext())
                .hasPrevious(tokenPage.hasPrevious())
                .build();
    }
    
    /**
     * Get verification token by ID
     */
    @Transactional(readOnly = true)
    public VerificationTokenDTO getVerificationTokenById(Long tokenId, Long adminId, HttpServletRequest httpRequest) {
        VerificationToken token = verificationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Verification token not found with ID: " + tokenId));
        
        // Log activity
        Map<String, Object> details = new HashMap<>();
        details.put("tokenId", tokenId);
        details.put("userId", token.getUserId());
        details.put("userType", token.getUserType());
        details.put("isExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));
        
        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "VerificationToken",
                tokenId.toString(),
                details,
                httpRequest
        );
        
        return convertVerificationTokenToDTO(token);
    }
    
    /**
     * Delete password reset token
     */
    @Transactional
    public void deletePasswordResetToken(Long tokenId, Long adminId, HttpServletRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Password reset token not found with ID: " + tokenId));
        
        // Collect token details before deletion
        Map<String, Object> details = new HashMap<>();
        details.put("userType", token.getUserType());
        details.put("userId", token.getUserId());
        details.put("expiryDate", token.getExpiryDate().toString());
        details.put("wasExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));
        
        passwordResetTokenRepository.deleteById(tokenId);
        
        // Log the activity
        adminActivityLogger.logActivity(adminId, "DELETE", "PasswordResetToken", tokenId.toString(), details, request);
        
        log.info("Deleted password reset token with ID: {} by admin {}", tokenId, adminId);
    }
    
    /**
     * Delete verification token
     */
    @Transactional
    public void deleteVerificationToken(Long tokenId, Long adminId, HttpServletRequest request) {
        VerificationToken token = verificationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Verification token not found with ID: " + tokenId));
        
        // Collect token details before deletion
        Map<String, Object> details = new HashMap<>();
        details.put("userType", token.getUserType());
        details.put("userId", token.getUserId());
        details.put("expiryDate", token.getExpiryDate().toString());
        details.put("wasExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));
        
        verificationTokenRepository.deleteById(tokenId);
        
        // Log the activity
        adminActivityLogger.logActivity(adminId, "DELETE", "VerificationToken", tokenId.toString(), details, request);
        
        log.info("Deleted verification token with ID: {} by admin {}", tokenId, adminId);
    }
    
    /**
     * Delete expired tokens (cleanup utility)
     */
    @Transactional
    public int deleteExpiredPasswordResetTokens(Long adminId, HttpServletRequest request) {
        Page<PasswordResetToken> expiredTokens = passwordResetTokenRepository
                .findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime.now(), 
                        PageRequest.of(0, 1000));
        
        int count = expiredTokens.getContent().size();
        passwordResetTokenRepository.deleteAll(expiredTokens.getContent());
        
        // Log the bulk cleanup activity
        Map<String, Object> details = new HashMap<>();
        details.put("tokensDeleted", count);
        details.put("operation", "bulk_cleanup_expired");
        adminActivityLogger.logActivity(adminId, "DELETE", "PasswordResetToken", "bulk", details, request);
        
        log.info("Deleted {} expired password reset tokens by admin {}", count, adminId);
        return count;
    }
    
    /**
     * Delete expired verification tokens (cleanup utility)
     */
    @Transactional
    public int deleteExpiredVerificationTokens(Long adminId, HttpServletRequest request) {
        Page<VerificationToken> expiredTokens = verificationTokenRepository
                .findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime.now(), 
                        PageRequest.of(0, 1000));
        
        int count = expiredTokens.getContent().size();
        verificationTokenRepository.deleteAll(expiredTokens.getContent());
        
        // Log the bulk cleanup activity
        Map<String, Object> details = new HashMap<>();
        details.put("tokensDeleted", count);
        details.put("operation", "bulk_cleanup_expired");
        adminActivityLogger.logActivity(adminId, "DELETE", "VerificationToken", "bulk", details, request);
        
        log.info("Deleted {} expired verification tokens by admin {}", count, adminId);
        return count;
    }
    
    /**
     * Convert PasswordResetToken to DTO
     */
    private PasswordResetTokenDTO convertPasswordResetTokenToDTO(PasswordResetToken token) {
        String username = "Unknown";
        String email = "Unknown";
        
        //TODO: update according to the user types in your application
        if ("client".equals(token.getUserType())) {
            Client client = clientRepository.findById(token.getUserId()).orElse(null);
            if (client != null) {
                username = client.getUsername();
                email = client.getEmail();
            }
        } else if ("coach".equals(token.getUserType())) {
            Coach coach = coachRepository.findById(token.getUserId()).orElse(null);
            if (coach != null) {
                username = coach.getUsername();
                email = coach.getEmail();
            }
        } else if ("admin".equals(token.getUserType())) {
            Admin admin = adminRepository.findById(token.getUserId()).orElse(null);
            if (admin != null) {
                username = admin.getUsername();
                email = admin.getEmail();
            }
        }
        
        return PasswordResetTokenDTO.builder()
                .id(token.getId())
                .token(token.getToken())
                .userId(token.getUserId())
                .userType(token.getUserType())
                .username(username)
                .email(email)
                .expiryDate(token.getExpiryDate())
                .createdDate(token.getCreatedDate())
                .attemptCount(token.getAttemptCount())
                .requestingIp(token.getRequestingIp())
                .expired(token.isExpired())
                .build();
    }
    
    /**
     * Convert VerificationToken to DTO
     */
    private VerificationTokenDTO convertVerificationTokenToDTO(VerificationToken token) {
        String username = "Unknown";
        String email = "Unknown";
        
        //TODO: update according to the user types in your application
        if ("client".equals(token.getUserType())) {
            Client client = clientRepository.findById(token.getUserId()).orElse(null);
            if (client != null) {
                username = client.getUsername();
                email = client.getEmail();
            }
        } else if ("coach".equals(token.getUserType())) {
            Coach coach = coachRepository.findById(token.getUserId()).orElse(null);
            if (coach != null) {
                username = coach.getUsername();
                email = coach.getEmail();
            }
        } else if ("admin".equals(token.getUserType())) {
            Admin admin = adminRepository.findById(token.getUserId()).orElse(null);
            if (admin != null) {
                username = admin.getUsername();
                email = admin.getEmail();
            }
        }
        
        return VerificationTokenDTO.builder()
                .id(token.getId())
                .token(token.getToken())
                .userId(token.getUserId())
                .userType(token.getUserType())
                .username(username)
                .email(email)
                .expiryDate(token.getExpiryDate())
                .createdDate(token.getCreatedDate())
                .expired(token.isExpired())
                .build();
    }
    
    /**
     * Validate password reset token sort field against whitelist to prevent sort field injection attacks.
     * Returns the validated field if allowed, otherwise returns the default.
     */
    private String validatePasswordResetSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }
        
        if (!ALLOWED_PASSWORD_RESET_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid password reset sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }
        
        return sortBy;
    }
    
    /**
     * Validate verification token sort field against whitelist to prevent sort field injection attacks.
     * Returns the validated field if allowed, otherwise returns the default.
     */
    private String validateVerificationSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }
        
        if (!ALLOWED_VERIFICATION_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid verification sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }
        
        return sortBy;
    }
}
