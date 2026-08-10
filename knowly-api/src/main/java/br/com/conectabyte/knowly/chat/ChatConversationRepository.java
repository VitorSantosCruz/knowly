package br.com.conectabyte.knowly.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByTenantIdAndOwnerIdAndKind(
            Long tenantId, Long ownerId, ChatConversationKind kind);

    /**
     * Deliberately not the inherited {@code findById(Long)} -- that uses {@code
     * EntityManager#find}, which Hibernate's {@code @Filter} mechanism does not apply to
     * (primary-key lookups bypass filters; only HQL/JPQL queries and collection fetches respect
     * them). Every tenant-scoping-sensitive read in this feature must go through this query method
     * instead, so {@link br.com.conectabyte.knowly.tenancy.TenantFilterAspect}'s enable/disable
     * actually has an effect.
     */
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> findByIdRespectingFilter(@Param("id") Long id);

    /**
     * REQ-27: non-archived, non-deleted (implicit via {@code SoftDeleteFilter}) PEER_GROUP rows
     * whose visibility is discoverable. Eligibility/already-joined filtering happens in {@link
     * ChatConversationService} after this page is fetched, per PLAN's decision not to push
     * eligibility into SQL.
     */
    @Query(
            "select c from ChatConversation c where c.kind = br.com.conectabyte.knowly.chat"
                    + ".ChatConversationKind.PEER_GROUP and c.archivedAt is null and c.visibility"
                    + " in (br.com.conectabyte.knowly.chat.ChatGroupVisibility.REQUEST_TO_JOIN,"
                    + " br.com.conectabyte.knowly.chat.ChatGroupVisibility.PUBLIC)")
    Page<ChatConversation> findDiscoverable(Pageable pageable);

    List<ChatConversation> findByIdIn(List<Long> ids);

    /**
     * Unified entity search (2026-08-10 amendment), REQ-19: discoverable-group title match backing
     * {@code ChatConversationService#searchDiscoverableGroups}.
     *
     * <p><b>AppSec correction:</b> despite being ordinary JPQL (not native SQL like {@code
     * ChatMessageSearchRepository}), this query still needs its own explicit {@code tenant_id =
     * :activeTenantId} predicate written into the query text -- relying on Hibernate's
     * {@code @Filter} alone is not sufficient. {@code TenantFilterAspect} is a global
     * {@code @Around} advice that disables {@code TenantFilter} session-wide whenever a caller is
     * staff-capable with no active tenant selected, regardless of whether the calling code itself
     * ever reads {@code isStaff()}/{@code isStaffAdmin()} -- the filter's disabled state is a
     * property of the current Hibernate session, not of the calling code. The caller ({@code
     * ChatConversationService.searchDiscoverableGroups}) resolves {@code
     * TenantContext#getActiveTenantId()} itself and fails closed (no query executed) when absent.
     */
    @Query(
            "select c from ChatConversation c where c.kind = br.com.conectabyte.knowly.chat"
                    + ".ChatConversationKind.PEER_GROUP and c.archivedAt is null and c.visibility"
                    + " in (br.com.conectabyte.knowly.chat.ChatGroupVisibility.REQUEST_TO_JOIN,"
                    + " br.com.conectabyte.knowly.chat.ChatGroupVisibility.PUBLIC) and c.title ilike"
                    + " :pattern and c.tenant.id = :activeTenantId")
    Page<ChatConversation> findDiscoverableByTitle(
            @Param("pattern") String pattern,
            @Param("activeTenantId") Long activeTenantId,
            Pageable pageable);

    /**
     * Message-search role-based scoping (2026-08-10 amendment), REQ-5f/REQ-5h/REQ-5i/REQ-19:
     * id-only sibling of {@link #findDiscoverable(Pageable)}, backing {@code
     * ChatMessageSearchService}'s {@code additionalVisibleConversationIds} computation for a
     * tenant-bound non-admin caller. Ids only (not full pagination) because the caller needs the
     * complete eligible-candidate set to fold into a single SQL {@code ANY(...)} bind parameter,
     * not a UI page.
     *
     * <p><b>AppSec-required cap:</b> explicit {@code LIMIT 100} in the query itself (same ceiling
     * as {@code ChatCursor.MAX_PAGE_SIZE}/{@code TenantService}/{@code StaffService}'s {@code
     * MAX_PAGE_SIZE}), not a Java-side truncation after fetch -- a tenant with an unusually large
     * discoverable-group count cannot turn this into an unbounded-query DoS vector.
     */
    @Query(
            value =
                    "select c.id from chat_conversations c where c.kind = 'PEER_GROUP' and"
                            + " c.archived_at is null and c.deleted_at is null and c.visibility in"
                            + " ('PUBLIC','REQUEST_TO_JOIN') and c.tenant_id = :tenantId order by"
                            + " c.id LIMIT 100",
            nativeQuery = true)
    List<Long> findDiscoverableIds(@Param("tenantId") Long tenantId);

    /**
     * Platform-wide sibling of {@link #findDiscoverableIds(Long)}, backing the REQ-5f staff-
     * no-active-tenant branch of {@code ChatMessageSearchService} (no {@code tenant_id} predicate
     * at all -- deliberate, mirrors that branch's own no-tenant-restriction posture for its
     * participant/discoverability scope). Same {@code LIMIT 100} cap and rationale as {@link
     * #findDiscoverableIds(Long)}.
     */
    @Query(
            value =
                    "select c.id from chat_conversations c where c.kind = 'PEER_GROUP' and"
                            + " c.archived_at is null and c.deleted_at is null and c.visibility in"
                            + " ('PUBLIC','REQUEST_TO_JOIN') order by c.id LIMIT 100",
            nativeQuery = true)
    List<Long> findDiscoverableIdsPlatformWide();
}
