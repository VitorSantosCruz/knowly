package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import br.com.conectabyte.knowly.icon.IconKey;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code participantUserIds} is intentionally unvalidated for emptiness here: for {@code
 * kind=DIRECT} an empty (or single-element) list is rejected by {@link
 * br.com.conectabyte.knowly.chat.ChatConversationService#createConversation} once the creator is
 * folded in and the resulting set isn't exactly 2 users (chat-unified-ui REQ-12/13 need {@code
 * kind=GROUP} to accept an empty list -- the creator joins alone, as admin, and invites others
 * later via the dedicated add-participants endpoint).
 */
public record CreateChatConversationRequestDto(
        @NotNull ChatConversationRequestKind kind,
        Long tenantId,
        @Size(max = 255) String title,
        List<Long> participantUserIds,
        // REQ-13/18 (chat-unified-ui): visibility is chosen at group-creation time. Nullable/
        // optional -- DIRECT conversations have no notion of visibility, and the service layer
        // defaults a missing value for GROUP to PRIVATE rather than rejecting the request outright.
        ChatGroupVisibility visibility,
        // REQ-1/REQ-4 (chat-group-naming-and-icon): optional icon, same fixed IconKey catalog as
        // RAG conversations. Null/unvalidated-for-presence for kind=DIRECT (1:1 conversations have
        // no icon concept), same as visibility is already ignored for DIRECT.
        IconKey icon) {

    public CreateChatConversationRequestDto {
        if (participantUserIds == null) {
            participantUserIds = List.of();
        }
    }

    public enum ChatConversationRequestKind {
        DIRECT,
        GROUP
    }
}
