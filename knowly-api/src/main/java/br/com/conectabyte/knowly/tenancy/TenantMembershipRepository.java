package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {

    Optional<TenantMembership> findByUserAndTenant(User user, Tenant tenant);

    List<TenantMembership> findByUserAndActiveTrue(User user);

    List<TenantMembership> findByTenantIdAndActiveTrue(Long tenantId);
}
