package br.com.conectabyte.knowly.metrics;

import java.util.List;

public record ConversationsTimeseriesDto(List<DailyCountDto> days) {}
