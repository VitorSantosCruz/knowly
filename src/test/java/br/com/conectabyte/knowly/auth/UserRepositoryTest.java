package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;

    @Autowired private EntityManager entityManager;

    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void findsUserByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("Someone@Example.com"));

        Optional<User> found = userRepository.findByEmailIgnoreCase("someone@example.com");

        assertThat(found).isPresent();
    }

    @Test
    void setsAuditFieldsOnCreate() {
        User saved = userRepository.saveAndFlush(new User("audited@example.com"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
    }

    @Test
    void recordsEnversRevisionOnChange() {
        User saved = userRepository.saveAndFlush(new User("history@example.com"));

        saved.setOneTimePasswordHash("hash");
        userRepository.saveAndFlush(saved);

        var revisions =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status ->
                                        AuditReaderFactory.get(entityManager)
                                                .getRevisions(User.class, saved.getId()));

        assertThat(revisions).hasSizeGreaterThanOrEqualTo(2);
    }
}
