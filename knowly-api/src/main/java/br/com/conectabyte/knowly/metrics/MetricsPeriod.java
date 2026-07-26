package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.metrics.exception.InvalidPeriodException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum MetricsPeriod {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    ALL(null);

    private final Integer days;

    MetricsPeriod(Integer days) {
        this.days = days;
    }

    public static MetricsPeriod from(String raw) {
        if (raw == null) {
            return ALL;
        }

        return switch (raw) {
            case "7d" -> SEVEN_DAYS;
            case "30d" -> THIRTY_DAYS;
            case "90d" -> NINETY_DAYS;
            case "all" -> ALL;
            default -> throw new InvalidPeriodException();
        };
    }

    public Optional<Instant> startInstant(Clock clock) {
        if (days == null) {
            return Optional.empty();
        }

        return Optional.of(Instant.now(clock).minus(days, ChronoUnit.DAYS));
    }

    /**
     * Full inclusive UTC calendar-day range (from N-1 days ago through today) used to zero-fill
     * day-bucketed time-series results. Empty for {@link #ALL}, since there is no bounded range to
     * zero-fill against.
     */
    public Optional<List<LocalDate>> dateRange(Clock clock) {
        if (days == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(days - 1);
        List<LocalDate> range = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            range.add(date);
        }

        return Optional.of(range);
    }
}
