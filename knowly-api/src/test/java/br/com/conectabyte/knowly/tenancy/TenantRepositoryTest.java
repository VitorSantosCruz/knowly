package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    // --- specify/features/global-staff-dashboard-trends/SPEC.md REQ-2a/4/11: cross-tenant,
    // day-bucketed and windowed counts for the trends endpoint ---

    /**
     * Noon UTC "today," not the literal {@code Instant.now()}: day-bucket assertions below compare
     * a UTC-computed {@code LocalDate} against what the DB's {@code date_trunc('day', ...)}
     * bucketed -- a backdated instant within a few hours of a real UTC midnight can land in the
     * "wrong" day depending on the DB session's timezone, which is exactly what broke this suite
     * when it happened to run near local midnight (see the incident this fixes, 2026-07-31/08-01
     * rollover). Anchoring every backdated instant to noon keeps a multi-hour timezone skew from
     * ever crossing a day boundary.
     */
    private static Instant safeAnchor() {
        return Instant.now().truncatedTo(ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS);
    }

    @Test
    void countTenantsByDaySinceReturnsRowsSortedChronologicallyAcrossAllTenants() {
        Instant cutoff = safeAnchor().minus(3, ChronoUnit.DAYS);
        Tenant dayOne = tenantRepository.saveAndFlush(new Tenant("Day One Co"));
        backdateTenant(dayOne, cutoff.plus(1, ChronoUnit.HOURS));
        Tenant dayTwo = tenantRepository.saveAndFlush(new Tenant("Day Two Co"));
        backdateTenant(dayTwo, cutoff.plus(1, ChronoUnit.DAYS));
        Tenant beforeCutoff = tenantRepository.saveAndFlush(new Tenant("Before Cutoff Trend Co"));
        backdateTenant(beforeCutoff, cutoff.minus(1, ChronoUnit.DAYS));

        List<DailyCountProjection> rows = tenantRepository.countTenantsByDaySince(cutoff);

        assertThat(rows)
                .extracting(DailyCountProjection::getDay)
                .contains(
                        LocalDate.ofInstant(cutoff.plus(1, ChronoUnit.HOURS), ZoneOffset.UTC),
                        LocalDate.ofInstant(cutoff.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC));
        assertThat(rows)
                .extracting(DailyCountProjection::getDay)
                .doesNotContain(LocalDate.ofInstant(beforeCutoff.getCreatedAt(), ZoneOffset.UTC));
        assertThat(rows).isSortedAccordingTo(java.util.Comparator.comparing(r -> r.getDay()));
    }

    @Test
    void countTenantsByDayReturnsAllRowsWithNoLowerBound() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("All Time Trend Co"));
        Instant createdAt = safeAnchor();
        backdateTenant(tenant, createdAt);

        List<DailyCountProjection> rows = tenantRepository.countTenantsByDay();

        assertThat(rows).isNotEmpty();
        assertThat(rows)
                .extracting(DailyCountProjection::getDay)
                .contains(LocalDate.ofInstant(createdAt, ZoneOffset.UTC));
    }

    @Test
    void countByCreatedAtWindowRespectsHalfOpenBounds() {
        Instant windowStart = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant windowEnd = Instant.now().minus(5, ChronoUnit.DAYS);

        Tenant insideWindow = tenantRepository.saveAndFlush(new Tenant("Inside Window Co"));
        backdateTenant(insideWindow, windowStart.plus(1, ChronoUnit.HOURS));

        Tenant atLowerBound = tenantRepository.saveAndFlush(new Tenant("At Lower Bound Co"));
        backdateTenant(atLowerBound, windowStart);

        Tenant atUpperBound = tenantRepository.saveAndFlush(new Tenant("At Upper Bound Co"));
        backdateTenant(atUpperBound, windowEnd);

        Tenant outsideWindow = tenantRepository.saveAndFlush(new Tenant("Outside Window Co"));
        backdateTenant(outsideWindow, windowEnd.plus(1, ChronoUnit.HOURS));

        long count =
                tenantRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        windowStart, windowEnd);

        assertThat(count).isEqualTo(2);
    }
}
