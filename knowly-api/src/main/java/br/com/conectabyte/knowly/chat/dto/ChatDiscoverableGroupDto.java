package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatGroupVisibility;

/**
 * REQ-27: deliberately narrower than {@link ChatConversationSummaryDto} -- exposes {@code
 * participantCount}, not the full participant identity list, to a non-member browsing for groups to
 * join (see PLAN.md's "API contracts" notes).
 */
public record ChatDiscoverableGroupDto(
        Long id,
        String title,
        Long tenantId,
        ChatGroupVisibility visibility,
        long participantCount) {

    public static ChatDiscoverableGroupDto from(
            ChatConversation conversation, long participantCount) {
        return new ChatDiscoverableGroupDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                conversation.getVisibility(),
                participantCount);
    }
}
