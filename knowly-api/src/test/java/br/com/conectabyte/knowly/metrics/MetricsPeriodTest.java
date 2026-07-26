package br.com.conectabyte.knowly.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.metrics.exception.InvalidPeriodException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetricsPeriodTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void parsesEachValidPeriodValue() {
        assertThat(MetricsPeriod.from("7d")).isEqualTo(MetricsPeriod.SEVEN_DAYS);
        assertThat(MetricsPeriod.from("30d")).isEqualTo(MetricsPeriod.THIRTY_DAYS);
        assertThat(MetricsPeriod.from("90d")).isEqualTo(MetricsPeriod.NINETY_DAYS);
        assertThat(MetricsPeriod.from("all")).isEqualTo(MetricsPeriod.ALL);
        assertThat(MetricsPeriod.from(null)).isEqualTo(MetricsPeriod.ALL);
    }

    @Test
    void throwsInvalidPeriodExceptionForAnythingElse() {
        assertThatThrownBy(() -> MetricsPeriod.from("bogus"))
                .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    void startInstantComputesTheCorrectUtcLowerBoundForEachNonAllValue() {
        assertThat(MetricsPeriod.SEVEN_DAYS.startInstant(FIXED_CLOCK))
                .contains(NOW.minus(7, ChronoUnit.DAYS));
        assertThat(MetricsPeriod.THIRTY_DAYS.startInstant(FIXED_CLOCK))
                .contains(NOW.minus(30, ChronoUnit.DAYS));
        assertThat(MetricsPeriod.NINETY_DAYS.startInstant(FIXED_CLOCK))
                .contains(NOW.minus(90, ChronoUnit.DAYS));
    }

    @Test
    void startInstantIsEmptyForAll() {
        assertThat(MetricsPeriod.ALL.startInstant(FIXED_CLOCK)).isEqualTo(Optional.empty());
    }
}
