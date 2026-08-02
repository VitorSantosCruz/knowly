package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * REQ-2/REQ-6's completeness definition, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProfileCompletenessServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private ProfileCompletenessService profileCompletenessService;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private UserProfile completeProfile(User user) {
        UserProfile profile = new UserProfile(user);
        profile.setFullName("Jane Doe");
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setCpf("12345678901");
        profile.setCpfBlindIndex("cpf-blind-index");
        profile.setRg("123456");
        profile.setRgBlindIndex("rg-blind-index");
        profile.setRgOrgaoEmissor("SSP");
        return userProfileRepository.saveAndFlush(profile);
    }

    private Address completeAddress(User user) {
        Address address = new Address(user);
        address.setCep("01000-000");
        address.setLogradouro("Rua Um");
        address.setBairro("Centro");
        address.setCidade("Sao Paulo");
        address.setEstado("SP");
        address.setPais("Brasil");
        return addressRepository.saveAndFlush(address);
    }

    private void oneContact(User user) {
        contactRepository.saveAndFlush(new Contact(user, ContactType.OTHER, "value", null, false));
    }

    @Test
    void aFullyCompleteProfileIsComplete() {
        User user = user("complete@example.com");
        completeProfile(user);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isTrue();
    }

    @Test
    void missingFullNameIsIncomplete() {
        User user = user("missing-name@example.com");
        UserProfile profile = completeProfile(user);
        profile.setFullName(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingBirthDateIsIncomplete() {
        User user = user("missing-birthdate@example.com");
        UserProfile profile = completeProfile(user);
        profile.setBirthDate(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingCpfIsIncomplete() {
        User user = user("missing-cpf@example.com");
        UserProfile profile = completeProfile(user);
        profile.setCpf(null);
        profile.setCpfBlindIndex(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingRgIsIncomplete() {
        User user = user("missing-rg@example.com");
        UserProfile profile = completeProfile(user);
        profile.setRg(null);
        profile.setRgBlindIndex(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingRgOrgaoEmissorIsIncomplete() {
        User user = user("missing-rg-orgao@example.com");
        UserProfile profile = completeProfile(user);
        profile.setRgOrgaoEmissor(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingAddressRowIsIncomplete() {
        User user = user("missing-address@example.com");
        completeProfile(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void addressMissingOneRequiredColumnIsIncomplete() {
        User user = user("address-missing-column@example.com");
        completeProfile(user);
        Address address = completeAddress(user);
        address.setBairro("  ");
        addressRepository.saveAndFlush(address);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void zeroContactsIsIncomplete() {
        User user = user("zero-contacts@example.com");
        completeProfile(user);
        completeAddress(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }
}
