package com.g4stly.templateApp.services;

import com.g4stly.templateApp.models.RefreshToken;
import com.g4stly.templateApp.repos.AdminRepository;
import com.g4stly.templateApp.repos.ClientRepository;
import com.g4stly.templateApp.repos.CoachRepository;
import com.g4stly.templateApp.repos.RefreshTokenRepository;
import com.g4stly.templateApp.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private ClientRepository clientRepository;
    @Mock private CoachRepository coachRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    // =====================================================================
    // createRefreshToken()
    // =====================================================================

    @Nested
    @DisplayName("createRefreshToken()")
    class CreateRefreshToken {

        @Test
        @DisplayName("Saves token and returns the persisted entity")
        void savesAndReturnsToken() {
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit/5.0");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            RefreshToken saved = new RefreshToken(1L, "client", 30L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            RefreshToken result = refreshTokenService.createRefreshToken(1L, "client", httpRequest);

            assertThat(result).isNotNull();
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Captures User-Agent as deviceInfo")
        void capturesDeviceInfo() {
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            RefreshToken saved = new RefreshToken(2L, "coach", 30L);
            when(refreshTokenRepository.save(captor.capture())).thenReturn(saved);

            refreshTokenService.createRefreshToken(2L, "coach", httpRequest);

            RefreshToken captured = captor.getValue();
            assertThat(captured.getDeviceInfo()).isEqualTo("Mozilla/5.0");
            assertThat(captured.getIpAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Uses X-Forwarded-For header for IP when available")
        void prefersForwardedForHeader() {
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            RefreshToken saved = new RefreshToken(3L, "admin", 30L);
            when(refreshTokenRepository.save(captor.capture())).thenReturn(saved);

            refreshTokenService.createRefreshToken(3L, "admin", httpRequest);

            assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.1");
        }
    }

    // =====================================================================
    // verifyRefreshToken()
    // =====================================================================

    @Nested
    @DisplayName("verifyRefreshToken()")
    class VerifyRefreshToken {

        @Test
        @DisplayName("Null token returns empty Optional")
        void nullToken_returnsEmpty() {
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken(null);
            assertThat(result).isEmpty();
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("Empty string token returns empty Optional")
        void emptyToken_returnsEmpty() {
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("");
            assertThat(result).isEmpty();
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("Token not found in DB returns empty Optional")
        void tokenNotFound_returnsEmpty() {
            when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("unknown-token");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Revoked token triggers revokeAllUserTokens and returns empty")
        void revokedToken_revokesAllAndReturnsEmpty() {
            RefreshToken revokedToken = new RefreshToken(10L, "client", 30L);
            revokedToken.setIsRevoked(true);

            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("revoked-token");

            assertThat(result).isEmpty();
            verify(refreshTokenRepository).revokeAllUserTokens(10L, "client");
        }

        @Test
        @DisplayName("Expired token returns empty Optional")
        void expiredToken_returnsEmpty() {
            RefreshToken expiredToken = new RefreshToken(11L, "coach", 30L);
            expiredToken.setIsRevoked(false);
            // Force expiry: set expiryDate to the past
            expiredToken.setExpiryDate(LocalDateTime.now().minusDays(1));

            when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("expired-token");

            assertThat(result).isEmpty();
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("Valid token returns present Optional")
        void validToken_returnsToken() {
            RefreshToken validToken = new RefreshToken(12L, "admin", 30L);
            validToken.setIsRevoked(false);
            // expiryDate is set 30 days from now by default in constructor — no overriding needed

            when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("valid-token");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(validToken);
        }
    }

    // =====================================================================
    // rotateRefreshToken()
    // =====================================================================

    @Nested
    @DisplayName("rotateRefreshToken()")
    class RotateRefreshToken {

        @Test
        @DisplayName("Marks old token as revoked and sets lastUsedAt")
        void revokesOldToken() {
            RefreshToken oldToken = new RefreshToken(20L, "client", 30L);
            oldToken.setIsRevoked(false);

            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Agent");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");

            RefreshToken newSaved = new RefreshToken(20L, "client", 30L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newSaved);

            refreshTokenService.rotateRefreshToken(oldToken, httpRequest);

            assertThat(oldToken.getIsRevoked()).isTrue();
            assertThat(oldToken.getLastUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("Creates and returns a new token for the same user")
        void createsNewToken_forSameUser() {
            RefreshToken oldToken = new RefreshToken(21L, "coach", 30L);
            oldToken.setIsRevoked(false);

            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Agent");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(httpRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");

            RefreshToken newToken = new RefreshToken(21L, "coach", 30L);
            // save called twice: once for oldToken (revoke), once for newToken
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(oldToken).thenReturn(newToken);

            RefreshToken result = refreshTokenService.rotateRefreshToken(oldToken, httpRequest);

            assertThat(result).isNotNull();
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }
    }

    // =====================================================================
    // revokeRefreshToken()
    // =====================================================================

    @Nested
    @DisplayName("revokeRefreshToken()")
    class RevokeRefreshToken {

        @Test
        @DisplayName("Found token is revoked and method returns true")
        void found_revokesAndReturnsTrue() {
            RefreshToken token = new RefreshToken(30L, "client", 30L);
            token.setIsRevoked(false);
            when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));

            boolean result = refreshTokenService.revokeRefreshToken("some-token");

            assertThat(result).isTrue();
            assertThat(token.getIsRevoked()).isTrue();
            verify(refreshTokenRepository).save(token);
        }

        @Test
        @DisplayName("Token not found returns false")
        void notFound_returnsFalse() {
            when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

            boolean result = refreshTokenService.revokeRefreshToken("ghost-token");

            assertThat(result).isFalse();
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // =====================================================================
    // revokeAllUserTokens()
    // =====================================================================

    @Nested
    @DisplayName("revokeAllUserTokens()")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("Delegates to repository and returns row count")
        void delegatesToRepository() {
            when(refreshTokenRepository.revokeAllUserTokens(5L, "client")).thenReturn(3);

            int result = refreshTokenService.revokeAllUserTokens(5L, "client");

            assertThat(result).isEqualTo(3);
            verify(refreshTokenRepository).revokeAllUserTokens(5L, "client");
        }
    }

    // =====================================================================
    // cleanupExpiredTokens()
    // =====================================================================

    @Nested
    @DisplayName("cleanupExpiredTokens()")
    class CleanupExpiredTokens {

        @Test
        @DisplayName("Calls cleanupRevokedAndExpired and deleteByExpiryDateBefore")
        void callsBothCleanupMethods() {
            when(refreshTokenRepository.cleanupRevokedAndExpired(any(LocalDateTime.class))).thenReturn(5);

            int result = refreshTokenService.cleanupExpiredTokens();

            assertThat(result).isEqualTo(5);
            verify(refreshTokenRepository).cleanupRevokedAndExpired(any(LocalDateTime.class));
            verify(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("deleteByExpiryDateBefore is called with approximately 7 days ago")
        void deleteCalledWith7DaysAgo() {
            when(refreshTokenRepository.cleanupRevokedAndExpired(any())).thenReturn(0);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            refreshTokenService.cleanupExpiredTokens();

            verify(refreshTokenRepository).deleteByExpiryDateBefore(captor.capture());
            LocalDateTime cutoff = captor.getValue();
            LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(7);
            // Allow 5-second tolerance for test execution time
            assertThat(cutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
        }
    }
}
