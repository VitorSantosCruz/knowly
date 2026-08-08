package br.com.conectabyte.knowly.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalAccessGroupPermissionRepository
        extends JpaRepository<GlobalAccessGroupPermission, Long> {

    List<GlobalAccessGroupPermission> findByGlobalAccessGroupIn(
            List<GlobalAccessGroup> globalAccessGroups);

    /**
     * Intentionally unfiltered -- this is the grant path's reactivate-or-create lookup, which must
     * see a soft-deleted row so it can reactivate it rather than colliding with the partial unique
     * index (mirrors {@code AccessGroupPermissionRepository#findByAccessGroupAndPermission}'s same
     * write-path-sees-deleted-rows split, DECISIONS.md 2026-08-04). Must stay unfiltered -- do not
     * "fix" this to exclude deleted rows, or reactivate-on-regrant breaks.
     */
    Optional<GlobalAccessGroupPermission> findByGlobalAccessGroupAndPermission(
            GlobalAccessGroup globalAccessGroup, GlobalPermission permission);
}
