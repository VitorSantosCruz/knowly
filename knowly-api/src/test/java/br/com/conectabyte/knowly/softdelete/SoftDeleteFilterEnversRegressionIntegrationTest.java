package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * soft-delete-default-filter SPEC requirement 5: a soft-deleted row's Envers {@code _AUD} revision
 * history remains fully reconstructable, including the revision that recorded the soft-delete
 * itself, even though {@link SoftDeleteFilter} now excludes the row from the live table by default
 * -- Hibernate {@code @Filter}s only ever apply to the live table, never {@code _AUD} tables, so
 * this is a smoke-level regression check rather than new Envers infrastructure.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterEnversRegressionIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void enversHistoryIncludesTheSoftDeleteRevisionAfterTheRowIsFilteredFromLiveQueries() {
        User user = userRepository.saveAndFlush(new User("softdelete-envers-history@example.com"));

        user.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(user);

        // The row is now excluded from live, filtered queries (via a real @Transactional service
        // call, the only context softDeleteFilter is ever enabled in)...
        assertThat(testSupportService.findUserById(user.getId())).isEmpty();

        // ...but its full revision history, including the soft-delete revision, is still there.
        var revisions =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status ->
                                        AuditReaderFactory.get(entityManager)
                                                .getRevisions(User.class, user.getId()));

        assertThat(revisions).hasSizeGreaterThanOrEqualTo(2);
    }
}
