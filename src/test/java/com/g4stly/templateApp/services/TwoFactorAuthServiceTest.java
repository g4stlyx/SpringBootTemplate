package com.g4stly.templateApp.services;

import com.g4stly.templateApp.dto.two_factor.TwoFactorSetupResponse;
import com.g4stly.templateApp.exception.ResourceNotFoundException;
import com.g4stly.templateApp.models.Admin;
import com.g4stly.templateApp.repos.AdminRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TwoFactorAuthService Unit Tests")
class TwoFactorAuthServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private GoogleAuthenticator mockGoogleAuth;

    private TwoFactorAuthService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TwoFactorAuthService();
        ReflectionTestUtils.setField(service, "adminRepository", adminRepository);
        ReflectionTestUtils.setField(service, "appName", "Test App");
        // Replace the real GoogleAuthenticator with the mock
        Field gaField = TwoFactorAuthService.class.getDeclaredField("googleAuthenticator");
        gaField.setAccessible(true);
        gaField.set(service, mockGoogleAuth);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Admin buildAdmin(Long id, String username, boolean twoFactorEnabled, String secret) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername(username);
        admin.setEmail(username + "@test.com");
        admin.setTwoFactorEnabled(twoFactorEnabled);
        admin.setTwoFactorSecret(secret);
        admin.setTwoFactorChallengeToken(null);
        admin.setTwoFactorChallengeExpiresAt(null);
        admin.setTwoFactorChallengeAttempts(0);
        return admin;
    }

    // =====================================================================
    // generateSecret()
    // =====================================================================

    @Nested
    @DisplayName("generateSecret()")
    class GenerateSecret {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.generateSecret(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Saves secret on admin but does NOT enable 2FA")
        void savesSecretButNotEnabled() {
            Admin admin = buildAdmin(1L, "adminUser", false, null);
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

            com.warrenstrange.googleauth.GoogleAuthenticatorKey mockKey =
                    mock(com.warrenstrange.googleauth.GoogleAuthenticatorKey.class);
            when(mockKey.getKey()).thenReturn("JBSWY3DPEHPK3PXP");
            when(mockGoogleAuth.createCredentials()).thenReturn(mockKey);

            when(adminRepository.save(any(Admin.class))).thenReturn(admin);

            TwoFactorSetupResponse response = service.generateSecret(1L);

            assertThat(response).isNotNull();
            assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
            // 2FA should NOT be enabled yet — only secret saved
            assertThat(admin.getTwoFactorEnabled()).isFalse();
            verify(adminRepository).save(admin);
        }
    }

    // =====================================================================
    // verifyAndEnable()
    // =====================================================================

    @Nested
    @DisplayName("verifyAndEnable()")
    class VerifyAndEnable {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.verifyAndEnable(99L, "123456"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Null secret throws IllegalStateException")
        void nullSecret_throwsIllegalState() {
            Admin admin = buildAdmin(1L, "adminUser", false, null);
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThatThrownBy(() -> service.verifyAndEnable(1L, "123456"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Non-numeric code returns false without calling authorizer")
        void nonNumericCode_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", false, "JBSWY3DPEHPK3PXP");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

            boolean result = service.verifyAndEnable(1L, "not-a-number");

            assertThat(result).isFalse();
            verify(mockGoogleAuth, never()).authorize(any(), anyInt());
        }

        @Test
        @DisplayName("Invalid TOTP code returns false without enabling 2FA")
        void invalidCode_returnsFalse_twoFaNotEnabled() {
            Admin admin = buildAdmin(1L, "adminUser", false, "JBSWY3DPEHPK3PXP");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("JBSWY3DPEHPK3PXP", 111111)).thenReturn(false);

            boolean result = service.verifyAndEnable(1L, "111111");

            assertThat(result).isFalse();
            assertThat(admin.getTwoFactorEnabled()).isFalse();
        }

        @Test
        @DisplayName("Valid TOTP code enables 2FA and returns true")
        void validCode_enablesTwoFaAndReturnsTrue() {
            Admin admin = buildAdmin(1L, "adminUser", false, "JBSWY3DPEHPK3PXP");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("JBSWY3DPEHPK3PXP", 123456)).thenReturn(true);
            when(adminRepository.save(any(Admin.class))).thenReturn(admin);

            boolean result = service.verifyAndEnable(1L, "123456");

            assertThat(result).isTrue();
            assertThat(admin.getTwoFactorEnabled()).isTrue();
            verify(adminRepository).save(admin);
        }
    }

    // =====================================================================
    // verifyCode()
    // =====================================================================

    @Nested
    @DisplayName("verifyCode()")
    class VerifyCode {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.verifyCode(99L, "123456"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("2FA not enabled throws IllegalStateException")
        void twoFaNotEnabled_throws() {
            Admin admin = buildAdmin(1L, "adminUser", false, "secret");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThatThrownBy(() -> service.verifyCode(1L, "123456"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("2FA enabled but null secret throws IllegalStateException")
        void nullSecret_throws() {
            Admin admin = buildAdmin(1L, "adminUser", true, null);
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThatThrownBy(() -> service.verifyCode(1L, "123456"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Non-numeric code returns false")
        void nonNumericCode_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThat(service.verifyCode(1L, "abc")).isFalse();
        }

        @Test
        @DisplayName("Valid TOTP code returns true")
        void validCode_returnsTrue() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 999999)).thenReturn(true);
            assertThat(service.verifyCode(1L, "999999")).isTrue();
        }

        @Test
        @DisplayName("Invalid TOTP code returns false")
        void invalidCode_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 000000)).thenReturn(false);
            assertThat(service.verifyCode(1L, "000000")).isFalse();
        }
    }

    // =====================================================================
    // verifyCodeByUsername() — challenge token flows
    // =====================================================================

    @Nested
    @DisplayName("verifyCodeByUsername()")
    class VerifyCodeByUsername {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.verifyCodeByUsername("ghost", "123456", "token"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("2FA not enabled throws IllegalStateException")
        void twoFaNotEnabled_throws() {
            Admin admin = buildAdmin(1L, "adminUser", false, "SECRET");
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            assertThatThrownBy(() -> service.verifyCodeByUsername("adminUser", "123456", "token"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Null challenge token returns false")
        void nullChallengeToken_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            // No challenge token set on admin
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            assertThat(service.verifyCodeByUsername("adminUser", "123456", "some-token")).isFalse();
        }

        @Test
        @DisplayName("Wrong challenge token returns false")
        void wrongChallengeToken_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("correct-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            assertThat(service.verifyCodeByUsername("adminUser", "123456", "wrong-token")).isFalse();
        }

        @Test
        @DisplayName("Expired challenge token returns false and clears the token")
        void expiredChallengeToken_returnsFalseAndClears() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().minusMinutes(1)); // expired
            admin.setTwoFactorChallengeAttempts(0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(adminRepository.save(any())).thenReturn(admin);

            assertThat(service.verifyCodeByUsername("adminUser", "123456", "my-token")).isFalse();

            // Token should be cleared
            assertThat(admin.getTwoFactorChallengeToken()).isNull();
        }

        @Test
        @DisplayName("5 or more failed attempts blocks even with valid challenge token")
        void fiveFailedAttempts_blocksAccess() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(5); // already maxed out
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            assertThat(service.verifyCodeByUsername("adminUser", "123456", "my-token")).isFalse();
        }

        @Test
        @DisplayName("Non-numeric code increments attempts and returns false")
        void nonNumericCode_incrementsAttempts() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(adminRepository.save(any())).thenReturn(admin);

            boolean result = service.verifyCodeByUsername("adminUser", "not-a-number", "my-token");

            assertThat(result).isFalse();
            assertThat(admin.getTwoFactorChallengeAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Invalid TOTP code increments attempts")
        void invalidTotpCode_incrementsAttempts() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 111111)).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            boolean result = service.verifyCodeByUsername("adminUser", "111111", "my-token");

            assertThat(result).isFalse();
            assertThat(admin.getTwoFactorChallengeAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Invalid TOTP on 5th attempt clears challenge token")
        void fifthFailedTotp_clearsChallengeToken() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(4); // one more will trigger clear
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 111111)).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            service.verifyCodeByUsername("adminUser", "111111", "my-token");

            assertThat(admin.getTwoFactorChallengeAttempts()).isEqualTo(5);
            assertThat(admin.getTwoFactorChallengeToken()).isNull();
        }

        @Test
        @DisplayName("Valid challenge token + valid TOTP code returns true and clears challenge")
        void validTokenAndCode_returnsTrueAndClears() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            admin.setTwoFactorChallengeToken("my-token");
            admin.setTwoFactorChallengeExpiresAt(LocalDateTime.now().plusMinutes(5));
            admin.setTwoFactorChallengeAttempts(0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 123456)).thenReturn(true);
            when(adminRepository.save(any())).thenReturn(admin);

            boolean result = service.verifyCodeByUsername("adminUser", "123456", "my-token");

            assertThat(result).isTrue();
            assertThat(admin.getTwoFactorChallengeToken()).isNull();
            assertThat(admin.getTwoFactorChallengeAttempts()).isEqualTo(0);
        }
    }

    // =====================================================================
    // disable()
    // =====================================================================

    @Nested
    @DisplayName("disable()")
    class Disable {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.disable(99L, "123456"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Invalid code throws IllegalArgumentException")
        void invalidCode_throwsIllegalArgument() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 999999)).thenReturn(false);

            assertThatThrownBy(() -> service.disable(1L, "999999"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid verification code");
        }

        @Test
        @DisplayName("Valid code disables 2FA and clears secret")
        void validCode_disablesTwoFa() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(mockGoogleAuth.authorize("SECRET", 123456)).thenReturn(true);
            when(adminRepository.save(any())).thenReturn(admin);

            service.disable(1L, "123456");

            assertThat(admin.getTwoFactorEnabled()).isFalse();
            assertThat(admin.getTwoFactorSecret()).isNull();
        }
    }

    // =====================================================================
    // isTwoFactorEnabled()
    // =====================================================================

    @Nested
    @DisplayName("isTwoFactorEnabled()")
    class IsTwoFactorEnabled {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.isTwoFactorEnabled(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns true when 2FA enabled")
        void enabled_returnsTrue() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThat(service.isTwoFactorEnabled(1L)).isTrue();
        }

        @Test
        @DisplayName("Returns false when 2FA not enabled")
        void disabled_returnsFalse() {
            Admin admin = buildAdmin(1L, "adminUser", false, null);
            when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
            assertThat(service.isTwoFactorEnabled(1L)).isFalse();
        }
    }

    // =====================================================================
    // isTwoFactorEnabledByUsername()
    // =====================================================================

    @Nested
    @DisplayName("isTwoFactorEnabledByUsername()")
    class IsTwoFactorEnabledByUsername {

        @Test
        @DisplayName("Admin not found throws ResourceNotFoundException")
        void adminNotFound_throws() {
            when(adminRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.isTwoFactorEnabledByUsername("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Returns true when 2FA enabled")
        void enabled_returnsTrue() {
            Admin admin = buildAdmin(1L, "adminUser", true, "SECRET");
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            assertThat(service.isTwoFactorEnabledByUsername("adminUser")).isTrue();
        }
    }
}
