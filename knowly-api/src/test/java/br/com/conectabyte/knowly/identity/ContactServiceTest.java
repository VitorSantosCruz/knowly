package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.exception.ContactCapExceededException;
import br.com.conectabyte.knowly.identity.exception.InvalidContactFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** REQ-3/3a/3b, per specify/features/identity-profile-model-v2/PLAN.md. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ContactServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ContactService contactService;
    @Autowired private ContactRepository contactRepository;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    @Test
    void addingASixthContactIsRejected() {
        User user = user("contacts-cap@example.com");

        for (int i = 0; i < 5; i++) {
            contactService.addContact(user, ContactType.OTHER, "value-" + i, null, false);
        }

        assertThatThrownBy(
                        () ->
                                contactService.addContact(
                                        user, ContactType.OTHER, "value-6", null, false))
                .isInstanceOf(ContactCapExceededException.class);
    }

    @Test
    void settingASecondPrimaryOfTheSameTypeClearsTheOldOne() {
        User user = user("contacts-primary@example.com");

        Contact first =
                contactService.addContact(user, ContactType.PHONE, "+5511999990000", null, true);
        Contact second =
                contactService.addContact(user, ContactType.PHONE, "+5511988880000", null, true);

        Contact reloadedFirst = contactRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.isPrimary()).isFalse();
        assertThat(second.isPrimary()).isTrue();
    }

    @Test
    void primaryOfDifferentTypesDoNotConflict() {
        User user = user("contacts-primary-diff-type@example.com");

        Contact phone =
                contactService.addContact(user, ContactType.PHONE, "+5511999990000", null, true);
        Contact whatsapp =
                contactService.addContact(user, ContactType.WHATSAPP, "+5511999990000", null, true);

        assertThat(phone.isPrimary()).isTrue();
        assertThat(whatsapp.isPrimary()).isTrue();
    }

    @Test
    void emailFormatIsValidated() {
        User user = user("contacts-email@example.com");

        assertThatThrownBy(
                        () ->
                                contactService.addContact(
                                        user, ContactType.EMAIL, "not-an-email", null, false))
                .isInstanceOf(InvalidContactFormatException.class);

        Contact valid =
                contactService.addContact(
                        user, ContactType.EMAIL, "reachable@example.com", null, false);
        assertThat(valid.getValue()).isEqualTo("reachable@example.com");
    }

    @Test
    void phoneFormatIsValidated() {
        User user = user("contacts-phone@example.com");

        assertThatThrownBy(
                        () ->
                                contactService.addContact(
                                        user, ContactType.PHONE, "abc", null, false))
                .isInstanceOf(InvalidContactFormatException.class);

        Contact valid =
                contactService.addContact(
                        user, ContactType.PHONE, "+55 (11) 99999-0000", null, false);
        assertThat(valid.getValue()).isEqualTo("+55 (11) 99999-0000");
    }

    @Test
    void otherTypeAcceptsAnyValue() {
        User user = user("contacts-other@example.com");

        Contact contact =
                contactService.addContact(
                        user, ContactType.OTHER, "any free-form value", null, false);

        assertThat(contact.getValue()).isEqualTo("any free-form value");
    }
}
