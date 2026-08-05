package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGlobalAccessGroupRepository
        extends JpaRepository<UserGlobalAccessGroup, Long> {

    List<UserGlobalAccessGroup> findByUserAndDeletedAtIsNull(User user);

    /** Used only by assignment-resolution/listing reads -- excludes unassigned rows. */
    Optional<UserGlobalAccessGroup> findByUserAndGlobalAccessGroupAndDeletedAtIsNull(
            User user, GlobalAccessGroup globalAccessGroup);

    /**
     * Used only by the assign/unassign write path, regardless of current deleted state, so an
     * unassigned-then-reassigned group reactivates the existing row instead of colliding with the
     * partial unique index -- logical-delete-everywhere (2026-08-04).
     */
    Optional<UserGlobalAccessGroup> findByUserAndGlobalAccessGroup(
            User user, GlobalAccessGroup globalAccessGroup);
}
