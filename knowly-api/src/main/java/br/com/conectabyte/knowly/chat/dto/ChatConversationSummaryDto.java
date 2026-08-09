package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import java.time.Instant;
import java.util.List;

public record ChatConversationSummaryDto(
        Long id,
        ChatConversationKind kind,
        Long tenantId,
        String title,
        List<Long> participantUserIds,
        Instant lastMessageAt) {

    /**
     * @param lastMessageAt the timestamp of the conversation's most recent message, or {@code
     *     conversation.getCreatedAt()} when it has none yet -- lets the frontend sort conversations
     *     by recent activity (WhatsApp/Telegram-style) instead of by id.
     */
    public static ChatConversationSummaryDto from(
            ChatConversation conversation, List<Long> participantUserIds, Instant lastMessageAt) {
        return new ChatConversationSummaryDto(
                conversation.getId(),
                conversation.getKind(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getTitle(),
                participantUserIds,
                lastMessageAt == null ? conversation.getCreatedAt() : lastMessageAt);
    }
}
