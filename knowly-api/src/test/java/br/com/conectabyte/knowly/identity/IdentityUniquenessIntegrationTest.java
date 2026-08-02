package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * cnpj/inscricaoEstadual (REQ-7/7a/7b) DB-level uniqueness on Tenant, bypassing service-layer
 * validation via direct repository access. The User-level cpf/rg (via blind index)/phone/address
 * uniqueness tests this class used to carry (V17 migration coverage, see
 * specify/features/identity-profile-model/PLAN.md's "Data schema" section) were removed when V19
 * (identity-profile-model-v2/TASKS.md 27) dropped those legacy User columns/fields -- the
 * cpf/rg-blind-index uniqueness they exercised is still enforced, now on user_profiles
 * (ux_user_profiles_cpf_blind_index/ux_user_profiles_rg_blind_index, see V18), not on User.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class IdentityUniquenessIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void multipleUsersWithUnsetIdentityFieldsCoexist() {
        userRepository.saveAndFlush(new User("unset-fields-1@example.com"));
        userRepository.saveAndFlush(new User("unset-fields-2@example.com"));

        assertThat(userRepository.findByEmailIgnoreCase("unset-fields-1@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("unset-fields-2@example.com")).isPresent();
    }

    // cpfAndRgAreStoredNonPlaintextAtTheRawColumnLevel was removed here for the same reason as
    // the other User-level tests above -- cpf/rg encryption-at-rest is now exercised on
    // user_profiles (see UserProfile/CpfRgEncryptionConverter and its dedicated tests).

    @Test
    void twoTenantsWithTheSameTaxIdAreRejectedAtTheDatabaseLevel() {
        String taxId = "TAXID-" + System.nanoTime();
        Tenant first = new Tenant("Tax Co 1");
        first.setTaxId(taxId);
        tenantRepository.saveAndFlush(first);

        Tenant second = new Tenant("Tax Co 2");
        second.setTaxId(taxId);

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleTenantsWithDistinctPlaceholderTaxIdsCoexist() {
        tenantRepository.saveAndFlush(new Tenant("No Tax Id Co 1"));
        tenantRepository.saveAndFlush(new Tenant("No Tax Id Co 2"));

        assertThat(tenantRepository.findAll())
                .extracting(Tenant::getName)
                .contains("No Tax Id Co 1", "No Tax Id Co 2");
    }
}
