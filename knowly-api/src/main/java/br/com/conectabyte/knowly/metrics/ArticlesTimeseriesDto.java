package br.com.conectabyte.knowly.metrics;

import java.util.List;

public record ArticlesTimeseriesDto(List<DailyCountDto> days) {}
