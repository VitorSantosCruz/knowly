package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.Message;
import br.com.conectabyte.knowly.icon.IconKey;
import java.util.List;

public record ConversationDetailDto(
        Long id, String title, IconKey icon, List<MessageDto> messages) {

    public static ConversationDetailDto from(Conversation conversation, List<Message> messages) {
        return new ConversationDetailDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getIcon(),
                messages.stream().map(MessageDto::from).toList());
    }
}
