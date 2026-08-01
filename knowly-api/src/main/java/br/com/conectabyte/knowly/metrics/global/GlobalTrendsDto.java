package br.com.conectabyte.knowly.metrics.global;

import br.com.conectabyte.knowly.metrics.DailyCountDto;
import java.util.List;

/**
 * specify/features/global-staff-dashboard-trends/SPEC.md REQ-2/3/4: cross-tenant daily series for
 * new tenants/articles read, plus a period-over-period comparison for all four {@code
 * globalMetrics()} counts.
 *
 * <p>specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1: {@code
 * totalTenantsPerDay}/{@code staffCountPerDay} are appended (not interleaved with the four existing
 * fields, per that feature's PLAN.md) — cumulative, carry-forward running-total series for the
 * "Total de tenants"/"Membros da equipe interna" cards, distinct in shape from {@code
 * newTenantsPerDay}/{@code articlesReadPerDay} above (which zero-fill instead of carrying forward).
 */
public record GlobalTrendsDto(
        List<DailyCountDto> newTenantsPerDay,
        List<DailyCountDto> articlesReadPerDay,
        PeriodComparisonDto totalTenants,
        PeriodComparisonDto newTenants,
        PeriodComparisonDto totalArticlesRead,
        PeriodComparisonDto staffCount,
        List<DailyCountDto> totalTenantsPerDay,
        List<DailyCountDto> staffCountPerDay) {}
