package com.g4stly.templateApp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.g4stly.templateApp.services.RateLimitService;

import java.io.IOException;

/**
 * Global rate limiting filter that applies to all incoming requests.
 * Limits requests to 30 per minute per IP address by default.
 * This filter runs before authentication and applies to all endpoints.
 *
 * IP resolution: Spring resolves the real client IP via
 * forward-headers-strategy=native
 * (set in application.properties), so request.getRemoteAddr() already returns
 * the
 * correct IP even when running behind Cloudflare or another reverse proxy.
 * We do NOT read X-Forwarded-For or similar headers here directly — doing so
 * would
 * allow any client to spoof their IP and bypass the rate limiter.
 */
@Slf4j
@Component
@Order(1) // Run early in the filter chain, before authentication
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // request.getRemoteAddr() is safe here: Spring has already resolved the real IP
        // via the forward-headers-strategy=native setting when running behind a trusted
        // proxy.
        String clientIp = request.getRemoteAddr();

        // Check if the IP address has exceeded the global rate limit
        if (rateLimitService.isGlobalRateLimitExceeded(clientIp)) {
            log.warn("Global rate limit exceeded for IP: {}, URI: {}", clientIp, request.getRequestURI());
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\",\"message\":\"Rate limit exceeded: 30 requests per minute\"}");
            return;
        }

        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }
}
