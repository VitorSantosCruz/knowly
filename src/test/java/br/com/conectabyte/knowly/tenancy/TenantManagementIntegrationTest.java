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

    @Test
    void onlyStaffCanCreateATenant() {
        User staff = userRepository.saveAndFlush(new User("staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);

        Cookie session = logIn("staff@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"adminEmail\":\"admin@acme.com\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(tenantRepository.findAll()).extracting(Tenant::getName).contains("Acme");
        User admin = userRepository.findByEmailIgnoreCase("admin@acme.com").orElseThrow();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(admin)).hasSize(1);
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(admin).get(0).getRole())
                .isEqualTo(MembershipRole.ADMIN);
    }

    @Test
    void aNonStaffUserCannotCreateATenant() {
        User user = userRepository.saveAndFlush(new User("regular@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Existing"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("regular@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"adminEmail\":\"nope@example.com\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCanAddAndRemoveAMemberInTheirOwnTenant() {
        User admin = userRepository.saveAndFlush(new User("admin@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.ADMIN));

        Cookie session = logIn("admin@own.com");

        var addResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newbie@own.com\",\"role\":\"MEMBER\"}")
                        .exchange();

        assertThat(addResponse).hasStatus(HttpStatus.OK);
        User newbie = userRepository.findByEmailIgnoreCase("newbie@own.com").orElseThrow();
        Long membershipId =
                tenantMembershipRepository.findByUserAndActiveTrue(newbie).get(0).getId();

        var removeResponse =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/members/" + membershipId)
                        .cookie(session)
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
                new TenantMembership(admin, tenantA, MembershipRole.ADMIN));

        Cookie session = logIn("admin@tenantA.com");

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenantB.getId() + "/members")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@tenantB.com\",\"role\":\"MEMBER\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCanGrantADirectPermissionToAMember() {
        User member = userRepository.saveAndFlush(new User("member@own.com"));
        User admin = userRepository.saveAndFlush(new User("admin2@own.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Own Tenant 2"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.ADMIN));
        TenantMembership memberMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));

        Cookie adminSession = logIn("admin2@own.com");

        var grantResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + memberMembership.getId()
                                        + "/permissions")
                        .cookie(adminSession)
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

        var grantResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/members/"
                                        + otherMembership.getId()
                                        + "/permissions")
                        .cookie(memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_MEMBER_MANAGE\"}")
                        .exchange();

        assertThat(grantResponse).hasStatus(HttpStatus.FORBIDDEN);

        var createGroupResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/access-groups")
                        .cookie(memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Exist\"}")
                        .exchange();

        assertThat(createGroupResponse).hasStatus(HttpStatus.FORBIDDEN);
    }
}
