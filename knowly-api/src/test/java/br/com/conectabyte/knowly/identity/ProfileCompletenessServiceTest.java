package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * REQ-2/REQ-6's completeness definition, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md. Updated 2026-08-02 for the
 * country-agnostic identity/address model amendment: {@code rg}/{@code rgOrgaoEmissor}/{@code
 * birthDate} are gone; {@code cpf} renamed {@code taxId}; {@code countryCode} (non-null) is now a
 * required completeness condition (mandatory-complete-profile/SPEC.md's fourth 2026-08-02 amendment
 * -- closes the appsec-flagged gap where a null {@code countryCode} would otherwise let a Brazilian
 * {@code taxId} skip checksum validation via this path).
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
        profile.setTaxId("52998224725");
        profile.setTaxIdBlindIndex("tax-id-blind-index");
        profile.setCountryCode("BR");
        return userProfileRepository.saveAndFlush(profile);
    }

    private Address completeAddress(User user) {
        Address address = new Address(user);
        address.setAddressLine1("Rua Um, 100");
        address.setAddressLine2("Centro");
        address.setCity("Sao Paulo");
        address.setStateRegion("SP");
        address.setPostalCode("01000000");
        address.setCountryCode("BR");
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
    void missingTaxIdIsIncomplete() {
        User user = user("missing-taxid@example.com");
        UserProfile profile = completeProfile(user);
        profile.setTaxId(null);
        profile.setTaxIdBlindIndex(null);
        userProfileRepository.saveAndFlush(profile);
        completeAddress(user);
        oneContact(user);

        assertThat(profileCompletenessService.isComplete(user)).isFalse();
    }

    @Test
    void missingCountryCodeIsIncomplete() {
        User user = user("missing-country-code@example.com");
        UserProfile profile = completeProfile(user);
        profile.setCountryCode(null);
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
        address.setCity("  ");
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
