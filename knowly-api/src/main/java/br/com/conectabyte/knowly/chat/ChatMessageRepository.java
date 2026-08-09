package br.com.conectabyte.knowly.chat;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** REQ-49: soft-delete every message of a deleted conversation, in the same transaction. */
    @Modifying
    @Query(
            "update ChatMessage m set m.deletedAt = :deletedAt where m.conversation.id ="
                    + " :conversationId")
    void softDeleteAllByConversationId(
            @Param("conversationId") Long conversationId, @Param("deletedAt") Instant deletedAt);

    @Query(
            "select m from ChatMessage m where m.conversation.id = :conversationId "
                    + "and m.id < :cursor order by m.id desc")
    List<ChatMessage> findBeforeCursor(
            @Param("conversationId") Long conversationId,
            @Param("cursor") Long cursor,
            org.springframework.data.domain.Pageable pageable);

    @Query(
            "select m from ChatMessage m where m.conversation.id = :conversationId "
                    + "order by m.id desc")
    List<ChatMessage> findNewestFirst(
            @Param("conversationId") Long conversationId,
            org.springframework.data.domain.Pageable pageable);

    @Query(
            "select m from ChatMessage m where m.conversation.id = :conversationId "
                    + "and m.id > :cursor order by m.id asc")
    List<ChatMessage> findAfterCursor(
            @Param("conversationId") Long conversationId,
            @Param("cursor") Long cursor,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Backs {@code ChatConversationSummaryDto#lastMessageAt} (chat-unified-ui frontend gap): one
     * aggregate query per {@code listConversations} call rather than N, keyed by conversation id so
     * a conversation with zero messages simply doesn't appear in the result (falls back to the
     * conversation's own {@code createdAt} in the caller).
     */
    @Query(
            "select m.conversation.id as conversationId, max(m.createdAt) as lastMessageAt "
                    + "from ChatMessage m where m.conversation.id in :conversationIds "
                    + "group by m.conversation.id")
    List<ConversationLastMessageAt> findLastMessageAtByConversationIdIn(
            @Param("conversationIds") List<Long> conversationIds);

    interface ConversationLastMessageAt {
        Long getConversationId();

        Instant getLastMessageAt();
    }
}
