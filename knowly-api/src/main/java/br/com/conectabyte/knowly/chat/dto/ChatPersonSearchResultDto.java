package br.com.conectabyte.knowly.chat.dto;

/**
 * Unified entity search (2026-08-10 amendment), REQ-20/REQ-24. Deliberately identical shape to
 * {@link CandidateUserDto} but kept as a distinct type -- a "could I start a conversation"
 * candidate and a "found via search" result mean different things even though today's field set
 * matches; a future field added to one for its own reason should not silently leak onto the other's
 * response shape.
 */
public record ChatPersonSearchResultDto(Long userId, String nickname, String avatarUrl) {}
