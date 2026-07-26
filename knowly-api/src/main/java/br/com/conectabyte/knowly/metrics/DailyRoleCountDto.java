package br.com.conectabyte.knowly.metrics;

import java.time.LocalDate;

public record DailyRoleCountDto(LocalDate date, long userCount, long assistantCount) {}
