package br.com.conectabyte.knowly.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

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
}
