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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * REQ-4/5/6/7/8 (specify/features/role-model-refinement/SPEC.md): a {@code STAFF} user, however
 * many {@link GlobalPermission}s they hold, can never manage another {@code STAFF}/{@code
 * STAFF_ADMIN} user's account or global permissions/access-groups — that ceiling stays exclusive to
 * {@code STAFF_ADMIN}, unconditionally, and every rejection is audited.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffServiceCeilingIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
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

    // Same reasoning as AuthControllerIntegrationTest#obtainCsrfCookie / StaffRbacIntegrationTest:
    // uses the real cookie-issuance flow rather than SecurityMockMvcRequestPostProcessors.csrf(),
    // which would corrupt the shared CsrfFilter bean's tokenRepository for the rest of this
    // class's tests.
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

    private User staff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    private User plainMember(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    /** Grants every existing {@link GlobalPermission} directly to {@code user}. */
    private void grantEveryPermission(User user) {
        for (GlobalPermission permission : GlobalPermission.values()) {
            directGlobalPermissionGrantRepository.saveAndFlush(
                    new DirectGlobalPermissionGrant(user, permission));
        }
    }

    // --- getStaffUserDetail ---

    @Test
    void fullyPermissionedStaffIsRejectedViewingAStaffTargetsDetail() {
        User actor = staff("full-perm-view@example.com");
        grantEveryPermission(actor);
        User target = staff("view-target-staff@example.com");
        Cookie session = logIn("full-perm-view@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullyPermissionedStaffIsRejectedViewingAStaffAdminTargetsDetail() {
        User actor = staff("full-perm-view2@example.com");
        grantEveryPermission(actor);
        User target = staffAdmin("view-target-staff-admin@example.com");
        Cookie session = logIn("full-perm-view2@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullyPermissionedStaffCanStillViewAPlainMembersDetail() {
        User actor = staff("full-perm-view3@example.com");
        grantEveryPermission(actor);
        User target = plainMember("view-target-plain@example.com");
        Cookie session = logIn("full-perm-view3@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void staffAdminCanViewAnotherStaffAdminsDetail() {
        staffAdmin("view-admin-actor@example.com");
        User target = staffAdmin("view-admin-target@example.com");
        Cookie session = logIn("view-admin-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void rejectedStaffUserDetailViewIsAudited() {
        User actor = staff("audited-view@example.com");
        grantEveryPermission(actor);
        User target = staff("audited-view-target@example.com");
        Cookie session = logIn("audited-view@example.com");

        mockMvc.get()
                .uri("/api/staff/users/" + target.getId() + "/permissions")
                .cookie(session)
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId());
        assertThat(events)
                .anySatisfy(
                        e -> {
                            assertThat(e.getAction()).isEqualTo("staff.user.detail.view");
                            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                        });
    }

    // --- grantPermission / revokePermission ---

    @Test
    void fullyPermissionedStaffIsRejectedGrantingAPermissionToAStaffTarget() {
        User actor = staff("full-perm-grant@example.com");
        grantEveryPermission(actor);
        User target = staff("grant-target-staff@example.com");
        Cookie session = logIn("full-perm-grant@example.com");
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

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullyPermissionedStaffCanStillGrantAPermissionToAPlainMember() {
        User actor = staff("full-perm-grant2@example.com");
        grantEveryPermission(actor);
        User target = plainMember("grant-target-plain@example.com");
        Cookie session = logIn("full-perm-grant2@example.com");
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
    }

    @Test
    void fullyPermissionedStaffIsRejectedRevokingAPermissionFromAStaffAdminTarget() {
        User actor = staff("full-perm-revoke@example.com");
        grantEveryPermission(actor);
        User target = staffAdmin("revoke-target-staff-admin@example.com");
        Cookie session = logIn("full-perm-revoke@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.delete()
                        .uri("/api/staff/users/" + target.getId() + "/permissions/TENANT_CREATE")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffAdminCanGrantAPermissionToAnotherStaffAdmin() {
        staffAdmin("grant-admin-actor@example.com");
        User target = staffAdmin("grant-admin-target@example.com");
        Cookie session = logIn("grant-admin-actor@example.com");
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
    }

    @Test
    void rejectedGrantPermissionIsAudited() {
        User actor = staff("audited-grant@example.com");
        grantEveryPermission(actor);
        User target = staff("audited-grant-target@example.com");
        Cookie session = logIn("audited-grant@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        mockMvc.post()
                .uri("/api/staff/users/" + target.getId() + "/permissions")
                .cookie(session)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permission\":\"TENANT_CREATE\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId());
        assertThat(events)
                .anySatisfy(
                        e -> {
                            assertThat(e.getAction()).isEqualTo("staff.permission.grant");
                            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                        });
    }

    // --- assignAccessGroup / unassignAccessGroup ---

    @Test
    void fullyPermissionedStaffIsRejectedAssigningAnAccessGroupToAStaffTarget() {
        User actor = staff("full-perm-assign@example.com");
        grantEveryPermission(actor);
        User target = staff("assign-target-staff@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Ceiling Group"));
        Cookie session = logIn("full-perm-assign@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/access-groups/"
                                        + group.getId())
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullyPermissionedStaffCanStillAssignAnAccessGroupToAPlainMember() {
        User actor = staff("full-perm-assign2@example.com");
        grantEveryPermission(actor);
        User target = plainMember("assign-target-plain@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Ceiling Group 2"));
        Cookie session = logIn("full-perm-assign2@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/access-groups/"
                                        + group.getId())
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void fullyPermissionedStaffIsRejectedUnassigningAnAccessGroupFromAStaffAdminTarget() {
        User actor = staff("full-perm-unassign@example.com");
        grantEveryPermission(actor);
        User target = staffAdmin("unassign-target-staff-admin@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Ceiling Group 3"));
        userGlobalAccessGroupRepository.saveAndFlush(new UserGlobalAccessGroup(target, group));
        Cookie session = logIn("full-perm-unassign@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.delete()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/access-groups/"
                                        + group.getId())
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffAdminCanAssignAnAccessGroupToAnotherStaffAdmin() {
        staffAdmin("assign-admin-actor@example.com");
        User target = staffAdmin("assign-admin-target@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(new GlobalAccessGroup("Ceiling Group 4"));
        Cookie session = logIn("assign-admin-actor@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/staff/users/"
                                        + target.getId()
                                        + "/access-groups/"
                                        + group.getId())
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    // --- createStaffUser ---

    @Test
    void staffGrantedStaffUserCreateIsRejectedFromCreatingAStaffUser() {
        User actor = staff("create-staff-user-create-only@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.STAFF_USER_CREATE));
        Cookie session = logIn("create-staff-user-create-only@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-staff@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullyPermissionedStaffIsRejectedFromCreatingAStaffUser() {
        User actor = staff("create-staff-full-perm@example.com");
        grantEveryPermission(actor);
        Cookie session = logIn("create-staff-full-perm@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-staff2@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffAdminCanStillCreateAStaffUser() {
        staffAdmin("create-staff-admin-actor@example.com");
        Cookie session = logIn("create-staff-admin-actor@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-staff3@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
    }
}
