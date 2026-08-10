package br.com.conectabyte.knowly.chat.dto;

/**
 * Unified entity search (2026-08-10 amendment): the non-blank-{@code q} response shape. {@code
 * support} is {@code null} when the caller has no reachable Support channel/label match -- Support
 * has no "more" concept (at most one result).
 */
public record ChatEntitySearchResponseDto(
        ChatEntitySearchSectionDto<ChatPersonSearchResultDto> people,
        ChatEntitySearchSectionDto<ChatGroupSearchResultDto> groups,
        ChatSupportSearchResultDto support,
        ChatEntitySearchSectionDto<ChatRagConversationSearchResultDto> rag) {}
