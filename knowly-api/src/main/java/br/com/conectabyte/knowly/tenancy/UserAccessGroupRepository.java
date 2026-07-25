package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessGroupRepository extends JpaRepository<UserAccessGroup, Long> {

    List<UserAccessGroup> findByTenantMembership(TenantMembership tenantMembership);

    Optional<UserAccessGroup> findByTenantMembershipAndAccessGroup(
            TenantMembership tenantMembership, AccessGroup accessGroup);
}
