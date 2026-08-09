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
}
