package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * role-permission-revoke REQ-3 (TASKS.md task 7-8): a permission revoked from a {@code
 * GlobalAccessGroup} must stop counting toward a member's effective permissions -- guards against
 * the read path silently ignoring the soft-delete this feature introduces.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class GlobalPermissionServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;
    @Autowired private UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
    @Autowired private GlobalPermissionService globalPermissionService;

    @Test
    void effectivePermissionsExcludesARevokedGlobalAccessGroupPermission() {
        User user = userRepository.saveAndFlush(new User("revoked-global-perm@example.com"));
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Revoked Perm Group"));
        GlobalAccessGroupPermission permission =
                globalAccessGroupPermissionRepository.saveAndFlush(
                        new GlobalAccessGroupPermission(group, GlobalPermission.STAFF_USER_CREATE));
        userGlobalAccessGroupRepository.saveAndFlush(new UserGlobalAccessGroup(user, group));
        permission.setDeletedAt(Instant.now());
        globalAccessGroupPermissionRepository.saveAndFlush(permission);

        assertThat(globalPermissionService.effectivePermissions(user))
                .doesNotContain(GlobalPermission.STAFF_USER_CREATE);
    }
}
