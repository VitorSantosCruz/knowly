package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.icon.IconKey;

public record ConversationSummaryDto(Long id, String title, IconKey icon) {

    public static ConversationSummaryDto from(Conversation conversation) {
        return new ConversationSummaryDto(
                conversation.getId(), conversation.getTitle(), conversation.getIcon());
    }
}
