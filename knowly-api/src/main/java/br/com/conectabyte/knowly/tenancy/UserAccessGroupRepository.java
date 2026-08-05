package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessGroupRepository extends JpaRepository<UserAccessGroup, Long> {

    List<UserAccessGroup> findByTenantMembershipAndDeletedAtIsNull(
            TenantMembership tenantMembership);

    /** Used only by assignment-resolution/listing reads -- excludes unassigned rows. */
    Optional<UserAccessGroup> findByTenantMembershipAndAccessGroupAndDeletedAtIsNull(
            TenantMembership tenantMembership, AccessGroup accessGroup);

    /**
     * Used only by the assign/unassign write path, regardless of current deleted state, so an
     * unassigned-then-reassigned group reactivates the existing row instead of colliding with the
     * partial unique index -- logical-delete-everywhere (2026-08-04).
     */
    Optional<UserAccessGroup> findByTenantMembershipAndAccessGroup(
            TenantMembership tenantMembership, AccessGroup accessGroup);
}
