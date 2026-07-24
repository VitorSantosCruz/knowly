package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.Message;
import java.util.List;

public record ConversationDetailDto(Long id, String title, List<MessageDto> messages) {

    public static ConversationDetailDto from(Conversation conversation, List<Message> messages) {
        return new ConversationDetailDto(
                conversation.getId(),
                conversation.getTitle(),
                messages.stream().map(MessageDto::from).toList());
    }
}
