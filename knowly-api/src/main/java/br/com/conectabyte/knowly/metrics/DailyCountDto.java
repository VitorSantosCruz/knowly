package br.com.conectabyte.knowly.metrics;

import java.time.LocalDate;

public record DailyCountDto(LocalDate date, long count) {}
