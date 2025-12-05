package  com.g4stly.templateApp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${app.jwt.algorithm:HS512}")
    private String algorithm;

    @Value("${app.jwt.issuer}")
    private String issuer;

    // Getters
    public String getSecret() { return secret; }
    public long getExpiration() { return expiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
    public String getAlgorithm() { return algorithm; }
    public String getIssuer() { return issuer; }

    // Helper methods
    public long getExpirationInSeconds() { return expiration / 1000; }
    public long getRefreshExpirationInSeconds() { return refreshExpiration / 1000; }
}