package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.tenancy.GlobalRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByGlobalRoleIn(List<GlobalRole> globalRoles);

    List<User> findByGlobalRoleInAndEmailContainingIgnoreCase(
            List<GlobalRole> globalRoles, String email);

    long countByGlobalRoleIn(List<GlobalRole> globalRoles);
}
