package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * specify/features/staff-audit-trail-view/SPEC.md REQ-1..REQ-9: {@code GET
 * /api/staff/users/{userId}/audit-trail} returns the target user's full audit history — including
 * rows from every tenant they've acted in — gated by the new, ceiling-independent {@link
 * GlobalPermission#AUDIT_TRAIL_VIEW}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAuditTrailIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
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

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
    }

    private User staff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    private User plainMember(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private void grant(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    private void insertEvent(Long actorUserId, Long tenantId, String action) {
        jdbcTemplate.update(
                "insert into audit_events (occurred_at, actor_user_id, tenant_id, action, outcome)"
                        + " values (now(), ?, ?, ?, ?)",
                actorUserId,
                tenantId,
                action,
                AuditOutcome.SUCCESS.name());
    }

    // --- REQ-6: caller without AUDIT_TRAIL_VIEW (and not STAFF_ADMIN) is rejected ---

    @Test
    void staffWithoutGrantIsRejectedFromViewingAnyAuditTrail() {
        staff("audit-trail-no-grant@example.com");
        User target = plainMember("audit-trail-no-grant-target@example.com");
        Cookie session = logIn("audit-trail-no-grant@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- REQ-7: tenant MEMBER with no GlobalRole is rejected ---

    @Test
    void plainMemberWithNoGlobalRoleIsRejectedFromViewingAnyAuditTrail() {
        User actor = plainMember("audit-trail-plain-actor@example.com");
        var tenant = tenantRepository.saveAndFlush(new Tenant("Audit Trail Plain Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(actor, tenant, MembershipRole.MEMBER));
        User target = plainMember("audit-trail-plain-target@example.com");
        Cookie session = logIn(actor.getEmail());

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- REQ-2: STAFF_ADMIN succeeds without an explicit grant ---

    @Test
    void staffAdminCanViewAnyUsersAuditTrailWithoutExplicitGrant() {
        staffAdmin("audit-trail-admin-actor@example.com");
        User target = plainMember("audit-trail-admin-target@example.com");
        insertEvent(target.getId(), null, "profile.view");
        Cookie session = logIn("audit-trail-admin-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].action")
                .asList()
                .contains("profile.view");
    }

    // --- REQ-9: STAFF ceiling does not block read-only audit-trail viewing of a STAFF/STAFF_ADMIN
    // target ---

    @Test
    void staffHoldingGrantCanViewAStaffAdminTargetsAuditTrail() {
        User actor = staff("audit-trail-ceiling-actor@example.com");
        grant(actor, GlobalPermission.AUDIT_TRAIL_VIEW);
        User target = staffAdmin("audit-trail-ceiling-target@example.com");
        Cookie session = logIn("audit-trail-ceiling-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    // --- REQ-5/REQ-1: STAFF holding the grant sees the target's events, reverse-chronological,
    // mapped to the full response shape, and gets an empty list for a target with no events ---

    @Test
    void grantedStaffSeesTargetsEventsReverseChronologicallyWithFullShape() {
        User actor = staff("audit-trail-shape-actor@example.com");
        grant(actor, GlobalPermission.AUDIT_TRAIL_VIEW);
        User target = plainMember("audit-trail-shape-target@example.com");
        insertEvent(target.getId(), null, "profile.view.older");
        insertEvent(target.getId(), null, "profile.view.newer");
        Cookie session = logIn("audit-trail-shape-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[0].action")
                .isEqualTo("profile.view.newer");
        assertThat(response)
                .bodyJson()
                .extractingPath("$[1].action")
                .isEqualTo("profile.view.older");
        assertThat(response).bodyJson().extractingPath("$[0].occurredAt").isNotNull();
        assertThat(response).bodyJson().extractingPath("$[0].outcome").isEqualTo("SUCCESS");
    }

    @Test
    void grantedStaffGetsEmptyListForATargetWithNoEvents() {
        User actor = staff("audit-trail-empty-actor@example.com");
        grant(actor, GlobalPermission.AUDIT_TRAIL_VIEW);
        User target = plainMember("audit-trail-empty-target@example.com");
        Cookie session = logIn("audit-trail-empty-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response).bodyJson().extractingPath("$").asList().isEmpty();
    }

    // --- REQ-4: cross-tenant rows all come back in one call, no active tenant selected ---

    @Test
    void auditTrailIncludesRowsFromEveryTenantTheTargetHasActedIn() {
        User actor = staff("audit-trail-cross-tenant-actor@example.com");
        grant(actor, GlobalPermission.AUDIT_TRAIL_VIEW);
        User target = plainMember("audit-trail-cross-tenant-target@example.com");
        var tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        var tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        insertEvent(target.getId(), tenantA.getId(), "tenant.a.action");
        insertEvent(target.getId(), tenantB.getId(), "tenant.b.action");
        insertEvent(target.getId(), null, "global.action");
        Cookie session = logIn("audit-trail-cross-tenant-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].action")
                .asList()
                .contains("tenant.a.action", "tenant.b.action", "global.action");
    }

    // --- REQ-8: nonexistent userId returns 404 ---

    @Test
    void nonexistentUserIdReturns404() {
        User actor = staffAdmin("audit-trail-404-actor@example.com");
        Cookie session = logIn("audit-trail-404-actor@example.com");
        long nonexistentUserId = actor.getId() + 999_999L;

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + nonexistentUserId + "/audit-trail")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- Observability NFR: the call itself is captured as a staff.audit_trail.view AuditEvent ---

    @Test
    void theCallItselfIsAudited() {
        User actor = staffAdmin("audit-trail-self-audit-actor@example.com");
        User target = plainMember("audit-trail-self-audit-target@example.com");
        Cookie session = logIn("audit-trail-self-audit-actor@example.com");

        mockMvc.get()
                .uri("/api/staff/users/" + target.getId() + "/audit-trail")
                .cookie(session)
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId());
        assertThat(events)
                .anySatisfy(
                        e -> {
                            assertThat(e.getAction()).isEqualTo("staff.audit_trail.view");
                            assertThat(e.getResourceType()).isEqualTo("User");
                            assertThat(e.getResourceId()).isEqualTo(String.valueOf(target.getId()));
                            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
                        });
    }
}
