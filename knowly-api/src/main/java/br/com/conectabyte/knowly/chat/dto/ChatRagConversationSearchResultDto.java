package br.com.conectabyte.knowly.chat.dto;

/**
 * Unified entity search (2026-08-10 amendment), REQ-22/REQ-24.
 *
 * <p><b>RAG conversation turn-content search (2026-08-11 amendment), REQ-27-REQ-33:</b> {@code
 * matchedSnippet}/{@code matchedRole} are additive, nullable fields -- {@code null} for a
 * title-only match, populated for a content-matched (or both-matched) conversation with the single
 * most-recent matching {@link br.com.conectabyte.knowly.conversation.Message}'s truncated content
 * and role (REQ-31's tie-break).
 */
public record ChatRagConversationSearchResultDto(
        Long id, String title, String matchedSnippet, String matchedRole) {

    public ChatRagConversationSearchResultDto(Long id, String title) {
        this(id, title, null, null);
    }
}
