package com.g4stly.templateApp.services;

import com.g4stly.templateApp.config.RateLimitConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService Unit Tests")
class RateLimitServiceTest {

    @Mock
    private RateLimitConfig rateLimitConfig;

    @InjectMocks
    private RateLimitService rateLimitService;

    // =====================================================================
    // Login Rate Limit
    // =====================================================================

    @Nested
    @DisplayName("isLoginRateLimitExceeded()")
    class LoginRateLimit {

        @BeforeEach
        void setupConfig() {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(3);
            when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L); // 1 min
            // reset the key before each test
            rateLimitService.resetRateLimit("login_rate_limit:1.2.3.4");
        }

        @Test
        @DisplayName("First N requests are within limit")
        void withinLimit_returnsFalse() {
            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
        }

        @Test
        @DisplayName("Exceeds limit after max attempts")
        void exceedsLimit_returnsTrue() {
            // consume all 3 slots
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");

            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isTrue();
        }

        @Test
        @DisplayName("Different IPs have independent counters")
        void differentIps_areIndependent() {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(1);
            rateLimitService.resetRateLimit("login_rate_limit:9.9.9.9");
            rateLimitService.resetRateLimit("login_rate_limit:8.8.8.8");

            rateLimitService.isLoginRateLimitExceeded("9.9.9.9"); // consume slot
            assertThat(rateLimitService.isLoginRateLimitExceeded("9.9.9.9")).isTrue();
            assertThat(rateLimitService.isLoginRateLimitExceeded("8.8.8.8")).isFalse();
        }

        @Test
        @DisplayName("resetRateLimit clears the counter")
        void reset_clearsCounter() {
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");
            rateLimitService.isLoginRateLimitExceeded("1.2.3.4");
            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isTrue();

            rateLimitService.resetRateLimit("login_rate_limit:1.2.3.4");

            assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
        }

        @Test
        @DisplayName("Expired window resets automatically")
        void expiredWindow_resetsCounter() throws InterruptedException {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(1);
            when(rateLimitConfig.getLoginWindow()).thenReturn(50L); // 50 ms window
            rateLimitService.resetRateLimit("login_rate_limit:expire.test");

            rateLimitService.isLoginRateLimitExceeded("expire.test");
            assertThat(rateLimitService.isLoginRateLimitExceeded("expire.test")).isTrue();

            Thread.sleep(100); // wait for window to expire

            // After expiry the next call should create a fresh window → not exceeded
            assertThat(rateLimitService.isLoginRateLimitExceeded("expire.test")).isFalse();
        }
    }

    // =====================================================================
    // API Rate Limit
    // =====================================================================

    @Nested
    @DisplayName("isApiRateLimitExceeded()")
    class ApiRateLimit {

        @BeforeEach
        void setupConfig() {
            when(rateLimitConfig.getApiCalls()).thenReturn(2);
            when(rateLimitConfig.getApiWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("api_rate_limit:user-42");
        }

        @Test
        @DisplayName("Within API limit returns false")
        void withinLimit_returnsFalse() {
            assertThat(rateLimitService.isApiRateLimitExceeded("user-42")).isFalse();
            assertThat(rateLimitService.isApiRateLimitExceeded("user-42")).isFalse();
        }

        @Test
        @DisplayName("Exceeds API limit returns true")
        void exceedsLimit_returnsTrue() {
            rateLimitService.isApiRateLimitExceeded("user-42");
            rateLimitService.isApiRateLimitExceeded("user-42");

            assertThat(rateLimitService.isApiRateLimitExceeded("user-42")).isTrue();
        }
    }

    // =====================================================================
    // Email Verification Rate Limit
    // =====================================================================

    @Nested
    @DisplayName("isEmailVerificationRateLimitExceeded()")
    class EmailVerificationRateLimit {

        @BeforeEach
        void setupConfig() {
            when(rateLimitConfig.getEmailVerificationAttempts()).thenReturn(2);
            when(rateLimitConfig.getEmailVerificationWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("email_verification_rate_limit:test@example.com");
        }

        @Test
        @DisplayName("Within email verification limit")
        void withinLimit_returnsFalse() {
            assertThat(rateLimitService.isEmailVerificationRateLimitExceeded("test@example.com")).isFalse();
            assertThat(rateLimitService.isEmailVerificationRateLimitExceeded("test@example.com")).isFalse();
        }

        @Test
        @DisplayName("Exceeds email verification limit")
        void exceedsLimit_returnsTrue() {
            rateLimitService.isEmailVerificationRateLimitExceeded("test@example.com");
            rateLimitService.isEmailVerificationRateLimitExceeded("test@example.com");

            assertThat(rateLimitService.isEmailVerificationRateLimitExceeded("test@example.com")).isTrue();
        }
    }

    // =====================================================================
    // Global Rate Limit
    // =====================================================================

    @Nested
    @DisplayName("isGlobalRateLimitExceeded()")
    class GlobalRateLimit {

        @BeforeEach
        void setupConfig() {
            when(rateLimitConfig.getGlobalRequests()).thenReturn(2);
            when(rateLimitConfig.getGlobalWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("global_rate_limit:5.5.5.5");
        }

        @Test
        @DisplayName("Within global limit")
        void withinLimit_returnsFalse() {
            assertThat(rateLimitService.isGlobalRateLimitExceeded("5.5.5.5")).isFalse();
            assertThat(rateLimitService.isGlobalRateLimitExceeded("5.5.5.5")).isFalse();
        }

        @Test
        @DisplayName("Exceeds global limit")
        void exceedsLimit_returnsTrue() {
            rateLimitService.isGlobalRateLimitExceeded("5.5.5.5");
            rateLimitService.isGlobalRateLimitExceeded("5.5.5.5");

            assertThat(rateLimitService.isGlobalRateLimitExceeded("5.5.5.5")).isTrue();
        }
    }

    // =====================================================================
    // getRemainingRequests()
    // =====================================================================

    @Nested
    @DisplayName("getRemainingRequests()")
    class GetRemainingRequests {

        @BeforeEach
        void setup() {
            rateLimitService.resetRateLimit("test_key");
        }

        @Test
        @DisplayName("Returns max when key not present")
        void noEntry_returnsMax() {
            assertThat(rateLimitService.getRemainingRequests("test_key", 5)).isEqualTo(5);
        }

        @Test
        @DisplayName("Decreases as requests accumulate")
        void decreasing_asCounterGrows() {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(5);
            when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("login_rate_limit:rem.test");

            rateLimitService.isLoginRateLimitExceeded("rem.test");
            rateLimitService.isLoginRateLimitExceeded("rem.test");

            int remaining = rateLimitService.getRemainingRequests("login_rate_limit:rem.test", 5);
            assertThat(remaining).isEqualTo(3);
        }

        @Test
        @DisplayName("Returns 0 when limit is exhausted")
        void exhausted_returnsZero() {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(2);
            when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("login_rate_limit:zero.test");

            rateLimitService.isLoginRateLimitExceeded("zero.test");
            rateLimitService.isLoginRateLimitExceeded("zero.test");

            assertThat(rateLimitService.getRemainingRequests("login_rate_limit:zero.test", 2)).isEqualTo(0);
        }
    }

    // =====================================================================
    // getTTL()
    // =====================================================================

    @Nested
    @DisplayName("getTTL()")
    class GetTTL {

        @Test
        @DisplayName("Unknown key returns -1")
        void unknownKey_returnsMinusOne() {
            assertThat(rateLimitService.getTTL("nonexistent_key")).isEqualTo(-1);
        }

        @Test
        @DisplayName("Active entry returns positive TTL")
        void activeEntry_returnsPositiveTTL() {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(5);
            when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
            rateLimitService.resetRateLimit("login_rate_limit:ttl.test");

            rateLimitService.isLoginRateLimitExceeded("ttl.test");

            long ttl = rateLimitService.getTTL("login_rate_limit:ttl.test");
            assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(60_000L);
        }

        @Test
        @DisplayName("Expired entry returns -1")
        void expiredEntry_returnsMinusOne() throws InterruptedException {
            when(rateLimitConfig.getLoginAttempts()).thenReturn(5);
            when(rateLimitConfig.getLoginWindow()).thenReturn(50L);
            rateLimitService.resetRateLimit("login_rate_limit:ttl.expire");

            rateLimitService.isLoginRateLimitExceeded("ttl.expire");
            Thread.sleep(100);

            assertThat(rateLimitService.getTTL("login_rate_limit:ttl.expire")).isEqualTo(-1);
        }
    }
}
