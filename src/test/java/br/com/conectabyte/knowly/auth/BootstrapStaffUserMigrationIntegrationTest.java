package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class BootstrapStaffUserMigrationIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap-test@conectabyte.com";

    @Autowired private UserRepository userRepository;

    @Autowired private TenantMembershipRepository tenantMembershipRepository;

    @Autowired private Flyway flyway;

    @Test
    void createsExactlyOneBootstrapStaffUserWithNoTenantMembership() {
        Optional<User> bootstrapUser = userRepository.findByEmailIgnoreCase(BOOTSTRAP_EMAIL);

        assertThat(bootstrapUser).isPresent();
        assertThat(bootstrapUser.get().getGlobalRole()).isEqualTo(GlobalRole.STAFF);
        assertThat(bootstrapUser.get().getOneTimePasswordHash()).isNull();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(bootstrapUser.get()))
                .isEmpty();
    }

    @Test
    void reRunningMigrationsIsANoOpAndDoesNotDuplicateTheBootstrapUser() {
        flyway.migrate();

        long count =
                userRepository.findAll().stream()
                        .filter(u -> BOOTSTRAP_EMAIL.equalsIgnoreCase(u.getEmail()))
                        .count();

        assertThat(count).isEqualTo(1);
    }
}
