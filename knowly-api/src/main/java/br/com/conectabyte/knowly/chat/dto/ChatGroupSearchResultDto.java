package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatGroupVisibility;

/**
 * Unified entity search (2026-08-10 amendment), REQ-19/REQ-24. {@code isParticipant} lets the
 * frontend distinguish "open directly" from "join/request-to-join" without a second round-trip.
 */
public record ChatGroupSearchResultDto(
        Long id, String title, boolean isParticipant, ChatGroupVisibility visibility) {}
