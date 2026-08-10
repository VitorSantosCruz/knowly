package br.com.conectabyte.knowly.chat.dto;

import java.time.Instant;

/**
 * Unified entity search (2026-08-10 amendment), REQ-25/REQ-26. {@code kind} is one of {@code
 * PEER_DIRECT}/{@code PEER_GROUP}/{@code SUPPORT} (from {@code ChatConversationKind}) or {@code
 * RAG} (the sibling {@code conversation.Conversation} entity, which has no {@code
 * ChatConversationKind} of its own) -- a plain {@code String}, not the enum itself, since the two
 * sources being merged here don't share one type.
 */
public record ChatRecentPlaceDto(
        Long conversationId, String kind, String title, Instant orderingTimestamp) {}
