package br.com.conectabyte.knowly.chat.dto;

/**
 * Unified entity search (2026-08-10 amendment), REQ-21. No richer shape needed -- Support defers
 * entirely to its own existing detail endpoints once opened; this result kind only needs to say
 * "yes, you have a reachable Support channel, here's its id."
 */
public record ChatSupportSearchResultDto(Long channelId) {}
