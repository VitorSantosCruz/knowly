package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * soft-delete-default-filter SPEC requirements 1/2/3: a plain repository call against {@code User},
 * made from inside a real {@code @Transactional} service method with no per-query opt-in, must
 * never return a soft-deleted row -- proving the exclusion is a standing default, not something
 * each query has to individually remember (the {@code ChatEligibilityService}/{@code
 * ChatConversationService} incident this feature fixes).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void findByIdExcludesASoftDeletedUserWithNoPerQueryOptIn() {
        User live = userRepository.saveAndFlush(new User("soft-delete-filter-live@example.com"));
        User deleted =
                userRepository.saveAndFlush(new User("soft-delete-filter-deleted@example.com"));
        deleted.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(deleted);

        assertThat(testSupportService.findUserById(live.getId())).isPresent();
        assertThat(testSupportService.findUserById(deleted.getId())).isEmpty();
    }

    @Test
    void findAllExcludesASoftDeletedUserWithNoPerQueryOptIn() {
        User live =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-findall-live@example.com"));
        User deleted =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-findall-deleted@example.com"));
        deleted.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(deleted);

        var found = testSupportService.findAllUsers();

        assertThat(found).extracting(User::getId).contains(live.getId());
        assertThat(found).extracting(User::getId).doesNotContain(deleted.getId());
    }

    /**
     * soft-delete-default-filter SPEC requirement 7: a call site can deliberately, narrowly disable
     * the exclusion for its own query only, without weakening the default for anything else.
     */
    @Test
    void allowDeletedForOversightSeesTheSoftDeletedRowWhilePlainCallsStayFiltered() {
        User deleted =
                userRepository.saveAndFlush(new User("soft-delete-filter-oversight@example.com"));
        deleted.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(deleted);

        assertThat(testSupportService.findUserByIdIgnoringSoftDelete(deleted.getId())).isPresent();

        // The bypass must not leak into a subsequent, non-annotated call in the same test.
        assertThat(testSupportService.findUserById(deleted.getId())).isEmpty();
    }
}
