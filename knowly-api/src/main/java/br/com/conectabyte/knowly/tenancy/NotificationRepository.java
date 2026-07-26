package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientAndResolvedFalse(User recipient);

    List<Notification> findByTenantMembership(TenantMembership tenantMembership);
}
