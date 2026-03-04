package com.g4stly.templateApp.security;

import com.g4stly.templateApp.services.UserActivityLogger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that automatically logs every authenticated USER request to non-auth,
 * non-admin paths.  Logging is delegated to {@link UserActivityLogger} which is
 * {@code @Async}, so this interceptor never blocks the request thread.
 *
 * Excluded paths (configured in WebMvcConfig):
 *   /api/v1/auth/**  — already logged explicitly per event in AuthService
 *   /api/v1/admin/** — covered by AdminActivityLogger
 *   /api/v1/health/**, /actuator/**, /v3/api-docs/**, /swagger-ui/**
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserActivityLoggingInterceptor implements HandlerInterceptor {

    private final UserActivityLogger userActivityLogger;

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // Skip re-dispatches (e.g. async/forward/error dispatches)
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return;
        }

        // Only log requests handled by a controller method
        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return;
            }

            // Only log USER role — admins have their own logger
            boolean isUser = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_USER".equals(a.getAuthority()));
            if (!isUser) {
                return;
            }

            Object details = authentication.getDetails();
            if (!(details instanceof Long)) {
                return;
            }
            Long userId = (Long) details;

            String method = request.getMethod();
            String path   = request.getRequestURI();
            String action = method + " " + path;

            boolean success = response.getStatus() < 400;

            Map<String, Object> logDetails = new HashMap<>();
            logDetails.put("responseStatus", response.getStatus());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isBlank()) {
                logDetails.put("query", queryString);
            }
            if (ex != null) {
                logDetails.put("error", ex.getMessage());
            }

            String failureReason = success ? null : "HTTP " + response.getStatus();

            userActivityLogger.logActivity(
                    userId, "user", action,
                    null, null,
                    logDetails, success, failureReason,
                    request
            );

        } catch (Exception e) {
            // Never let interceptor failures bubble up to the response
            log.debug("UserActivityLoggingInterceptor: failed to log request", e);
        }
    }
}
