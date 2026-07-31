package br.com.conectabyte.knowly.chat.dto;

import java.util.List;

public record ChatMessagePageDto(List<ChatMessageDto> messages, String nextCursor) {}
