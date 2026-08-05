package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectGlobalPermissionGrantRepository
        extends JpaRepository<DirectGlobalPermissionGrant, Long> {

    List<DirectGlobalPermissionGrant> findByUserAndDeletedAtIsNull(User user);

    /** Used only by permission-resolution/listing reads -- excludes revoked grants. */
    Optional<DirectGlobalPermissionGrant> findByUserAndPermissionAndDeletedAtIsNull(
            User user, GlobalPermission permission);

    /**
     * Used only by the grant/revoke write path, regardless of current deleted state, so a
     * revoked-then-re-granted permission reactivates the existing row instead of colliding with the
     * partial unique index -- logical-delete-everywhere (2026-08-04).
     */
    Optional<DirectGlobalPermissionGrant> findByUserAndPermission(
            User user, GlobalPermission permission);
}
