package br.com.conectabyte.knowly.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccessGroupRepository extends JpaRepository<UserAccessGroup, Long> {

    List<UserAccessGroup> findByTenantMembershipAndDeletedAtIsNull(
            TenantMembership tenantMembership);

    /** Used only by assignment-resolution/listing reads -- excludes unassigned rows. */
    Optional<UserAccessGroup> findByTenantMembershipAndAccessGroupAndDeletedAtIsNull(
            TenantMembership tenantMembership, AccessGroup accessGroup);

    /**
     * Used only by the assign/unassign write path, regardless of current deleted state, so an
     * unassigned-then-reassigned group reactivates the existing row instead of colliding with the
     * partial unique index -- logical-delete-everywhere (2026-08-04).
     */
    Optional<UserAccessGroup> findByTenantMembershipAndAccessGroup(
            TenantMembership tenantMembership, AccessGroup accessGroup);

    /**
     * tenant-access-group-bulk-and-delete REQ-13's cascade: a single bulk {@code UPDATE}, not a
     * per-row load-and-save, per PLAN.md's Performance/SLA note. Only currently-live rows are
     * touched.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE UserAccessGroup u SET u.deletedAt = :deletedAt "
                    + "WHERE u.accessGroup.id = :accessGroupId AND u.deletedAt IS NULL")
    void softDeleteByAccessGroupId(
            @Param("accessGroupId") Long accessGroupId, @Param("deletedAt") Instant deletedAt);
}
