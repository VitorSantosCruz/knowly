package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import java.util.List;
import java.util.Map;

public record ChatConversationDetailDto(
        Long id,
        ChatConversationKind kind,
        Long tenantId,
        String title,
        List<Long> participantUserIds,
        Map<Long, String> participantNicknames) {

    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames) {
        return new ChatConversationDetailDto(
                conversation.getId(),
                conversation.getKind(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getTitle(),
                participantUserIds,
                participantNicknames);
    }
}
