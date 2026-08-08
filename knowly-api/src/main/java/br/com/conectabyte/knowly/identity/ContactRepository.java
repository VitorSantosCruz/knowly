package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    /**
     * Derived (HQL-backed, no explicit {@code deletedAt} predicate) -- proves {@link
     * br.com.conectabyte.knowly.softdelete.SoftDeleteFilter} excludes soft-deleted rows on its own,
     * with no per-query opt-in (specify/features/soft-delete-default-filter/SPEC.md requirement 3).
     */
    List<Contact> findByUser(User user);

    List<Contact> findByUserAndDeletedAtIsNull(User user);

    long countByUserAndDeletedAtIsNull(User user);

    List<Contact> findByUserAndTypeAndDeletedAtIsNull(User user, ContactType type);
}
