package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectPermissionGrantRepository
        extends JpaRepository<DirectPermissionGrant, Long> {

    List<DirectPermissionGrant> findByTenantMembership(TenantMembership tenantMembership);

    Optional<DirectPermissionGrant> findByTenantMembershipAndPermission(
            TenantMembership tenantMembership, Permission permission);
}
