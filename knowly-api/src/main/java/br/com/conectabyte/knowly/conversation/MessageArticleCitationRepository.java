package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.ArticleUsageDto;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageArticleCitationRepository
        extends JpaRepository<MessageArticleCitation, Long> {

    @Query(
            """
            select new br.com.conectabyte.knowly.metrics.ArticleUsageDto(a.id, a.title, count(c))
            from MessageArticleCitation c join c.article a
            where a.tenant.id = :tenantId and a.active = true
            group by a.id, a.title
            order by count(c) desc
            """)
    List<ArticleUsageDto> usageByTenant(@Param("tenantId") Long tenantId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /**
     * specify/features/global-staff-dashboard-trends/SPEC.md REQ-2b/11: cross-tenant, day-bucketed
     * articles-read counts — deliberately no {@code tenant_id} predicate, this endpoint is never
     * scoped by {@code TenantFilter}.
     */
    @Query(
            value =
                    """
                    select date_trunc('day', created_at)::date as day, count(*) as count
                    from message_article_citations
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countCitationsByDay();

    @Query(
            value =
                    """
                    select date_trunc('day', created_at)::date as day, count(*) as count
                    from message_article_citations
                    where created_at >= :from
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countCitationsByDaySince(@Param("from") Instant from);
}
