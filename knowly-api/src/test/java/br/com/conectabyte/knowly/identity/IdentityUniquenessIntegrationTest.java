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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V17 migration coverage (see specify/features/identity-profile-model/PLAN.md's "Data schema"
 * section): DB-level uniqueness on cpf/rg (via blind index)/phone/address (REQ-2/2a), cnpj/
 * inscricaoEstadual (REQ-7/7a/7b), and cpf/rg stored non-plaintext at the raw column level (REQ-3),
 * all bypassing service-layer validation via direct repository/JDBC access.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class IdentityUniquenessIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BlindIndexService blindIndexService;

    @Test
    void twoUsersWithTheSameCpfBlindIndexAreRejectedAtTheDatabaseLevel() {
        String blindIndex = blindIndexService.hmac("11122233344");
        User first = new User("cpf-collision-1@example.com");
        first.setCpfBlindIndex(blindIndex);
        userRepository.saveAndFlush(first);

        User second = new User("cpf-collision-2@example.com");
        second.setCpfBlindIndex(blindIndex);

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoUsersWithTheSameRgBlindIndexAreRejectedAtTheDatabaseLevel() {
        String blindIndex = blindIndexService.hmac("998877665");
        User first = new User("rg-collision-1@example.com");
        first.setRgBlindIndex(blindIndex);
        userRepository.saveAndFlush(first);

        User second = new User("rg-collision-2@example.com");
        second.setRgBlindIndex(blindIndex);

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoUsersWithTheSamePhoneAreRejectedAtTheDatabaseLevel() {
        User first = new User("phone-collision-1@example.com");
        first.setPhone("+5511999990000");
        userRepository.saveAndFlush(first);

        User second = new User("phone-collision-2@example.com");
        second.setPhone("+5511999990000");

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoUsersWithTheSameAddressAreRejectedAtTheDatabaseLevel() {
        User first = new User("address-collision-1@example.com");
        first.setAddress("Rua Um, 100");
        userRepository.saveAndFlush(first);

        User second = new User("address-collision-2@example.com");
        second.setAddress("Rua Um, 100");

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleUsersWithUnsetIdentityFieldsCoexist() {
        userRepository.saveAndFlush(new User("unset-fields-1@example.com"));
        userRepository.saveAndFlush(new User("unset-fields-2@example.com"));

        assertThat(userRepository.findByEmailIgnoreCase("unset-fields-1@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("unset-fields-2@example.com")).isPresent();
    }

    @Test
    void cpfAndRgAreStoredNonPlaintextAtTheRawColumnLevel() {
        User user = new User("raw-column-check@example.com");
        user.setCpf("55566677788");
        user.setCpfBlindIndex(blindIndexService.hmac("55566677788"));
        user.setRg("112233445");
        user.setRgBlindIndex(blindIndexService.hmac("112233445"));
        User saved = userRepository.saveAndFlush(user);

        String rawCpf =
                jdbcTemplate.queryForObject(
                        "SELECT cpf FROM users WHERE id = ?", String.class, saved.getId());
        String rawRg =
                jdbcTemplate.queryForObject(
                        "SELECT rg FROM users WHERE id = ?", String.class, saved.getId());

        assertThat(rawCpf).isNotNull().doesNotContain("55566677788");
        assertThat(rawRg).isNotNull().doesNotContain("112233445");
    }

    @Test
    void twoTenantsWithTheSameCnpjAreRejectedAtTheDatabaseLevel() {
        Tenant first = new Tenant("Cnpj Co 1");
        first.setCnpj("11.111.111/0001-11");
        tenantRepository.saveAndFlush(first);

        Tenant second = new Tenant("Cnpj Co 2");
        second.setCnpj("11.111.111/0001-11");

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoTenantsWithTheSameInscricaoEstadualAreRejectedAtTheDatabaseLevel() {
        Tenant first = new Tenant("Inscricao Co 1");
        first.setInscricaoEstadual("111222333");
        tenantRepository.saveAndFlush(first);

        Tenant second = new Tenant("Inscricao Co 2");
        second.setInscricaoEstadual("111222333");

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleTenantsWithUnsetCnpjCoexist() {
        tenantRepository.saveAndFlush(new Tenant("No Cnpj Co 1"));
        tenantRepository.saveAndFlush(new Tenant("No Cnpj Co 2"));

        assertThat(tenantRepository.findAll())
                .extracting(Tenant::getName)
                .contains("No Cnpj Co 1", "No Cnpj Co 2");
    }
}
