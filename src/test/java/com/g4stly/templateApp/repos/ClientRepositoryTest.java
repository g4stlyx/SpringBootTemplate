package com.g4stly.templateApp.repos;

import com.g4stly.templateApp.models.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ClientRepository")
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    private Client buildClient(String username, String email, boolean active, boolean emailVerified) {
        Client c = new Client();
        c.setUsername(username);
        c.setEmail(email);
        c.setPasswordHash("hash");
        c.setSalt("salt");
        c.setIsActive(active);
        c.setEmailVerified(emailVerified);
        return c;
    }

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
        clientRepository.saveAll(List.of(
            buildClient("alice",   "alice@test.com",   true,  true),
            buildClient("bob",     "bob@test.com",     true,  false),
            buildClient("charlie", "charlie@test.com", false, true),
            buildClient("diana",   "diana@test.com",   false, false)
        ));
    }

    // -----------------------------------------------------------------------
    // Basic lookups
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByUsername / findByEmail")
    class LookupTests {

        @Test
        @DisplayName("finds client by username")
        void findByUsername_found() {
            Optional<Client> result = clientRepository.findByUsername("alice");
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("returns empty for unknown username")
        void findByUsername_notFound() {
            assertThat(clientRepository.findByUsername("ghost")).isEmpty();
        }

        @Test
        @DisplayName("finds client by email")
        void findByEmail_found() {
            Optional<Client> result = clientRepository.findByEmail("bob@test.com");
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("bob");
        }

        @Test
        @DisplayName("findByUsernameOrEmail matches on username")
        void findByUsernameOrEmail_matchUsername() {
            Optional<Client> result = clientRepository.findByUsernameOrEmail("alice", "noemail@x.com");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("findByUsernameOrEmail matches on email")
        void findByUsernameOrEmail_matchEmail() {
            Optional<Client> result = clientRepository.findByUsernameOrEmail("nousername", "charlie@test.com");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("existsByUsername returns true")
        void existsByUsername_true() {
            assertThat(clientRepository.existsByUsername("alice")).isTrue();
        }

        @Test
        @DisplayName("existsByUsername returns false")
        void existsByUsername_false() {
            assertThat(clientRepository.existsByUsername("ghost")).isFalse();
        }

        @Test
        @DisplayName("existsByEmail returns true")
        void existsByEmail_true() {
            assertThat(clientRepository.existsByEmail("diana@test.com")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail returns false")
        void existsByEmail_false() {
            assertThat(clientRepository.existsByEmail("nobody@x.com")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Admin filtering methods
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByIsActive (paginated)")
    class ActiveFilterTests {

        @Test
        @DisplayName("returns only active clients")
        void returnsOnlyActive() {
            Page<Client> page = clientRepository.findByIsActive(true, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(Client::getIsActive);
        }

        @Test
        @DisplayName("returns only inactive clients")
        void returnsOnlyInactive() {
            Page<Client> page = clientRepository.findByIsActive(false, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).noneMatch(Client::getIsActive);
        }

        @Test
        @DisplayName("pagination is respected")
        void paginationRespected() {
            Page<Client> page = clientRepository.findByIsActive(true, PageRequest.of(0, 1));
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("findByEmailVerified (paginated)")
    class EmailVerifiedFilterTests {

        @Test
        @DisplayName("returns only email-verified clients")
        void returnsOnlyVerified() {
            Page<Client> page = clientRepository.findByEmailVerified(true, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(Client::getEmailVerified);
        }

        @Test
        @DisplayName("returns only unverified clients")
        void returnsOnlyUnverified() {
            Page<Client> page = clientRepository.findByEmailVerified(false, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("findByIsActiveAndEmailVerified (paginated)")
    class CombinedFilterTests {

        @Test
        @DisplayName("returns active AND email-verified clients")
        void returnsActiveAndVerified() {
            Page<Client> page = clientRepository.findByIsActiveAndEmailVerified(true, true, PageRequest.of(0, 10));
            // Only alice matches both conditions
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getContent().get(0).getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("returns empty when no client matches combined filter")
        void returnsEmptyForNoMatch() {
            // active=true AND emailVerified=false → only bob
            Page<Client> page = clientRepository.findByIsActiveAndEmailVerified(true, false, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getContent().get(0).getUsername()).isEqualTo("bob");
        }
    }
}
