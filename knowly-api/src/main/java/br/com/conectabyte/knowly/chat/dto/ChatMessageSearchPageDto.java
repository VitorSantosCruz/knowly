package br.com.conectabyte.knowly.chat.dto;

import java.util.List;

public record ChatMessageSearchPageDto(
        List<ChatMessageSearchResultDto> results, String nextCursor) {}
