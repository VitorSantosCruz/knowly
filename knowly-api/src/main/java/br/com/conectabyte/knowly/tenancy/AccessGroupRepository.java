package br.com.conectabyte.knowly.tenancy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGroupRepository extends JpaRepository<AccessGroup, Long> {

    /** REQ-17: listing/read path -- excludes soft-deleted groups. */
    List<AccessGroup> findByTenantAndDeletedAtIsNull(Tenant tenant);

    /**
     * REQ-3: bulk existence check for batch-assign -- the service compares the returned set's size
     * against the submitted id set's size to detect any id that didn't resolve (wrong tenant,
     * unknown, or soft-deleted), atomically, before any write.
     */
    List<AccessGroup> findByTenantAndIdInAndDeletedAtIsNull(Tenant tenant, Collection<Long> ids);

    /** REQ-17: single-group read/write paths that must not see a soft-deleted group. */
    Optional<AccessGroup> findByIdAndDeletedAtIsNull(Long id);
}
