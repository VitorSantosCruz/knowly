package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Conversation> findByIdAndOwnerId(Long id, Long ownerId);

    long countByTenantId(Long tenantId);

    /**
     * Cascades a tenant's own {@code deletedAt} to every one of its still-live conversations
     * (2026-08-04 product decision: a deleted tenant's own resources no longer make sense to keep
     * live) -- bulk update, not a Java loop, same reasoning as {@code
     * TenantMembershipRepository#deactivateAllByTenant}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "update Conversation c set c.deletedAt = CURRENT_TIMESTAMP where c.tenant.id ="
                    + " :tenantId and c.deletedAt is null")
    void softDeleteAllByTenant(@Param("tenantId") Long tenantId);

    long countByTenantIdAndCreatedAtGreaterThanEqual(Long tenantId, Instant from);

    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from conversations
                    where tenant_id = :tenantId
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countByDayForTenant(@Param("tenantId") Long tenantId);

    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from conversations
                    where tenant_id = :tenantId and created_at >= :from
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countByDayForTenantSince(
            @Param("tenantId") Long tenantId, @Param("from") Instant from);
}
