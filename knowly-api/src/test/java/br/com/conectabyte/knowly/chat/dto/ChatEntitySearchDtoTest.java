package br.com.conectabyte.knowly.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unified entity search (2026-08-10 amendment): asserts each new DTO exists with exactly the fields
 * from PLAN's "Amended" API contracts section.
 */
class ChatEntitySearchDtoTest {

    @Test
    void personSearchResultDtoHasExactlyTheContractFields() {
        var dto = new ChatPersonSearchResultDto(1L, "Nick", "https://avatar");

        assertThat(dto.userId()).isEqualTo(1L);
        assertThat(dto.nickname()).isEqualTo("Nick");
        assertThat(dto.avatarUrl()).isEqualTo("https://avatar");
    }

    @Test
    void groupSearchResultDtoHasExactlyTheContractFields() {
        var dto = new ChatGroupSearchResultDto(1L, "Title", true, ChatGroupVisibility.PUBLIC);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.title()).isEqualTo("Title");
        assertThat(dto.isParticipant()).isTrue();
        assertThat(dto.visibility()).isEqualTo(ChatGroupVisibility.PUBLIC);
    }

    @Test
    void supportSearchResultDtoHasExactlyTheContractFields() {
        var dto = new ChatSupportSearchResultDto(7L);

        assertThat(dto.channelId()).isEqualTo(7L);
    }

    @Test
    void ragConversationSearchResultDtoHasExactlyTheContractFields() {
        var dto = new ChatRagConversationSearchResultDto(1L, "Base de artigos");

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.title()).isEqualTo("Base de artigos");
    }

    @Test
    void entitySearchSectionDtoHasExactlyTheContractFields() {
        var dto = new ChatEntitySearchSectionDto<>(List.of("a"), true);

        assertThat(dto.results()).containsExactly("a");
        assertThat(dto.hasMore()).isTrue();
    }

    @Test
    void entitySearchResponseDtoHasExactlyTheContractFields() {
        var people = new ChatEntitySearchSectionDto<>(List.<ChatPersonSearchResultDto>of(), false);
        var groups = new ChatEntitySearchSectionDto<>(List.<ChatGroupSearchResultDto>of(), false);
        var support = new ChatSupportSearchResultDto(1L);
        var rag =
                new ChatEntitySearchSectionDto<>(
                        List.<ChatRagConversationSearchResultDto>of(), false);

        var dto = new ChatEntitySearchResponseDto(people, groups, support, rag);

        assertThat(dto.people()).isEqualTo(people);
        assertThat(dto.groups()).isEqualTo(groups);
        assertThat(dto.support()).isEqualTo(support);
        assertThat(dto.rag()).isEqualTo(rag);
    }

    @Test
    void recentPlaceDtoHasExactlyTheContractFields() {
        Instant now = Instant.now();
        var dto = new ChatRecentPlaceDto(1L, "PEER_GROUP", "Title", now);

        assertThat(dto.conversationId()).isEqualTo(1L);
        assertThat(dto.kind()).isEqualTo("PEER_GROUP");
        assertThat(dto.title()).isEqualTo("Title");
        assertThat(dto.orderingTimestamp()).isEqualTo(now);
    }

    @Test
    void entitySearchResultDtoHasExactlyTheContractFields() {
        var place = new ChatRecentPlaceDto(1L, "RAG", "Title", Instant.now());
        var dto = new ChatEntitySearchResultDto(List.of(place));

        assertThat(dto.recentPlaces()).containsExactly(place);
    }
}
