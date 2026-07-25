package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectGlobalPermissionGrantRepository
        extends JpaRepository<DirectGlobalPermissionGrant, Long> {

    List<DirectGlobalPermissionGrant> findByUser(User user);

    Optional<DirectGlobalPermissionGrant> findByUserAndPermission(
            User user, GlobalPermission permission);
}
