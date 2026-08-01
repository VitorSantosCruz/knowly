package br.com.conectabyte.knowly.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ActiveMemberSnapshotRepository extends JpaRepository<ActiveMemberSnapshot, Long> {

    /**
     * specify/features/active-members-trend/PLAN.md REQ-2/3: the {@code ON CONFLICT} upsert is what
     * makes a retried/duplicate job run for the same (tenant, day) idempotent -- at most one row
     * per (tenant_id, snapshot_date), the latest value winning.
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    insert into active_member_snapshots
                        (tenant_id, snapshot_date, active_count, created_by, updated_by)
                    values (:tenantId, :snapshotDate, :activeCount, :actor, :actor)
                    on conflict (tenant_id, snapshot_date)
                    do update set active_count = excluded.active_count, updated_by = excluded.updated_by, updated_at = now()
                    """,
            nativeQuery = true)
    void upsert(
            @Param("tenantId") Long tenantId,
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("activeCount") long activeCount,
            @Param("actor") String actor);

    /**
     * specify/features/active-members-trend/PLAN.md: appsec-reviewed -- this read query and {@link
     * #countByTenant(Long)} each carry their own explicit {@code tenant_id = :tenantId} predicate,
     * never collapsed into a single query that only applies it on one branch.
     */
    @Query(
            value =
                    """
                    select snapshot_date as day, active_count as count
                    from active_member_snapshots
                    where tenant_id = :tenantId
                    order by snapshot_date
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countByTenant(@Param("tenantId") Long tenantId);

    @Query(
            value =
                    """
                    select snapshot_date as day, active_count as count
                    from active_member_snapshots
                    where tenant_id = :tenantId and snapshot_date >= :from
                    order by snapshot_date
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countByTenantSince(
            @Param("tenantId") Long tenantId, @Param("from") Instant from);
}
