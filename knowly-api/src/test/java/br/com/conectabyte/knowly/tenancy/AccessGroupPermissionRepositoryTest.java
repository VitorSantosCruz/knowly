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
import org.springframework.transaction.annotation.Transactional;

/**
 * tenant-access-group-bulk-and-delete REQ-9/REQ-13/REQ-17: {@code deletedAt} retrofit + the new
 * repository methods (PLAN.md's "Testing strategy" / TASKS.md section 1-2).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AccessGroupPermissionRepositoryTest {

    @Autowired private AccessGroupPermissionRepository accessGroupPermissionRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void persistsAndReadsBackANonNullDeletedAt() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Persist Permission Co"));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        AccessGroupPermission permission =
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE);
        permission.setDeletedAt(Instant.now());

        AccessGroupPermission saved = accessGroupPermissionRepository.saveAndFlush(permission);

        assertThat(accessGroupPermissionRepository.findById(saved.getId()))
                .hasValueSatisfying(p -> assertThat(p.getDeletedAt()).isNotNull());
    }

    @Test
    void aNewRowWithTheSameGroupAndPermissionAsASoftDeletedOneSavesSuccessfully() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reuse Permission Co"));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        AccessGroupPermission deleted =
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE);
        deleted.setDeletedAt(Instant.now());
        accessGroupPermissionRepository.saveAndFlush(deleted);

        AccessGroupPermission recreated =
                accessGroupPermissionRepository.saveAndFlush(
                        new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE));

        assertThat(recreated.getId()).isNotEqualTo(deleted.getId());
    }

    @Test
    void findByAccessGroupInAndDeletedAtIsNullExcludesSoftDeletedRows() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Effective Perm Co"));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        accessGroupPermissionRepository.saveAndFlush(
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE));
        AccessGroupPermission deleted = new AccessGroupPermission(group, Permission.ARTICLE_VIEW);
        deleted.setDeletedAt(Instant.now());
        accessGroupPermissionRepository.saveAndFlush(deleted);

        List<AccessGroupPermission> live =
                accessGroupPermissionRepository.findByAccessGroupInAndDeletedAtIsNull(
                        List.of(group));

        assertThat(live)
                .extracting(AccessGroupPermission::getPermission)
                .containsExactly(Permission.TENANT_MEMBER_MANAGE);
    }

    @Test
    @Transactional
    void softDeleteByAccessGroupIdSetsDeletedAtOnlyOnLiveRowsForThatGroup() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Bulk Softdelete Co"));
        AccessGroup targetGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Target"));
        AccessGroup otherGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Other"));
        AccessGroupPermission targetLive =
                accessGroupPermissionRepository.saveAndFlush(
                        new AccessGroupPermission(targetGroup, Permission.TENANT_MEMBER_MANAGE));
        AccessGroupPermission targetAlreadyDeleted =
                new AccessGroupPermission(targetGroup, Permission.ARTICLE_VIEW);
        targetAlreadyDeleted.setDeletedAt(Instant.now().minusSeconds(60));
        accessGroupPermissionRepository.saveAndFlush(targetAlreadyDeleted);
        AccessGroupPermission otherGroupRow =
                accessGroupPermissionRepository.saveAndFlush(
                        new AccessGroupPermission(otherGroup, Permission.TENANT_MEMBER_MANAGE));
        Instant deletedAt = Instant.now();

        accessGroupPermissionRepository.softDeleteByAccessGroupId(targetGroup.getId(), deletedAt);

        assertThat(accessGroupPermissionRepository.findById(targetLive.getId()))
                .hasValueSatisfying(p -> assertThat(p.getDeletedAt()).isNotNull());
        assertThat(accessGroupPermissionRepository.findById(targetAlreadyDeleted.getId()))
                .hasValueSatisfying(
                        p -> assertThat(p.getDeletedAt()).isBefore(deletedAt.minusSeconds(1)));
        assertThat(accessGroupPermissionRepository.findById(otherGroupRow.getId()))
                .hasValueSatisfying(p -> assertThat(p.getDeletedAt()).isNull());
    }
}
