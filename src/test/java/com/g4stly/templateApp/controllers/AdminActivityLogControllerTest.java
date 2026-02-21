package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.dto.admin.AdminActivityLogDTO;
import com.g4stly.templateApp.dto.admin.AdminActivityLogListResponse;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.services.AdminActivityLogService;
import com.g4stly.templateApp.security.AdminLevelAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for AdminActivityLogController.
 *
 * Design notes:
 *  - Class-level @PreAuthorize uses SpEL "@adminLevelAuthorizationService.isLevel0()".
 *    We @MockBean that service and stub it to return true in every test so Spring Security
 *    always passes the authorization check. The controller ALSO has a manual level check
 *    (admin.getLevel() != 0 → 403) which we exercise separately.
 *  - jwtUtils.extractUserId() returns Integer — stub returns int literal.
 *  - All requests include an Authorization header and the admin auth token.
 */
@WebMvcTest(AdminActivityLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminActivityLogControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminActivityLogService activityLogService;

    @MockitoBean
    private AdminRepository adminRepository;

    /** Required by class-level SpEL: @adminLevelAuthorizationService.isLevel0() */
    @MockitoBean
    private AdminLevelAuthorizationService adminLevelAuthorizationService;

    private static final String AUTH_HEADER = "Bearer fake-token";

    @BeforeEach
    void stubDefaults() {
        // Pass the SpEL @PreAuthorize check in every test
        when(adminLevelAuthorizationService.isLevel0()).thenReturn(true);

        // Controller calls jwtUtils.extractUserId(token.substring(7)) → Integer
        when(jwtUtils.extractUserId("fake-token")).thenReturn(1);

        // Default admin: level 0 (successfully passes manual level check)
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setLevel(0);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    // ================================================================
    // GET /api/v1/admin/activity-logs
    // ================================================================

    @Test
    void getAllLogs_level0Admin_returns200WithSuccessTrue() throws Exception {
        AdminActivityLogListResponse serviceResponse = new AdminActivityLogListResponse();
        serviceResponse.setLogs(Collections.emptyList());
        serviceResponse.setCurrentPage(0);
        serviceResponse.setTotalPages(0);
        serviceResponse.setTotalElements(0);
        serviceResponse.setPageSize(20);

        when(activityLogService.getAllActivityLogs(
                isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(20), eq("createdAt"), eq("desc"),
                eq(1L), any()))
                .thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void getAllLogs_level1Admin_returns403WithAccessDeniedMessage() throws Exception {
        // Override the default level-0 admin with a level-1 admin
        Admin level1Admin = new Admin();
        level1Admin.setId(1L);
        level1Admin.setLevel(1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(level1Admin));

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Access denied. Only Level 0 Super Admins can view activity logs."));
    }

    @Test
    void getAllLogs_serviceThrowsGenericException_returns500() throws Exception {
        when(activityLogService.getAllActivityLogs(
                any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString(),
                anyLong(), any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ================================================================
    // GET /api/v1/admin/activity-logs/{logId}
    // ================================================================

    @Test
    void getLogById_level0Admin_returns200WithSuccessTrue() throws Exception {
        AdminActivityLogDTO logDTO = new AdminActivityLogDTO();
        logDTO.setId(42L);

        when(activityLogService.getActivityLogById(eq(42L), eq(1L), any()))
                .thenReturn(logDTO);

        mockMvc.perform(get("/api/v1/admin/activity-logs/42")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @Test
    void getLogById_notFoundException_returns404WithSuccessFalse() throws Exception {
        when(activityLogService.getActivityLogById(eq(999L), eq(1L), any()))
                .thenThrow(new RuntimeException("Activity log not found"));

        mockMvc.perform(get("/api/v1/admin/activity-logs/999")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
