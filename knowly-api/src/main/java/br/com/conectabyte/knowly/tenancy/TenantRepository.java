package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    long countByCreatedAtGreaterThanEqual(Instant from);

    /**
     * REQ-4/REQ-5 (tenant-creation): proactive uniqueness check, see TenantService#createTenant.
     * tenant-crud REQ-12: scoped to active (non-soft-deleted) tenants only -- the partial unique
     * index (V25) already enforces this at the DB level, but this proactive check must mirror the
     * same scope, or a soft-deleted tenant's taxId would still be rejected here before ever
     * reaching the DB constraint.
     */
    boolean existsByTaxIdAndDeletedAtIsNull(String taxId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /**
     * specify/features/global-staff-dashboard-trends/SPEC.md REQ-2a/11: cross-tenant, day-bucketed
     * new-tenant counts — deliberately no {@code tenant_id} predicate, this endpoint is never
     * scoped by {@code TenantFilter}.
     */
    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from tenants
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countTenantsByDay();

    @Query(
            value =
                    """
                    select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as count
                    from tenants
                    where created_at >= :from
                    group by day
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countTenantsByDaySince(@Param("from") Instant from);

    /**
     * specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1/2: cross-tenant,
     * day-bucketed cumulative running total of tenants, computed over full history regardless of
     * the requested period (see that feature's PLAN.md "Architectural decisions" — bounding this
     * query by the display window would understate the true running total for early days in the
     * window). The projection's {@code count} column is aliased to reuse {@link
     * br.com.conectabyte.knowly.metrics.DailyCountProjection} even though it now holds a cumulative
     * value, not a per-day-created value.
     */
    @Query(
            value =
                    """
                    with daily as (
                      select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as cnt
                      from tenants
                      group by day
                    )
                    select day, sum(cnt) over (order by day) as count
                    from daily
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countCumulativeTenantsByDay();

    /**
     * specify/features/tenant-pagination-search/SPEC.md REQ-2/5/6/7/9 (field names updated by
     * tenant-creation/PLAN.md's reconciliation): DB-level pagination and case-insensitive substring
     * search across {@code name}/{@code legalName}/{@code taxId}, OR'd together. {@code search ==
     * null} short-circuits the {@code WHERE} clause to match every row.
     */
    @Query(
            """
            SELECT t FROM Tenant t
            WHERE CAST(:search AS string) IS NULL
               OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(t.legalName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(t.taxId) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
            """)
    Page<Tenant> search(@Param("search") String search, Pageable pageable);
}
