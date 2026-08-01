package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.metrics.TenantActiveCountProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {

    Optional<TenantMembership> findByUserAndTenant(User user, Tenant tenant);

    List<TenantMembership> findByUserAndActiveTrue(User user);

    List<TenantMembership> findByTenantIdAndActiveTrue(Long tenantId);

    long countByTenantIdAndActive(Long tenantId, boolean active);

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
