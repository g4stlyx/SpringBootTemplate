package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.VerificationToken;
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
@DisplayName("VerificationTokenRepository")
class VerificationTokenRepositoryTest {

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    /** Saves a token with overridden expiryDate for expiry tests. */
    private VerificationToken buildToken(Long userId, String userType, LocalDateTime expiryDate) {
        VerificationToken t = new VerificationToken(userId, userType);
        t.setExpiryDate(expiryDate);
        return t;
    }

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        tokenRepository.saveAll(List.of(
            buildToken(1L, "client", NOW.plusHours(24)),   // valid
            buildToken(2L, "coach",  NOW.plusHours(24)),   // valid
            buildToken(3L, "client", NOW.minusHours(1)),   // expired
            buildToken(4L, "admin",  NOW.minusHours(2))    // expired
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
            Optional<VerificationToken> result = tokenRepository.findByToken(tokenValue);
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
        @DisplayName("returns token for known user")
        void found() {
            Optional<VerificationToken> result = tokenRepository.findByUserIdAndUserType(1L, "client");
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void notFound() {
            Optional<VerificationToken> result = tokenRepository.findByUserIdAndUserType(99L, "client");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByUserTypeOrderByCreatedDateDesc")
    class FindFirstByUserTypeTests {

        @Test
        @DisplayName("returns the most recent token for user type")
        void returnsLatest() {
            // Save a second client token
            VerificationToken newer = buildToken(5L, "client", NOW.plusHours(24));
            tokenRepository.save(newer);

            Optional<VerificationToken> result = tokenRepository.findFirstByUserTypeOrderByCreatedDateDesc("client");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for user type with no tokens")
        void returnsEmpty() {
            Optional<VerificationToken> result = tokenRepository.findFirstByUserTypeOrderByCreatedDateDesc("unknown_type");
            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Delete queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByUserIdAndUserType")
    class DeleteTests {

        @Test
        @DisplayName("deletes token for given user")
        void deletesUserToken() {
            long countBefore = tokenRepository.count();
            tokenRepository.deleteByUserIdAndUserType(1L, "client");
            assertThat(tokenRepository.count()).isEqualTo(countBefore - 1);
            assertThat(tokenRepository.findByUserIdAndUserType(1L, "client")).isEmpty();
        }

        @Test
        @DisplayName("does not delete tokens of other users")
        void doesNotDeleteOthers() {
            tokenRepository.deleteByUserIdAndUserType(1L, "client");
            assertThat(tokenRepository.findByUserIdAndUserType(2L, "coach")).isPresent();
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
            Page<VerificationToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("page size is respected")
        void pageSizeRespected() {
            Page<VerificationToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByUserTypeOrderByCreatedDateDesc (paginated)")
    class FindByUserTypeTests {

        @Test
        @DisplayName("returns tokens for given user type")
        void returnsForUserType() {
            Page<VerificationToken> page = tokenRepository
                    .findByUserTypeOrderByCreatedDateDesc("client", PageRequest.of(0, 10));
            // userId 1 and userId 3 are both "client"
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns empty for unknown user type")
        void returnsEmpty() {
            Page<VerificationToken> page = tokenRepository
                    .findByUserTypeOrderByCreatedDateDesc("unknown", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByExpiryDateBefore (expired tokens)")
    class FindExpiredTests {

        @Test
        @DisplayName("returns tokens that have expired")
        void returnsExpired() {
            Page<VerificationToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(t -> t.getExpiryDate().isBefore(NOW));
        }

        @Test
        @DisplayName("returns empty when no tokens are expired")
        void returnsEmpty() {
            Page<VerificationToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW.minusDays(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }
}
