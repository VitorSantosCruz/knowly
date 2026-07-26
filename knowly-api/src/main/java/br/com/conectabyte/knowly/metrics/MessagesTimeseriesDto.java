package br.com.conectabyte.knowly.metrics;

import java.util.List;

public record MessagesTimeseriesDto(List<DailyRoleCountDto> days) {}
