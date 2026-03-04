package com.g4stly.templateApp.services;

import com.g4stly.templateApp.models.User;
import com.g4stly.templateApp.repos.RefreshTokenRepository;
import com.g4stly.templateApp.repos.UserRepository;
import com.g4stly.templateApp.services.scheduled.AccountCleanupScheduledService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCleanupScheduledService Unit Tests")
class AccountCleanupScheduledServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ImageUploadService imageUploadService;

    @InjectMocks
    private AccountCleanupScheduledService accountCleanupScheduledService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountCleanupScheduledService, "gracePeriodDays", 30);
    }

    private User buildExpiredUser(Long id, String profilePictureUrl) {
        User user = new User();
        user.setId(id);
        user.setUsername("someUser");
        user.setEmail("some@test.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhone("+905551234567");
        user.setBio("Some bio");
        user.setProfilePicture(profilePictureUrl);
        user.setPasswordHash("oldHash");
        user.setSalt("oldSalt");
        user.setIsActive(false);
        user.setDeactivatedAt(LocalDateTime.now().minusDays(31)); // beyond grace period
        return user;
    }

    // ─── no expired accounts ─────────────────────────────────────────────────

    @Test
    @DisplayName("anonymiseExpiredAccounts → does nothing when no expired accounts exist")
    void anonymiseExpiredAccounts_noExpiredAccounts_doesNothing() {
        when(userRepository.findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(any()))
                .thenReturn(Collections.emptyList());

        accountCleanupScheduledService.anonymiseExpiredAccounts();

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllUserTokens(anyLong(), anyString());
        verifyNoInteractions(imageUploadService);
    }

    // ─── expired account with profile picture ────────────────────────────────

    @Test
    @DisplayName("anonymiseExpiredAccounts → anonymises PII, deletes image, revokes tokens")
    void anonymiseExpiredAccounts_anonymisesPiiAndRevokesTokens() {
        String profilePicUrl = "https://cdn.example.com/users/1/profile.jpg";
        User user = buildExpiredUser(1L, profilePicUrl);

        when(userRepository.findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(any()))
                .thenReturn(List.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountCleanupScheduledService.anonymiseExpiredAccounts();

        // PII overwritten
        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();

        assertThat(saved.getUsername()).isEqualTo("deleted_1");
        assertThat(saved.getEmail()).isEqualTo("deleted_1@deleted.invalid");
        assertThat(saved.getFirstName()).isNull();
        assertThat(saved.getLastName()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getProfilePicture()).isNull();
        assertThat(saved.getPasswordHash()).isEqualTo("ANONYMISED");
        assertThat(saved.getSalt()).isEqualTo("ANONYMISED");
        assertThat(saved.getIsActive()).isFalse(); // stays false

        // Image deletion and token revocation
        verify(imageUploadService).deleteImage(profilePicUrl);
        verify(refreshTokenRepository).revokeAllUserTokens(1L, "user");
    }

    // ─── expired account without profile picture ─────────────────────────────

    @Test
    @DisplayName("anonymiseExpiredAccounts → skips image deletion when no profile picture")
    void anonymiseExpiredAccounts_noProfilePicture_skipsImageDeletion() {
        User user = buildExpiredUser(2L, null);

        when(userRepository.findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(any()))
                .thenReturn(List.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountCleanupScheduledService.anonymiseExpiredAccounts();

        verifyNoInteractions(imageUploadService);
        verify(refreshTokenRepository).revokeAllUserTokens(2L, "user");
    }

    // ─── image deletion failure is non-fatal ─────────────────────────────────

    @Test
    @DisplayName("anonymiseExpiredAccounts → continues with DB anonymisation when image deletion fails")
    void anonymiseExpiredAccounts_imageDeletionFails_continuesDatabaseAnonymisation() {
        User user = buildExpiredUser(3L, "https://cdn.example.com/users/3/profile.jpg");

        when(userRepository.findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(any()))
                .thenReturn(List.of(user));
        doThrow(new RuntimeException("R2 connection timeout"))
                .when(imageUploadService).deleteImage(any());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw — image failure is non-fatal
        accountCleanupScheduledService.anonymiseExpiredAccounts();

        // DB anonymisation still happens
        verify(userRepository).save(argThat(u -> "deleted_3".equals(u.getUsername())));
        verify(refreshTokenRepository).revokeAllUserTokens(3L, "user");
    }

    // ─── multiple accounts ───────────────────────────────────────────────────

    @Test
    @DisplayName("anonymiseExpiredAccounts → processes all expired accounts in batch")
    void anonymiseExpiredAccounts_multipleAccounts_allProcessed() {
        User user1 = buildExpiredUser(10L, null);
        User user2 = buildExpiredUser(11L, null);

        when(userRepository.findByIsActiveFalseAndAdminDeactivatedFalseAndDeactivatedAtBefore(any()))
                .thenReturn(List.of(user1, user2));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountCleanupScheduledService.anonymiseExpiredAccounts();

        verify(userRepository, times(2)).save(any());
        verify(refreshTokenRepository).revokeAllUserTokens(10L, "user");
        verify(refreshTokenRepository).revokeAllUserTokens(11L, "user");
    }
}
