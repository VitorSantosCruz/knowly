package br.com.conectabyte.knowly.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ActiveMemberSnapshotRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ActiveMemberSnapshotRepository activeMemberSnapshotRepository;

    @Test
    void upsertInsertsANewRowWhenNoneExistsForTenantAndDay() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        LocalDate day = LocalDate.now();

        activeMemberSnapshotRepository.upsert(tenant.getId(), day, 5L, "system:test");

        List<ActiveMemberSnapshot> rows =
                activeMemberSnapshotRepository.findAll().stream()
                        .filter(row -> row.getTenant().getId().equals(tenant.getId()))
                        .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSnapshotDate()).isEqualTo(day);
        assertThat(rows.get(0).getActiveCount()).isEqualTo(5L);
    }

    @Test
    void upsertingTwiceForTheSameTenantAndDayLeavesExactlyOneRowWithTheLatestCount() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        LocalDate day = LocalDate.now();

        activeMemberSnapshotRepository.upsert(tenant.getId(), day, 5L, "system:test");
        activeMemberSnapshotRepository.upsert(tenant.getId(), day, 9L, "system:test");

        List<ActiveMemberSnapshot> rows =
                activeMemberSnapshotRepository.findAll().stream()
                        .filter(row -> row.getTenant().getId().equals(tenant.getId()))
                        .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActiveCount()).isEqualTo(9L);
    }

    @Test
    void dayBucketedReadReturnsOnlyTheActiveTenantsRowsOrderedChronologically() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        LocalDate today = LocalDate.now();
        LocalDate twoDaysAgo = today.minusDays(2);
        activeMemberSnapshotRepository.upsert(tenantA.getId(), twoDaysAgo, 3L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantA.getId(), today, 7L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantB.getId(), today, 100L, "system:test");

        List<DailyCountProjection> rows =
                activeMemberSnapshotRepository.countByTenant(tenantA.getId());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getDay()).isEqualTo(twoDaysAgo);
        assertThat(rows.get(0).getCount()).isEqualTo(3L);
        assertThat(rows.get(1).getDay()).isEqualTo(today);
        assertThat(rows.get(1).getCount()).isEqualTo(7L);
    }

    @Test
    void dayBucketedReadSinceOnlyReturnsRowsFromTheBoundAndForTheActiveTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        LocalDate today = LocalDate.now();
        LocalDate longAgo = today.minusDays(10);
        activeMemberSnapshotRepository.upsert(tenantA.getId(), longAgo, 1L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantA.getId(), today, 4L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantB.getId(), today, 100L, "system:test");
        Instant from = Instant.now().minus(3, ChronoUnit.DAYS);

        List<DailyCountProjection> rows =
                activeMemberSnapshotRepository.countByTenantSince(tenantA.getId(), from);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDay()).isEqualTo(today);
        assertThat(rows.get(0).getCount()).isEqualTo(4L);
    }
}
