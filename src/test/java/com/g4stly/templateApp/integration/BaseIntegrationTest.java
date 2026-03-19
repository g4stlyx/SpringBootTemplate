package com.g4stly.templateApp.integration;

import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.models.enums.UserType;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.RefreshTokenRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.repos.VerificationTokenRepository;
import com.g4stly.templateApp.services.EmailService;
import com.g4stly.templateApp.services.PasswordService;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

/**
 * Base class for integration tests.
 *
 * Uses a real Spring context with:
 * - H2 in-memory DB (MODE=MySQL) via application-integration.properties
 * - EmailService mocked to prevent SMTP calls
 * - TestRestTemplate for real HTTP calls through the filter chain
 *
 * No @Transactional — refresh tokens stored in DB must survive between
 * multiple HTTP calls within the same test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("integration")
public abstract class BaseIntegrationTest {

    protected static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF = new ParameterizedTypeReference<>() {
    };

    // ---- Mocked external dependencies ----
    @MockitoBean
    protected EmailService emailService;

    // ---- HTTP client ----
    @Autowired
    protected TestRestTemplate restTemplate;

    // ---- Repositories for test data setup & cleanup ----
    @Autowired
    protected AdminRepository adminRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected VerificationTokenRepository verificationTokenRepository;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- Cleanup between tests ----
    // H2 enforces FK constraints, so we disable referential integrity while
    // truncating to avoid failures when logs/child records reference
    // admins/clients.

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            refreshTokenRepository.deleteAll();
            verificationTokenRepository.deleteAll();
            userRepository.deleteAll();
            adminRepository.deleteAll();
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    // ---- Helper: create a fully active admin directly in DB ----

    protected Admin createAdmin(String username, String email, String password, int level) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(password, salt);

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(hash);
        admin.setSalt(salt);
        admin.setLevel(level);
        admin.setIsActive(true);

        return adminRepository.save(admin);
    }

    // ---- Helper: create a user with email already verified ----

    protected User createVerifiedUser(String username, String email, String password) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setEmailVerified(true);
        user.setIsActive(true);
        user.setUserType(UserType.APP_USER);

        return userRepository.save(user);
    }

    // ---- Helper: POST /api/v1/auth/login and return full ResponseEntity ----

    protected ResponseEntity<Map<String, Object>> login(String username, String password, String role) {
        Map<String, String> body = Map.of(
                "username", username,
                "password", password,
                "role", role);
        return restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE_REF);
    }

    // ---- Helper: extract accessToken from login response ----

    protected String extractAccessToken(ResponseEntity<Map<String, Object>> response) {
        Object token = response.getBody().get("accessToken");
        return token != null ? token.toString() : null;
    }

    // ---- Helper: extract refreshToken from login response ----

    protected String extractRefreshToken(ResponseEntity<Map<String, Object>> response) {
        Object token = response.getBody().get("refreshToken");
        return token != null ? token.toString() : null;
    }

    // ---- Helper: build Authorization header ----

    protected HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }

    // ---- Helper: authenticated GET ----

    protected ResponseEntity<Map<String, Object>> authenticatedGet(String url, String accessToken) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(accessToken));
        return restTemplate.exchange(url, HttpMethod.GET, entity, MAP_TYPE_REF);
    }
}
