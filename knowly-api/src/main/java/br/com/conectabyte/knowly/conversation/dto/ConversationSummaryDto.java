package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.conversation.Conversation;

public record ConversationSummaryDto(Long id, String title) {

    public static ConversationSummaryDto from(Conversation conversation) {
        return new ConversationSummaryDto(conversation.getId(), conversation.getTitle());
    }
}
