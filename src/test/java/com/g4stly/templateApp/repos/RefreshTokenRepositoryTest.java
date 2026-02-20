package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("RefreshTokenRepository")
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshToken buildToken(Long userId, String userType, boolean revoked, long expiryDays) {
        RefreshToken t = new RefreshToken(userId, userType, expiryDays);
        t.setIsRevoked(revoked);
        return t;
    }

    private RefreshToken buildTokenWithIp(Long userId, String userType, boolean revoked, long expiryDays, String ip) {
        RefreshToken t = buildToken(userId, userType, revoked, expiryDays);
        t.setIpAddress(ip);
        return t;
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        refreshTokenRepository.saveAll(List.of(
            buildTokenWithIp(1L, "client", false, 30, "192.168.1.1"),  // active
            buildTokenWithIp(1L, "client", true,  30, "192.168.1.1"),  // revoked
            buildTokenWithIp(2L, "coach",  false, 30, "10.0.0.1"),     // active
            buildToken(3L, "admin",  false, -1)                         // expired
        ));
    }

    // -----------------------------------------------------------------------
    // Basic lookups
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByToken")
    class FindByTokenTests {

        @Test
        @DisplayName("returns token by value")
        void found() {
            String token = refreshTokenRepository.findAll().get(0).getToken();
            Optional<RefreshToken> result = refreshTokenRepository.findByToken(token);
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for unknown token value")
        void notFound() {
            assertThat(refreshTokenRepository.findByToken("bad-token-value")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndUserType")
    class FindByUserTests {

        @Test
        @DisplayName("returns all tokens (revoked + active) for user")
        void returnsAllUserTokens() {
            List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndUserType(1L, "client");
            assertThat(tokens).hasSize(2);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void returnsEmptyForUnknownUser() {
            List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndUserType(99L, "client");
            assertThat(tokens).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndUserTypeAndIsRevokedFalse")
    class FindActiveUserTokensTests {

        @Test
        @DisplayName("returns only non-revoked tokens for user")
        void returnsOnlyActive() {
            List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndUserTypeAndIsRevokedFalse(1L, "client");
            assertThat(tokens).hasSize(1);
            assertThat(tokens).allMatch(t -> !t.getIsRevoked());
        }

        @Test
        @DisplayName("returns empty when all user tokens are revoked")
        void returnsEmptyWhenAllRevoked() {
            refreshTokenRepository.findByUserIdAndUserType(1L, "client")
                    .forEach(t -> { t.setIsRevoked(true); refreshTokenRepository.save(t); });
            List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndUserTypeAndIsRevokedFalse(1L, "client");
            assertThat(tokens).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Delete operations
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByToken")
    class DeleteByTokenTests {

        @Test
        @DisplayName("deletes a specific token by value")
        void deletesToken() {
            String token = refreshTokenRepository.findAll().get(0).getToken();
            long countBefore = refreshTokenRepository.count();
            refreshTokenRepository.deleteByToken(token);
            assertThat(refreshTokenRepository.count()).isEqualTo(countBefore - 1);
            assertThat(refreshTokenRepository.findByToken(token)).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByExpiryDateBefore")
    class DeleteExpiredTests {

        @Test
        @DisplayName("deletes tokens whose expiry date is in the past")
        void deletesExpiredTokens() {
            long countBefore = refreshTokenRepository.count();
            refreshTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
            // One expired token (expiryDays=-1) should be removed
            assertThat(refreshTokenRepository.count()).isEqualTo(countBefore - 1);
        }

        @Test
        @DisplayName("does not delete tokens that are still valid")
        void keepsValidTokens() {
            refreshTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now().minusDays(60));
            // All tokens have expiryDate >= now-1day, so none deleted for cutoff 60 days ago
            assertThat(refreshTokenRepository.count()).isEqualTo(4L);
        }
    }

    // -----------------------------------------------------------------------
    // Bulk update / cleanup queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("revokeAllUserTokens")
    class RevokeAllTests {

        @Test
        @DisplayName("marks all active tokens for user as revoked")
        void revokesAllActive() {
            int affected = refreshTokenRepository.revokeAllUserTokens(1L, "client");
            // User 1/client has 1 non-revoked token
            assertThat(affected).isEqualTo(1);
            List<RefreshToken> remaining = refreshTokenRepository.findByUserIdAndUserTypeAndIsRevokedFalse(1L, "client");
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("returns 0 when user has no active tokens")
        void returnsZeroWhenNoneActive() {
            int affected = refreshTokenRepository.revokeAllUserTokens(99L, "client");
            assertThat(affected).isEqualTo(0);
        }

        @Test
        @DisplayName("does not affect tokens of other users")
        void doesNotAffectOtherUsers() {
            refreshTokenRepository.revokeAllUserTokens(1L, "client");
            List<RefreshToken> coachTokens = refreshTokenRepository.findByUserIdAndUserTypeAndIsRevokedFalse(2L, "coach");
            assertThat(coachTokens).hasSize(1);
        }
    }

    @Nested
    @DisplayName("cleanupRevokedAndExpired")
    class CleanupTests {

        @Test
        @DisplayName("deletes revoked and expired tokens")
        void deletesRevokedAndExpired() {
            int deleted = refreshTokenRepository.cleanupRevokedAndExpired(LocalDateTime.now());
            // 1 revoked + 1 expired = 2
            assertThat(deleted).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("keeps active non-expired tokens")
        void keepsActiveTokens() {
            refreshTokenRepository.cleanupRevokedAndExpired(LocalDateTime.now());
            List<RefreshToken> remaining = refreshTokenRepository.findAll();
            assertThat(remaining).allMatch(t -> !t.getIsRevoked() && !t.isExpired());
        }
    }

    // -----------------------------------------------------------------------
    // Count queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("count queries")
    class CountTests {

        @Test
        @DisplayName("countByUserIdAndUserType returns all tokens for user")
        void countByUser() {
            long count = refreshTokenRepository.countByUserIdAndUserType(1L, "client");
            assertThat(count).isEqualTo(2L);
        }

        @Test
        @DisplayName("countAllActiveTokens counts non-revoked, non-expired")
        void countActiveTokens() {
            long count = refreshTokenRepository.countAllActiveTokens(LocalDateTime.now());
            // 2 active tokens (userId=1 non-revoked + userId=2 coach); expired one excluded
            assertThat(count).isEqualTo(2L);
        }
    }

    // -----------------------------------------------------------------------
    // Filter query
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findWithFilters")
    class FilterTests {

        @Test
        @DisplayName("filters by userType")
        void filterByUserType() {
            List<RefreshToken> result = refreshTokenRepository.findWithFilters("coach", null, null, null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("filters by isRevoked=true")
        void filterByRevoked() {
            List<RefreshToken> result = refreshTokenRepository.findWithFilters(null, null, true, null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsRevoked()).isTrue();
        }

        @Test
        @DisplayName("filters by ipAddress")
        void filterByIp() {
            List<RefreshToken> result = refreshTokenRepository.findWithFilters(null, null, null, "10.0.0.1");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns all when all filters are null")
        void returnsAllWhenNoFilters() {
            List<RefreshToken> result = refreshTokenRepository.findWithFilters(null, null, null, null);
            assertThat(result).hasSize(4);
        }
    }
}
