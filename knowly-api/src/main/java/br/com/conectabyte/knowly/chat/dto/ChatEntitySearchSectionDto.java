package br.com.conectabyte.knowly.chat.dto;

import java.util.List;

/**
 * Unified entity search (2026-08-10 amendment): a per-kind, per-group-capped result group, with a
 * {@code hasMore} signal the frontend's "see more" action can act on (a {@code type}+{@code offset}
 * request against the same endpoint) rather than an exact overflow count (avoids a cheap extra
 * {@code COUNT(*)} query per section on every keystroke).
 */
public record ChatEntitySearchSectionDto<T>(List<T> results, boolean hasMore) {}
