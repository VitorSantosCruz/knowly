package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByGlobalRoleIn(List<GlobalRole> globalRoles);

    List<User> findByGlobalRoleInAndEmailContainingIgnoreCase(
            List<GlobalRole> globalRoles, String email);

    long countByGlobalRoleIn(List<GlobalRole> globalRoles);

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
