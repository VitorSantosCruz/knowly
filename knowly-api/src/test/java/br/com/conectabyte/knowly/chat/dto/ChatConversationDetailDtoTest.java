package br.com.conectabyte.knowly.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatConversationDetailDtoTest {

    @Test
    void fromIncludesVisibilityArchivedAtAndAdminUserIds() {
        ChatConversation conversation =
                new ChatConversation(ChatConversationKind.PEER_GROUP, null, "g", null);
        conversation.setId(1L);
        conversation.setVisibility(ChatGroupVisibility.PUBLIC);
        Instant archivedAt = Instant.now();
        conversation.setArchivedAt(archivedAt);

        ChatConversationDetailDto dto =
                ChatConversationDetailDto.from(
                        conversation, List.of(1L, 2L), Map.of(1L, "a", 2L, "b"), List.of(1L));

        assertThat(dto.visibility()).isEqualTo(ChatGroupVisibility.PUBLIC);
        assertThat(dto.archivedAt()).isEqualTo(archivedAt);
        assertThat(dto.adminUserIds()).containsExactly(1L);
        assertThat(dto.participantUserIds()).containsExactly(1L, 2L);
    }

    @Test
    void fromWithoutAvatarUrlsDefaultsToAnEmptyMap() {
        ChatConversation conversation =
                new ChatConversation(ChatConversationKind.PEER_GROUP, null, "g", null);
        conversation.setId(1L);

        ChatConversationDetailDto dto =
                ChatConversationDetailDto.from(
                        conversation, List.of(1L, 2L), Map.of(1L, "a", 2L, "b"), List.of(1L));

        assertThat(dto.participantAvatarUrls()).isEmpty();
    }

    @Test
    void fromIncludesPerParticipantAvatarUrlsWhenProvided() {
        ChatConversation conversation =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, null, null, null);
        conversation.setId(1L);

        ChatConversationDetailDto dto =
                ChatConversationDetailDto.from(
                        conversation,
                        List.of(1L, 2L),
                        Map.of(1L, "a", 2L, "b"),
                        List.of(),
                        Map.of(2L, "https://minio.local/avatars/2"));

        assertThat(dto.participantAvatarUrls()).containsEntry(2L, "https://minio.local/avatars/2");
    }
}
