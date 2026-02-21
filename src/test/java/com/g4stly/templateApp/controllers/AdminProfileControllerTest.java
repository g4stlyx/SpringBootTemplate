package com.g4stly.templateApp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.dto.profile.AdminProfileDTO;
import com.g4stly.templateApp.dto.profile.ChangePasswordRequest;
import com.g4stly.templateApp.dto.profile.UpdateAdminProfileRequest;
import com.g4stly.templateApp.security.AdminLevelAuthorizationService;
import com.g4stly.templateApp.services.AdminProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProfileControllerTest extends BaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AdminProfileService adminProfileService;
    @MockitoBean AdminLevelAuthorizationService adminLevelAuthorizationService;

    @BeforeEach
    void setUp() {
        when(adminLevelAuthorizationService.isLevel0Or1Or2()).thenReturn(true);
    }

    private AdminProfileDTO sampleProfile() {
        AdminProfileDTO dto = new AdminProfileDTO();
        dto.setId(1L);
        dto.setUsername("testAdmin");
        dto.setEmail("admin@test.com");
        dto.setLevel(1);
        dto.setIsActive(true);
        return dto;
    }

    // ─── GET /api/v1/admin/profile ────────────────────────────────────────────

    @Test
    @DisplayName("GET /profile → 200 with profile DTO")
    void getAdminProfile_returns200() throws Exception {
        when(adminProfileService.getAdminProfile(1L)).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/admin/profile")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testAdmin"));
    }

    // ─── PUT /api/v1/admin/profile ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /profile → 200 with updated DTO")
    void updateAdminProfile_returns200() throws Exception {
        UpdateAdminProfileRequest req = new UpdateAdminProfileRequest();
        req.setFirstName("Updated");
        when(adminProfileService.updateAdminProfile(any(), any())).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/admin/profile")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─── POST /api/v1/admin/profile/change-password ───────────────────────────

    @Test
    @DisplayName("POST /change-password → 200 with success=true and correct message")
    void changePassword_returns200WithSuccessMessage() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest("oldPassword", "NewPassword1!", "NewPassword1!");
        doNothing().when(adminProfileService).changePassword(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/change-password")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    // ─── POST /api/v1/admin/profile/{id}/deactivate ───────────────────────────

    @Test
    @DisplayName("POST /{id}/deactivate → 200 with success=true")
    void deactivateAccount_returns200WithSuccessBody() throws Exception {
        doNothing().when(adminProfileService).deactivateAccount(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/2/deactivate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin account deactivated successfully"));
    }

    // ─── POST /api/v1/admin/profile/{id}/reactivate ───────────────────────────

    @Test
    @DisplayName("POST /{id}/reactivate → 200 with success=true")
    void reactivateAccount_returns200WithSuccessBody() throws Exception {
        doNothing().when(adminProfileService).reactivateAccount(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/2/reactivate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin account reactivated successfully"));
    }
}
