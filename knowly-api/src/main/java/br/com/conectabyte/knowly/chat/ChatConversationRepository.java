package br.com.conectabyte.knowly.chat;

import java.util.Optional;
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
}
