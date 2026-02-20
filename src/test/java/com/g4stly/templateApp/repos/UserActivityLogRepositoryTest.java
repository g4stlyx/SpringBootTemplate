package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.UserActivityLog;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserActivityLogRepository")
class UserActivityLogRepositoryTest {

    @Autowired
    private UserActivityLogRepository logRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    private UserActivityLog buildLog(Long userId, String userType, String action,
                                     boolean success, String ipAddress) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setUserType(userType);
        log.setAction(action);
        log.setSuccess(success);
        log.setIpAddress(ipAddress);
        return log;
    }

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        logRepository.saveAll(List.of(
            buildLog(1L, "client", "LOGIN",          true,  "1.1.1.1"),
            buildLog(1L, "client", "PROFILE_UPDATE", true,  "1.1.1.1"),
            buildLog(1L, "client", "LOGIN",          false, "2.2.2.2"),  // failed attempt
            buildLog(2L, "coach",  "LOGIN",          true,  "3.3.3.3"),
            buildLog(2L, "coach",  "LOGOUT",         true,  "3.3.3.3")
        ));
    }

    // -----------------------------------------------------------------------
    // Find by user
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndUserType")
    class FindByUserTests {

        @Test
        @DisplayName("returns all logs for given user (paginated)")
        void paginatedForUser() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndUserTypeOrderByCreatedAtDesc(1L, "client", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns all logs for given user (list)")
        void listForUser() {
            List<UserActivityLog> logs = logRepository
                    .findByUserIdAndUserTypeOrderByCreatedAtDesc(1L, "client");
            assertThat(logs).hasSize(3);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void emptyForUnknownUser() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndUserTypeOrderByCreatedAtDesc(99L, "client", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByUserType")
    class FindByUserTypeTests {

        @Test
        @DisplayName("returns logs for given user type")
        void returnsForUserType() {
            Page<UserActivityLog> page = logRepository
                    .findByUserTypeOrderByCreatedAtDesc("coach", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }
    }

    // -----------------------------------------------------------------------
    // Find by action
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByAction")
    class FindByActionTests {

        @Test
        @DisplayName("returns logs for given action")
        void returnsForAction() {
            Page<UserActivityLog> page = logRepository
                    .findByActionOrderByCreatedAtDesc("LOGIN", PageRequest.of(0, 10));
            // 2 successful + 1 failed LOGIN
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns empty for unknown action")
        void returnsEmptyForUnknownAction() {
            Page<UserActivityLog> page = logRepository
                    .findByActionOrderByCreatedAtDesc("UNKNOWN_ACTION", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByActionAndUserType")
    class FindByActionAndUserTypeTests {

        @Test
        @DisplayName("filters by action AND user type")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByActionAndUserTypeOrderByCreatedAtDesc("LOGIN", "coach", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getContent().get(0).getUserId()).isEqualTo(2L);
        }
    }

    // -----------------------------------------------------------------------
    // Find all ordered
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByOrderByCreatedAtDesc")
    class FindAllOrderedTests {

        @Test
        @DisplayName("returns all logs paginated")
        void returnsAll() {
            Page<UserActivityLog> page = logRepository
                    .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }
    }

    // -----------------------------------------------------------------------
    // Date range queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByCreatedAtAfterOrderByCreatedAtDesc")
    class DateAfterTests {

        @Test
        @DisplayName("returns logs after given date")
        void returnsAfterDate() {
            Page<UserActivityLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }

        @Test
        @DisplayName("returns empty when all logs are before the date")
        void returnsEmptyBeforeDate() {
            Page<UserActivityLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.plusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByUserTypeAndCreatedAtAfter")
    class DateAfterByUserTypeTests {

        @Test
        @DisplayName("filters by user type and date")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByUserTypeAndCreatedAtAfterOrderByCreatedAtDesc("client", NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndUserTypeAndCreatedAtAfter")
    class DateAfterByUserTests {

        @Test
        @DisplayName("filters by user and date range")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndUserTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                            1L, "client", NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }
    }

    // -----------------------------------------------------------------------
    // Count queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("countByUserIdAndUserTypeAndCreatedAtAfter")
    class CountByUserDateTests {

        @Test
        @DisplayName("counts recent logs for user")
        void countsRecent() {
            long count = logRepository.countByUserIdAndUserTypeAndCreatedAtAfter(
                    1L, "client", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns 0 for future cutoff date")
        void returnsZeroForFutureCutoff() {
            long count = logRepository.countByUserIdAndUserTypeAndCreatedAtAfter(
                    1L, "client", NOW.plusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countByActionSince")
    class CountByActionTests {

        @Test
        @DisplayName("counts recent logs for given action")
        void countsAction() {
            long count = logRepository.countByActionSince("LOGIN", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns 0 for unknown action")
        void returnsZeroForUnknownAction() {
            long count = logRepository.countByActionSince("GHOST_ACTION", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countBySuccessAndCreatedAtAfter")
    class CountBySuccessTests {

        @Test
        @DisplayName("counts successful logs")
        void countsSuccess() {
            long count = logRepository.countBySuccessAndCreatedAtAfter(true, NOW.minusMinutes(1));
            assertThat(count).isEqualTo(4L);
        }

        @Test
        @DisplayName("counts failed logs")
        void countsFailed() {
            long count = logRepository.countBySuccessAndCreatedAtAfter(false, NOW.minusMinutes(1));
            assertThat(count).isEqualTo(1L);
        }
    }

    // -----------------------------------------------------------------------
    // Failure queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndUserTypeAndSuccessFalse")
    class FailedLogsTests {

        @Test
        @DisplayName("returns only failed logs for given user")
        void returnsFailedLogs() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndUserTypeAndSuccessFalseOrderByCreatedAtDesc(
                            1L, "client", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getContent().get(0).getSuccess()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // IP address query
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByIpAddressOrderByCreatedAtDesc")
    class FindByIpTests {

        @Test
        @DisplayName("returns logs by IP address")
        void returnsForIp() {
            Page<UserActivityLog> page = logRepository
                    .findByIpAddressOrderByCreatedAtDesc("1.1.1.1", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }
    }

    // -----------------------------------------------------------------------
    // Bulk delete
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByCreatedAtBefore")
    class DeleteBeforeTests {

        @Test
        @DisplayName("deletes logs older than given date")
        void deletesOldLogs() {
            long before = logRepository.count();
            int deleted = logRepository.deleteByCreatedAtBefore(NOW.plusMinutes(1));
            assertThat(deleted).isEqualTo((int) before);
            assertThat(logRepository.count()).isEqualTo(0L);
        }

        @Test
        @DisplayName("does not delete logs newer than the cutoff")
        void keepsNewLogs() {
            int deleted = logRepository.deleteByCreatedAtBefore(NOW.minusDays(1));
            assertThat(deleted).isEqualTo(0);
            assertThat(logRepository.count()).isEqualTo(5L);
        }
    }
}
