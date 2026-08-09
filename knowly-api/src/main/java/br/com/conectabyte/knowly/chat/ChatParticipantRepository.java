package br.com.conectabyte.knowly.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ChatParticipant> findByConversationId(Long conversationId);

    List<ChatParticipant> findByUserId(Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);

    List<ChatParticipant> findByConversationIdAndAdminTrue(Long conversationId);

    long countByConversationIdAndAdminTrue(Long conversationId);

    /**
     * REQ-54's succession-selection query -- explicit {@code @Query} since the tie-break spans two
     * columns, one of which ({@code user.id}) is on the associated {@code User}, mirroring {@link
     * ChatConversationRepository#findByIdRespectingFilter}'s precedent of preferring an explicit
     * JPQL query over a derived-method name once the query crosses an association.
     */
    @Query(
            "select p from ChatParticipant p where p.conversation.id = :conversationId order by"
                    + " p.joinedAt asc, p.user.id asc")
    List<ChatParticipant> findRemainingOrderedBySeniority(
            @Param("conversationId") Long conversationId);

    /** REQ-49: soft-delete every participant row of a deleted conversation, same transaction. */
    @Modifying
    @Query(
            "update ChatParticipant p set p.deletedAt = :deletedAt where p.conversation.id ="
                    + " :conversationId")
    void softDeleteAllByConversationId(
            @Param("conversationId") Long conversationId, @Param("deletedAt") Instant deletedAt);
}
