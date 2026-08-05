package br.com.conectabyte.knowly.article;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByTenantIdAndActiveTrue(Long tenantId);

    long countByTenantIdAndActiveTrue(Long tenantId);

    /**
     * Cascades a tenant's own {@code deletedAt} to every one of its still-active articles
     * (2026-08-04 product decision: a deleted tenant's own resources no longer make sense to keep
     * live) -- bulk update, not a Java loop, same reasoning as {@code
     * TenantMembershipRepository#deactivateAllByTenant}.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "update Article a set a.active = false where a.tenant.id = :tenantId and a.active = true")
    void deactivateAllByTenant(@Param("tenantId") Long tenantId);

    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from articles
                    where tenant_id = :tenantId and active = true
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countActiveByDayForTenant(@Param("tenantId") Long tenantId);

    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from articles
                    where tenant_id = :tenantId and active = true and created_at >= :from
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countActiveByDayForTenantSince(
            @Param("tenantId") Long tenantId, @Param("from") Instant from);
}
