package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatMessage;
import java.time.Instant;

public record ChatMessageDto(
        Long id, Long senderUserId, String senderNickname, String content, Instant createdAt) {

    public static ChatMessageDto from(ChatMessage message, String senderNickname) {
        return new ChatMessageDto(
                message.getId(),
                message.getSender().getId(),
                senderNickname,
                message.getContent(),
                message.getCreatedAt());
    }
}
