package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code Tenant}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterTenantIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedTenantWithNoPerQueryOptIn() {
        String marker = "SoftDeleteFilterTenant" + System.nanoTime();
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker));
        deleted.setDeletedAt(Instant.now());
        tenantRepository.saveAndFlush(deleted);

        assertThat(testSupportService.findTenantByName(marker)).isEmpty();
    }
}
