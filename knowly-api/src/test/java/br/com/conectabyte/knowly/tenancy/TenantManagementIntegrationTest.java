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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantManagementIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
    @Autowired private AuditEventRepository auditEventRepository;
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
     * /api/tenants/** is not CSRF-exempt (only /api/tenants/active is, see SecurityConfig) so every
     * state-changing call in this test needs a real XSRF-TOKEN cookie + header, same convention as
     * AuthControllerIntegrationTest#obtainCsrfCookie().
     */
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    /**
     * tenant-creation: full {@code POST /api/tenants} payload (company identification + first
     * admin's complete mandatory profile + optional role), per
     * specify/features/tenant-creation/PLAN.md's "API contracts" section.
     */
    private String createTenantPayload(String name, String taxId, String adminEmail, String role) {
        String roleField = role == null ? "" : ",\"role\":\"" + role + "\"";
        return "{"
                + "\"name\":\""
                + name
                + "\",\"legalName\":\""
                + name
                + " Ltda\",\"taxId\":\""
                + taxId
                + "\",\"country\":\"BR\",\"contactEmail\":\"contact@"
                + taxId
                + ".example.com\",\"contactPhone\":\"11999999999\","
                + "\"address\":{\"postalCode\":\"01000-000\",\"street\":\"Rua Um\",\"number\":\"1\","
                + "\"neighborhood\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\"},"
                + "\"adminEmail\":\""
                + adminEmail
                + "\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\","
                + "\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\","
                + "\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\","
                + "\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},"
                + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}"
                + roleField
                + "}";
    }

    @Test
    void onlyStaffCanCreateATenant() {
        User staff = userRepository.saveAndFlush(new User("staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);

        Cookie session = logIn("staff@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "TAXID" + System.nanoTime();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTenantPayload("Acme", taxId, "admin@acme.com", null))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(tenantRepository.findAll()).extracting(Tenant::getName).contains("Acme");
        User admin = userRepository.findByEmailIgnoreCase("admin@acme.com").orElseThrow();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(admin)).hasSize(1);
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(admin).get(0).getRole())
                .isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

    @Test
    void aNonStaffUserCannotCreateATenant() {
        User user = userRepository.saveAndFlush(new User("regular@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Existing"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("regular@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "TAXID" + System.nanoTime();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTenantPayload("Nope", taxId, "nope@example.com", null))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCanAddAndRemoveAMemberInTheirOwnTenant() {
        User admin = userRepository.saveAndFlush(new User("admin@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("admin@own.com");
        Cookie csrf = obtainCsrfCookie();

        var addResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"newbie@own.com\",\"role\":\"MEMBER\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(addResponse).hasStatus(HttpStatus.OK);
        User newbie = userRepository.findByEmailIgnoreCase("newbie@own.com").orElseThrow();
        Long membershipId =
                tenantMembershipRepository.findByUserAndActiveTrue(newbie).get(0).getId();
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member", membershipId.toString(), admin, null);

        var removeResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membershipId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(removeResponse).hasStatus(HttpStatus.OK);
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(newbie)).isEmpty();
        assertThat(tenantMembershipRepository.findById(membershipId)).isPresent();
    }

    @Test
    void tenantAdminCannotManageAnotherTenant() {
        User admin = userRepository.saveAndFlush(new User("admin@tenantA.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenantA, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("admin@tenantA.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenantB.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"outsider@tenantB.com\",\"role\":\"MEMBER\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCanGrantADirectPermissionToAMember() {
        User member = userRepository.saveAndFlush(new User("member@own.com"));
        User admin = userRepository.saveAndFlush(new User("admin2@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant 2"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership memberMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        Cookie adminSession = logIn("admin2@own.com");
        Cookie csrf = obtainCsrfCookie();

        var grantResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + memberMembership.getId()
                                        + "/permissions")
                        .cookie(adminSession)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();

        assertThat(grantResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void aPlainMemberCannotGrantPermissionsOrCreateAccessGroups() {
        User member = userRepository.saveAndFlush(new User("plainmember@own.com"));
        User otherMember = userRepository.saveAndFlush(new User("othermember@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant 3"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, tenant, MembershipRole.MEMBER));
        TenantMembership otherMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(otherMember, tenant, MembershipRole.MEMBER));

        Cookie memberSession = logIn("plainmember@own.com");
        Cookie csrf = obtainCsrfCookie();

        var grantResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + otherMembership.getId()
                                        + "/permissions")
                        .cookie(memberSession)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();

        assertThat(grantResponse).hasStatus(HttpStatus.FORBIDDEN);

        var createGroupResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(memberSession)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Exist\"}")
                        .exchange();

        assertThat(createGroupResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanListMembersOfTheirOwnTenant() throws Exception {
        User admin = userRepository.saveAndFlush(new User("admin3@own.com"));
        User member = userRepository.saveAndFlush(new User("member3@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("admin3@own.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("admin3@own.com");
        assertThat(response.getResponse().getContentAsString()).contains("member3@own.com");
        assertThat(response.getResponse().getContentAsString())
                .contains("\"userId\":" + member.getId());
        assertThat(response.getResponse().getContentAsString())
                .contains("\"userId\":" + admin.getId());
    }

    @Test
    void listingMembersOfAnotherTenantIsForbidden() {
        User admin = userRepository.saveAndFlush(new User("admin4@own.com"));
        Tenant ownTenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant 4"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Other Tenant 4"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, ownTenant, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("admin4@own.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/" + otherTenant.getId() + "/members")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanViewAMembersDetailIncludingEffectivePermissions() throws Exception {
        User admin = userRepository.saveAndFlush(new User("admin5@own.com"));
        User member = userRepository.saveAndFlush(new User("member5@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Detail Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership memberMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("admin5@own.com");
        Cookie csrf = obtainCsrfCookie();
        mockMvc.post()
                .uri(
                        "/api/tenants/"
                                + tenant.getId()
                                + "/members/"
                                + memberMembership.getId()
                                + "/permissions")
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                .exchange();

        var response =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + memberMembership.getId())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("TENANT_MEMBER_MANAGE");
        assertThat(response.getResponse().getContentAsString()).contains("member5@own.com");
        assertThat(response.getResponse().getContentAsString())
                .contains("\"userId\":" + member.getId());
    }

    @Test
    void adminCanListAndUnassignAccessGroups() throws Exception {
        User admin = userRepository.saveAndFlush(new User("admin6@own.com"));
        User member = userRepository.saveAndFlush(new User("member6@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Group Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership memberMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("admin6@own.com");
        Cookie csrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Editors\"}")
                .exchange();

        var listResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(session)
                        .exchange();
        assertThat(listResponse).hasStatus(HttpStatus.OK);
        assertThat(listResponse.getResponse().getContentAsString()).contains("Editors");

        long accessGroupId =
                (long)
                        (int)
                                com.jayway.jsonpath.JsonPath.read(
                                        listResponse.getResponse().getContentAsString(), "$[0].id");

        mockMvc.post()
                .uri(
                        "/api/tenants/"
                                + tenant.getId()
                                + "/members/"
                                + memberMembership.getId()
                                + "/access-groups/"
                                + accessGroupId)
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();

        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-access-group",
                        memberMembership.getId() + ":" + accessGroupId,
                        admin,
                        null);

        var unassignResponse =
                mockMvc.delete()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + memberMembership.getId()
                                        + "/access-groups/"
                                        + accessGroupId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(unassignResponse).hasStatus(HttpStatus.OK);
    }

    // user-role-selection-at-creation: REQ-7/REQ-8 end-to-end.

    @Test
    void aStaffAdminCanAddAMemberWithRoleMemberAdminAndTheAuditEventRecordsTheRole() {
        User staffAdmin =
                userRepository.saveAndFlush(new User("staffadmin-role-select@example.com"));
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Staff Admin Co"));

        Cookie session = logIn("staffadmin-role-select@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-memberadmin-staff@example.com\",\"role\":\"MEMBER_ADMIN\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        User newMember =
                userRepository
                        .findByEmailIgnoreCase("new-memberadmin-staff@example.com")
                        .orElseThrow();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(newMember).get(0).getRole())
                .isEqualTo(MembershipRole.MEMBER_ADMIN);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staffAdmin.getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("tenant.member.add");
                            assertThat(event.getMetadata()).contains("MEMBER_ADMIN");
                        });
    }

    @Test
    void aTenantsMemberAdminCanAddAMemberWithRoleMemberAdmin() {
        User admin = userRepository.saveAndFlush(new User("tenantadmin-role-select@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Tenant Admin Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("tenantadmin-role-select@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-memberadmin-tenant@example.com\",\"role\":\"MEMBER_ADMIN\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        User newMember =
                userRepository
                        .findByEmailIgnoreCase("new-memberadmin-tenant@example.com")
                        .orElseThrow();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(newMember).get(0).getRole())
                .isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

    @Test
    void aPlainMemberRequestingRoleMemberAdminIsRejectedAndCreatesNoMembership() {
        User plainMember =
                userRepository.saveAndFlush(new User("plainmember-role-select@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Plain Member Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(plainMember, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("plainmember-role-select@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"rejected-memberadmin@example.com\",\"role\":\"MEMBER_ADMIN\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findByEmailIgnoreCase("rejected-memberadmin@example.com"))
                .isEmpty();
    }
}
