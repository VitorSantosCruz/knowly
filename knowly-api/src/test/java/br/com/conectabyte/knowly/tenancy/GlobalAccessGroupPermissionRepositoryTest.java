package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * role-permission-revoke REQ-3/REQ-10 (TASKS.md task 4): {@code deletedAt} retrofit on {@code
 * global_access_group_permissions} (V30) + the repository methods this feature relies on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class GlobalAccessGroupPermissionRepositoryTest {

    @Autowired private GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;

    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;

    @Test
    void findByGlobalAccessGroupAndPermissionIsUnfilteredByDeletedAt() {
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Unfiltered Lookup Group"));
        GlobalAccessGroupPermission deleted =
                new GlobalAccessGroupPermission(group, GlobalPermission.STAFF_PERMISSION_MANAGE);
        deleted.setDeletedAt(Instant.now());
        GlobalAccessGroupPermission saved =
                globalAccessGroupPermissionRepository.saveAndFlush(deleted);

        assertThat(
                        globalAccessGroupPermissionRepository.findByGlobalAccessGroupAndPermission(
                                group, GlobalPermission.STAFF_PERMISSION_MANAGE))
                .hasValueSatisfying(p -> assertThat(p.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void findByGlobalAccessGroupInAndDeletedAtIsNullExcludesSoftDeletedRows() {
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Effective Group"));
        globalAccessGroupPermissionRepository.saveAndFlush(
                new GlobalAccessGroupPermission(group, GlobalPermission.STAFF_PERMISSION_MANAGE));
        GlobalAccessGroupPermission deleted =
                new GlobalAccessGroupPermission(group, GlobalPermission.STAFF_USER_CREATE);
        deleted.setDeletedAt(Instant.now());
        globalAccessGroupPermissionRepository.saveAndFlush(deleted);

        List<GlobalAccessGroupPermission> live =
                globalAccessGroupPermissionRepository.findByGlobalAccessGroupInAndDeletedAtIsNull(
                        List.of(group));

        assertThat(live)
                .extracting(GlobalAccessGroupPermission::getPermission)
                .containsExactly(GlobalPermission.STAFF_PERMISSION_MANAGE);
    }
}
