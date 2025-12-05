package com.g4stly.templateApp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.g4stly.templateApp.config.RateLimitConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    // In-memory storage for rate limiting
    private final ConcurrentMap<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();
    
    private static class RateLimitEntry {
        int count;
        long expiresAt;
        
        RateLimitEntry(int count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Check if API rate limit is exceeded for a user
     */
    public boolean isApiRateLimitExceeded(String userId) {
        String key = "api_rate_limit:" + userId;
        return isRateLimitExceeded(key, rateLimitConfig.getApiCalls(), rateLimitConfig.getApiWindow());
    }

    /**
     * Check if login rate limit is exceeded for an IP
     */
    public boolean isLoginRateLimitExceeded(String ipAddress) {
        String key = "login_rate_limit:" + ipAddress;
        return isRateLimitExceeded(key, rateLimitConfig.getLoginAttempts(), rateLimitConfig.getLoginWindow());
    }

    /**
     * Check if email verification resend rate limit is exceeded for an email
     */
    public boolean isEmailVerificationRateLimitExceeded(String email) {
        String key = "email_verification_rate_limit:" + email;
        return isRateLimitExceeded(key, rateLimitConfig.getEmailVerificationAttempts(), rateLimitConfig.getEmailVerificationWindow());
    }

    /**
     * Generic rate limiting using sliding window counter (in-memory)
     */
    private boolean isRateLimitExceeded(String key, int maxRequests, long windowMs) {
        try {
            long now = System.currentTimeMillis();
            
            // Clean up expired entries periodically
            rateLimitStore.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
            
            RateLimitEntry entry = rateLimitStore.get(key);
            
            // Check if entry exists and is not expired
            if (entry != null) {
                if (entry.expiresAt < now) {
                    // Entry expired, remove it
                    rateLimitStore.remove(key);
                    entry = null;
                } else if (entry.count >= maxRequests) {
                    // Rate limit exceeded
                    return true;
                }
            }
            
            // Increment or create counter
            if (entry == null) {
                // First request in window
                rateLimitStore.put(key, new RateLimitEntry(1, now + windowMs));
            } else {
                // Increment existing counter
                entry.count++;
            }
            
            return false; // Within limit
        } catch (Exception e) {
            // On failure, allow the request (fail open)
            return false;
        }
    }

    /**
     * Reset rate limit for a key (useful for testing or manual reset)
     */
    public void resetRateLimit(String key) {
        rateLimitStore.remove(key);
    }

    /**
     * Get remaining requests for a key
     */
    public int getRemainingRequests(String key, int maxRequests) {
        try {
            long now = System.currentTimeMillis();
            RateLimitEntry entry = rateLimitStore.get(key);
            
            if (entry == null || entry.expiresAt < now) {
                return maxRequests;
            }
            
            return Math.max(0, maxRequests - entry.count);
        } catch (Exception e) {
            return maxRequests; // On error, assume no requests made
        }
    }

    /**
     * Get TTL for a rate limit key (in milliseconds)
     */
    public long getTTL(String key) {
        try {
            long now = System.currentTimeMillis();
            RateLimitEntry entry = rateLimitStore.get(key);
            
            if (entry == null || entry.expiresAt < now) {
                return -1; // No expiration or expired
            }
            
            return entry.expiresAt - now;
        } catch (Exception e) {
            return -1; // Error or no expiration
        }
    }
}