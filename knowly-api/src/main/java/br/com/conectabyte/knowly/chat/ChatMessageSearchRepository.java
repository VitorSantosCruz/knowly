package br.com.conectabyte.knowly.chat;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * REQ-1/2/3/4/5/6-15 (REQ-1 through REQ-15 original; REQ-5e-REQ-5j role-based scoping amendment):
 * the native full-text search queries backing {@code ChatMessageSearchService}. Deliberately
 * <b>not</b> a JPQL/HQL query -- {@code websearch_to_tsquery()}, the {@code chat_participants}
 * {@code EXISTS} subquery, and the dynamic optional filters this feature needs cannot be expressed
 * portably in HQL. Also deliberately its own repository, not folded into {@link
 * ChatMessageRepository}: this query returns a different projection shape (message + resolved
 * sender/conversation fields for {@code ChatMessageSearchResultDto}) and a fundamentally different
 * composition strategy (dynamic optional-filter native SQL) than that repository's fixed-shape
 * cursor queries.
 *
 * <p><b>Critical gotcha (AppSec correction, see PLAN.md):</b> as native SQL, none of the methods
 * below are covered by Hibernate's {@code TenantFilter}/{@code SoftDeleteFilter} {@code @Filter}
 * mechanism -- only HQL/JPQL queries and collection fetches respect it (mirrors {@link
 * ChatConversationRepository#findByIdRespectingFilter}'s own precedent Javadoc for the same class
 * of gap). Every scoping condition (tenant, participant, conversation kind, archive/delete state)
 * is therefore hand-written into the query text itself, in the same {@code WHERE} clause as every
 * other predicate -- never a post-filter step in Java, and never omittable by a future edit that
 * adds a new filter without re-reading this Javadoc.
 *
 * <p><b>Role-based scoping (REQ-5e-REQ-5j, 2026-08-10 amendment, context-boundary correction):</b>
 * {@code BASE_PREDICATE} is always applied (conversation kind, archive/delete state, and the
 * optional {@code senderId}/{@code conversationId}/date-range/cursor filters); one of three
 * mutually exclusive <b>scope fragments</b> is appended on top of it, selected in Java by {@code
 * ChatMessageSearchService} -- after it first resolves the caller's current context (active tenant
 * vs. staff scope) and only then branches admin-vs-non-admin within that context -- before the
 * query method is chosen -- never combined, and never a post-filter step:
 *
 * <ul>
 *   <li>{@code searchStaffScopeUnrestrictedPt}/{@code En} (REQ-5e, corrected, {@code STAFF_ADMIN}
 *       currently in staff scope): {@code cc.tenant_id IS NULL} only, no participant/
 *       discoverability predicate -- unrestricted within staff scope, but never a tenant-owned
 *       conversation, even one the caller separately holds a participant row on (REQ-5j). <b>There
 *       used to be a {@code searchUnrestrictedPt}/{@code En} pair here with no tenant predicate at
 *       all ("platform-wide") -- deleted 2026-08-10 after a live Playwright test confirmed it let a
 *       {@code STAFF_ADMIN} viewing chat in staff scope see a tenant member's message. Do not
 *       reintroduce a fragment with no {@code tenant_id} predicate for any role.</b>
 *   <li>{@code searchTenantUnrestrictedPt}/{@code En} (REQ-5g, active-tenant {@code MEMBER_ADMIN}):
 *       {@code cc.tenant_id = :activeTenantId} only, no participant/ discoverability predicate --
 *       unrestricted within that one tenant, never cross-tenant.
 *   <li>{@code searchScopedPt}/{@code En} (REQ-5f/REQ-5h, every non-admin): the {@code
 *       chat_participants} {@code EXISTS} clause, OR'd with {@code cc.id = ANY(
 *       :additionalVisibleConversationIds)} (public/request-to-join discoverable groups the caller
 *       is eligible for but hasn't joined, resolved in Java -- see {@code
 *       ChatMessageSearchService}), both inside the same {@code ((:activeTenantId IS NULL AND
 *       cc.tenant_id IS NULL) OR cc.tenant_id = :activeTenantId)} guard. This nullable-aware guard
 *       is what strictly isolates REQ-5f's staff-no-active-tenant case (a {@code null} {@code
 *       activeTenantId}) to staff-scope conversations only ({@code cc.tenant_id IS NULL}) --
 *       <b>never</b> a tenant-owned conversation the caller happens to also be a participant of via
 *       an unrelated tenant membership -- while REQ-5h's tenant-{@code MEMBER} case (a non-null
 *       {@code activeTenantId}) stays bound to that one tenant. (AppSec correction, 2026-08-10: an
 *       earlier version of this guard used a bare {@code (:activeTenantId IS NULL OR cc.tenant_id =
 *       :activeTenantId)}, which is trivially true for every row once {@code activeTenantId} is
 *       {@code null} -- collapsing REQ-5f's "staff, no active tenant" case into an unrestricted
 *       cross-tenant participant scan instead of a staff-scope-only one. Fixed by requiring {@code
 *       cc.tenant_id IS NULL} in that branch too.) {@code ChatMessageSearchService} asserts, via an
 *       {@code IllegalStateException} invariant, that {@code activeTenantId} is only ever {@code
 *       null} for the staff-no-tenant branch, never silently for the tenant-bound branch.
 * </ul>
 */
public interface ChatMessageSearchRepository extends Repository<ChatMessage, Long> {

    String SELECT_AND_JOIN =
            "SELECT m.id AS id, m.conversation_id AS conversationId, cc.title AS"
                    + " conversationTitle, m.sender_user_id AS senderUserId, COALESCE(up.full_name,"
                    + " u.email) AS senderNickname, m.content AS content, m.created_at AS createdAt"
                    + " FROM chat_messages m JOIN chat_conversations cc ON cc.id ="
                    + " m.conversation_id JOIN users u ON u.id = m.sender_user_id LEFT JOIN"
                    + " user_profiles up ON up.user_id = m.sender_user_id ";

    String BASE_PREDICATE =
            "WHERE cc.kind IN ('PEER_DIRECT','PEER_GROUP') AND cc.archived_at IS NULL AND"
                    + " cc.deleted_at IS NULL AND m.deleted_at IS NULL AND (:senderId IS NULL OR"
                    + " m.sender_user_id = :senderId) AND (:conversationId IS NULL OR"
                    + " m.conversation_id = :conversationId) AND (CAST(:dateFrom AS timestamptz) IS"
                    + " NULL OR m.created_at >= :dateFrom) AND (CAST(:dateTo AS timestamptz) IS NULL"
                    + " OR m.created_at <= :dateTo) AND (:cursor IS NULL OR m.id < :cursor) ";

    String SCOPE_STAFF_SCOPE_UNRESTRICTED = "AND cc.tenant_id IS NULL ";

    String SCOPE_TENANT_UNRESTRICTED = "AND cc.tenant_id = :activeTenantId ";

    String SCOPE_PARTICIPANT_AND_DISCOVERABLE =
            "AND ((CAST(:activeTenantId AS bigint) IS NULL AND cc.tenant_id IS NULL) OR"
                    + " cc.tenant_id = :activeTenantId) AND (EXISTS (SELECT 1 FROM"
                    + " chat_participants cp WHERE cp.conversation_id = cc.id AND cp.user_id ="
                    + " :callerId AND cp.deleted_at IS NULL) OR cc.id ="
                    + " ANY(:additionalVisibleConversationIds)) ";

    String ORDER_AND_LIMIT = "ORDER BY m.id DESC LIMIT :limit";

    /**
     * Bugfix (type-ahead prefix matching): {@code websearch_to_tsquery} matches the query only
     * against the exact stemmed lexeme it derives from {@code :q} -- fine for a complete word (e.g.
     * "mensag", which happens to equal Portuguese's stem of "mensagem"), but wrong for this
     * feature's live type-ahead use case, where "men" (a true in-progress prefix of "mensagem")
     * must also match. Postgres's own recommended pattern for this is a hand-built {@code
     * to_tsquery} with a trailing {@code :*} on the last lexeme -- but unlike {@code
     * websearch_to_tsquery}, raw {@code to_tsquery} treats {@code & | ! ( ) :} and quote characters
     * in its input as boolean/ prefix/weight *operators*, not literal text, so a hand-built query
     * string is an injection surface for user-controlled {@code :q} (e.g. a crafted {@code "a | b"}
     * could turn an intended AND-match into an OR-match across otherwise-unrelated messages). This
     * expression therefore:
     *
     * <ol>
     *   <li>strips every character from {@code :q} except letters (including the Latin-1 Supplement
     *       range covering Portuguese diacritics), digits, and whitespace -- eliminating every
     *       {@code to_tsquery} operator character before it ever reaches the parser;
     *   <li>collapses runs of whitespace and trims, so multiple spaces don't produce empty tokens;
     *   <li>appends {@code :*} to the *last* whitespace-delimited token only, prefix-matching just
     *       the term the user is presumably still typing while leaving earlier, presumably-complete
     *       terms exact/stemmed-matched;
     *   <li>joins the (now-safe) tokens with {@code &} to preserve the existing multi-word AND
     *       semantics.
     * </ol>
     *
     * A {@code :q} that sanitizes down to the empty string yields an empty {@code to_tsquery}
     * result, which matches nothing (not an error) -- the same effectively-no-match outcome the
     * caller already gets from any other non-matching query.
     */
    String PREFIX_TSQUERY_PT =
            "to_tsquery('portuguese', regexp_replace(regexp_replace(btrim(regexp_replace(regexp_replace(:q,"
                    + " '[^a-zA-Z0-9À-ÿ\\s]', ' ', 'g'), '\\s+', ' ', 'g')), '(\\S+)$', '\\1:*'), ' ',"
                    + " ' & ', 'g'))";

    String PREFIX_TSQUERY_EN =
            "to_tsquery('english', regexp_replace(regexp_replace(btrim(regexp_replace(regexp_replace(:q,"
                    + " '[^a-zA-Z0-9À-ÿ\\s]', ' ', 'g'), '\\s+', ' ', 'g')), '(\\S+)$', '\\1:*'), ' ',"
                    + " ' & ', 'g'))";

    // --- REQ-5e (corrected): STAFF_SCOPE_UNRESTRICTED (STAFF_ADMIN, currently in staff scope) ---

    @Query(
            value =
                    SELECT_AND_JOIN
                            + BASE_PREDICATE
                            + SCOPE_STAFF_SCOPE_UNRESTRICTED
                            + "AND m.content_tsv_pt @@ "
                            + PREFIX_TSQUERY_PT
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchStaffScopeUnrestrictedPt(
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
                            + BASE_PREDICATE
                            + SCOPE_STAFF_SCOPE_UNRESTRICTED
                            + "AND m.content_tsv_en @@ "
                            + PREFIX_TSQUERY_EN
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchStaffScopeUnrestrictedEn(
            @Param("q") String q,
            @Param("senderId") Long senderId,
            @Param("conversationId") Long conversationId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    // --- REQ-5g: TENANT_UNRESTRICTED (active-tenant MEMBER_ADMIN) ---

    @Query(
            value =
                    SELECT_AND_JOIN
                            + BASE_PREDICATE
                            + SCOPE_TENANT_UNRESTRICTED
                            + "AND m.content_tsv_pt @@ "
                            + PREFIX_TSQUERY_PT
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchTenantUnrestrictedPt(
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
                            + BASE_PREDICATE
                            + SCOPE_TENANT_UNRESTRICTED
                            + "AND m.content_tsv_en @@ "
                            + PREFIX_TSQUERY_EN
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchTenantUnrestrictedEn(
            @Param("activeTenantId") Long activeTenantId,
            @Param("q") String q,
            @Param("senderId") Long senderId,
            @Param("conversationId") Long conversationId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    // --- REQ-5f/REQ-5h/REQ-5i: PARTICIPANT_AND_DISCOVERABLE (every non-admin) ---

    @Query(
            value =
                    SELECT_AND_JOIN
                            + BASE_PREDICATE
                            + SCOPE_PARTICIPANT_AND_DISCOVERABLE
                            + "AND m.content_tsv_pt @@ "
                            + PREFIX_TSQUERY_PT
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchScopedPt(
            @Param("callerId") Long callerId,
            @Param("activeTenantId") Long activeTenantId,
            @Param("additionalVisibleConversationIds") Long[] additionalVisibleConversationIds,
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
                            + BASE_PREDICATE
                            + SCOPE_PARTICIPANT_AND_DISCOVERABLE
                            + "AND m.content_tsv_en @@ "
                            + PREFIX_TSQUERY_EN
                            + " "
                            + ORDER_AND_LIMIT,
            nativeQuery = true)
    List<ChatMessageSearchRow> searchScopedEn(
            @Param("callerId") Long callerId,
            @Param("activeTenantId") Long activeTenantId,
            @Param("additionalVisibleConversationIds") Long[] additionalVisibleConversationIds,
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
