package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
 * tenant-access-group-bulk-and-delete: controller-level coverage for {@code POST
 * .../access-groups:batch}, {@code GET .../access-groups/{id}/deletion-confirmation-token}, and
 * {@code DELETE .../access-groups/{id}} -- same {@code MockMvcTester}/session/CSRF shape as {@link
 * TenantManagementIntegrationTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessGroupBulkAndDeleteIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
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

    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    @Test
    void batchAssignReturns204OnAValidPayload() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Batch 204 Co"));
        User admin = userRepository.saveAndFlush(new User("batch-204-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        User target = userRepository.saveAndFlush(new User("batch-204-target@example.com"));
        TenantMembership targetMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(target, tenant, MembershipRole.MEMBER));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "A"));

        Cookie session = logIn("batch-204-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + targetMembership.getId()
                                        + "/access-groups:batch")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessGroupIds\":[" + group.getId() + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(
                        userAccessGroupRepository.findByTenantMembershipAndDeletedAtIsNull(
                                targetMembership))
                .hasSize(1);
    }

    @Test
    void batchAssignReturns400OnAnEmptyPayload() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Batch 400 Co"));
        User admin = userRepository.saveAndFlush(new User("batch-400-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        User target = userRepository.saveAndFlush(new User("batch-400-target@example.com"));
        TenantMembership targetMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(target, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("batch-400-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + targetMembership.getId()
                                        + "/access-groups:batch")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessGroupIds\":[]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void batchAssignReturns403ForACallerWithoutTheGrantPermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Batch 403 Co"));
        User plainMember = userRepository.saveAndFlush(new User("batch-403-member@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(plainMember, tenant, MembershipRole.MEMBER));
        User target = userRepository.saveAndFlush(new User("batch-403-target@example.com"));
        TenantMembership targetMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(target, tenant, MembershipRole.MEMBER));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "A"));

        Cookie session = logIn("batch-403-member@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + targetMembership.getId()
                                        + "/access-groups:batch")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessGroupIds\":[" + group.getId() + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenGenerationAndDeleteRoundTripSucceedsForAPermittedCaller() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Round Trip Co"));
        User admin = userRepository.saveAndFlush(new User("delete-rt-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        Cookie session = logIn("delete-rt-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var tokenResponse =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/"
                                        + group.getId()
                                        + "/deletion-confirmation-token")
                        .cookie(session)
                        .exchange();

        assertThat(tokenResponse).hasStatus(HttpStatus.OK);
        String word =
                tokenResponse
                        .getResponse()
                        .getContentAsString()
                        .replaceAll(".*\"word\":\"(.*)\"}", "$1");

        var deleteResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups/" + group.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(deleteResponse).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(accessGroupRepository.findByIdAndDeletedAtIsNull(group.getId())).isEmpty();
    }

    @Test
    void deleteWithoutAValidTokenLeavesTheGroupUntouched() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete No Token Co"));
        User admin = userRepository.saveAndFlush(new User("delete-no-token-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        Cookie session = logIn("delete-no-token-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var deleteResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups/" + group.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"wrong-word\"}")
                        .exchange();

        assertThat(deleteResponse.getResponse().getStatus())
                .isNotEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(accessGroupRepository.findByIdAndDeletedAtIsNull(group.getId())).isPresent();
    }

    @Test
    void tokenGenerationAndDeleteReturn403ForACallerWithoutTheDeletePermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete 403 Co"));
        User plainMember = userRepository.saveAndFlush(new User("delete-403-member@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(plainMember, tenant, MembershipRole.MEMBER));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        Cookie session = logIn("delete-403-member@example.com");
        Cookie csrf = obtainCsrfCookie();

        var tokenResponse =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/"
                                        + group.getId()
                                        + "/deletion-confirmation-token")
                        .cookie(session)
                        .exchange();

        assertThat(tokenResponse).hasStatus(HttpStatus.FORBIDDEN);

        var deleteResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups/" + group.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"whatever\"}")
                        .exchange();

        assertThat(deleteResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(accessGroupRepository.findByIdAndDeletedAtIsNull(group.getId())).isPresent();
    }

    @Test
    void tokenGenerationAndDeleteReturn404ForAnUnknownAccessGroupId() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete 404 Co"));
        User admin = userRepository.saveAndFlush(new User("delete-404-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        Cookie session = logIn("delete-404-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var tokenResponse =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/access-groups/999999999/deletion-confirmation-token")
                        .cookie(session)
                        .exchange();

        assertThat(tokenResponse).hasStatus(HttpStatus.NOT_FOUND);

        var deleteResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups/999999999")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"whatever\"}")
                        .exchange();

        assertThat(deleteResponse).hasStatus(HttpStatus.NOT_FOUND);
    }
}
