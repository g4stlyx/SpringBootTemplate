package com.g4stly.templateApp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.dto.auth.LoginRequest;
import com.g4stly.templateApp.dto.auth.RegisterRequest;
import com.g4stly.templateApp.dto.auth.AuthResponse;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.Client;
import com.g4stly.templateApp.models.Coach;
import com.g4stly.templateApp.models.RefreshToken;
import com.g4stly.templateApp.repos.*;
import com.g4stly.templateApp.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private CoachRepository coachRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PasswordService passwordService;
    @Mock private JwtUtils jwtUtils;
    @Mock private EmailService emailService;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private RateLimitService rateLimitService;
    @Mock private UserActivityLogger userActivityLogger;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthService authService;

    // =====================================================================
    // Helpers
    // =====================================================================

    private RegisterRequest buildRegisterRequest(String userType) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("Password1!");
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setUserType(userType);
        return req;
    }

    private LoginRequest buildLoginRequest(String username, String password, String userType) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setUserType(userType);
        return req;
    }

    private Client buildClient(Long id, boolean active, boolean emailVerified, LocalDateTime lockedUntil, int attempts) {
        Client c = new Client();
        c.setId(id);
        c.setUsername("clientUser");
        c.setEmail("client@example.com");
        c.setPasswordHash("hash");
        c.setSalt("salt");
        c.setIsActive(active);
        c.setEmailVerified(emailVerified);
        c.setLockedUntil(lockedUntil);
        c.setLoginAttempts(attempts);
        return c;
    }

    private Coach buildCoach(Long id, boolean active, boolean emailVerified, boolean isVerified, LocalDateTime lockedUntil) {
        Coach c = new Coach();
        c.setId(id);
        c.setUsername("coachUser");
        c.setEmail("coach@example.com");
        c.setPasswordHash("hash");
        c.setSalt("salt");
        c.setIsActive(active);
        c.setEmailVerified(emailVerified);
        c.setIsVerified(isVerified);
        c.setLockedUntil(lockedUntil);
        c.setLoginAttempts(0);
        return c;
    }

    private Admin buildAdmin(Long id, boolean active, LocalDateTime lockedUntil, boolean twoFaEnabled, int attempts) {
        Admin a = new Admin();
        a.setId(id);
        a.setUsername("adminUser");
        a.setEmail("admin@example.com");
        a.setPasswordHash("hash");
        a.setSalt("salt");
        a.setIsActive(active);
        a.setLockedUntil(lockedUntil);
        a.setTwoFactorEnabled(twoFaEnabled);
        a.setLoginAttempts(attempts);
        return a;
    }

    private RefreshToken buildRefreshToken() {
        RefreshToken rt = new RefreshToken(1L, "client", 30L);
        rt.setId(1L);
        return rt;
    }

    // =====================================================================
    // register()
    // =====================================================================

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Admin userType returns success=false")
        void adminUserType_rejected() {
            RegisterRequest req = buildRegisterRequest("admin");
            AuthResponse resp = authService.register(req, httpRequest);
            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid user type");
        }

        @Test
        @DisplayName("Duplicate username returns success=false")
        void duplicateUsername_rejected() {
            RegisterRequest req = buildRegisterRequest("client");
            when(clientRepository.existsByUsername("testuser")).thenReturn(true);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("username already exists");
        }

        @Test
        @DisplayName("Duplicate email returns success=false")
        void duplicateEmail_rejected() {
            RegisterRequest req = buildRegisterRequest("client");
            when(clientRepository.existsByUsername("testuser")).thenReturn(false);
            when(coachRepository.existsByUsername("testuser")).thenReturn(false);
            when(adminRepository.existsByUsername("testuser")).thenReturn(false);
            when(clientRepository.existsByEmail("test@example.com")).thenReturn(true);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("email already exists");
        }

        @Test
        @DisplayName("Valid client registration: success=true, no tokens, email sent")
        void validClientRegistration() {
            RegisterRequest req = buildRegisterRequest("client");
            when(clientRepository.existsByUsername(any())).thenReturn(false);
            when(coachRepository.existsByUsername(any())).thenReturn(false);
            when(adminRepository.existsByUsername(any())).thenReturn(false);
            when(clientRepository.existsByEmail(any())).thenReturn(false);
            when(coachRepository.existsByEmail(any())).thenReturn(false);
            when(adminRepository.existsByEmail(any())).thenReturn(false);
            when(passwordService.generateSalt()).thenReturn("randomsalt");
            when(passwordService.hashPassword(any(), any())).thenReturn("hashed");

            Client saved = buildClient(1L, true, false, null, 0);
            when(clientRepository.save(any(Client.class))).thenReturn(saved);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isNull();
            assertThat(resp.getRefreshToken()).isNull();
            verify(verificationTokenRepository).save(any());
            verify(emailService).sendVerificationEmail(any(), any(), any());
            verify(userActivityLogger).logRegister(eq(1L), eq("client"), eq(true), isNull(), eq(httpRequest));
        }

        @Test
        @DisplayName("Valid coach registration: success=true, pending approval message")
        void validCoachRegistration() throws Exception {
            RegisterRequest req = buildRegisterRequest("coach");
            when(clientRepository.existsByUsername(any())).thenReturn(false);
            when(coachRepository.existsByUsername(any())).thenReturn(false);
            when(adminRepository.existsByUsername(any())).thenReturn(false);
            when(clientRepository.existsByEmail(any())).thenReturn(false);
            when(coachRepository.existsByEmail(any())).thenReturn(false);
            when(adminRepository.existsByEmail(any())).thenReturn(false);
            when(passwordService.generateSalt()).thenReturn("randomsalt");
            when(passwordService.hashPassword(any(), any())).thenReturn("hashed");

            Coach saved = buildCoach(2L, true, false, false, null);
            when(coachRepository.save(any(Coach.class))).thenReturn(saved);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getMessage()).containsIgnoringCase("coach registered");
            assertThat(resp.getAccessToken()).isNull();
            verify(emailService).sendVerificationEmail(any(), any(), any());
        }
    }

    // =====================================================================
    // login() — client
    // =====================================================================

    @Nested
    @DisplayName("login() — client")
    class LoginClient {

        @Test
        @DisplayName("Client not found returns success=false")
        void notFound_returnsFalse() {
            LoginRequest req = buildLoginRequest("clientUser", "pass", "client");
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.empty());

            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid client credentials");
        }

        @Test
        @DisplayName("Inactive client returns success=false")
        void inactive_returnsFalse() {
            Client client = buildClient(1L, false, true, null, 0);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));

            LoginRequest req = buildLoginRequest("clientUser", "pass", "client");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("deactivated");
        }

        @Test
        @DisplayName("Email not verified returns success=false")
        void emailNotVerified_returnsFalse() {
            Client client = buildClient(1L, true, false, null, 0);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));

            LoginRequest req = buildLoginRequest("clientUser", "pass", "client");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("email must be verified");
        }

        @Test
        @DisplayName("Locked account returns success=false")
        void locked_returnsFalse() {
            Client client = buildClient(1L, true, true, LocalDateTime.now().plusMinutes(10), 5);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));

            LoginRequest req = buildLoginRequest("clientUser", "pass", "client");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("temporarily locked");
        }

        @Test
        @DisplayName("Wrong password returns success=false and increments loginAttempts")
        void wrongPassword_incrementsAttempts() {
            Client client = buildClient(1L, true, true, null, 0);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(clientRepository.save(any())).thenReturn(client);

            LoginRequest req = buildLoginRequest("clientUser", "wrong", "client");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(client.getLoginAttempts()).isEqualTo(1);
            assertThat(client.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("5th wrong password locks the account for 15 minutes")
        void fifthWrongPassword_locksAccount() {
            Client client = buildClient(1L, true, true, null, 4); // already at 4
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(clientRepository.save(any())).thenReturn(client);

            LoginRequest req = buildLoginRequest("clientUser", "wrong", "client");
            authService.login(req, httpRequest);

            assertThat(client.getLoginAttempts()).isEqualTo(5);
            assertThat(client.getLockedUntil()).isNotNull();
            assertThat(client.getLockedUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Successful login resets attempts and returns tokens")
        void validLogin_returnsTokens() {
            Client client = buildClient(1L, true, true, null, 2);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(clientRepository.save(any())).thenReturn(client);
            when(jwtUtils.generateToken(eq("clientUser"), eq(1L), eq("client"), isNull())).thenReturn("jwt-access-token");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

            RefreshToken rt = buildRefreshToken();
            when(refreshTokenService.createRefreshToken(1L, "client", httpRequest)).thenReturn(rt);

            LoginRequest req = buildLoginRequest("clientUser", "correct", "client");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isEqualTo("jwt-access-token");
            assertThat(resp.getRefreshToken()).isNotNull();
            assertThat(client.getLoginAttempts()).isEqualTo(0);
            assertThat(client.getLockedUntil()).isNull();
        }
    }

    // =====================================================================
    // login() — coach
    // =====================================================================

    @Nested
    @DisplayName("login() — coach")
    class LoginCoach {

        @Test
        @DisplayName("Coach not found returns success=false")
        void notFound_returnsFalse() {
            when(coachRepository.findByUsernameOrEmail("coachUser", "coachUser")).thenReturn(Optional.empty());

            LoginRequest req = buildLoginRequest("coachUser", "pass", "coach");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid coach credentials");
        }

        @Test
        @DisplayName("Coach email not verified returns success=false")
        void emailNotVerified_returnsFalse() {
            Coach coach = buildCoach(2L, true, false, true, null);
            when(coachRepository.findByUsernameOrEmail("coachUser", "coachUser")).thenReturn(Optional.of(coach));

            LoginRequest req = buildLoginRequest("coachUser", "pass", "coach");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("email must be verified");
        }

        @Test
        @DisplayName("Coach not approved by admin returns success=false with pending message")
        void notAdminVerified_returnsFalse() {
            Coach coach = buildCoach(2L, true, true, false, null);
            when(coachRepository.findByUsernameOrEmail("coachUser", "coachUser")).thenReturn(Optional.of(coach));

            LoginRequest req = buildLoginRequest("coachUser", "pass", "coach");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("pending admin approval");
        }

        @Test
        @DisplayName("Valid coach login returns tokens")
        void validLogin_returnsTokens() {
            Coach coach = buildCoach(2L, true, true, true, null);
            when(coachRepository.findByUsernameOrEmail("coachUser", "coachUser")).thenReturn(Optional.of(coach));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(coachRepository.save(any())).thenReturn(coach);
            when(jwtUtils.generateToken(eq("coachUser"), eq(2L), eq("coach"), isNull())).thenReturn("coach-jwt");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

            RefreshToken rt = new RefreshToken(2L, "coach", 30L);
            when(refreshTokenService.createRefreshToken(2L, "coach", httpRequest)).thenReturn(rt);

            LoginRequest req = buildLoginRequest("coachUser", "correct", "coach");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isEqualTo("coach-jwt");
        }
    }

    // =====================================================================
    // login() — admin
    // =====================================================================

    @Nested
    @DisplayName("login() — admin")
    class LoginAdmin {

        @Test
        @DisplayName("Admin not found returns success=false")
        void notFound_returnsFalse() {
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.empty());

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid admin credentials");
        }

        @Test
        @DisplayName("Inactive admin returns success=false")
        void inactive_returnsFalse() {
            Admin admin = buildAdmin(3L, false, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("deactivated");
        }

        @Test
        @DisplayName("Locked admin returns success=false")
        void locked_returnsFalse() {
            Admin admin = buildAdmin(3L, true, LocalDateTime.now().plusMinutes(5), false, 5);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("temporarily locked");
        }

        @Test
        @DisplayName("Wrong admin password returns success=false and increments attempts")
        void wrongPassword_incrementsAttempts() {
            Admin admin = buildAdmin(3L, true, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            LoginRequest req = buildLoginRequest("adminUser", "wrong", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(admin.getLoginAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Valid password + 2FA enabled → requiresTwoFactor=true with challenge token")
        void validPassword_twoFaEnabled_returnsChallengeToken() {
            Admin admin = buildAdmin(3L, true, null, true, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(adminRepository.save(any())).thenReturn(admin);

            LoginRequest req = buildLoginRequest("adminUser", "correct", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse(); // NOT fully authenticated
            assertThat(resp.isRequiresTwoFactor()).isTrue();
            assertThat(resp.getTwoFactorChallengeToken()).isNotNull().isNotBlank();
            assertThat(admin.getTwoFactorChallengeToken()).isNotNull();
            assertThat(admin.getTwoFactorChallengeExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Valid password + no 2FA → success=true with access and refresh tokens")
        void validPassword_noTwoFa_returnsTokens() {
            Admin admin = buildAdmin(3L, true, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(adminRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(eq("adminUser"), eq(3L), eq("admin"), any())).thenReturn("admin-jwt");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

            RefreshToken rt = new RefreshToken(3L, "admin", 30L);
            when(refreshTokenService.createRefreshToken(3L, "admin", httpRequest)).thenReturn(rt);

            LoginRequest req = buildLoginRequest("adminUser", "correct", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isEqualTo("admin-jwt");
            assertThat(resp.getRefreshToken()).isNotNull();
        }

        @Test
        @DisplayName("5th wrong admin password locks account for 15 minutes")
        void fifthWrongPassword_locksAccount() {
            Admin admin = buildAdmin(3L, true, null, false, 4);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            LoginRequest req = buildLoginRequest("adminUser", "wrong", "admin");
            authService.login(req, httpRequest);

            assertThat(admin.getLoginAttempts()).isEqualTo(5);
            assertThat(admin.getLockedUntil()).isNotNull().isAfter(LocalDateTime.now());
        }
    }

    // =====================================================================
    // login() — no userType (auto-detection)
    // =====================================================================

    @Nested
    @DisplayName("login() — no userType (auto-detect)")
    class LoginNoUserType {

        @Test
        @DisplayName("Falls through to client when found first")
        void findsClientFirst() {
            Client client = buildClient(1L, true, true, null, 0);
            when(clientRepository.findByUsernameOrEmail("clientUser", "clientUser")).thenReturn(Optional.of(client));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(clientRepository.save(any())).thenReturn(client);
            when(jwtUtils.generateToken(anyString(), anyLong(), anyString(), any())).thenReturn("jwt");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);
            RefreshToken rt = buildRefreshToken();
            when(refreshTokenService.createRefreshToken(any(), any(), any())).thenReturn(rt);

            LoginRequest req = buildLoginRequest("clientUser", "correct", null);
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            verify(coachRepository, never()).findByUsernameOrEmail(any(), any());
        }

        @Test
        @DisplayName("No user in any table returns success=false")
        void noUserAnywhere_returnsFalse() {
            when(clientRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
            when(coachRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
            when(adminRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

            LoginRequest req = buildLoginRequest("ghost", "pass", null);
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid credentials");
        }
    }
}
