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
class StaffRbacIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;
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

    @Test
    void staffAdminRetainsUnconditionalTenantCreation() {
        staffAdmin("admin@example.com");
        Cookie session = logIn("admin@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"adminEmail\":\"tenant-admin@acme.com\"}")
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

        var createResponse =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"adminEmail\":\"tenant-admin@acme.com\"}")
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
}
