package br.com.conectabyte.knowly.chat;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * REQ-1/2/3/4/5/6-15: the native full-text search query backing {@code
 * ChatMessageSearchService}. Deliberately <b>not</b> a JPQL/HQL query -- {@code
 * websearch_to_tsquery()}, the {@code chat_participants} {@code EXISTS} subquery, and the dynamic
 * optional filters this feature needs cannot be expressed portably in HQL. Also deliberately its
 * own repository, not folded into {@link ChatMessageRepository}: this query returns a different
 * projection shape (message + resolved sender/conversation fields for {@code
 * ChatMessageSearchResultDto}) and a fundamentally different composition strategy (dynamic
 * optional-filter native SQL) than that repository's fixed-shape cursor queries.
 *
 * <p><b>Critical gotcha (AppSec correction, see PLAN.md):</b> as native SQL, {@code
 * searchPt}/{@code searchEn} are <b>not</b> covered by Hibernate's {@code TenantFilter}/{@code
 * SoftDeleteFilter} {@code @Filter} mechanism -- only HQL/JPQL queries and collection fetches
 * respect it (mirrors {@link ChatConversationRepository#findByIdRespectingFilter}'s own
 * precedent Javadoc for the same class of gap). Every scoping condition (tenant, participant,
 * conversation kind, archive/delete state) is therefore hand-written into the query text itself,
 * in the same {@code WHERE} clause as every other predicate -- never a post-filter step in Java,
 * and never omittable by a future edit that adds a new filter without re-reading this Javadoc.
 * Both methods take {@code activeTenantId} as a required bind parameter; the caller ({@code
 * ChatMessageSearchService}) resolves it from {@code TenantContext#getActiveTenantId()} and fails
 * closed (no query executed at all) when it is absent -- see that service's Javadoc.
 */
public interface ChatMessageSearchRepository extends Repository<ChatMessage, Long> {

    String SELECT_AND_JOIN =
            "SELECT m.id AS id, m.conversation_id AS conversationId, cc.title AS"
                    + " conversationTitle, m.sender_user_id AS senderUserId, COALESCE(up.full_name,"
                    + " u.email) AS senderNickname, m.content AS content, m.created_at AS createdAt"
                    + " FROM chat_messages m JOIN chat_conversations cc ON cc.id ="
                    + " m.conversation_id JOIN users u ON u.id = m.sender_user_id LEFT JOIN"
                    + " user_profiles up ON up.user_id = m.sender_user_id ";

    String SCOPE_PREDICATE =
            "WHERE EXISTS (SELECT 1 FROM chat_participants cp WHERE cp.conversation_id = cc.id AND"
                    + " cp.user_id = :callerId AND cp.deleted_at IS NULL) AND cc.tenant_id ="
                    + " :activeTenantId AND cc.kind IN ('PEER_DIRECT','PEER_GROUP') AND"
                    + " cc.archived_at IS NULL AND cc.deleted_at IS NULL AND m.deleted_at IS NULL"
                    + " AND (:senderId IS NULL OR m.sender_user_id = :senderId) AND (:conversationId"
                    + " IS NULL OR m.conversation_id = :conversationId) AND (CAST(:dateFrom AS"
                    + " timestamptz) IS NULL OR m.created_at >= :dateFrom) AND (CAST(:dateTo AS"
                    + " timestamptz) IS NULL OR m.created_at <= :dateTo) AND (:cursor IS NULL OR"
                    + " m.id < :cursor) ";

    String ORDER_AND_LIMIT = "ORDER BY m.id DESC LIMIT :limit";

    @Query(
            value =
                    SELECT_AND_JOIN
                            + SCOPE_PREDICATE
                            + "AND m.content_tsv_pt @@ websearch_to_tsquery('portuguese', :q) "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchPt(
            @Param("callerId") Long callerId,
            @Param("activeTenantId") Long activeTenantId,
            @Param("q") String q,
            @Param("senderId") Long senderId,
            @Param("conversationId") Long conversationId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    @Query(
            value =
                    SELECT_AND_JOIN
                            + SCOPE_PREDICATE
                            + "AND m.content_tsv_en @@ websearch_to_tsquery('english', :q) "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchEn(
            @Param("callerId") Long callerId,
            @Param("activeTenantId") Long activeTenantId,
            @Param("q") String q,
            @Param("senderId") Long senderId,
            @Param("conversationId") Long conversationId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    interface ChatMessageSearchRow {
        Long getId();

        Long getConversationId();

        String getConversationTitle();

        Long getSenderUserId();

        String getSenderNickname();

        String getContent();

        Instant getCreatedAt();
    }
}
