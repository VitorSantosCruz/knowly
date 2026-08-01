package br.com.conectabyte.knowly.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers {@link MetricsService#membersTimeseries(MetricsPeriod)} per
 * specify/features/active-members-trend/SPEC.md REQ-4/6/7/8.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MetricsServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ActiveMemberSnapshotRepository activeMemberSnapshotRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private MetricsService metricsService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    @Test
    void membersTimeseriesZeroFillsSevenDaysForTheActiveTenantOnly() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        LocalDate today = LocalDate.now();
        activeMemberSnapshotRepository.upsert(tenantA.getId(), today, 5L, "system:test");
        activeMemberSnapshotRepository.upsert(
                tenantA.getId(), today.minusDays(2), 3L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantB.getId(), today, 999L, "system:test");
        tenantContext.setActiveTenantId(tenantA.getId());

        MembersTimeseriesDto result = metricsService.membersTimeseries(MetricsPeriod.SEVEN_DAYS);

        assertThat(result.days()).hasSize(7);
        assertThat(result.days()).extracting(DailyCountDto::count).contains(5L, 3L);
        long total = result.days().stream().mapToLong(DailyCountDto::count).sum();
        assertThat(total).isEqualTo(8L);
    }

    @Test
    void membersTimeseriesReturnsOnlySparseSnapshotDaysForAll() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        LocalDate today = LocalDate.now();
        activeMemberSnapshotRepository.upsert(
                tenant.getId(), today.minusDays(30), 1L, "system:test");
        activeMemberSnapshotRepository.upsert(tenant.getId(), today, 4L, "system:test");
        tenantContext.setActiveTenantId(tenant.getId());

        MembersTimeseriesDto result = metricsService.membersTimeseries(MetricsPeriod.ALL);

        assertThat(result.days()).hasSize(2);
        assertThat(result.days())
                .extracting(DailyCountDto::date)
                .containsExactly(today.minusDays(30), today);
    }
}
