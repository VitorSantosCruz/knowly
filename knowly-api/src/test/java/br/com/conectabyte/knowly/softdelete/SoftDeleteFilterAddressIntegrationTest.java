package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.Address;
import br.com.conectabyte.knowly.identity.AddressRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code Address}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterAddressIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedAddressWithNoPerQueryOptIn() {
        User liveUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-address-live@example.com"));
        Address liveAddress = newAddress(liveUser);
        addressRepository.saveAndFlush(liveAddress);

        User deletedUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-address-deleted@example.com"));
        Address deletedAddress = newAddress(deletedUser);
        deletedAddress.setDeletedAt(Instant.now());
        addressRepository.saveAndFlush(deletedAddress);

        assertThat(testSupportService.findAddressByUserId(liveUser.getId())).isPresent();
        assertThat(testSupportService.findAddressByUserId(deletedUser.getId())).isEmpty();
    }

    private Address newAddress(User user) {
        Address address = new Address(user);
        address.setAddressLine1("123 Main St");
        address.setCity("Metropolis");
        address.setPostalCode("00000-000");
        address.setCountryCode("BR");
        return address;
    }
}
