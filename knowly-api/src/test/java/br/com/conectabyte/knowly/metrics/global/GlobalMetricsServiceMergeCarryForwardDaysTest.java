package br.com.conectabyte.knowly.metrics.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.metrics.DailyCountDto;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.metrics.MetricsPeriod;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-2/3/4/5/6: unit coverage for
 * {@link GlobalMetricsService}'s private {@code mergeCarryForwardDays} helper, exercised indirectly
 * through the package-visible test hook {@link
 * GlobalMetricsService#mergeCarryForwardDaysForTest(List, MetricsPeriod)}. Plain unit test (no
 * Spring context, mocked repositories) since the helper is pure date-range/carry-forward logic with
 * no DB interaction of its own.
 */
class GlobalMetricsServiceMergeCarryForwardDaysTest {

    // 2026-07-26 is mid-July; matches the fixed clock convention used elsewhere in this suite.
    private static final LocalDate FIXED_TODAY = LocalDate.parse("2026-07-26");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private final GlobalMetricsService service =
            new GlobalMetricsService(
                    mock(TenantRepository.class),
                    mock(MessageArticleCitationRepository.class),
                    mock(UserRepository.class),
                    FIXED_CLOCK);

    private static DailyCountProjection row(LocalDate day, long count) {
        return new DailyCountProjection() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    @Test
    void carriesTheLastKnownTotalForwardAcrossAQuietDayWithinABoundedPeriod() {
        // THIRTY_DAYS window: a bump on day 1, then quiet for the rest of the window.
        LocalDate windowStart = FIXED_TODAY.minusDays(29);
        List<DailyCountProjection> rows = List.of(row(windowStart, 5L));

        List<DailyCountDto> merged =
                service.mergeCarryForwardDaysForTest(rows, MetricsPeriod.THIRTY_DAYS);

        assertThat(merged).hasSize(30);
        assertThat(merged).allMatch(day -> day.count() == 5L);
    }

    @Test
    void seedsTheFirstDisplayedDayFromACumulativeValueRecordedBeforeTheRangeStarts() {
        // Tenant created 6 months ago: the cumulative row predates the 7d window entirely.
        LocalDate sixMonthsAgo = FIXED_TODAY.minusMonths(6);
        List<DailyCountProjection> rows = List.of(row(sixMonthsAgo, 1L));

        List<DailyCountDto> merged =
                service.mergeCarryForwardDaysForTest(rows, MetricsPeriod.SEVEN_DAYS);

        assertThat(merged).hasSize(7);
        assertThat(merged.get(0).count()).isEqualTo(1L);
        assertThat(merged).allMatch(day -> day.count() == 1L);
    }

    @Test
    void allPeriodSpansFromTheEarliestRowsDayThroughToday() {
        LocalDate earliest = FIXED_TODAY.minusDays(40);
        LocalDate later = FIXED_TODAY.minusDays(10);
        List<DailyCountProjection> rows = List.of(row(earliest, 1L), row(later, 3L));

        List<DailyCountDto> merged = service.mergeCarryForwardDaysForTest(rows, MetricsPeriod.ALL);

        assertThat(merged).hasSize(41); // earliest..today inclusive
        assertThat(merged.get(0).date()).isEqualTo(earliest);
        assertThat(merged.get(merged.size() - 1).date()).isEqualTo(FIXED_TODAY);
        assertThat(merged.get(0).count()).isEqualTo(1L);
        assertThat(merged.get(merged.size() - 1).count()).isEqualTo(3L);
    }

    @Test
    void allPeriodReturnsEmptyListWhenThereAreNoRowsAtAll() {
        List<DailyCountDto> merged =
                service.mergeCarryForwardDaysForTest(List.of(), MetricsPeriod.ALL);

        assertThat(merged).isEmpty();
    }

    @Test
    void boundedPeriodWithNoRowsAtAllZeroFillsEveryDay() {
        List<DailyCountDto> merged =
                service.mergeCarryForwardDaysForTest(List.of(), MetricsPeriod.SEVEN_DAYS);

        assertThat(merged).hasSize(7);
        assertThat(merged).allMatch(day -> day.count() == 0L);
    }
}
