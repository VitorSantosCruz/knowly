package br.com.conectabyte.knowly.chat.dto;

import java.time.Instant;

public record ChatMessageSearchResultDto(
        Long id,
        Long conversationId,
        String conversationTitle,
        Long senderUserId,
        String senderNickname,
        String content,
        Instant createdAt) {}
