package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.metrics.TenantActiveCountProjection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {

    Optional<TenantMembership> findByUserAndTenant(User user, Tenant tenant);

    List<TenantMembership> findByUserAndActiveTrue(User user);

    List<TenantMembership> findByTenantIdAndActiveTrue(Long tenantId);

    long countByTenantIdAndActive(Long tenantId, boolean active);

    /**
     * specify/features/staff-rbac-management-operations/PLAN.md: non-locking sibling of {@link
     * #findByTenantIdAndRoleAndActiveTrueForUpdate}, used by the read-only {@code
     * isLastAdminOfType} detail-DTO field -- counts only active memberships of {@code role} in
     * {@code tenantId}.
     */
    long countByTenantIdAndRoleAndActiveTrue(Long tenantId, MembershipRole role);

    /**
     * specify/features/staff-rbac-management-operations/PLAN.md: pessimistic write lock over every
     * active membership of {@code role} in {@code tenantId}, used by demote/delete-{@code
     * MEMBER_ADMIN} to close the TOCTOU window on the last-admin-per-tenant floor check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select m from TenantMembership m where m.tenant.id = :tenantId and m.role = :role and"
                    + " m.active = true")
    List<TenantMembership> findByTenantIdAndRoleAndActiveTrueForUpdate(
            Long tenantId, MembershipRole role);

    /**
     * specify/features/active-members-trend/PLAN.md: cross-tenant, systemwide aggregate feeding the
     * daily {@code ActiveMemberSnapshotScheduler} job -- deliberately no {@code tenant_id}
     * predicate, mirroring {@code TenantRepository.countTenantsByDay()}'s shape for a systemwide
     * job (never used for a per-request read).
     */
    @Query(
            value =
                    """
                    select tenant_id as tenantId, count(*) as count
                    from tenant_memberships
                    where active = true
                    group by tenant_id
                    """,
            nativeQuery = true)
    List<TenantActiveCountProjection> countActiveGroupedByTenant();
}
