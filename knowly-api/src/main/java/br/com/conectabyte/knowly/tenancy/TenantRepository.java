package br.com.conectabyte.knowly.tenancy;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    long countByCreatedAtGreaterThanEqual(Instant from);
}
