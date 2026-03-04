package com.g4stly.templateApp.config;

import com.g4stly.templateApp.security.UserActivityLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration.
 * Registers {@link UserActivityLoggingInterceptor} to auto-log every authenticated
 * USER request, while excluding auth, admin, and infrastructure paths that are
 * either handled by dedicated loggers or don't need activity tracking.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserActivityLoggingInterceptor userActivityLoggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userActivityLoggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // Auth events are logged explicitly per event in AuthService
                        "/api/v1/auth/**",
                        // Admin actions handled by AdminActivityLogger
                        "/api/v1/admin/**",
                        // Infrastructure / docs
                        "/api/v1/health/**",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }
}
