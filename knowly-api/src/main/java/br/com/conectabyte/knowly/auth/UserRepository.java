package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Used by existence/uniqueness checks (invite dedup, staff creation) so a soft-deleted user's
     * email is treated as available for reuse -- logical-delete-everywhere (2026-08-04).
     */
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    List<User> findByGlobalRoleInAndDeletedAtIsNull(List<GlobalRole> globalRoles);

    List<User> findByGlobalRoleInAndEmailContainingIgnoreCaseAndDeletedAtIsNull(
            List<GlobalRole> globalRoles, String email);

    long countByGlobalRoleInAndDeletedAtIsNull(List<GlobalRole> globalRoles);

    /**
     * specify/features/staff-rbac-management-operations/PLAN.md: pessimistic write lock over every
     * user of {@code role}, used by demote/delete-{@code STAFF_ADMIN} to close the TOCTOU window on
     * the last-admin floor check -- locks every current holder (including the target), so a second
     * concurrent demote/delete against a different "last remaining" admin blocks until the first
     * transaction commits/rolls back. Excludes soft-deleted users (logical-delete-everywhere,
     * 2026-08-04) -- a deleted admin no longer occupies a floor-check seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.globalRole = :role and u.deletedAt is null")
    List<User> findByGlobalRoleForUpdate(GlobalRole role);

    long countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            List<GlobalRole> globalRoles, Instant from, Instant to);

    /**
     * specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1/2: cross-tenant,
     * day-bucketed cumulative running total of internal staff headcount (`STAFF`/`STAFF_ADMIN`),
     * computed over full history regardless of the requested period, same reasoning as {@link
     * br.com.conectabyte.knowly.tenancy.TenantRepository#countCumulativeTenantsByDay()}. `STAFF`/
     * `STAFF_ADMIN` are hardcoded string literals matching {@code GlobalRole}'s string persistence
     * — a fixed, non-parameterized pair of roles, not client-supplied.
     */
    @Query(
            value =
                    """
                    with daily as (
                      select date_trunc('day', created_at AT TIME ZONE 'UTC')::date as day, count(*) as cnt
                      from users
                      where global_role in ('STAFF', 'STAFF_ADMIN')
                      group by day
                    )
                    select day, sum(cnt) over (order by day) as count
                    from daily
                    order by day
                    """,
            nativeQuery = true)
    List<DailyCountProjection> countCumulativeStaffByDay();
}
