package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGroupRepository extends JpaRepository<AccessGroup, Long> {

    List<AccessGroup> findByTenant(Tenant tenant);
}
