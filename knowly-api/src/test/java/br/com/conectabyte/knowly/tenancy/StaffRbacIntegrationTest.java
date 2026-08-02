package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffRbacIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;
    @Autowired private UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
    @MockitoBean private JavaMailSender mailSender;

    // This class logs in many users across many @Test methods, all from MockMvc's shared
    // loopback "IP". CaptchaService's login velocity counters are keyed by IP and persist in the
    // shared Testcontainers Redis for the whole class run, so without resetting them per test,
    // enough tests in this class trip the real CAPTCHA_REQUIRED velocity guard (a false failure
    // unrelated to what's under test here).
    @BeforeEach
    void resetLoginVelocityCounters() {
        Set<String> keys = redisTemplate.keys("auth:login-velocity:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

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

    // Same reasoning as AuthControllerIntegrationTest#obtainCsrfCookie: uses the real
    // cookie-issuance flow rather than SecurityMockMvcRequestPostProcessors.csrf(), which would
    // corrupt the shared CsrfFilter bean's tokenRepository for the rest of this class's tests.
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
    }

    private User limitedStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    /**
     * tenant-creation: full {@code POST /api/tenants} payload (company identification + first
     * admin's complete mandatory profile), per specify/features/tenant-creation/PLAN.md's "API
     * contracts" section.
     */
    private String createTenantPayload(String taxId, String adminEmail) {
        return "{\"name\":\"Acme\",\"legalName\":\"Acme Ltda\",\"taxId\":\""
                + taxId
                + "\",\"country\":\"BR\",\"contactEmail\":\"contact@"
                + taxId
                + ".example.com\",\"contactPhone\":\"11999999999\","
                + "\"address\":{\"postalCode\":\"01000-000\",\"street\":\"Rua Um\",\"number\":\"1\","
                + "\"neighborhood\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\"},"
                + "\"adminEmail\":\""
                + adminEmail
                + "\",\"profile\":{\"fullName\":\"Test User\","
                + "\"taxId\":\"52998224725\",\"countryCode\":\"BR\","
                + "\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\","
                + "\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                + "\"countryCode\":\"BR\"},"
                + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}";
    }

    @Test
    void staffAdminRetainsUnconditionalTenantCreation() {
        staffAdmin("admin@example.com");
        Cookie session = logIn("admin@example.com");

        Cookie csrf = obtainCsrfCookie();
        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                createTenantPayload(
                                        "TAXID" + System.nanoTime(), "tenant-admin@acme.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void zeroGrantStaffIsRejectedFromCreatingATenant() {
        limitedStaff("nogrant@example.com");
        Cookie session = logIn("nogrant@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"adminEmail\":\"tenant-admin@acme.com\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void directlyGrantedStaffCanCreateATenantButNothingElseStaffGated() {
        User user = limitedStaff("directgrant@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, GlobalPermission.TENANT_CREATE));
        Cookie session = logIn("directgrant@example.com");
        Cookie csrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                createTenantPayload(
                                        "TAXID" + System.nanoTime(),
                                        "tenant-admin-directgrant@acme.com"))
                        .exchange();

        assertThat(createResponse).hasStatus(HttpStatus.OK);

        var listAllResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(listAllResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void groupGrantedStaffHasEquivalentAccessAndLosesItWhenUnassigned() {
        User user = limitedStaff("groupgrant@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Tenant Ops"));
        globalAccessGroupPermissionRepository.saveAndFlush(
                new GlobalAccessGroupPermission(group, GlobalPermission.TENANT_ACT_AS_ANY));
        UserGlobalAccessGroup assignment =
                userGlobalAccessGroupRepository.saveAndFlush(
                        new UserGlobalAccessGroup(user, group));
        Cookie session = logIn("groupgrant@example.com");

        var listAllResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();
        assertThat(listAllResponse).hasStatus(HttpStatus.OK);

        userGlobalAccessGroupRepository.delete(assignment);

        var afterRemovalResponse = mockMvc.get().uri("/api/tenants").cookie(session).exchange();
        assertThat(afterRemovalResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyStaffAdminOrGrantedStaffCanManageGlobalPermissions() {
        User otherPermissionHolder = limitedStaff("other-perm@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(
                        otherPermissionHolder, GlobalPermission.TENANT_CREATE));
        Cookie otherPermissionSession = logIn("other-perm@example.com");

        var rejectedResponse =
                mockMvc.get()
                        .uri("/api/staff/access-groups")
                        .cookie(otherPermissionSession)
                        .exchange();
        assertThat(rejectedResponse).hasStatus(HttpStatus.FORBIDDEN);

        staffAdmin("admin2@example.com");
        Cookie adminSession = logIn("admin2@example.com");

        var allowedResponse =
                mockMvc.get().uri("/api/staff/access-groups").cookie(adminSession).exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void grantingAGlobalPermissionEmitsAnAuditEvent() {
        User staffAdminUser = staffAdmin("granter@example.com");
        User target = limitedStaff("target@example.com");
        Cookie session = logIn("granter@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_CREATE\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);

        List<br.com.conectabyte.knowly.audit.AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staffAdminUser.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("staff.permission.grant");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void
            staffPermissionRevocationDeletionConfirmationTokenGenerationIsGatedByStaffPermissionManage()
                    throws Exception {
        User target = limitedStaff("revoke-token-target@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(target, GlobalPermission.TENANT_CREATE));

        User unprivilegedStaff = limitedStaff("revoke-token-nogrant@example.com");
        Cookie noGrantSession = logIn("revoke-token-nogrant@example.com");

        var deniedResponse =
                mockMvc.post()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/permissions/TENANT_CREATE/deletion-confirmation-token")
                        .cookie(noGrantSession)
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unprivilegedStaff).isNotNull();

        staffAdmin("revoke-token-admin@example.com");
        Cookie adminSession = logIn("revoke-token-admin@example.com");
        Cookie adminCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/permissions/TENANT_CREATE/deletion-confirmation-token")
                        .cookie(adminSession)
                        .cookie(adminCsrf)
                        .header("X-XSRF-TOKEN", adminCsrf.getValue())
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
        assertThat(allowedResponse.getResponse().getContentAsString()).contains("\"word\"");
    }

    @Test
    void ownPermissionsForZeroGrantStaffReportsIsStaffAccountTrueWithEmptyPermissions()
            throws Exception {
        limitedStaff("zerograntperms@example.com");
        Cookie session = logIn("zerograntperms@example.com");

        var response = mockMvc.get().uri("/api/staff/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"isStaffAccount\":true")
                .contains("\"permissions\":[]");
    }

    @Test
    void ownPermissionsForStaffAdminReportsIsStaffAccountTrueWithFullPermissionList()
            throws Exception {
        staffAdmin("staffadminperms@example.com");
        Cookie session = logIn("staffadminperms@example.com");

        var response = mockMvc.get().uri("/api/staff/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"isStaffAccount\":true")
                .contains("TENANT_CREATE");
    }

    @Test
    void ownPermissionsForPlainTenantMemberReportsIsStaffAccountFalseWithEmptyPermissions()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Plain Member Co"));
        User member = userRepository.saveAndFlush(new User("plainmember@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, tenant, MembershipRole.MEMBER));
        Cookie session = logIn("plainmember@example.com");

        var response = mockMvc.get().uri("/api/staff/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"isStaffAccount\":false")
                .contains("\"permissions\":[]");
    }

    private void grantGlobalPermission(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    // The 9 remaining call sites gated by requireAdminOfTenantOrStaff, individually confirmed here
    // (staff-rbac-split TASKS.md task 6 gap): each already goes through requireStaff/
    // requireAdminOfTenantOrStaff exercised above via createTenant/listAllTenants, but
    // per-call-site
    // parameterization by a distinct GlobalPermission constant means each needs its own coverage.

    @Test
    void addMemberIsGatedByTenantMemberManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Add Member Co"));
        limitedStaff("nogrant-addmember@example.com");
        Cookie noGrantSession = logIn("nogrant-addmember@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"newmember@example.com\",\"role\":\"MEMBER\",\"profile\":{\"fullName\":\"Test User\",\"taxId\":\"52998224725\",\"countryCode\":\"BR\",\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\",\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\",\"countryCode\":\"BR\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-addmember@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_CREATE);
        Cookie grantedSession = logIn("grant-addmember@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"newmember2@example.com\",\"role\":\"MEMBER\",\"profile\":{\"fullName\":\"Test User\",\"taxId\":\"52998224725\",\"countryCode\":\"BR\",\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\",\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\",\"countryCode\":\"BR\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void removeMemberIsGatedByTenantMemberManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Remove Member Co"));
        User member = userRepository.saveAndFlush(new User("removable@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-removemember@example.com");
        Cookie noGrantSession = logIn("nogrant-removemember@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-removemember@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_DELETE);
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_VIEW);
        Cookie grantedSession = logIn("grant-removemember@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member", membership.getId().toString(), grantedStaff, null);

        var allowedResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void memberRemovalDeletionConfirmationTokenGenerationIsGatedByTenantMemberManageAny()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Token Gen Co"));
        User member = userRepository.saveAndFlush(new User("tokengen-removable@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-tokengen-remove@example.com");
        Cookie noGrantSession = logIn("nogrant-tokengen-remove@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/deletion-confirmation-token")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-tokengen-remove@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_DELETE);
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_VIEW);
        Cookie grantedSession = logIn("grant-tokengen-remove@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/deletion-confirmation-token")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
        assertThat(allowedResponse.getResponse().getContentAsString()).contains("\"word\"");
    }

    @Test
    void listMembersIsGatedByTenantMemberManageAny() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Members Co"));
        User member = userRepository.saveAndFlush(new User("listable@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-listmembers@example.com");
        Cookie noGrantSession = logIn("nogrant-listmembers@example.com");

        var deniedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(noGrantSession)
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-listmembers@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_VIEW);
        Cookie grantedSession = logIn("grant-listmembers@example.com");

        var allowedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(grantedSession)
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
        assertThat(allowedResponse.getResponse().getContentAsString())
                .contains("listable@example.com");
    }

    @Test
    void createAccessGroupIsGatedByTenantAccessGroupManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Create Group Co"));

        limitedStaff("nogrant-creategroup@example.com");
        Cookie noGrantSession = logIn("nogrant-creategroup@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Editors\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-creategroup@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_ACCESS_GROUP_CREATE);
        Cookie grantedSession = logIn("grant-creategroup@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Editors\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void listAccessGroupsIsGatedByTenantAccessGroupManageAny() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Groups Co"));
        accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Reviewers"));

        limitedStaff("nogrant-listgroups@example.com");
        Cookie noGrantSession = logIn("nogrant-listgroups@example.com");

        var deniedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(noGrantSession)
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-listgroups@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_ACCESS_GROUP_VIEW);
        Cookie grantedSession = logIn("grant-listgroups@example.com");

        var allowedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(grantedSession)
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
        assertThat(allowedResponse.getResponse().getContentAsString()).contains("Reviewers");
    }

    @Test
    void grantPermissionIsGatedByTenantPermissionGrantManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Grant Perm Co"));
        User member = userRepository.saveAndFlush(new User("grantee@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-grantperm@example.com");
        Cookie noGrantSession = logIn("nogrant-grantperm@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/permissions")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-grantperm@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);
        Cookie grantedSession = logIn("grant-grantperm@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/permissions")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void revokePermissionIsGatedByTenantPermissionGrantManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Revoke Perm Co"));
        User member = userRepository.saveAndFlush(new User("revokee@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-revokeperm@example.com");
        Cookie noGrantSession = logIn("nogrant-revokeperm@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/permissions/TENANT_MEMBER_MANAGE")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-revokeperm@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_VIEW);
        Cookie grantedSession = logIn("grant-revokeperm@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission",
                        membership.getId() + ":" + Permission.TENANT_MEMBER_MANAGE,
                        grantedStaff,
                        null);

        var allowedResponse =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/permissions/TENANT_MEMBER_MANAGE")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void assignAccessGroupIsGatedByTenantPermissionGrantManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Assign Group Co"));
        User member = userRepository.saveAndFlush(new User("assignee@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));
        AccessGroup accessGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Assignable Group"));

        limitedStaff("nogrant-assigngroup@example.com");
        Cookie noGrantSession = logIn("nogrant-assigngroup@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId())
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-assigngroup@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);
        Cookie grantedSession = logIn("grant-assigngroup@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId())
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void unassignAccessGroupIsGatedByTenantPermissionGrantManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Unassign Group Co"));
        User member = userRepository.saveAndFlush(new User("unassignee@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));
        AccessGroup accessGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Unassignable Group"));

        limitedStaff("nogrant-unassigngroup@example.com");
        Cookie noGrantSession = logIn("nogrant-unassigngroup@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId())
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-unassigngroup@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_VIEW);
        Cookie grantedSession = logIn("grant-unassigngroup@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-access-group",
                        membership.getId() + ":" + accessGroup.getId(),
                        grantedStaff,
                        null);

        var allowedResponse =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId())
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void getMemberDetailIsGatedByTenantPermissionGrantManageAny() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Member Detail Co"));
        User member = userRepository.saveAndFlush(new User("detailed@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        limitedStaff("nogrant-memberdetail@example.com");
        Cookie noGrantSession = logIn("nogrant-memberdetail@example.com");

        var deniedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
                        .cookie(noGrantSession)
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-memberdetail@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_VIEW);
        Cookie grantedSession = logIn("grant-memberdetail@example.com");

        var allowedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
                        .cookie(grantedSession)
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    // permission-granularity-model REQ-2/REQ-5: edit/delete GlobalPermission requires its view
    // companion, enforced by TenantService#requireAdminOfTenantOrStaff for the staff branch.

    @Test
    void removeMemberWithDeleteButWithoutViewIsDenied() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("No View Remove Co"));
        User member = userRepository.saveAndFlush(new User("noview-removable@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        User grantedStaff = limitedStaff("noview-removemember@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_MEMBER_DELETE);
        Cookie session = logIn("noview-removemember@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membership.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void grantAccessGroupPermissionIsGatedByTenantAccessGroupEditWithViewDependency() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Group Edit Perm Co"));
        AccessGroup accessGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editable Group"));

        limitedStaff("nogrant-groupeditperm@example.com");
        Cookie noGrantSession = logIn("nogrant-groupeditperm@example.com");
        Cookie noGrantCsrf = obtainCsrfCookie();

        var deniedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId()
                                        + "/permissions")
                        .cookie(noGrantSession)
                        .cookie(noGrantCsrf)
                        .header("X-XSRF-TOKEN", noGrantCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        User editOnlyStaff = limitedStaff("edit-only-groupeditperm@example.com");
        grantGlobalPermission(editOnlyStaff, GlobalPermission.TENANT_ACCESS_GROUP_EDIT);
        Cookie editOnlySession = logIn("edit-only-groupeditperm@example.com");
        Cookie editOnlyCsrf = obtainCsrfCookie();

        var editWithoutViewResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId()
                                        + "/permissions")
                        .cookie(editOnlySession)
                        .cookie(editOnlyCsrf)
                        .header("X-XSRF-TOKEN", editOnlyCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();
        assertThat(editWithoutViewResponse).hasStatus(HttpStatus.FORBIDDEN);

        User grantedStaff = limitedStaff("grant-groupeditperm@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_ACCESS_GROUP_EDIT);
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_ACCESS_GROUP_VIEW);
        Cookie grantedSession = logIn("grant-groupeditperm@example.com");
        Cookie grantedCsrf = obtainCsrfCookie();

        var allowedResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/"
                                        + accessGroup.getId()
                                        + "/permissions")
                        .cookie(grantedSession)
                        .cookie(grantedCsrf)
                        .header("X-XSRF-TOKEN", grantedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void revokePermissionWithDeleteButWithoutViewIsDenied() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("No View Revoke Co"));
        User member = userRepository.saveAndFlush(new User("noview-revokee@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        User grantedStaff = limitedStaff("noview-revokeperm@example.com");
        grantGlobalPermission(grantedStaff, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);
        Cookie session = logIn("noview-revokeperm@example.com");
        Cookie csrf = obtainCsrfCookie();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission",
                        membership.getId() + ":" + Permission.TENANT_MEMBER_MANAGE,
                        grantedStaff,
                        null);

        var response =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + membership.getId()
                                        + "/permissions/TENANT_MEMBER_MANAGE")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }
}
