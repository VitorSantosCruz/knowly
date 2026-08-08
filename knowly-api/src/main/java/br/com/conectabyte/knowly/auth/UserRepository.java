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
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Overrides {@code JpaRepository}'s inherited {@code findById}, which delegates to {@code
     * EntityManager#find} -- a well-known Hibernate limitation is that entity-by-primary-key
     * loading does not honor {@code @Filter}s (see {@code
     * ChatConversationRepository#findByIdRespectingFilter} for the identical, already-established
     * workaround for {@code TenantFilter}). Expressing this as an explicit JPQL {@code SELECT}
     * instead makes {@link br.com.conectabyte.knowly.softdelete.SoftDeleteFilter} actually apply,
     * closing the exact gap {@code ChatEligibilityService}/{@code ChatConversationService}'s
     * unfiltered {@code findById} calls exploited
     * (specify/features/soft-delete-default-filter/SPEC.md).
     */
    @Override
    @Query("select u from User u where u.id = :id")
    Optional<User> findById(@Param("id") Long id);

    /**
     * General email lookup -- used across many call sites (auth flows, permission-check aspects,
     * audit aspects) that are not all guaranteed to run inside a {@code @Transactional} *service*
     * method (e.g. an {@code @Around} advice wrapping the service call), so this method
     * deliberately keeps no explicit {@code deletedAt} predicate and is **not** collapsed with
     * {@link #findByEmailIgnoreCaseAndDeletedAtIsNull} — unlike the other renames in
     * specify/features/soft-delete-default-filter/PLAN.md, these two have genuinely different
     * call-site guarantees, so merging them is explicitly out of scope for this feature's Phase 5
     * (see TASKS.md).
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Used by existence/uniqueness checks (invite dedup, staff creation) so a soft-deleted user's
     * email is treated as available for reuse -- logical-delete-everywhere (2026-08-04).
     */
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /**
     * Not renamed to drop the {@code DeletedAtIsNull} suffix despite
     * specify/features/soft-delete-default-filter/PLAN.md's rename table listing it: {@link
     * br.com.conectabyte.knowly.identity.ProfileEditRequestService} calls this from a deliberately
     * non-{@code @Transactional} method (see that class's own Javadoc), so {@link
     * br.com.conectabyte.knowly.softdelete.SoftDeleteFilterAspect} never enables {@code
     * softDeleteFilter} for that call -- the explicit predicate is genuinely load-bearing there,
     * not redundant, even though every other current caller (all {@code @Transactional}) would be
     * fine either way. Renaming would silently reintroduce the leak class this feature fixes for
     * that one caller.
     */
    List<User> findByGlobalRoleInAndDeletedAtIsNull(List<GlobalRole> globalRoles);

    /**
     * Used by chat participant resolution (createConversation) so a soft-deleted user's id can no
     * longer be added to a brand-new conversation -- logical-delete-everywhere (2026-08-04).
     */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Used by {@link br.com.conectabyte.knowly.chat.ChatEligibilityService#listCandidates} so a
     * soft-deleted user never appears as an eligible chat participant candidate --
     * logical-delete-everywhere (2026-08-04).
     */
    List<User> findAllByDeletedAtIsNull();

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
