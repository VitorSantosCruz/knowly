package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * {@code /api/notifications}, per specify/features/tenant-membership-acceptance/PLAN.md's API
 * contracts table.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private LoginCodeService loginCodeService;
    @MockitoBean private JavaMailSender mailSender;

    private Cookie logIn(String email) {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        String code = loginCodeService.generate(email);
        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        return result.getResponse().getCookie("SESSION");
    }

    private TenantMembership pendingMembership(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        membership.setStatus(MembershipStatus.PENDING);
        membership.setActive(false);
        return tenantMembershipRepository.saveAndFlush(membership);
    }

    private Notification pendingNotificationFor(TenantMembership membership) {
        return notificationRepository.saveAndFlush(
                new Notification(
                        membership.getUser(),
                        NotificationType.MEMBERSHIP_INVITATION_PENDING,
                        membership));
    }

    @Test
    void listNotificationsReturnsOnlyTheCallersOwnUnresolvedNotifications() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller List Co"));
        TenantMembership mine = pendingMembership("controllerlistmine@example.com", tenant);
        pendingNotificationFor(mine);
        Cookie session = logIn("controllerlistmine@example.com");

        var response = mockMvc.get().uri("/api/notifications").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("MEMBERSHIP_INVITATION_PENDING");
    }

    @Test
    void acceptingAPendingInvitationSucceeds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller Accept Co"));
        TenantMembership invitee = pendingMembership("controlleraccept@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);
        Cookie session = logIn("controlleraccept@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/notifications/" + notification.getId() + "/accept")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(tenantMembershipRepository.findById(invitee.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        // Non-functional requirement "Observability": every state transition this feature
        // introduces (including accept) must be audit-logged.
        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        invitee.getUser().getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("notification.membership.accept");
    }

    @Test
    void decliningAPendingInvitationSucceeds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller Decline Co"));
        TenantMembership invitee = pendingMembership("controllerdecline@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);
        Cookie session = logIn("controllerdecline@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/notifications/" + notification.getId() + "/decline")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(tenantMembershipRepository.findById(invitee.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.DECLINED);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        invitee.getUser().getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("notification.membership.decline");
    }

    @Test
    void acceptingAnotherUsersNotificationIsForbidden() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller Wrong Co"));
        TenantMembership invitee = pendingMembership("controllerrealrecipient@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);
        userRepository.saveAndFlush(new User("controllerimpostor@example.com"));
        Cookie session = logIn("controllerimpostor@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/notifications/" + notification.getId() + "/accept")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptingAnAlreadyResolvedNotificationConflicts() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller Conflict Co"));
        TenantMembership invitee = pendingMembership("controllerconflict@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);
        Cookie session = logIn("controllerconflict@example.com");
        mockMvc.post()
                .uri("/api/notifications/" + notification.getId() + "/accept")
                .cookie(session)
                .exchange();

        var response =
                mockMvc.post()
                        .uri("/api/notifications/" + notification.getId() + "/accept")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void acceptingAnUnknownNotificationIsNotFound() {
        userRepository.saveAndFlush(new User("controllerunknown@example.com"));
        Cookie session = logIn("controllerunknown@example.com");

        var response =
                mockMvc.post().uri("/api/notifications/999999/accept").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }
}
