package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import({TestcontainersConfiguration.class, TenantIsolationIntegrationTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private AccessGroupQueryService accessGroupQueryService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    @Test
    void aRequestScopedToOneTenantNeverSeesAnotherTenantsRows() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantA, "A-only group"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantB, "B-only group"));

        tenantContext.setActiveTenantId(tenantA.getId());

        assertThat(accessGroupQueryService.findAll())
                .extracting(AccessGroup::getName)
                .containsExactly("A-only group");
    }

    @Test
    void noActiveTenantReturnsNothingRatherThanEverythingOrAnError() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Some Tenant"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Some group"));

        assertThat(tenantContext.getActiveTenantId()).isEmpty();
        assertThat(accessGroupQueryService.findAll()).isEmpty();
    }

    @Test
    void staffWithNoActiveTenantSeesAcrossAllTenants() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantA, "A-only group"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantB, "B-only group"));

        tenantContext.setStaff(true);

        assertThat(accessGroupQueryService.findAll())
                .extracting(AccessGroup::getName)
                .contains("A-only group", "B-only group");
    }

    @Test
    void staffActingAsATenantIsScopedLikeAnyMember() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantA, "A-only group"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenantB, "B-only group"));

        tenantContext.setStaff(true);
        tenantContext.setActiveTenantId(tenantA.getId());

        assertThat(accessGroupQueryService.findAll())
                .extracting(AccessGroup::getName)
                .containsExactly("A-only group");
    }

    static class AccessGroupQueryService {
        private final AccessGroupRepository accessGroupRepository;

        AccessGroupQueryService(AccessGroupRepository accessGroupRepository) {
            this.accessGroupRepository = accessGroupRepository;
        }

        @Transactional(readOnly = true)
        List<AccessGroup> findAll() {
            return accessGroupRepository.findAll();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        AccessGroupQueryService accessGroupQueryService(
                AccessGroupRepository accessGroupRepository) {
            return new AccessGroupQueryService(accessGroupRepository);
        }
    }
}
