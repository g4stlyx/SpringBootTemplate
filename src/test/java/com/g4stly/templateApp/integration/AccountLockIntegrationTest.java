package com.g4stly.templateApp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for brute-force account locking:
 *  7. Five consecutive failed logins lock the account; the next attempt
 *     (even with the correct password) is rejected with 401.
 */
@DisplayName("Account Lock Integration")
class AccountLockIntegrationTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------ //
    // Test 7 — 5 failed logins lock account; correct password still 401  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("5 failed logins lock account; correct password returns 401 locked")
    void fiveFailedLogins_thenCorrectPassword_returns401_accountLocked() {
        final String username = "bruteuser";
        final String correctPassword = "Correct1!";

        createVerifiedClient(username, "bruteuser@test.com", correctPassword);

        // 5 failed login attempts with wrong password
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map<String, Object>> failResp = login(username, "WrongPass" + i + "!", "client");
            assertThat(failResp.getStatusCode())
                    .as("Attempt %d should return 401", i + 1)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt with correct password — account must now be locked
        ResponseEntity<Map<String, Object>> lockedResp = login(username, correctPassword, "client");

        assertThat(lockedResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(lockedResp.getBody()).containsKey("message");
        assertThat(lockedResp.getBody().get("message").toString())
                .containsIgnoringCase("locked");
    }
}
