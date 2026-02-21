package com.g4stly.templateApp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.models.RefreshToken;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for RefreshTokenController.
 *
 * What we cover:
 *  - /refresh  → 401 when token missing, 401 when invalid, 200 with accessToken on success
 *  - /logout   → always 200 (even without a token — controller swallows exceptions)
 *  - /logout-all → 401 no header, 401 null userId, 200 with revokedTokens count
 */
@WebMvcTest(RefreshTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefreshTokenControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private AdminRepository adminRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // ================================================================
    // POST /api/v1/auth/refresh
    // ================================================================

    @Test
    void refresh_noToken_returns401WithError() throws Exception {
        // No cookie, no body → extractRefreshToken returns null
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(refreshTokenService.verifyRefreshToken("bad-token")).thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "bad-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void refresh_validAdminToken_returns200WithAccessToken() throws Exception {
        // Setup: old token → valid, rotate → new token, admin level lookup → 200
        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken("old-refresh-token");
        oldToken.setUserId(1L);
        oldToken.setUserType("admin");

        RefreshToken newToken = new RefreshToken();
        newToken.setToken("new-refresh-token");
        newToken.setUserId(1L);
        newToken.setUserType("admin");

        Admin admin = new Admin();
        admin.setId(1L);
        admin.setLevel(0);

        when(refreshTokenService.verifyRefreshToken("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(refreshTokenService.rotateRefreshToken(eq(oldToken), any())).thenReturn(newToken);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        // resolveUsername returns "admin_1" for admin type
        when(jwtUtils.generateToken(anyString(), eq(1L), eq("admin"), eq(0)))
                .thenReturn("new-access-token");
        when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    // ================================================================
    // POST /api/v1/auth/logout
    // ================================================================

    @Test
    void logout_noToken_stillReturns200() throws Exception {
        // logout always returns 200 — even without a token (clears cookie and succeeds)
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ================================================================
    // POST /api/v1/auth/logout-all
    // ================================================================

    @Test
    void logoutAll_noAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void logoutAll_nullUserIdFromToken_returns401() throws Exception {
        // Header present but extractUserIdAsLong returns null
        when(jwtUtils.extractUserIdAsLong("some-access-token")).thenReturn(null);
        when(jwtUtils.extractUserType("some-access-token")).thenReturn("admin");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer some-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid access token"));
    }

    @Test
    void logoutAll_validToken_returns200WithRevokedCount() throws Exception {
        when(jwtUtils.extractUserIdAsLong("valid-access-token")).thenReturn(1L);
        when(jwtUtils.extractUserType("valid-access-token")).thenReturn("admin");
        when(refreshTokenService.revokeAllUserTokens(1L, "admin")).thenReturn(3);

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.revokedTokens").value(3));
    }
}
