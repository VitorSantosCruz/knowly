package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
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
 * REQ-2/REQ-3/REQ-12 regression coverage per
 * specify/features/tenant-membership-acceptance/PLAN.md's testing strategy — every scenario here is
 * expected to pass with zero production code changes beyond {@code addMember}'s {@code
 * userAlreadyExisted} branch, confirming {@code PermissionAspect}/{@code isActive()}/{@code
 * removeMember} need no modification for this feature.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipAcceptanceIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
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

    /**
     * /api/tenants/{tenantId}/members/** is not CSRF-exempt (only /api/tenants/active is, see
     * SecurityConfig) so state-changing calls to it need a real XSRF-TOKEN cookie + header, same
     * convention as AuthControllerIntegrationTest#obtainCsrfCookie().
     */
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private Cookie switchActiveTenant(Cookie session, Long tenantId) {
        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);

        // The session ID itself doesn't change on switch (it's the same HttpSession, only its
        // ACTIVE_TENANT_ID attribute changes), so MockMvc's response carries no fresh SESSION
        // cookie to pick up here — reuse the original session cookie for follow-up requests, same
        // as TenantSessionIntegrationTest's equivalent scenarios.
        return session;
    }

    @Test
    void aPendingOnlyMembershipCannotEvenSelectItsTenantLetAloneUseARequiresPermissionEndpoint() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Pending Only Co"));
        User user = userRepository.saveAndFlush(new User("pendingonly@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        membership.setStatus(MembershipStatus.PENDING);
        membership.setActive(false);
        tenantMembershipRepository.saveAndFlush(membership);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.DASHBOARD_VIEW));
        Cookie session = logIn("pendingonly@example.com");

        var switchResponse =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenant.getId() + "}")
                        .exchange();

        assertThat(switchResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffAdminBypassIsUnaffectedByHoldingAFreshPendingMembership() {
        User staffAdmin = userRepository.saveAndFlush(new User("bypassadmin@example.com"));
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        Cookie session = logIn("bypassadmin@example.com");

        var beforeResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();
        assertThat(beforeResponse).hasStatus(HttpStatus.OK);

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Staff Pending Co"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(staffAdmin, tenant, MembershipRole.MEMBER));
        membership.setStatus(MembershipStatus.PENDING);
        membership.setActive(false);
        tenantMembershipRepository.saveAndFlush(membership);

        var afterResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();
        assertThat(afterResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void aStaffUsersSeparatelyHeldActiveMembershipInAnotherTenantIsUnaffectedByANewPendingRow() {
        User staff = userRepository.saveAndFlush(new User("otherbypassstaff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_ACT_AS_ANY));

        Tenant activeTenant = tenantRepository.saveAndFlush(new Tenant("Active Co"));
        TenantMembership activeMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(staff, activeTenant, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(activeMembership, Permission.DASHBOARD_VIEW));

        Cookie session = logIn("otherbypassstaff@example.com");
        Cookie switched = switchActiveTenant(session, activeTenant.getId());

        var beforeResponse =
                mockMvc.get().uri("/api/tenants/metrics/articles").cookie(switched).exchange();
        assertThat(beforeResponse).hasStatus(HttpStatus.OK);

        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Pending Elsewhere Co"));
        TenantMembership pendingMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(staff, otherTenant, MembershipRole.MEMBER));
        pendingMembership.setStatus(MembershipStatus.PENDING);
        pendingMembership.setActive(false);
        tenantMembershipRepository.saveAndFlush(pendingMembership);

        var afterResponse =
                mockMvc.get().uri("/api/tenants/metrics/articles").cookie(switched).exchange();
        assertThat(afterResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void adminCanDeactivateAPlainMemberRowBelongingToAUserWhoHasSinceBecomeStaff() {
        User admin = userRepository.saveAndFlush(new User("deactivateadmin@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Deactivate Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        User nowStaff = userRepository.saveAndFlush(new User("nowstaff@example.com"));
        TenantMembership staleMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(nowStaff, tenant, MembershipRole.MEMBER));
        nowStaff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(nowStaff);

        Cookie session = logIn("deactivateadmin@example.com");
        Cookie csrf = obtainCsrfCookie();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member", staleMembership.getId().toString(), admin, null);

        var response =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + staleMembership.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(
                        tenantMembershipRepository
                                .findById(staleMembership.getId())
                                .orElseThrow()
                                .isActive())
                .isFalse();
    }

    @Test
    void addMemberEmitsAnAuditEventRegardlessOfPendingOrActiveOutcome() {
        User admin = userRepository.saveAndFlush(new User("auditadmin@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("auditadmin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"freshinvitee@example.com\",\"role\":\"MEMBER\",\"profile\":{\"fullName\":\"Test User\",\"taxId\":\"52998224725\",\"countryCode\":\"BR\",\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\",\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\",\"countryCode\":\"BR\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        User admin2 = userRepository.findByEmailIgnoreCase("auditadmin@example.com").orElseThrow();
        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin2.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("tenant.member.add");
    }
}
