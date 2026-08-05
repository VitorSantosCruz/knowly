package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByUserAndDeletedAtIsNull(User user);

    long countByUserAndDeletedAtIsNull(User user);

    List<Contact> findByUserAndTypeAndDeletedAtIsNull(User user, ContactType type);
}
