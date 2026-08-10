package br.com.conectabyte.knowly.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMessageSearchDtoTest {

    @Test
    void resultDtoHasExactlyTheContractFields() {
        Instant now = Instant.now();
        ChatMessageSearchResultDto dto =
                new ChatMessageSearchResultDto(1L, 2L, "Title", 3L, "Nick", "content", now);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.conversationId()).isEqualTo(2L);
        assertThat(dto.conversationTitle()).isEqualTo("Title");
        assertThat(dto.senderUserId()).isEqualTo(3L);
        assertThat(dto.senderNickname()).isEqualTo("Nick");
        assertThat(dto.content()).isEqualTo("content");
        assertThat(dto.createdAt()).isEqualTo(now);
    }

    @Test
    void pageDtoHasExactlyTheContractFields() {
        ChatMessageSearchResultDto result =
                new ChatMessageSearchResultDto(
                        1L, 2L, "Title", 3L, "Nick", "content", Instant.now());
        ChatMessageSearchPageDto page = new ChatMessageSearchPageDto(List.of(result), "cursor");

        assertThat(page.results()).containsExactly(result);
        assertThat(page.nextCursor()).isEqualTo("cursor");
    }
}
