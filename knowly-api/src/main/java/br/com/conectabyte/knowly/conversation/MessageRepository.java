package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.DailyRoleCountProjection;
import java.time.Instant;
import java.util.List;
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
}
