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
 * tenant-access-group-bulk-and-delete REQ-8/REQ-3/REQ-17: {@code deletedAt} retrofit + the new
 * repository methods (PLAN.md's "Testing strategy" / TASKS.md section 1-2).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AccessGroupRepositoryTest {

    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void persistsAndReadsBackANonNullDeletedAt() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Persist Deleted Co"));
        AccessGroup group = new AccessGroup(tenant, "Editors");
        group.setDeletedAt(Instant.now());

        AccessGroup saved = accessGroupRepository.saveAndFlush(group);

        assertThat(accessGroupRepository.findById(saved.getId()))
                .hasValueSatisfying(g -> assertThat(g.getDeletedAt()).isNotNull());
    }

    @Test
    void aNewGroupWithTheSameTenantAndNameAsASoftDeletedOneSavesSuccessfully() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reuse Name Co"));
        AccessGroup deleted = new AccessGroup(tenant, "Editors");
        deleted.setDeletedAt(Instant.now());
        accessGroupRepository.saveAndFlush(deleted);

        AccessGroup recreated =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        assertThat(recreated.getId()).isNotEqualTo(deleted.getId());
    }

    @Test
    void findByTenantAndIdInAndDeletedAtIsNullExcludesSoftDeletedAndOtherTenantRows() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Batch Lookup Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Batch Foreign Co"));
        AccessGroup live = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Live"));
        AccessGroup deleted = new AccessGroup(tenant, "Deleted");
        deleted.setDeletedAt(Instant.now());
        accessGroupRepository.saveAndFlush(deleted);
        AccessGroup foreign =
                accessGroupRepository.saveAndFlush(new AccessGroup(otherTenant, "Foreign"));

        List<AccessGroup> resolved =
                accessGroupRepository.findByTenantAndIdInAndDeletedAtIsNull(
                        tenant, List.of(live.getId(), deleted.getId(), foreign.getId()));

        assertThat(resolved).extracting(AccessGroup::getId).containsExactly(live.getId());
    }

    @Test
    void findByIdAndDeletedAtIsNullReturnsEmptyWhenSoftDeleted() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Find Live Co"));
        AccessGroup live = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Live"));
        AccessGroup deleted = new AccessGroup(tenant, "Deleted");
        deleted.setDeletedAt(Instant.now());
        accessGroupRepository.saveAndFlush(deleted);

        assertThat(accessGroupRepository.findByIdAndDeletedAtIsNull(live.getId())).isPresent();
        assertThat(accessGroupRepository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
    }

    @Test
    void findByTenantAndDeletedAtIsNullExcludesSoftDeletedRowsForTheTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Live Co"));
        AccessGroup live = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Live"));
        AccessGroup deleted = new AccessGroup(tenant, "Deleted");
        deleted.setDeletedAt(Instant.now());
        accessGroupRepository.saveAndFlush(deleted);

        List<AccessGroup> resolved = accessGroupRepository.findByTenantAndDeletedAtIsNull(tenant);

        assertThat(resolved).extracting(AccessGroup::getId).containsExactly(live.getId());
    }
}
