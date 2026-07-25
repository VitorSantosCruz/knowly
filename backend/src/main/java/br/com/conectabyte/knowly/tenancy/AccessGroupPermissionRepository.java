package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGroupPermissionRepository
        extends JpaRepository<AccessGroupPermission, Long> {

    List<AccessGroupPermission> findByAccessGroupIn(List<AccessGroup> accessGroups);

    Optional<AccessGroupPermission> findByAccessGroupAndPermission(
            AccessGroup accessGroup, Permission permission);
}
