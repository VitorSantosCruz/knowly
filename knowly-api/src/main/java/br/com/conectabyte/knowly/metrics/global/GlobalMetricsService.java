package br.com.conectabyte.knowly.metrics.global;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.metrics.DailyCountDto;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.metrics.MetricsPeriod;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-only, cross-tenant aggregation for internal operational visibility — the global counterpart
 * of {@link br.com.conectabyte.knowly.metrics.MetricsService}, per
 * specify/features/global-staff-dashboard-metrics/SPEC.md. Deliberately never touches {@code
 * TenantContext}/{@code TenantFilter}: every query here is intentionally unscoped (SPEC REQ-11),
 * kept in its own class/package so a future edit can't accidentally scope a global query by mixing
 * it into the tenant-scoped {@code MetricsService}.
 */
@Service
public class GlobalMetricsService {

    private final TenantRepository tenantRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public GlobalMetricsService(
            TenantRepository tenantRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            UserRepository userRepository,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)
    @AuditLog(action = "metrics.global.view", resourceType = "Metrics")
    public GlobalMetricsDto globalMetrics() {
        Instant startOfCurrentUtcMonth =
                LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long tenantCount = tenantRepository.count();
        long newTenantsThisMonth =
                tenantRepository.countByCreatedAtGreaterThanEqual(startOfCurrentUtcMonth);
        long articlesReadTotal = messageArticleCitationRepository.count();
        long staffCount =
                userRepository.countByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        return new GlobalMetricsDto(
                tenantCount, newTenantsThisMonth, articlesReadTotal, staffCount);
    }

    private static final List<GlobalRole> STAFF_ROLES =
            List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN);

    /**
     * specify/features/global-staff-dashboard-trends/SPEC.md REQ-1/2/3/4/5/6/11: cross-tenant daily
     * series for new tenants/articles read, plus a period-over-period comparison for all four
     * {@link #globalMetrics()} counts. Never scoped through {@code TenantFilter}/{@code
     * TenantContext}, same deliberate exception as {@link #globalMetrics()}.
     */
    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)
    @AuditLog(action = "metrics.global.trends.view", resourceType = "Metrics")
    public GlobalTrendsDto globalTrends(MetricsPeriod period) {
        Instant now = Instant.now(clock);
        Optional<Instant> currentStart = period.startInstant(clock);
        Optional<Instant> previousStart =
                currentStart.map(start -> previousWindowStart(period, start, clock));

        List<DailyCountProjection> tenantDayRows =
                currentStart
                        .map(tenantRepository::countTenantsByDaySince)
                        .orElseGet(tenantRepository::countTenantsByDay);
        List<DailyCountProjection> citationDayRows =
                currentStart
                        .map(messageArticleCitationRepository::countCitationsByDaySince)
                        .orElseGet(messageArticleCitationRepository::countCitationsByDay);

        List<DailyCountDto> newTenantsPerDay = mergeZeroCountDays(tenantDayRows, period);
        List<DailyCountDto> articlesReadPerDay = mergeZeroCountDays(citationDayRows, period);

        long tenantWindowCurrent =
                currentStart
                        .map(
                                start ->
                                        tenantRepository
                                                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        start, now))
                        .orElseGet(tenantRepository::count);
        Long tenantWindowPrevious =
                previousStart
                        .map(
                                prevStart ->
                                        tenantRepository
                                                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        prevStart, currentStart.orElseThrow()))
                        .orElse(null);

        long citationWindowCurrent =
                currentStart
                        .map(
                                start ->
                                        messageArticleCitationRepository
                                                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        start, now))
                        .orElseGet(messageArticleCitationRepository::count);
        Long citationWindowPrevious =
                previousStart
                        .map(
                                prevStart ->
                                        messageArticleCitationRepository
                                                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        prevStart, currentStart.orElseThrow()))
                        .orElse(null);

        long staffWindowCurrent =
                currentStart
                        .map(
                                start ->
                                        userRepository
                                                .countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        STAFF_ROLES, start, now))
                        .orElseGet(() -> userRepository.countByGlobalRoleIn(STAFF_ROLES));
        Long staffWindowPrevious =
                previousStart
                        .map(
                                prevStart ->
                                        userRepository
                                                .countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                                        STAFF_ROLES,
                                                        prevStart,
                                                        currentStart.orElseThrow()))
                        .orElse(null);

        return new GlobalTrendsDto(
                newTenantsPerDay,
                articlesReadPerDay,
                comparison(tenantWindowCurrent, tenantWindowPrevious),
                comparison(tenantWindowCurrent, tenantWindowPrevious),
                comparison(citationWindowCurrent, citationWindowPrevious),
                comparison(staffWindowCurrent, staffWindowPrevious),
                List.of(),
                List.of());
    }

    /**
     * "Immediately preceding period of equal length": for a bounded period {@code [currentStart,
     * now)}, returns the start of {@code [currentStart - N days, currentStart)} — exclusive of
     * {@code currentStart}, so a row created exactly at the boundary instant is never counted in
     * both windows. Never called for {@link MetricsPeriod#ALL} (REQ-5).
     */
    Instant previousWindowStart(MetricsPeriod period, Instant currentStart, Clock clock) {
        long days =
                switch (period) {
                    case SEVEN_DAYS -> 7;
                    case THIRTY_DAYS -> 30;
                    case NINETY_DAYS -> 90;
                    case ALL ->
                            throw new IllegalArgumentException(
                                    "MetricsPeriod.ALL has no previous window");
                };

        return currentStart.minus(days, ChronoUnit.DAYS);
    }

    /**
     * REQ-6: never divides by zero. {@code previous == null} (period=all) yields a {@code null}
     * percentChange (REQ-5); {@code previous == 0} yields {@code null} unless {@code current} is
     * also zero (no change), never {@code NaN}/{@code Infinity}.
     */
    private PeriodComparisonDto comparison(long current, Long previous) {
        if (previous == null) {
            return new PeriodComparisonDto(current, null, null);
        }

        if (previous == 0) {
            return new PeriodComparisonDto(current, previous, current == 0 ? 0.0 : null);
        }

        double rawPercentChange = ((current - previous) / (double) previous) * 100.0;
        double percentChange = Math.round(rawPercentChange * 10) / 10.0;
        return new PeriodComparisonDto(current, previous, percentChange);
    }

    private List<DailyCountDto> mergeZeroCountDays(
            List<DailyCountProjection> rows, MetricsPeriod period) {
        Map<LocalDate, Long> counts =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        DailyCountProjection::getDay,
                                        DailyCountProjection::getCount));
        List<LocalDate> dates =
                period.dateRange(clock).orElseGet(() -> counts.keySet().stream().sorted().toList());

        return dates.stream()
                .map(date -> new DailyCountDto(date, counts.getOrDefault(date, 0L)))
                .toList();
    }

    /**
     * specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-2/3/4/5/6: cumulative,
     * carry-forward day-bucketed merge — distinct from {@link #mergeZeroCountDays}. {@code rows}
     * already holds one row per day-with-activity, sorted ascending by day (per the {@code ORDER BY
     * day} in {@code countCumulativeTenantsByDay()}/{@code countCumulativeStaffByDay()}), each
     * value already a cumulative running total as of that day.
     *
     * <p>For a bounded period, the first displayed day seeds its carry value from the last
     * cumulative total recorded strictly before the display range starts (REQ-3) — not {@code 0} —
     * so a tenant created long before a {@code 7d} window still shows its true running total on day
     * 1 of that window, not a reset to zero.
     *
     * <p>For {@link MetricsPeriod#ALL}, an empty {@code rows} yields an empty list (REQ-5); a
     * non-empty {@code rows} spans from its earliest day through today (REQ-4) — {@code
     * period.dateRange(clock)} cannot be used for {@code ALL} since it returns {@link
     * Optional#empty()} for that period.
     */
    private List<DailyCountDto> mergeCarryForwardDays(
            List<DailyCountProjection> rows, MetricsPeriod period) {
        Map<LocalDate, Long> cumulativeByDay =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        DailyCountProjection::getDay,
                                        DailyCountProjection::getCount));

        List<LocalDate> dates;
        long carry;
        if (period == MetricsPeriod.ALL) {
            if (rows.isEmpty()) {
                return List.of();
            }

            LocalDate earliest = rows.get(0).getDay();
            LocalDate today = LocalDate.now(clock);
            dates = new ArrayList<>();
            for (LocalDate date = earliest; !date.isAfter(today); date = date.plusDays(1)) {
                dates.add(date);
            }
            carry = 0L;
        } else {
            dates = period.dateRange(clock).orElseThrow();
            LocalDate rangeStart = dates.get(0);
            carry =
                    rows.stream()
                            .filter(r -> r.getDay().isBefore(rangeStart))
                            .reduce((first, last) -> last)
                            .map(DailyCountProjection::getCount)
                            .orElse(0L);
        }

        List<DailyCountDto> merged = new ArrayList<>();
        for (LocalDate date : dates) {
            Long rowValue = cumulativeByDay.get(date);
            if (rowValue != null) {
                carry = rowValue;
            }
            merged.add(new DailyCountDto(date, carry));
        }

        return merged;
    }

    /**
     * Package-visible test hook for {@link #mergeCarryForwardDays}, kept {@code private} on the
     * production API surface (same convention as {@link #previousWindowStart}, which is exposed
     * package-visibly for its own unit test) — this method exists purely so a plain, mocked-
     * repository unit test can exercise the merge algorithm without a Spring context.
     */
    List<DailyCountDto> mergeCarryForwardDaysForTest(
            List<DailyCountProjection> rows, MetricsPeriod period) {
        return mergeCarryForwardDays(rows, period);
    }
}
