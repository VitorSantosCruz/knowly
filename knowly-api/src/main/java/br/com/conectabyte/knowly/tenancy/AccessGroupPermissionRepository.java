package br.com.conectabyte.knowly.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessGroupPermissionRepository
        extends JpaRepository<AccessGroupPermission, Long> {

    /** REQ-17: effective-permission resolution -- excludes soft-deleted permission grants. */
    List<AccessGroupPermission> findByAccessGroupInAndDeletedAtIsNull(
            List<AccessGroup> accessGroups);

    /**
     * Intentionally unfiltered -- this is the grant path's reactivate-or-create lookup, which must
     * see a soft-deleted row so it can reactivate it rather than colliding with the partial unique
     * index (mirrors {@code UserAccessGroupRepository#findByTenantMembershipAndAccessGroup}'s same
     * write-path-sees-deleted-rows split, DECISIONS.md 2026-08-04).
     */
    Optional<AccessGroupPermission> findByAccessGroupAndPermission(
            AccessGroup accessGroup, Permission permission);

    /**
     * REQ-13's cascade: a single bulk {@code UPDATE}, not a per-row load-and-save, per PLAN.md's
     * Performance/SLA note. Only currently-live rows are touched.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE AccessGroupPermission p SET p.deletedAt = :deletedAt "
                    + "WHERE p.accessGroup.id = :accessGroupId AND p.deletedAt IS NULL")
    void softDeleteByAccessGroupId(
            @Param("accessGroupId") Long accessGroupId, @Param("deletedAt") Instant deletedAt);
}
