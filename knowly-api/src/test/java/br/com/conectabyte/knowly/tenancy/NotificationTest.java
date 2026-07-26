package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class NotificationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;

    @Test
    void constructingANotificationDefaultsUnresolvedAndPopulatesAuditColumns() {
        User recipient = userRepository.saveAndFlush(new User("recipient@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Notif Co"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(recipient, tenant, MembershipRole.MEMBER));

        Notification saved =
                notificationRepository.saveAndFlush(
                        new Notification(
                                recipient,
                                NotificationType.MEMBERSHIP_INVITATION_PENDING,
                                membership));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecipient()).isEqualTo(recipient);
        assertThat(saved.getType()).isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
        assertThat(saved.getTenantMembership()).isEqualTo(membership);
        assertThat(saved.isResolved()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isNotNull();
    }
}
