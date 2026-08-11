package br.com.conectabyte.knowly.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMessageSearchDtoTest {

    @Test
    void resultDtoHasExactlyTheContractFields() {
        Instant now = Instant.now();
        ChatMessageSearchResultDto dto =
                new ChatMessageSearchResultDto(
                        1L,
                        2L,
                        "Title",
                        3L,
                        "Nick",
                        "content",
                        now,
                        true,
                        ChatGroupVisibility.PUBLIC);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.conversationId()).isEqualTo(2L);
        assertThat(dto.conversationTitle()).isEqualTo("Title");
        assertThat(dto.senderUserId()).isEqualTo(3L);
        assertThat(dto.senderNickname()).isEqualTo("Nick");
        assertThat(dto.content()).isEqualTo("content");
        assertThat(dto.createdAt()).isEqualTo(now);
        assertThat(dto.isParticipant()).isTrue();
        assertThat(dto.visibility()).isEqualTo(ChatGroupVisibility.PUBLIC);
    }

    // REQ-44: a PEER_DIRECT result always serializes visibility: null, isParticipant: true.
    @Test
    void peerDirectResultSerializesNullVisibility() {
        ChatMessageSearchResultDto dto =
                new ChatMessageSearchResultDto(
                        1L, 2L, "Title", 3L, "Nick", "content", Instant.now(), true, null);

        assertThat(dto.isParticipant()).isTrue();
        assertThat(dto.visibility()).isNull();
    }

    // REQ-46: a non-participant PEER_GROUP result serializes its real visibility, never null.
    @Test
    void nonParticipantPeerGroupResultSerializesRealVisibility() {
        ChatMessageSearchResultDto dto =
                new ChatMessageSearchResultDto(
                        1L,
                        2L,
                        "Title",
                        3L,
                        "Nick",
                        "content",
                        Instant.now(),
                        false,
                        ChatGroupVisibility.REQUEST_TO_JOIN);

        assertThat(dto.isParticipant()).isFalse();
        assertThat(dto.visibility()).isEqualTo(ChatGroupVisibility.REQUEST_TO_JOIN);
    }

    @Test
    void pageDtoHasExactlyTheContractFields() {
        ChatMessageSearchResultDto result =
                new ChatMessageSearchResultDto(
                        1L,
                        2L,
                        "Title",
                        3L,
                        "Nick",
                        "content",
                        Instant.now(),
                        true,
                        ChatGroupVisibility.PUBLIC);
        ChatMessageSearchPageDto page = new ChatMessageSearchPageDto(List.of(result), "cursor");

        assertThat(page.results()).containsExactly(result);
        assertThat(page.nextCursor()).isEqualTo("cursor");
    }
}
