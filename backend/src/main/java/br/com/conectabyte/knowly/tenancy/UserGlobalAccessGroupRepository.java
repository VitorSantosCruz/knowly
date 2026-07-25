package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGlobalAccessGroupRepository
        extends JpaRepository<UserGlobalAccessGroup, Long> {

    List<UserGlobalAccessGroup> findByUser(User user);

    Optional<UserGlobalAccessGroup> findByUserAndGlobalAccessGroup(
            User user, GlobalAccessGroup globalAccessGroup);
}
