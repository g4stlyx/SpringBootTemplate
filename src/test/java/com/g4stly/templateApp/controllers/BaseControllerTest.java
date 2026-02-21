package com.g4stly.templateApp.controllers;

import com.g4stly.templateApp.security.CustomAccessDeniedHandler;
import com.g4stly.templateApp.security.GlobalRateLimitFilter;
import com.g4stly.templateApp.security.JwtAuthEntryPoint;
import com.g4stly.templateApp.security.JwtAuthFilter;
import com.g4stly.templateApp.security.JwtUtils;
import com.g4stly.templateApp.security.SensitiveEndpointAccessFilter;
import com.g4stly.templateApp.services.AuthErrorLogService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for all controller (web layer) tests.
 *
 * Provides:
 *  - @MockBean stubs for all security filter beans that SecurityConfig depends on.
 *    Without these, @WebMvcTest fails to start because SecurityConfig @Autowires them.
 *  - Helper to build an Authentication object with ROLE_ADMIN and a typed Long admin ID
 *    as `details` — matching what JwtAuthFilter sets in production.
 *
 * Every subclass must still declare @WebMvcTest(TheController.class) and
 * @AutoConfigureMockMvc(addFilters = false) on itself; neither annotation is
 * inheritable in all Spring Boot versions.
 */
@TestPropertySource(properties = {
    "recaptcha.enabled=false"  // override the ${RECAPTCHA_ENABLED:true} default
})
public abstract class BaseControllerTest {

    // ----- Security filter mocks -----
    // SecurityConfig @Autowires these. We mock the beans so the context can be
    // assembled without their real implementations being started.

    @MockitoBean protected JwtAuthFilter jwtAuthFilter;
    @MockitoBean protected JwtAuthEntryPoint jwtAuthEntryPoint;
    @MockitoBean protected CustomAccessDeniedHandler customAccessDeniedHandler;
    @MockitoBean protected GlobalRateLimitFilter globalRateLimitFilter;
    @MockitoBean protected SensitiveEndpointAccessFilter sensitiveEndpointAccessFilter;

    // ----- GlobalExceptionHandler dependencies -----
    // @RestControllerAdvice is always loaded in @WebMvcTest; its injected
    // services must be present in the context or startup will fail.

    @MockitoBean protected AuthErrorLogService authErrorLogService;
    @MockitoBean protected JwtUtils jwtUtils;

    // ----- Helpers -----

    /**
     * Creates an Authentication that satisfies hasRole('ADMIN') and returns
     * the given adminId from authentication.getDetails().
     */
    protected Authentication makeAdminAuth(Long adminId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testAdmin", null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        auth.setDetails(adminId);
        return auth;
    }
}
