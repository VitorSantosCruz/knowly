package br.com.conectabyte.knowly.metrics.global;

/**
 * specify/features/global-staff-dashboard-trends/SPEC.md REQ-4/5/6: period-over-period comparison
 * for one metric. {@code previous}/{@code percentChange} are boxed so Jackson serializes JSON
 * {@code null} for {@code period=all} (no previous window) or a zero previous-period count, instead
 * of {@code 0}/{@code NaN}/{@code Infinity}.
 */
public record PeriodComparisonDto(long current, Long previous, Double percentChange) {}
