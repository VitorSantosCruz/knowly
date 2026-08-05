package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectPermissionGrantRepository
        extends JpaRepository<DirectPermissionGrant, Long> {

    List<DirectPermissionGrant> findByTenantMembershipAndDeletedAtIsNull(
            TenantMembership tenantMembership);

    /** Used only by permission-resolution/listing reads -- excludes revoked grants. */
    Optional<DirectPermissionGrant> findByTenantMembershipAndPermissionAndDeletedAtIsNull(
            TenantMembership tenantMembership, Permission permission);

    /**
     * Used only by the grant/revoke write path, regardless of current deleted state, so a
     * revoked-then-re-granted permission reactivates the existing row instead of colliding with the
     * partial unique index -- logical-delete-everywhere (2026-08-04).
     */
    Optional<DirectPermissionGrant> findByTenantMembershipAndPermission(
            TenantMembership tenantMembership, Permission permission);
}
