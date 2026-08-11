package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.DailyRoleCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversation_Tenant_IdAndRole(Long tenantId, MessageRole role);

    long countByConversation_Tenant_IdAndRoleAndCreatedAtGreaterThanEqual(
            Long tenantId, MessageRole role, Instant from);

    @Query(
            value =
                    """
                    select date_trunc('day', m.created_at AT TIME ZONE 'UTC')::date as day, m.role as role, count(*) as count
                    from messages m
                    join conversations c on c.id = m.conversation_id
                    where c.tenant_id = :tenantId
                    group by day, role
                    order by day
                    """,
            nativeQuery = true)
    List<DailyRoleCountProjection> countByDayAndRoleForTenant(@Param("tenantId") Long tenantId);

    @Query(
            value =
                    """
                    select date_trunc('day', m.created_at AT TIME ZONE 'UTC')::date as day, m.role as role, count(*) as count
                    from messages m
                    join conversations c on c.id = m.conversation_id
                    where c.tenant_id = :tenantId and m.created_at >= :from
                    group by day, role
                    order by day
                    """,
            nativeQuery = true)
    List<DailyRoleCountProjection> countByDayAndRoleForTenantSince(
            @Param("tenantId") Long tenantId, @Param("from") Instant from);

    /**
     * RAG conversation turn-content search (2026-08-11 amendment), REQ-27-REQ-33: backs {@code
     * ConversationService#searchOwn}'s content-match half.
     *
     * <p><b>Explicit predicates, same rationale as {@code
     * ConversationRepository#searchByOwnerAndTitle}:</b> {@code ownerId}/{@code tenantId}/{@code
     * deletedAt IS NULL} are written directly into this query's text rather than relying on {@code
     * Conversation}'s own {@code @Filter(TenantFilter)}/{@code @Filter(SoftDeleteFilter)} -- {@code
     * Message} itself carries no {@code @Filter}, and Hibernate filters do not automatically
     * propagate through a {@code ManyToOne} traversal (Message -> Conversation) inside a
     * {@code @Query}'s {@code WHERE} clause the way they would apply to an entity-rooted query. A
     * staff caller with no active tenant selected must not silently widen this into "every tenant's
     * turns this owner ever wrote."
     */
    @Query(
            "SELECT m FROM Message m WHERE m.conversation.owner.id = :ownerId"
                    + " AND m.conversation.tenant.id = :tenantId"
                    + " AND m.conversation.deletedAt IS NULL"
                    + " AND LOWER(m.content) LIKE LOWER(:pattern)"
                    + " ORDER BY m.createdAt DESC")
    Page<Message> searchByConversationOwnerAndContent(
            @Param("ownerId") Long ownerId,
            @Param("tenantId") Long tenantId,
            @Param("pattern") String pattern,
            Pageable pageable);
}
