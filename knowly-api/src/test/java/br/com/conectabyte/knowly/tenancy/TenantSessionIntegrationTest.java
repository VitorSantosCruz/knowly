package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({TestcontainersConfiguration.class, TenantSessionIntegrationTest.Config.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantSessionIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private AuditEventRepository auditEventRepository;
    @MockitoBean private JavaMailSender mailSender;

    private void stubMail() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    private Cookie logIn(String email) {
        stubMail();
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

    @Test
    void singleMembershipAutoSelectsTheTenant() throws Exception {
        User user = userRepository.saveAndFlush(new User("solo@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Solo Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("solo@example.com");

        var response = mockMvc.get().uri("/api/tenants/memberships").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Solo Tenant");
        assertThat(response.getResponse().getContentAsString()).contains("\"active\":true");
    }

    @Test
    void multiMembershipLeavesSelectionPendingAndBlocksTenantScopedEndpoints() throws Exception {
        User user = userRepository.saveAndFlush(new User("multi@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantA, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantB, MembershipRole.MEMBER));

        Cookie session = logIn("multi@example.com");

        var response = mockMvc.get().uri("/api/test/tenant-scoped").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString())
                .contains("TENANT_SELECTION_REQUIRED");
    }

    @Test
    void switchingToAMemberTenantUpdatesTheSessionWithoutANewLogin() {
        User user = userRepository.saveAndFlush(new User("switcher@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantA, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantB, MembershipRole.MEMBER));

        Cookie session = logIn("switcher@example.com");

        var switchResponse =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantA.getId() + "}")
                        .exchange();

        assertThat(switchResponse).hasStatus(HttpStatus.OK);

        var scopedResponse =
                mockMvc.get().uri("/api/test/tenant-scoped").cookie(session).exchange();

        assertThat(scopedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void membershipsListMarksTheActiveTenantAfterSwitching() throws Exception {
        User user = userRepository.saveAndFlush(new User("marker@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantA, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantB, MembershipRole.MEMBER));

        Cookie session = logIn("marker@example.com");
        mockMvc.post()
                .uri("/api/tenants/active")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":" + tenantB.getId() + "}")
                .exchange();

        var response = mockMvc.get().uri("/api/tenants/memberships").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body)
                .contains("\"tenantName\":\"Tenant B\",\"role\":\"MEMBER\",\"active\":true");
        assertThat(body)
                .contains("\"tenantName\":\"Tenant A\",\"role\":\"MEMBER\",\"active\":false");
    }

    @Test
    void ownPermissionsReturnsTheCallersEffectivePermissionsInTheActiveTenant() throws Exception {
        User user = userRepository.saveAndFlush(new User("permviewer@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Perm Tenant"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_VIEW));

        Cookie session = logIn("permviewer@example.com");

        var response = mockMvc.get().uri("/api/tenants/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"permissions\":[\"ARTICLE_VIEW\"]");
    }

    @Test
    void ownPermissionsReturnsTheFullPermissionSetForMemberAdminWithNoExplicitGrants()
            throws Exception {
        User user = userRepository.saveAndFlush(new User("memberadmin-perms@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Member Admin Perm Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("memberadmin-perms@example.com");

        var response = mockMvc.get().uri("/api/tenants/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        for (Permission permission : Permission.values()) {
            assertThat(body).contains(permission.name());
        }
    }

    @Test
    void anyTenantPermissionCheckReturnsTrueWhenAnyMembershipGrantsIt() throws Exception {
        User user = userRepository.saveAndFlush(new User("anytenant-granted@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Any Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Any Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantA, MembershipRole.MEMBER));
        TenantMembership membershipB =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenantB, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membershipB, Permission.PROFILE_EDIT));

        Cookie session = logIn("anytenant-granted@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"granted\":true");
    }

    @Test
    void anyTenantPermissionCheckReturnsFalseWhenNoMembershipGrantsIt() throws Exception {
        User user = userRepository.saveAndFlush(new User("anytenant-ungranted@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Any Tenant C"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("anytenant-ungranted@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"granted\":false");
    }

    @Test
    void anyTenantPermissionCheckReturnsFalseForACallerWithNoMemberships() throws Exception {
        userRepository.saveAndFlush(new User("anytenant-nomembership@example.com"));

        Cookie session = logIn("anytenant-nomembership@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"granted\":false");
    }

    @Test
    void anyTenantPermissionCheckReturnsTrueForStaffAdminWithNoMemberships() throws Exception {
        User staff = userRepository.saveAndFlush(new User("anytenant-staffadmin@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);

        Cookie session = logIn("anytenant-staffadmin@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"granted\":true");
    }

    @Test
    void anyTenantPermissionCheckRequiresAuthentication() {
        var response =
                mockMvc.get()
                        .uri("/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void switchingToANonMemberTenantIsRejectedAndAudited() throws Exception {
        User user = userRepository.saveAndFlush(new User("outsider@example.com"));
        Tenant ownTenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Other Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, ownTenant, MembershipRole.MEMBER));

        Cookie session = logIn("outsider@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + otherTenant.getId() + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(response.getResponse().getContentAsString()).contains("TENANT_ACCESS_DENIED");

        List<br.com.conectabyte.knowly.audit.AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(events.get(0).getAction()).isEqualTo("tenant.active_tenant.switch");
    }

    @Test
    void staffWithNoMembershipsCanListAllTenantsAndActAsAny() throws Exception {
        User staff = userRepository.saveAndFlush(new User("staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));

        Cookie session = logIn("staff@example.com");

        var listResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(listResponse).hasStatus(HttpStatus.OK);
        String listBody = listResponse.getResponse().getContentAsString();
        assertThat(listBody).contains("Tenant A");

        var switchResponse =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantA.getId() + "}")
                        .exchange();

        assertThat(switchResponse).hasStatus(HttpStatus.OK);

        var scopedResponse =
                mockMvc.get().uri("/api/test/tenant-scoped").cookie(session).exchange();

        assertThat(scopedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void aFreshLoginClearsAStaleActiveTenantIdLeftBehindByAPriorLoginOnTheSameSession()
            throws Exception {
        User staff = userRepository.saveAndFlush(new User("stale-staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Stale Tenant"));

        Cookie firstSession = logIn("stale-staff@example.com");
        var switchResponse =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenant.getId() + "}")
                        .exchange();
        assertThat(switchResponse).hasStatus(HttpStatus.OK);

        // Reuses the same (now post-switch) SESSION cookie to simulate a fresh login on a HTTP
        // session that already carried a stale ACTIVE_TENANT_ID attribute from an earlier login.
        stubMail();
        String code = loginCodeService.generate("stale-staff@example.com");
        var reLoginResult =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"stale-staff@example.com\",\"code\":\"" + code + "\"}")
                        .cookie(firstSession)
                        .exchange();
        assertThat(reLoginResult).hasStatus(HttpStatus.OK);
        Cookie freshSession = reLoginResult.getResponse().getCookie("SESSION");

        // A fresh login must not carry over the previous session's active-tenant selection. If the
        // stale ACTIVE_TENANT_ID leaked into this new session, this would incorrectly return 200
        // (staff admins get every permission for whatever tenant is "active"); with a clean session
        // there is no active tenant, so this must fail with TENANT_ACCESS_DENIED instead.
        var permissionsResponse =
                mockMvc.get().uri("/api/tenants/permissions").cookie(freshSession).exchange();

        assertThat(permissionsResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(permissionsResponse.getResponse().getContentAsString())
                .contains("TENANT_ACCESS_DENIED");
    }

    /**
     * /api/tenants/active/clear is not CSRF-exempt (only /api/tenants/active is, see
     * SecurityConfig), so every call in this suite needs a real XSRF-TOKEN cookie + header, same
     * convention as TenantManagementIntegrationTest#obtainCsrfCookie().
     */
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    @Test
    void staffClearingTheirActiveTenantReturnsToTheTenantLessGlobalStaffView() throws Exception {
        User staff = userRepository.saveAndFlush(new User("leaver@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Leave Tenant"));

        Cookie session = logIn("leaver@example.com");
        Cookie csrf = obtainCsrfCookie();

        var switchResponse =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenant.getId() + "}")
                        .exchange();
        assertThat(switchResponse).hasStatus(HttpStatus.OK);

        var clearResponse =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(clearResponse).hasStatus(HttpStatus.OK);

        // AC1: staff-only global-scope endpoint still succeeds.
        var listResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();
        assertThat(listResponse).hasStatus(HttpStatus.OK);

        // AC1/AC2: tenant-scoped endpoint now behaves as if no tenant is selected, proving
        // ACTIVE_TENANT_ID is absent from the session.
        var permissionsResponse =
                mockMvc.get().uri("/api/tenants/permissions").cookie(session).exchange();
        assertThat(permissionsResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(permissionsResponse.getResponse().getContentAsString())
                .contains("TENANT_ACCESS_DENIED");
    }

    @Test
    void clearingActiveTenantRecordsAnAuditEventWithThePreviouslyActiveTenantId() throws Exception {
        User staff = userRepository.saveAndFlush(new User("audited-leaver@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Audited Tenant A"));

        Cookie session = logIn("audited-leaver@example.com");
        Cookie csrf = obtainCsrfCookie();

        mockMvc.post()
                .uri("/api/tenants/active")
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":" + tenantA.getId() + "}")
                .exchange();

        var clearResponse =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();
        assertThat(clearResponse).hasStatus(HttpStatus.OK);

        List<br.com.conectabyte.knowly.audit.AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("tenant.active_tenant.clear");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events.get(0).getResourceId()).isEqualTo(String.valueOf(tenantA.getId()));
    }

    @Test
    void staffWithNoActiveTenantCanCallClearAsANoOp() throws Exception {
        User staff = userRepository.saveAndFlush(new User("noop-staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);

        Cookie session = logIn("noop-staff@example.com");
        Cookie csrf = obtainCsrfCookie();

        var clearResponse =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(clearResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void nonStaffCallingClearActiveTenantIsRejectedAndSessionIsUnchanged() throws Exception {
        User user = userRepository.saveAndFlush(new User("regular-leaver@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Regular Leaver Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("regular-leaver@example.com");
        Cookie csrf = obtainCsrfCookie();

        var clearResponse =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(clearResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(clearResponse.getResponse().getContentAsString())
                .contains("TENANT_ACCESS_DENIED");

        var membershipsResponse =
                mockMvc.get().uri("/api/tenants/memberships").cookie(session).exchange();
        assertThat(membershipsResponse).hasStatus(HttpStatus.OK);
        assertThat(membershipsResponse.getResponse().getContentAsString())
                .contains("Regular Leaver Tenant");
    }

    @Test
    void clearActiveTenantRequiresAuthentication() {
        // No SESSION cookie at all -- a valid CSRF cookie/header is still supplied so the
        // unauthenticated rejection observed here is genuinely Spring Security's authentication
        // check (401), not an incidental CSRF-filter rejection (403) from omitting the token.
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clearActiveTenantIsNotCsrfExempt() {
        User staff = userRepository.saveAndFlush(new User("csrf-leaver@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);

        Cookie session = logIn("csrf-leaver@example.com");

        var response = mockMvc.post().uri("/api/tenants/active/clear").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffActingAsATenantSeesItAsActiveWithNoRole() throws Exception {
        User staff = userRepository.saveAndFlush(new User("active-staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Active Staff Tenant"));

        Cookie session = logIn("active-staff@example.com");
        mockMvc.post()
                .uri("/api/tenants/active")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":" + tenant.getId() + "}")
                .exchange();

        var response = mockMvc.get().uri("/api/tenants/active").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"tenantId\":" + tenant.getId());
        assertThat(body).contains("\"tenantName\":\"Active Staff Tenant\"");
        assertThat(body).doesNotContain("\"role\"");
    }

    @Test
    void memberWithAnActiveMembershipSeesItsRoleInActiveTenant() throws Exception {
        User user = userRepository.saveAndFlush(new User("active-member@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Active Member Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("active-member@example.com");
        mockMvc.post()
                .uri("/api/tenants/active")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":" + tenant.getId() + "}")
                .exchange();

        var response = mockMvc.get().uri("/api/tenants/active").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"tenantId\":" + tenant.getId());
        assertThat(body).contains("\"tenantName\":\"Active Member Tenant\"");
        assertThat(body).contains("\"role\":\"MEMBER\"");
    }

    @Test
    void noActiveTenantReturnsNoContent() throws Exception {
        userRepository.saveAndFlush(new User("no-active-tenant@example.com"));

        Cookie session = logIn("no-active-tenant@example.com");

        var response = mockMvc.get().uri("/api/tenants/active").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void nonStaffCannotListAllTenants() {
        User user = userRepository.saveAndFlush(new User("regular@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Regular Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("regular@example.com");

        var response = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @RestController
    static class TenantScopedTestController {
        @GetMapping("/api/test/tenant-scoped")
        String tenantScoped() {
            return "ok";
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        TenantScopedTestController tenantScopedTestController() {
            return new TenantScopedTestController();
        }
    }
}
