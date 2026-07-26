package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * specify/features/global-staff-dashboard-metrics/SPEC.md REQ-4: {@code
 * TenantRepository.countByCreatedAtGreaterThanEqual(Instant)} backs the "new tenants this month"
 * count.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private void backdateTenant(Tenant tenant, Instant createdAt) {
        jdbcTemplate.update(
                "update tenants set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                tenant.getId());
    }

    @Test
    void excludesATenantCreatedBeforeTheCutoff() {
        Instant cutoff = Instant.now();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Before Cutoff Co"));
        backdateTenant(tenant, cutoff.minus(1, ChronoUnit.DAYS));

        long count = tenantRepository.countByCreatedAtGreaterThanEqual(cutoff);

        assertThat(count).isZero();
    }

    @Test
    void includesATenantCreatedAtOrAfterTheCutoff() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        tenantRepository.saveAndFlush(new Tenant("After Cutoff Co"));

        long count = tenantRepository.countByCreatedAtGreaterThanEqual(cutoff);

        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void returnsZeroWhenNoTenantMatches() {
        Instant farFuture = Instant.now().plus(365, ChronoUnit.DAYS);

        long count = tenantRepository.countByCreatedAtGreaterThanEqual(farFuture);

        assertThat(count).isZero();
    }
}
