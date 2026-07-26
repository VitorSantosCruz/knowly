package br.com.conectabyte.knowly.metrics;

import java.time.LocalDate;

public interface DailyRoleCountProjection {
    LocalDate getDay();

    String getRole();

    Long getCount();
}
