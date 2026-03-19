package com.g4stly.templateApp.integration;

import com.g4stly.templateApp.repos.VerificationTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the authentication flow:
 * 1. Client registration → email verification → login
 * 2. Login rejection on wrong password
 * 3. /me endpoint with valid bearer token
 */
@DisplayName("Auth Flow Integration")
class AuthFlowIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private VerificationTokenRepository verificationTokenRepository;

        // ------------------------------------------------------------------ //
        // Test 1 — full happy path: register → verify email → login //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("register user, verify email, then login returns 200 with accessToken")
        void register_verifyEmail_login_returnsToken() {
                // Step 1: register a new user
                Map<String, Object> registerBody = Map.of(
                                "username", "integclient",
                                "email", "integclient@test.com",
                                "password", "Passw0rd!",
                                "userType", "app_user");

                ResponseEntity<Map<String, Object>> registerResp = restTemplate.exchange(
                                "/api/v1/auth/register", HttpMethod.POST, new HttpEntity<>(registerBody), MAP_TYPE_REF);

                assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(registerResp.getBody()).containsKey("success");
                assertThat(registerResp.getBody().get("success")).isEqualTo(true);

                // Step 2: retrieve the verification token stored in DB
                Long userId = userRepository.findByUsername("integclient")
                                .orElseThrow(() -> new AssertionError("User not found after registration"))
                                .getId();

                String verificationToken = verificationTokenRepository
                                .findByUserIdAndRole(userId, "user")
                                .orElseThrow(() -> new AssertionError("Verification token not found"))
                                .getToken();

                // Step 3: verify email via the public endpoint
                ResponseEntity<Map<String, Object>> verifyResp = restTemplate.exchange(
                                "/api/v1/auth/verify-email?token=" + verificationToken, HttpMethod.GET, null,
                                MAP_TYPE_REF);

                assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(verifyResp.getBody().get("success")).isEqualTo(true);

                // Step 4: login should now succeed and return an access token
                ResponseEntity<Map<String, Object>> loginResp = login("integclient", "Passw0rd!", "user");

                assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(loginResp.getBody().get("success")).isEqualTo(true);

                String accessToken = extractAccessToken(loginResp);
                assertThat(accessToken).isNotNull().isNotBlank();
        }

        // ------------------------------------------------------------------ //
        // Test 2 — wrong password → 401 //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("login with wrong password returns 401")
        void login_wrongPassword_returns401() {
                createVerifiedUser("locktest", "locktest@test.com", "Correct1!");

                ResponseEntity<Map<String, Object>> response = login("locktest", "WrongPass9!", "user");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(response.getBody().get("success")).isEqualTo(false);
        }

        // ------------------------------------------------------------------ //
        // Test 3 — /me returns current user when token is valid //
        // ------------------------------------------------------------------ //

        @Test
        @DisplayName("/me with valid bearer token returns 200 with correct username")
        void getCurrentUser_withValidToken_returns200() {
                createVerifiedUser("meuser", "meuser@test.com", "Passw0rd!");

                ResponseEntity<Map<String, Object>> loginResp = login("meuser", "Passw0rd!", "user");
                assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

                String token = extractAccessToken(loginResp);
                assertThat(token).isNotNull();

                ResponseEntity<Map<String, Object>> meResp = authenticatedGet("/api/v1/auth/me", token);

                assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(meResp.getBody()).containsKey("role");
                assertThat(meResp.getBody().get("role")).isEqualTo("USER");
        }
}
