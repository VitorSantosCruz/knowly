package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code DirectGlobalPermissionGrant}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterDirectGlobalPermissionGrantIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedDirectGlobalPermissionGrantWithNoPerQueryOptIn() {
        User user = userRepository.saveAndFlush(new User("soft-delete-filter-dgpg@example.com"));

        DirectGlobalPermissionGrant grant =
                directGlobalPermissionGrantRepository.saveAndFlush(
                        new DirectGlobalPermissionGrant(user, GlobalPermission.TENANT_ACT_AS_ANY));
        grant.setDeletedAt(Instant.now());
        directGlobalPermissionGrantRepository.saveAndFlush(grant);

        var found = testSupportService.findDirectGlobalPermissionGrantsByUser(user);

        assertThat(found).isEmpty();
    }
}
