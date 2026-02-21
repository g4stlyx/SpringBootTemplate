package com.g4stly.templateApp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the refresh token lifecycle:
 *  4. Valid refresh token → new access token + token rotation
 *  5. Logout revokes token → subsequent refresh returns 401
 *  6. Reuse of a rotated (revoked) refresh token → 401 (replay attack prevention)
 */
@DisplayName("Refresh Token Integration")
class RefreshTokenIntegrationTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------ //
    // Test 4 — refresh with valid token returns new access token          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("refresh with valid token returns 200 with new accessToken")
    void refresh_validToken_returnsNewAccessToken() {
        createAdmin("refreshadmin", "refreshadmin@test.com", "Admin1234!", 0);
        ResponseEntity<Map<String, Object>> loginResp = login("refreshadmin", "Admin1234!", "admin");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String originalRefreshToken = extractRefreshToken(loginResp);
        assertThat(originalRefreshToken).isNotNull().isNotBlank();

        // Use refresh token to get a new access token
        Map<String, String> body = Map.of("refreshToken", originalRefreshToken);
        ResponseEntity<Map<String, Object>> refreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE_REF);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResp.getBody()).containsKey("accessToken");
        assertThat(refreshResp.getBody().get("accessToken")).isNotNull();
    }

    // ------------------------------------------------------------------ //
    // Test 5 — logout revokes token; further refresh returns 401          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("logout revokes refresh token; subsequent refresh returns 401")
    void logout_thenRefresh_returns401() {
        createAdmin("logoutadmin", "logoutadmin@test.com", "Admin1234!", 0);
        ResponseEntity<Map<String, Object>> loginResp = login("logoutadmin", "Admin1234!", "admin");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String refreshToken = extractRefreshToken(loginResp);
        assertThat(refreshToken).isNotNull().isNotBlank();

        // Logout — revoke the refresh token
        Map<String, String> logoutBody = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map<String, Object>> logoutResp = restTemplate.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutBody), MAP_TYPE_REF);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoutResp.getBody().get("success")).isEqualTo(true);

        // Attempt to refresh with the revoked token
        Map<String, String> refreshBody = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map<String, Object>> refreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshBody), MAP_TYPE_REF);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------ //
    // Test 6 — reuse of a rotated (revoked) refresh token returns 401     //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("reuse of rotated refresh token returns 401 (replay attack)")
    void reuseRotatedRefreshToken_returns401() {
        createAdmin("rotationadmin", "rotationadmin@test.com", "Admin1234!", 0);
        ResponseEntity<Map<String, Object>> loginResp = login("rotationadmin", "Admin1234!", "admin");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String firstRefreshToken = extractRefreshToken(loginResp);
        assertThat(firstRefreshToken).isNotNull().isNotBlank();

        // First refresh — rotates the token (firstRefreshToken now revoked)
        Map<String, String> body = Map.of("refreshToken", firstRefreshToken);
        ResponseEntity<Map<String, Object>> firstRefreshResp = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE_REF);
        assertThat(firstRefreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Attempt to reuse the original (now rotated/revoked) refresh token
        ResponseEntity<Map<String, Object>> reuseResp = restTemplate.exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE_REF);

        assertThat(reuseResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
