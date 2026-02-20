package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.PasswordResetToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PasswordResetTokenRepository")
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    private PasswordResetToken buildToken(Long userId, String userType,
                                          String requestingIp, LocalDateTime expiryDate) {
        PasswordResetToken t = new PasswordResetToken(userId, userType, requestingIp);
        t.setExpiryDate(expiryDate);
        return t;
    }

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        tokenRepository.saveAll(List.of(
            buildToken(1L, "client", "1.1.1.1", NOW.plusMinutes(15)),  // valid
            buildToken(1L, "client", "1.1.1.1", NOW.minusMinutes(5)),  // expired (same user)
            buildToken(2L, "coach",  "2.2.2.2", NOW.plusMinutes(15)),  // valid
            buildToken(3L, "admin",  "3.3.3.3", NOW.minusMinutes(10))  // expired
        ));
    }

    // -----------------------------------------------------------------------
    // Basic lookups
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByToken")
    class FindByTokenTests {

        @Test
        @DisplayName("returns token by its string value")
        void found() {
            String tokenValue = tokenRepository.findAll().get(0).getToken();
            Optional<PasswordResetToken> result = tokenRepository.findByToken(tokenValue);
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for unknown token string")
        void notFound() {
            assertThat(tokenRepository.findByToken("not-a-real-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndUserType")
    class FindByUserTests {

        @Test
        @DisplayName("returns ALL tokens for user regardless of expiry")
        void returnsAllUserTokens() {
            List<PasswordResetToken> tokens = tokenRepository.findByUserIdAndUserType(1L, "client");
            // Both the valid and expired token for userId=1 should appear
            assertThat(tokens).hasSize(2);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void returnsEmptyForUnknownUser() {
            assertThat(tokenRepository.findByUserIdAndUserType(99L, "client")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByRequestingIpOrderByCreatedDateDesc")
    class FindByIpTests {

        @Test
        @DisplayName("returns the most recent token for given IP")
        void returnsMostRecent() {
            Optional<PasswordResetToken> result = tokenRepository
                    .findFirstByRequestingIpOrderByCreatedDateDesc("1.1.1.1");
            assertThat(result).isPresent();
            assertThat(result.get().getRequestingIp()).isEqualTo("1.1.1.1");
        }

        @Test
        @DisplayName("returns empty for unknown IP")
        void returnsEmptyForUnknownIp() {
            Optional<PasswordResetToken> result = tokenRepository
                    .findFirstByRequestingIpOrderByCreatedDateDesc("9.9.9.9");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByOrderByCreatedDateDesc")
    class FindFirstTest {

        @Test
        @DisplayName("returns the most recently created token globally")
        void returnsMostRecentGlobal() {
            Optional<PasswordResetToken> result = tokenRepository.findFirstByOrderByCreatedDateDesc();
            assertThat(result).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // Delete queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByExpiryDateBefore")
    class DeleteExpiredTests {

        @Test
        @DisplayName("deletes tokens that have already expired")
        void deletesExpiredTokens() {
            long countBefore = tokenRepository.count();
            tokenRepository.deleteByExpiryDateBefore(NOW);
            // 2 expired tokens (userId=1 old + userId=3)
            assertThat(tokenRepository.count()).isEqualTo(countBefore - 2);
        }

        @Test
        @DisplayName("does not delete valid tokens")
        void keepsValidTokens() {
            tokenRepository.deleteByExpiryDateBefore(NOW);
            List<PasswordResetToken> remaining = tokenRepository.findAll();
            assertThat(remaining).allMatch(t -> t.getExpiryDate().isAfter(NOW));
        }
    }

    @Nested
    @DisplayName("deleteByUserIdAndUserType")
    class DeleteByUserTests {

        @Test
        @DisplayName("deletes all tokens for a given user")
        void deletesUserTokens() {
            tokenRepository.deleteByUserIdAndUserType(1L, "client");
            assertThat(tokenRepository.findByUserIdAndUserType(1L, "client")).isEmpty();
        }

        @Test
        @DisplayName("does not affect other users")
        void doesNotAffectOthers() {
            tokenRepository.deleteByUserIdAndUserType(1L, "client");
            assertThat(tokenRepository.findByUserIdAndUserType(2L, "coach")).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // Admin panel queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByOrderByCreatedDateDesc (paginated)")
    class FindAllTests {

        @Test
        @DisplayName("returns all tokens paginated")
        void returnsAll() {
            Page<PasswordResetToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("page size is respected")
        void pageSizeRespected() {
            Page<PasswordResetToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findByUserTypeOrderByCreatedDateDesc (paginated)")
    class FindByUserTypeTests {

        @Test
        @DisplayName("returns tokens for given user type")
        void returnsForUserType() {
            Page<PasswordResetToken> page = tokenRepository
                    .findByUserTypeOrderByCreatedDateDesc("client", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns empty for unknown user type")
        void returnsEmpty() {
            Page<PasswordResetToken> page = tokenRepository
                    .findByUserTypeOrderByCreatedDateDesc("unknown", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByExpiryDateBefore (expired tokens)")
    class FindExpiredTests {

        @Test
        @DisplayName("returns tokens whose expiry date is in the past")
        void returnsExpired() {
            Page<PasswordResetToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(t -> t.getExpiryDate().isBefore(NOW));
        }

        @Test
        @DisplayName("returns empty when no tokens are expired")
        void returnsEmpty() {
            Page<PasswordResetToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW.minusDays(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }
}
