package  com.g4stly.templateApp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import  com.g4stly.templateApp.config.JwtConfig;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Autowired
    private JwtConfig jwtConfig;

    // Change return type from Key to SecretKey
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, Long userId, String userType) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("userType", userType);
        
        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(String username, Long userId, String userType, Integer adminLevel) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("userType", userType);
        if ("admin".equals(userType) && adminLevel != null) {
            claimsMap.put("adminLevel", adminLevel);
        }
        
        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(String username, Integer userId, String userType) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("userType", userType);
        
        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(String username, Integer userId, String userType, Integer adminLevel) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("userType", userType);
        if ("admin".equals(userType) && adminLevel != null) {
            claimsMap.put("adminLevel", adminLevel);
        }
        
        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }
    
    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(getSigningKey())
                .compact();
    }
    
    public Long getAccessTokenExpiration() {
        return jwtConfig.getExpirationInSeconds();
    }
    
    public Long getRefreshTokenExpiration() {
        return jwtConfig.getRefreshExpirationInSeconds();
    }
    
    public Long getRefreshTokenExpirationDays() {
        // Convert milliseconds to days
        return jwtConfig.getRefreshExpiration() / (1000 * 60 * 60 * 24);
    }
    
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtConfig.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            
            // Explicit expiration check
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Integer extractUserId(String token) {
        return extractAllClaims(token).get("userId", Integer.class);
    }
    
    public Long extractUserIdAsLong(String token) {
        Object userId = extractAllClaims(token).get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }
    
    public String extractUserType(String token) {
        return extractAllClaims(token).get("userType", String.class);
    }
    
    public Integer extractAdminLevel(String token) {
        return extractAllClaims(token).get("adminLevel", Integer.class);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}