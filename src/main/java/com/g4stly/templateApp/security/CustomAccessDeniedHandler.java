package  com.g4stly.templateApp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g4stly.templateApp.services.AuthErrorLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom handler for 403 Forbidden errors (insufficient permissions)
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomAccessDeniedHandler.class);
    
    @Autowired
    private AuthErrorLogService authErrorLogService;
    
    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        
        logger.error("Access denied error: {}", accessDeniedException.getMessage());
        
        // Extract user info
        Long userId = null;
        String username = null;
        String userType = null;
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                if (authentication.getDetails() instanceof Long) {
                    userId = (Long) authentication.getDetails();
                }
                if (authentication.getPrincipal() instanceof String) {
                    username = (String) authentication.getPrincipal();
                }
            }
            
            // Try to get from JWT token if not in context
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    if (username == null) {
                        username = jwtUtils.extractUsername(token);
                    }
                    if (userId == null) {
                        userId = jwtUtils.extractUserIdAsLong(token);
                    }
                    if (userType == null) {
                        userType = jwtUtils.extractUserType(token);
                    }
                } catch (Exception e) {
                    logger.debug("Could not extract user info from JWT token", e);
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting user info for access denied log: {}", e.getMessage());
        }
        
        // Log 403 error
        try {
            authErrorLogService.log403(
                userId,
                userType,
                username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                accessDeniedException.getMessage(),
                "Access denied to protected resource"
            );
        } catch (Exception e) {
            logger.error("Failed to log 403 error: {}", e.getMessage());
        }
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        
        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("error", "Forbidden");
        body.put("message", "Access denied. You do not have permission to access this resource.");
        body.put("path", request.getServletPath());
        body.put("timestamp", System.currentTimeMillis());
        
        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
