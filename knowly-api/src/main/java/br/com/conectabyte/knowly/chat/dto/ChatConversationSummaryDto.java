package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import java.util.List;

public record ChatConversationSummaryDto(
        Long id,
        ChatConversationKind kind,
        Long tenantId,
        String title,
        List<Long> participantUserIds) {

    public static ChatConversationSummaryDto from(
            ChatConversation conversation, List<Long> participantUserIds) {
        return new ChatConversationSummaryDto(
                conversation.getId(),
                conversation.getKind(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getTitle(),
                participantUserIds);
    }
}
