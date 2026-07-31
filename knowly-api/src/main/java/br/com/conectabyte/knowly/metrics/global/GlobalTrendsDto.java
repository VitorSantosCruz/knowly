package br.com.conectabyte.knowly.metrics.global;

import br.com.conectabyte.knowly.metrics.DailyCountDto;
import java.util.List;

/**
 * specify/features/global-staff-dashboard-trends/SPEC.md REQ-2/3/4: cross-tenant daily series for
 * new tenants/articles read, plus a period-over-period comparison for all four {@code
 * globalMetrics()} counts.
 */
public record GlobalTrendsDto(
        List<DailyCountDto> newTenantsPerDay,
        List<DailyCountDto> articlesReadPerDay,
        PeriodComparisonDto totalTenants,
        PeriodComparisonDto newTenants,
        PeriodComparisonDto totalArticlesRead,
        PeriodComparisonDto staffCount) {}
