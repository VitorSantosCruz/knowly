package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.conversation.Message;
import br.com.conectabyte.knowly.conversation.MessageRole;

public record MessageDto(Long id, MessageRole role, String content) {

    public static MessageDto from(Message message) {
        return new MessageDto(message.getId(), message.getRole(), message.getContent());
    }
}
