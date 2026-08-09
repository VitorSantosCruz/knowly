package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatConversationDetailDto(
        Long id,
        ChatConversationKind kind,
        Long tenantId,
        String title,
        List<Long> participantUserIds,
        Map<Long, String> participantNicknames,
        ChatGroupVisibility visibility,
        Instant archivedAt,
        List<Long> adminUserIds) {

    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames) {
        return from(conversation, participantUserIds, participantNicknames, List.of());
    }

    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames,
            List<Long> adminUserIds) {
        return new ChatConversationDetailDto(
                conversation.getId(),
                conversation.getKind(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getTitle(),
                participantUserIds,
                participantNicknames,
                conversation.getVisibility(),
                conversation.getArchivedAt(),
                adminUserIds);
    }
}
