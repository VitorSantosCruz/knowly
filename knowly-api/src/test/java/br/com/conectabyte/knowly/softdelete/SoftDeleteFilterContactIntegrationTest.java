package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.Contact;
import br.com.conectabyte.knowly.identity.ContactRepository;
import br.com.conectabyte.knowly.identity.ContactType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code Contact}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterContactIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedContactWithNoPerQueryOptIn() {
        User user = userRepository.saveAndFlush(new User("soft-delete-filter-contact@example.com"));
        Contact live =
                contactRepository.saveAndFlush(
                        new Contact(user, ContactType.EMAIL, "live@example.com", "Live", true));
        Contact deleted =
                contactRepository.saveAndFlush(
                        new Contact(
                                user, ContactType.EMAIL, "deleted@example.com", "Deleted", false));
        deleted.setDeletedAt(Instant.now());
        contactRepository.saveAndFlush(deleted);

        var found = testSupportService.findContactsByUser(user);

        assertThat(found).extracting(Contact::getId).containsExactly(live.getId());
    }
}
