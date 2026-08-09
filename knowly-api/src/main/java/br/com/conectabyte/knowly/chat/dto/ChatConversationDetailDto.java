package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import br.com.conectabyte.knowly.icon.IconKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatConversationDetailDto(
        Long id,
        ChatConversationKind kind,
        Long tenantId,
        String title,
        IconKey icon,
        List<Long> participantUserIds,
        Map<Long, String> participantNicknames,
        ChatGroupVisibility visibility,
        Instant archivedAt,
        List<Long> adminUserIds,
        Map<Long, String> participantAvatarUrls) {

    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames) {
        return from(conversation, participantUserIds, participantNicknames, List.of(), Map.of());
    }

    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames,
            List<Long> adminUserIds) {
        return from(conversation, participantUserIds, participantNicknames, adminUserIds, Map.of());
    }

    /**
     * @param participantAvatarUrls per-participant avatarUrl (nullable per entry), same
     *     MinIO-backed source already used by {@code CandidateUserDto} -- primarily consumed by the
     *     frontend for a DIRECT conversation's header, per chat-unified-ui's follow-up request; a
     *     PEER_GROUP conversation deliberately has no group-level avatar of its own (out of scope).
     */
    public static ChatConversationDetailDto from(
            ChatConversation conversation,
            List<Long> participantUserIds,
            Map<Long, String> participantNicknames,
            List<Long> adminUserIds,
            Map<Long, String> participantAvatarUrls) {
        return new ChatConversationDetailDto(
                conversation.getId(),
                conversation.getKind(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getTitle(),
                conversation.getIcon(),
                participantUserIds,
                participantNicknames,
                conversation.getVisibility(),
                conversation.getArchivedAt(),
                adminUserIds,
                participantAvatarUrls);
    }
}
