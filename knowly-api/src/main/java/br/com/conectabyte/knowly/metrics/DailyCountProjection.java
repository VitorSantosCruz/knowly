package br.com.conectabyte.knowly.metrics;

import java.time.LocalDate;

public interface DailyCountProjection {
    LocalDate getDay();

    Long getCount();
}
