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
        staff.setGlobalRole(GlobalRole.STAFF);
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
