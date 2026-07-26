package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Conversation> findByIdAndOwnerId(Long id, Long ownerId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndCreatedAtGreaterThanEqual(Long tenantId, Instant from);

    @Query(
            value =
                    """
                    select date_trunc('day', created_at)::date as day, count(*) as count
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
                    select date_trunc('day', created_at)::date as day, count(*) as count
                    from conversations
                    where tenant_id = :tenantId and created_at >= :from
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countByDayForTenantSince(
            @Param("tenantId") Long tenantId, @Param("from") Instant from);
}
