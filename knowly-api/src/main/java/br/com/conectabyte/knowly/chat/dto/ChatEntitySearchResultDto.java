package br.com.conectabyte.knowly.chat.dto;

import java.util.List;

/**
 * Unified entity search (2026-08-10 amendment), REQ-25: returned only for the blank-query ("recent
 * places") case -- a distinct top-level type from {@link ChatEntitySearchResponseDto} rather than a
 * fifth optional field on it, since the two are mutually exclusive response shapes for the same
 * endpoint (present {@code q} vs. blank {@code q}), not a gradually-filled-in single shape.
 */
public record ChatEntitySearchResultDto(List<ChatRecentPlaceDto> recentPlaces) {}
