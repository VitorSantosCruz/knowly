package br.com.conectabyte.knowly.metrics;

import java.util.List;

public record MembersTimeseriesDto(List<DailyCountDto> days) {}
