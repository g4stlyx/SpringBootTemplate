package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.auth.*;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.Client;
import com.g4stly.templateApp.models.Coach;
import com.g4stly.templateApp.models.PasswordResetToken;
import com.g4stly.templateApp.models.VerificationToken;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.ClientRepository;
import com.g4stly.templateApp.repos.CoachRepository;
import com.g4stly.templateApp.repos.PasswordResetTokenRepository;
import com.g4stly.templateApp.repos.VerificationTokenRepository;
import com.g4stly.templateApp.security.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.models.RefreshToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class AuthService {
    
    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private CoachRepository coachRepository;
    
    @Autowired
    private AdminRepository adminRepository;
    
    @Autowired
    private PasswordService passwordService;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private VerificationTokenRepository verificationTokenRepository;
    
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private UserActivityLogger userActivityLogger;

    @Autowired
    private RefreshTokenService refreshTokenService;
    
    /**
     * Register a new user (clients or coaches can register via API, admins cannot)
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            // Validate userType
            String userType = request.getUserType() != null ? request.getUserType().toLowerCase() : "client";
            
            if (!"client".equals(userType) && !"coach".equals(userType)) {
                return AuthResponse.builder()
                    .success(false)
                    .message("Invalid user type. Only 'client' or 'coach' are allowed.")
                    .build();
            }
            
            // Check if username already exists in any table
            //TODO: update based on the user types in the application
            if (clientRepository.existsByUsername(request.getUsername()) || 
                coachRepository.existsByUsername(request.getUsername()) ||
                adminRepository.existsByUsername(request.getUsername())) {
                return AuthResponse.builder()
                    .success(false)
                    .message("Username already exists")
                    .build();
            }
            
            // Check if email already exists in any table
            //TODO: update based on the user types in the application
            if (clientRepository.existsByEmail(request.getEmail()) || 
                coachRepository.existsByEmail(request.getEmail()) ||
                adminRepository.existsByEmail(request.getEmail())) {
                return AuthResponse.builder()
                    .success(false)
                    .message("Email already exists")
                    .build();
            }
            
            // Register based on user type
            if ("coach".equals(userType)) {
                return registerCoach(request, httpRequest);
            } else {
                return registerClient(request, httpRequest);
            }
            
        } catch (Exception e) {
            log.error("Registration failed for user: {}", request.getUsername(), e);
            return AuthResponse.builder()
                .success(false)
                .message("Registration failed. Please try again.")
                .build();
        }
    }
    
    //TODO: update based on the user types in the application
    private AuthResponse registerClient(RegisterRequest request, HttpServletRequest httpRequest) {
        // Create new client
        String salt = passwordService.generateSalt();
        String hashedPassword = passwordService.hashPassword(request.getPassword(), salt);
        
        Client client = new Client();
        client.setUsername(request.getUsername());
        client.setEmail(request.getEmail());
        client.setPasswordHash(hashedPassword);
        client.setSalt(salt);
        client.setFirstName(request.getFirstName());
        client.setLastName(request.getLastName());
        client.setPhone(request.getPhone());
        client.setBio(request.getBio());
        client.setOccupation(request.getOccupation());
        client.setIsActive(true);
        client.setEmailVerified(false);
        client.setOnboardingCompleted(false);
        
        client = clientRepository.save(client);
        
        // Create verification token
        VerificationToken verificationToken = new VerificationToken(client.getId(), "client");
        verificationTokenRepository.save(verificationToken);
        
        // Send verification email
        emailService.sendVerificationEmail(client.getEmail(), verificationToken.getToken(), 
            client.getFirstName() != null ? client.getFirstName() : client.getUsername());
        
        // Log successful registration
        userActivityLogger.logRegister(client.getId(), "client", true, null, httpRequest);
        
        // Do NOT generate tokens - client must verify email first
        return AuthResponse.builder()
            .success(true)
            .message("Client registered successfully. Please check your email to verify your account before logging in.")
            .accessToken(null)
            .refreshToken(null)
            .expiresIn(null)
            .user(AuthResponse.UserInfo.builder()
                .id(client.getId())
                .username(client.getUsername())
                .email(client.getEmail())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .profilePicture(client.getProfilePicture())
                .isActive(client.getIsActive())
                .emailVerified(client.getEmailVerified())
                .userType("client")
                .lastLoginAt(client.getLastLoginAt())
                .build())
            .build();
    }
    
    //TODO: update based on the user types in the application
    private AuthResponse registerCoach(RegisterRequest request, HttpServletRequest httpRequest) {
        // Create new coach
        String salt = passwordService.generateSalt();
        String hashedPassword = passwordService.hashPassword(request.getPassword(), salt);
        
        Coach coach = new Coach();
        coach.setUsername(request.getUsername());
        coach.setEmail(request.getEmail());
        coach.setPasswordHash(hashedPassword);
        coach.setSalt(salt);
        coach.setFirstName(request.getFirstName());
        coach.setLastName(request.getLastName());
        coach.setPhone(request.getPhone());
        coach.setBio(request.getBio());
        
        // Convert lists to JSON strings
        try {
            if (request.getSpecializations() != null && !request.getSpecializations().isEmpty()) {
                coach.setSpecializations(objectMapper.writeValueAsString(request.getSpecializations()));
            }
            if (request.getCertifications() != null && !request.getCertifications().isEmpty()) {
                coach.setCertifications(objectMapper.writeValueAsString(request.getCertifications()));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to convert lists to JSON", e);
            throw new RuntimeException("Failed to process coach data");
        }
        
        coach.setYearsOfExperience(request.getYearsOfExperience());
        coach.setHourlyRate(request.getHourlyRate());
        coach.setIsActive(true);
        coach.setIsVerified(false); // Coaches need admin approval
        coach.setEmailVerified(false);
        
        coach = coachRepository.save(coach);
        
        // Create verification token
        VerificationToken verificationToken = new VerificationToken(coach.getId(), "coach");
        verificationTokenRepository.save(verificationToken);
        
        // Send verification email
        emailService.sendVerificationEmail(coach.getEmail(), verificationToken.getToken(), 
            coach.getFirstName() != null ? coach.getFirstName() : coach.getUsername());
        
        // Log successful registration
        userActivityLogger.logRegister(coach.getId(), "coach", true, null, httpRequest);
        
        // Do NOT generate tokens - coach must verify email first (and wait for admin approval)
        return AuthResponse.builder()
            .success(true)
            .message("Coach registered successfully. Please verify your email. Your account will be reviewed by an administrator before you can access all features.")
            .accessToken(null)
            .refreshToken(null)
            .expiresIn(null)
            .user(AuthResponse.UserInfo.builder()
                .id(coach.getId())
                .username(coach.getUsername())
                .email(coach.getEmail())
                .firstName(coach.getFirstName())
                .lastName(coach.getLastName())
                .profilePicture(coach.getProfilePicture())
                .isActive(coach.getIsActive())
                .emailVerified(coach.getEmailVerified())
                .userType("coach")
                .isVerified(coach.getIsVerified())
                .lastLoginAt(coach.getLastLoginAt())
                .build())
            .build();
    }
    
    
    /**
     * Authenticate user (supports client, coach, and admin login)
     * If userType is specified, search only that type
     * If userType is not specified, search in order: client -> coach -> admin
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            String userType = request.getUserType() != null ? request.getUserType().toLowerCase() : null;
            
            //TODO: update based on the user types in the application
            // If userType is specified, only search in that specific type
            if ("client".equals(userType)) {
                Optional<Client> clientOpt = clientRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (clientOpt.isPresent()) {
                    return authenticateClient(clientOpt.get(), request.getPassword(), httpRequest);
                } else {
                    return AuthResponse.builder()
                        .success(false)
                        .message("Invalid client credentials")
                        .build();
                }
            } else if ("coach".equals(userType)) {
                Optional<Coach> coachOpt = coachRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (coachOpt.isPresent()) {
                    return authenticateCoach(coachOpt.get(), request.getPassword(), httpRequest);
                } else {
                    return AuthResponse.builder()
                        .success(false)
                        .message("Invalid coach credentials")
                        .build();
                }
            } else if ("admin".equals(userType)) {
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (adminOpt.isPresent()) {
                    return authenticateAdmin(adminOpt.get(), request.getPassword(), httpRequest);
                } else {
                    return AuthResponse.builder()
                        .success(false)
                        .message("Invalid admin credentials")
                        .build();
                }
            } else {
                // No userType specified: try client -> coach -> admin
                Optional<Client> clientOpt = clientRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                    
                if (clientOpt.isPresent()) {
                    return authenticateClient(clientOpt.get(), request.getPassword(), httpRequest);
                }
                
                Optional<Coach> coachOpt = coachRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                    
                if (coachOpt.isPresent()) {
                    return authenticateCoach(coachOpt.get(), request.getPassword(), httpRequest);
                }
                
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                    
                if (adminOpt.isPresent()) {
                    return authenticateAdmin(adminOpt.get(), request.getPassword(), httpRequest);
                }
                
                return AuthResponse.builder()
                    .success(false)
                    .message("Invalid credentials")
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return AuthResponse.builder()
                .success(false)
                .message("Login failed. Please try again.")
                .build();
        }
    }
    
    private AuthResponse authenticateClient(Client client, String password, HttpServletRequest httpRequest) {
        // Check if account is active
        if (!client.getIsActive()) {
            return AuthResponse.builder()
                .success(false)
                .message("Account is deactivated")
                .build();
        }
        
        // Check if email is verified
        if (!client.getEmailVerified()) {
            return AuthResponse.builder()
                .success(false)
                .message("Email must be verified before login. Please check your email for verification link.")
                .build();
        }
        
        // Check if account is locked
        if (client.getLockedUntil() != null && client.getLockedUntil().isAfter(LocalDateTime.now())) {
            return AuthResponse.builder()
                .success(false)
                .message("Account is temporarily locked. Please try again later.")
                .build();
        }
        
        // Verify password
        boolean isValidPassword = passwordService.verifyPassword(password, client.getSalt(), client.getPasswordHash());
        
        if (!isValidPassword) {
            // Increment login attempts
            client.setLoginAttempts(client.getLoginAttempts() + 1);
            
            // Lock account after 5 failed attempts
            if (client.getLoginAttempts() >= 5) {
                client.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            
            clientRepository.save(client);
            
            // Log failed login attempt
            userActivityLogger.logLoginFailure(client.getId(), "client", "Invalid password", httpRequest);
            
            return AuthResponse.builder()
                .success(false)
                .message("Invalid credentials")
                .build();
        }
        
        // Reset login attempts on successful login
        client.setLoginAttempts(0);
        client.setLockedUntil(null);
        client.setLastLoginAt(LocalDateTime.now());
        clientRepository.save(client);
        
        // Log successful login
        userActivityLogger.logLoginSuccess(client.getId(), "client", httpRequest);
        
        // Generate access token
        String accessToken = jwtUtils.generateToken(client.getUsername(), client.getId(), "client", null);
        
        // Create and save refresh token in database
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
            client.getId(), "client", httpRequest);
        
        log.info("Returning refresh token: {}...", refreshTokenEntity.getToken().substring(0, 8));
        
        return AuthResponse.builder()
            .success(true)
            .message("Login successful")
            .accessToken(accessToken)
            .refreshToken(refreshTokenEntity.getToken())
            .expiresIn(jwtUtils.getAccessTokenExpiration())
            .user(AuthResponse.UserInfo.builder()
                .id(client.getId())
                .username(client.getUsername())
                .email(client.getEmail())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .profilePicture(client.getProfilePicture())
                .isActive(client.getIsActive())
                .emailVerified(client.getEmailVerified())
                .userType("client")
                .lastLoginAt(client.getLastLoginAt())
                .build())
            .build();
    }
    
    private AuthResponse authenticateCoach(Coach coach, String password, HttpServletRequest httpRequest) {
        // Check if account is active
        if (!coach.getIsActive()) {
            return AuthResponse.builder()
                .success(false)
                .message("Account is deactivated")
                .build();
        }
        
        // Check if email is verified
        if (!coach.getEmailVerified()) {
            return AuthResponse.builder()
                .success(false)
                .message("Email must be verified before login. Please check your email for verification link.")
                .build();
        }
        
        // Check if coach is verified by admin
        if (!coach.getIsVerified()) {
            return AuthResponse.builder()
                .success(false)
                .message("Your coach account is pending admin approval. You will be notified once approved.")
                .build();
        }
        
        // Check if account is locked
        if (coach.getLockedUntil() != null && coach.getLockedUntil().isAfter(LocalDateTime.now())) {
            return AuthResponse.builder()
                .success(false)
                .message("Account is temporarily locked. Please try again later.")
                .build();
        }
        
        // Verify password
        boolean isValidPassword = passwordService.verifyPassword(password, coach.getSalt(), coach.getPasswordHash());
        
        if (!isValidPassword) {
            // Increment login attempts
            coach.setLoginAttempts(coach.getLoginAttempts() + 1);
            
            // Lock account after 5 failed attempts
            if (coach.getLoginAttempts() >= 5) {
                coach.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            
            coachRepository.save(coach);
            
            // Log failed login attempt
            userActivityLogger.logLoginFailure(coach.getId(), "coach", "Invalid password", httpRequest);
            
            return AuthResponse.builder()
                .success(false)
                .message("Invalid credentials")
                .build();
        }
        
        // Reset login attempts on successful login
        coach.setLoginAttempts(0);
        coach.setLockedUntil(null);
        coach.setLastLoginAt(LocalDateTime.now());
        coachRepository.save(coach);
        
        // Log successful login
        userActivityLogger.logLoginSuccess(coach.getId(), "coach", httpRequest);
        
        // Generate access token
        String accessToken = jwtUtils.generateToken(coach.getUsername(), coach.getId(), "coach", null);
        
        // Create and save refresh token in database
        com.g4stly.templateApp.models.RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
            coach.getId(), "coach", httpRequest);
        
        log.info("Returning refresh token: {}...", refreshTokenEntity.getToken().substring(0, 8));
        
        return AuthResponse.builder()
            .success(true)
            .message("Login successful")
            .accessToken(accessToken)
            .refreshToken(refreshTokenEntity.getToken())
            .expiresIn(jwtUtils.getAccessTokenExpiration())
            .user(AuthResponse.UserInfo.builder()
                .id(coach.getId())
                .username(coach.getUsername())
                .email(coach.getEmail())
                .firstName(coach.getFirstName())
                .lastName(coach.getLastName())
                .profilePicture(coach.getProfilePicture())
                .isActive(coach.getIsActive())
                .emailVerified(coach.getEmailVerified())
                .userType("coach")
                .lastLoginAt(coach.getLastLoginAt())
                .build())
            .build();
    }
    
    private AuthResponse authenticateAdmin(Admin admin, String password, HttpServletRequest httpRequest) {
        // Check if account is active
        if (!admin.getIsActive()) {
            return AuthResponse.builder()
                .success(false)
                .message("Admin account is deactivated")
                .build();
        }
        
        // Check if account is locked
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now())) {
            return AuthResponse.builder()
                .success(false)
                .message("Admin account is temporarily locked. Please try again later.")
                .build();
        }
        
        // Verify password
        boolean isValidPassword = passwordService.verifyPassword(password, admin.getSalt(), admin.getPasswordHash());
        
        if (!isValidPassword) {
            // Increment login attempts
            admin.setLoginAttempts(admin.getLoginAttempts() + 1);
            
            // Lock account after 5 failed attempts
            if (admin.getLoginAttempts() >= 5) {
                admin.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            
            adminRepository.save(admin);
            
            return AuthResponse.builder()
                .success(false)
                .message("Invalid credentials")
                .build();
        }
        
        // Reset login attempts on successful password
        admin.setLoginAttempts(0);
        admin.setLockedUntil(null);
        
        // Check if 2FA is enabled - if so, don't complete login yet
        if (Boolean.TRUE.equals(admin.getTwoFactorEnabled())) {
            log.info("2FA required for admin: {}", admin.getUsername());
            
            // Generate a secure challenge token (32 bytes = 64 hex chars)
            String challengeToken = java.util.UUID.randomUUID().toString().replace("-", "") + 
                                    java.util.UUID.randomUUID().toString().replace("-", "");
            
            // Store challenge token with 5-minute expiration
            admin.setTwoFactorChallengeToken(challengeToken);
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(0);
            adminRepository.save(admin);
            
            log.info("Generated 2FA challenge token for admin: {} (expires in 5 minutes)", admin.getUsername());
            
            return AuthResponse.builder()
                .success(false) // Not fully authenticated yet
                .requiresTwoFactor(true)
                .twoFactorChallengeToken(challengeToken)
                .message("Two-factor authentication required")
                .user(AuthResponse.UserInfo.builder()
                    .username(admin.getUsername())
                    .userType("admin")
                    .build())
                .build();
        }
        
        adminRepository.save(admin);
        
        // No 2FA - complete login
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);
        
        // Generate access token with admin level
        String accessToken = jwtUtils.generateToken(admin.getUsername(), admin.getId(), "admin", admin.getLevel());
        
        // Create and save refresh token in database
        com.g4stly.templateApp.models.RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
            admin.getId(), "admin", httpRequest);
        
        log.info("Returning refresh token: {}...", refreshTokenEntity.getToken().substring(0, 8));
        
        return AuthResponse.builder()
            .success(true)
            .message("Admin login successful")
            .accessToken(accessToken)
            .refreshToken(refreshTokenEntity.getToken())
            .expiresIn(jwtUtils.getAccessTokenExpiration())
            .user(AuthResponse.UserInfo.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .profilePicture(admin.getProfilePicture())
                .isActive(admin.getIsActive())
                .emailVerified(true) // Admins are auto-verified
                .userType("admin")
                .level(admin.getLevel())
                .lastLoginAt(admin.getLastLoginAt())
                .build())
            .build();
    }
    
    /**
     * Verify user password (for password change operations)
     * Supports optional userType parameter. If not provided, searches in order: client -> coach -> admin
     */
    public boolean verifyPassword(VerifyPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            String userType = request.getUserType() != null ? request.getUserType().toLowerCase() : null;
            
            if ("client".equals(userType)) {
                Optional<Client> clientOpt = clientRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (clientOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        clientOpt.get().getSalt(), clientOpt.get().getPasswordHash());
                }
            } else if ("coach".equals(userType)) {
                Optional<Coach> coachOpt = coachRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (coachOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        coachOpt.get().getSalt(), coachOpt.get().getPasswordHash());
                }
            } else if ("admin".equals(userType)) {
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (adminOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        adminOpt.get().getSalt(), adminOpt.get().getPasswordHash());
                }
            } else {
                // No userType specified: try client -> coach -> admin
                Optional<Client> clientOpt = clientRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (clientOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        clientOpt.get().getSalt(), clientOpt.get().getPasswordHash());
                }
                
                Optional<Coach> coachOpt = coachRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (coachOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        coachOpt.get().getSalt(), coachOpt.get().getPasswordHash());
                }
                
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                    request.getUsername(), request.getUsername());
                
                if (adminOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(), 
                        adminOpt.get().getSalt(), adminOpt.get().getPasswordHash());
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Password verification failed for user: {}", request.getUsername(), e);
            return false;
        }
    }
    
    /**
     * Initiate forgot password process
     * Supports optional userType parameter. If not provided, searches in order: client -> coach -> admin
     */
    @Transactional
    public boolean forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            String userType = request.getUserType() != null ? request.getUserType().toLowerCase() : null;
            
            if ("client".equals(userType)) {
                Optional<Client> clientOpt = clientRepository.findByEmail(request.getEmail());
                if (clientOpt.isPresent()) {
                    createPasswordResetToken(clientOpt.get().getId(), "client", request.getEmail(), clientIp);
                    return true;
                }
            } else if ("coach".equals(userType)) {
                Optional<Coach> coachOpt = coachRepository.findByEmail(request.getEmail());
                if (coachOpt.isPresent()) {
                    createPasswordResetToken(coachOpt.get().getId(), "coach", request.getEmail(), clientIp);
                    return true;
                }
            } else if ("admin".equals(userType)) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(request.getEmail());
                if (adminOpt.isPresent()) {
                    createPasswordResetToken(adminOpt.get().getId(), "admin", request.getEmail(), clientIp);
                    return true;
                }
            } else {
                // No userType specified: try client -> coach -> admin
                Optional<Client> clientOpt = clientRepository.findByEmail(request.getEmail());
                if (clientOpt.isPresent()) {
                    createPasswordResetToken(clientOpt.get().getId(), "client", request.getEmail(), clientIp);
                    return true;
                }
                
                Optional<Coach> coachOpt = coachRepository.findByEmail(request.getEmail());
                if (coachOpt.isPresent()) {
                    createPasswordResetToken(coachOpt.get().getId(), "coach", request.getEmail(), clientIp);
                    return true;
                }
                
                Optional<Admin> adminOpt = adminRepository.findByEmail(request.getEmail());
                if (adminOpt.isPresent()) {
                    createPasswordResetToken(adminOpt.get().getId(), "admin", request.getEmail(), clientIp);
                    return true;
                }
            }
            
            // Return true even if email not found for security reasons
            return true;
            
        } catch (Exception e) {
            log.error("Forgot password failed for email: {}", request.getEmail(), e);
            return false;
        }
    }
    
    private void createPasswordResetToken(Long userId, String userType, String email, String clientIp) {
        // Remove any existing tokens for this user
        passwordResetTokenRepository.deleteByUserIdAndUserType(userId, userType);
        
        // Create new token
        PasswordResetToken resetToken = new PasswordResetToken(userId, userType, clientIp);
        passwordResetTokenRepository.save(resetToken);
        
        // Log password reset request (only for client/coach, not admin)
        if ("client".equals(userType) || "coach".equals(userType)) {
            userActivityLogger.logPasswordResetRequest(userId, userType, null);
        }
        
        // Send reset email
        emailService.sendPasswordResetEmail(email, resetToken.getToken(), 
            getUserNameByEmailAndType(email, userType));
    }
    
    /**
     * Reset password using token
     */
    @Transactional
    public boolean resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(request.getToken());
            
            if (tokenOpt.isEmpty()) {
                return false;
            }
            
            PasswordResetToken resetToken = tokenOpt.get();
            
            // Check if token is expired
            if (resetToken.isExpired()) {
                passwordResetTokenRepository.delete(resetToken);
                return false;
            }
            
            // Check if too many attempts
            if (resetToken.hasTooManyAttempts()) {
                passwordResetTokenRepository.delete(resetToken);
                return false;
            }
            
            // Update password
            String salt = passwordService.generateSalt();
            String hashedPassword = passwordService.hashPassword(request.getNewPassword(), salt);
            
            if ("client".equals(resetToken.getUserType())) {
                Optional<Client> clientOpt = clientRepository.findById(resetToken.getUserId());
                if (clientOpt.isPresent()) {
                    Client client = clientOpt.get();
                    client.setPasswordHash(hashedPassword);
                    client.setSalt(salt);
                    client.setLoginAttempts(0); // Reset login attempts
                    client.setLockedUntil(null); // Unlock account
                    clientRepository.save(client);
                    // Log password reset (no httpRequest available here)
                    userActivityLogger.logPasswordResetComplete(client.getId(), "client", true, httpRequest);
                }
            } else if ("coach".equals(resetToken.getUserType())) {
                Optional<Coach> coachOpt = coachRepository.findById(resetToken.getUserId());
                if (coachOpt.isPresent()) {
                    Coach coach = coachOpt.get();
                    coach.setPasswordHash(hashedPassword);
                    coach.setSalt(salt);
                    coach.setLoginAttempts(0); // Reset login attempts
                    coach.setLockedUntil(null); // Unlock account
                    coachRepository.save(coach);
                    // Log password reset (no httpRequest available here)
                    userActivityLogger.logPasswordResetComplete(coach.getId(), "coach", true, httpRequest);
                }
            } else if ("admin".equals(resetToken.getUserType())) {
                Optional<Admin> adminOpt = adminRepository.findById(resetToken.getUserId());
                if (adminOpt.isPresent()) {
                    Admin admin = adminOpt.get();
                    admin.setPasswordHash(hashedPassword);
                    admin.setSalt(salt);
                    admin.setLoginAttempts(0); // Reset login attempts
                    admin.setLockedUntil(null); // Unlock account
                    adminRepository.save(admin);
                    // Note: Admin password resets are not logged in user activity logs (admin activities are separate)
                }
            }
            
            // Delete the used token
            passwordResetTokenRepository.delete(resetToken);
            
            return true;
            
        } catch (Exception e) {
            log.error("Password reset failed for token: {}", request.getToken(), e);
            return false;
        }
    }
    
    /**
     * Verify email using verification token
     */
    @Transactional
    public boolean verifyEmail(String token) {
        try {
            Optional<VerificationToken> tokenOpt = verificationTokenRepository.findByToken(token);
            
            if (tokenOpt.isEmpty()) {
                return false;
            }
            
            VerificationToken verificationToken = tokenOpt.get();
            
            // Check if token is expired
            if (verificationToken.isExpired()) {
                verificationTokenRepository.delete(verificationToken);
                return false;
            }
            
            // Mark user as verified
            if ("client".equals(verificationToken.getUserType())) {
                Optional<Client> clientOpt = clientRepository.findById(verificationToken.getUserId());
                if (clientOpt.isPresent()) {
                    Client client = clientOpt.get();
                    client.setEmailVerified(true);
                    clientRepository.save(client);
                    // Log email verification (no httpRequest available here)
                    userActivityLogger.logEmailVerification(client.getId(), "client", true, null);
                }
            } else if ("coach".equals(verificationToken.getUserType())) {
                Optional<Coach> coachOpt = coachRepository.findById(verificationToken.getUserId());
                if (coachOpt.isPresent()) {
                    Coach coach = coachOpt.get();
                    coach.setEmailVerified(true);
                    coachRepository.save(coach);
                    // Log email verification (no httpRequest available here)
                    userActivityLogger.logEmailVerification(coach.getId(), "coach", true, null);
                }
            }
            
            // Delete the used token
            verificationTokenRepository.delete(verificationToken);
            
            return true;
            
        } catch (Exception e) {
            log.error("Email verification failed for token: {}", token, e);
            return false;
        }
    }
    
    /**
     * Resend verification email
     * Supports optional userType parameter. If not provided, searches in order: client -> coach
     */
    @Transactional
    public boolean resendVerificationEmail(ResendVerificationRequest request) {
        try {
            String email = request.getEmail();
            String userType = request.getUserType() != null ? request.getUserType().toLowerCase() : null;
            
            // Check rate limiting first
            if (rateLimitService.isEmailVerificationRateLimitExceeded(email)) {
                log.warn("Rate limit exceeded for verification email resend: {}", email);
                // Return true for security reasons (don't reveal rate limiting details)
                return true;
            }
            
            if ("client".equals(userType)) {
                Optional<Client> clientOpt = clientRepository.findByEmail(email);
                
                if (clientOpt.isEmpty()) {
                    log.warn("Verification resend requested for non-existent client email: {}", email);
                    return true; // Return true for security reasons
                }
                
                Client client = clientOpt.get();
                
                if (client.getEmailVerified()) {
                    log.info("Verification resend requested for already verified client email: {}", email);
                    return true; // Return true but don't send email
                }
                
                // Delete any existing verification tokens for this client
                verificationTokenRepository.deleteByUserIdAndUserType(client.getId(), "client");
                
                // Create new verification token
                VerificationToken verificationToken = new VerificationToken(client.getId(), "client");
                verificationTokenRepository.save(verificationToken);
                
                // Send verification email
                emailService.sendVerificationEmail(client.getEmail(), verificationToken.getToken(), 
                    client.getFirstName() != null ? client.getFirstName() : client.getUsername());
                
                log.info("Verification email resent to client: {}", email);
                return true;
                
            } else if ("coach".equals(userType)) {
                Optional<Coach> coachOpt = coachRepository.findByEmail(email);
                
                if (coachOpt.isEmpty()) {
                    log.warn("Verification resend requested for non-existent coach email: {}", email);
                    return true; // Return true for security reasons
                }
                
                Coach coach = coachOpt.get();
                
                if (coach.getEmailVerified()) {
                    log.info("Verification resend requested for already verified coach email: {}", email);
                    return true; // Return true but don't send email
                }
                
                // Delete any existing verification tokens for this coach
                verificationTokenRepository.deleteByUserIdAndUserType(coach.getId(), "coach");
                
                // Create new verification token
                VerificationToken verificationToken = new VerificationToken(coach.getId(), "coach");
                verificationTokenRepository.save(verificationToken);
                
                // Send verification email
                emailService.sendVerificationEmail(coach.getEmail(), verificationToken.getToken(), 
                    coach.getFirstName() != null ? coach.getFirstName() : coach.getUsername());
                
                log.info("Verification email resent to coach: {}", email);
                return true;
                
            } else {
                // No userType specified: try client -> coach
                Optional<Client> clientOpt = clientRepository.findByEmail(email);
                
                if (clientOpt.isPresent()) {
                    Client client = clientOpt.get();
                    
                    if (client.getEmailVerified()) {
                        log.info("Verification resend requested for already verified client email: {}", email);
                        return true;
                    }
                    
                    verificationTokenRepository.deleteByUserIdAndUserType(client.getId(), "client");
                    VerificationToken verificationToken = new VerificationToken(client.getId(), "client");
                    verificationTokenRepository.save(verificationToken);
                    emailService.sendVerificationEmail(client.getEmail(), verificationToken.getToken(), 
                        client.getFirstName() != null ? client.getFirstName() : client.getUsername());
                    
                    log.info("Verification email resent to client: {}", email);
                    return true;
                }
                
                Optional<Coach> coachOpt = coachRepository.findByEmail(email);
                
                if (coachOpt.isPresent()) {
                    Coach coach = coachOpt.get();
                    
                    if (coach.getEmailVerified()) {
                        log.info("Verification resend requested for already verified coach email: {}", email);
                        return true;
                    }
                    
                    verificationTokenRepository.deleteByUserIdAndUserType(coach.getId(), "coach");
                    VerificationToken verificationToken = new VerificationToken(coach.getId(), "coach");
                    verificationTokenRepository.save(verificationToken);
                    emailService.sendVerificationEmail(coach.getEmail(), verificationToken.getToken(), 
                        coach.getFirstName() != null ? coach.getFirstName() : coach.getUsername());
                    
                    log.info("Verification email resent to coach: {}", email);
                    return true;
                }
                
                // Return true for security reasons (don't reveal if email exists)
                log.warn("Verification resend requested for non-existent email: {}", email);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Resend verification email failed for email: {}", request.getEmail(), e);
            return false;
        }
    }
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Helper method to get user name by email and type
     */
    private String getUserNameByEmailAndType(String email, String userType) {
        if ("client".equals(userType)) {
            Optional<Client> clientOpt = clientRepository.findByEmail(email);
            if (clientOpt.isPresent()) {
                Client client = clientOpt.get();
                return client.getFirstName() != null ? client.getFirstName() : client.getUsername();
            }
        } else if ("coach".equals(userType)) {
            Optional<Coach> coachOpt = coachRepository.findByEmail(email);
            if (coachOpt.isPresent()) {
                Coach coach = coachOpt.get();
                return coach.getFirstName() != null ? coach.getFirstName() : coach.getUsername();
            }
        } else if ("admin".equals(userType)) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(email);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                return admin.getFirstName() != null ? admin.getFirstName() : admin.getUsername();
            }
        }
        return "User";
    }
    
    /**
     * Get current user's minimal session information from JWT token
     * Returns only essential data needed for UI header (id, name, profile picture, role)
     * This is an optimized method to avoid returning full profile data
     */
    public  com.g4stly.templateApp.dto.auth.UserSessionDTO getCurrentUserSession(String token) {
        try {
            if (!jwtUtils.validateToken(token)) {
                return null;
            }
            
            Long userId = jwtUtils.extractUserIdAsLong(token);
            String userType = jwtUtils.extractUserType(token);
            
            if (userId == null || userType == null) {
                return null;
            }
            
            switch (userType.toLowerCase()) {
                case "client":
                    return clientRepository.findById(userId)
                        .map(client ->  com.g4stly.templateApp.dto.auth.UserSessionDTO.builder()
                            .id(client.getId())
                            .firstName(client.getFirstName())
                            .lastName(client.getLastName())
                            .profilePicture(client.getProfilePicture())
                            .role("CLIENT")
                            .build())
                        .orElse(null);
                        
                case "coach":
                    return coachRepository.findById(userId)
                        .map(coach ->  com.g4stly.templateApp.dto.auth.UserSessionDTO.builder()
                            .id(coach.getId())
                            .firstName(coach.getFirstName())
                            .lastName(coach.getLastName())
                            .profilePicture(coach.getProfilePicture())
                            .role("COACH")
                            .build())
                        .orElse(null);
                        
                case "admin":
                    return adminRepository.findById(userId)
                        .map(admin ->  com.g4stly.templateApp.dto.auth.UserSessionDTO.builder()
                            .id(admin.getId())
                            .firstName(admin.getFirstName())
                            .lastName(admin.getLastName())
                            .profilePicture(admin.getProfilePicture())
                            .role("ADMIN")
                            .adminLevel(admin.getLevel())
                            .build())
                        .orElse(null);
                        
                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("Error getting user session from token", e);
            return null;
        }
    }
}