package  com.g4stly.templateApp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;
    
    //TODO: jwt auth atlayan endpointler.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        return path.startsWith("/api/v1/auth/") || 
               (path.matches("/api/v1/polyclinics") && "GET".equals(method));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Extract token from header
            String token = authHeader.substring(7);
            
            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.extractUsername(token);
                Long userId = jwtUtils.extractUserIdAsLong(token);
                String userType = jwtUtils.extractUserType(token);
                // Create authentication object with authorities
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username, 
                    null, 
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_" + userType.toUpperCase(Locale.ENGLISH)))
                );
                
                // Store user ID and type in the authentication details
                authentication.setDetails(userId);
                
                // Set userId and userType as request attributes for controllers
                request.setAttribute("userId", userId);
                request.setAttribute("userType", userType);
                
                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}" + e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}