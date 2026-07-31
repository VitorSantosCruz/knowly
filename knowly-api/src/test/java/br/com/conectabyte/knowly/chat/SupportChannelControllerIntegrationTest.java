package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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

/**
 * SupportChannelController is nested under /api/tenants/** and does NOT inherit any CSRF exemption
 * (the exemption was already narrowed to the exact /api/tenants/active path ahead of this feature)
 * -- every mutating call here obtains and sends a real CSRF token, same convention as
 * ConversationControllerIntegrationTest/StaffRbacIntegrationTest.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportChannelControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private StringRedisTemplate redisTemplate;
    @MockitoBean private JavaMailSender mailSender;

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

    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private User member(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));
        return user;
    }

    private User staffWithSupportHandle(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(user);
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, GlobalPermission.STAFF_SUPPORT_HANDLE));
        return user;
    }

    private User plainStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void openingATicketRequiresCsrfAndSucceedsForTheOwningMember() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Support Co"));
        member("support-member@example.com", tenant);
        Cookie session = logIn("support-member@example.com");
        Cookie csrf = obtainCsrfCookie();

        var noTokenResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(session)
                        .exchange();
        assertThat(noTokenResponse).hasStatus(HttpStatus.FORBIDDEN);

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void openingASecondTicketWhileOneIsOpenConflicts() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Conflict Co"));
        member("conflict-member@example.com", tenant);
        Cookie session = logIn("conflict-member@example.com");
        Cookie csrf = obtainCsrfCookie();

        var first =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();
        assertThat(first).hasStatus(HttpStatus.CREATED);

        var second =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void listingUnclaimedTicketsRequiresStaffSupportHandle() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Unclaimed Co"));
        member("unclaimed-member@example.com", tenant);
        Cookie memberSession = logIn("unclaimed-member@example.com");
        Cookie memberCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                .cookie(memberSession)
                .cookie(memberCsrf)
                .header("X-XSRF-TOKEN", memberCsrf.getValue())
                .exchange();

        plainStaff("nohandle-staff@example.com");
        Cookie noHandleSession = logIn("nohandle-staff@example.com");
        var deniedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets/unclaimed")
                        .cookie(noHandleSession)
                        .exchange();
        assertThat(deniedResponse).hasStatus(HttpStatus.FORBIDDEN);

        staffWithSupportHandle("handle-staff@example.com");
        Cookie handleSession = logIn("handle-staff@example.com");
        var allowedResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets/unclaimed")
                        .cookie(handleSession)
                        .exchange();
        assertThat(allowedResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void claimTransferAndCloseFullLifecycle() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Lifecycle Co"));
        member("lifecycle-member@example.com", tenant);
        Cookie memberSession = logIn("lifecycle-member@example.com");
        Cookie memberCsrf = obtainCsrfCookie();
        var openResponse =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(memberSession)
                        .cookie(memberCsrf)
                        .header("X-XSRF-TOKEN", memberCsrf.getValue())
                        .exchange();
        Long ticketId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        openResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        User assignee = staffWithSupportHandle("assignee@example.com");
        Cookie assigneeSession = logIn("assignee@example.com");
        Cookie assigneeCsrf = obtainCsrfCookie();
        var claimResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + ticketId
                                        + "/claim")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .exchange();
        assertThat(claimResponse).hasStatus(HttpStatus.OK);

        User newAssignee = staffWithSupportHandle("new-assignee@example.com");
        var transferResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + ticketId
                                        + "/transfer")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStaffUserId\":" + newAssignee.getId() + "}")
                        .exchange();
        assertThat(transferResponse).hasStatus(HttpStatus.OK);

        Cookie newAssigneeSession = logIn("new-assignee@example.com");
        Cookie newAssigneeCsrf = obtainCsrfCookie();
        var closeResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + ticketId
                                        + "/close")
                        .cookie(newAssigneeSession)
                        .cookie(newAssigneeCsrf)
                        .header("X-XSRF-TOKEN", newAssigneeCsrf.getValue())
                        .exchange();
        assertThat(closeResponse).hasStatus(HttpStatus.OK);

        var secondCloseResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + ticketId
                                        + "/close")
                        .cookie(newAssigneeSession)
                        .cookie(newAssigneeCsrf)
                        .header("X-XSRF-TOKEN", newAssigneeCsrf.getValue())
                        .exchange();
        assertThat(secondCloseResponse).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void memberWithSupportChannelViewCanReadAnotherMembersChannel() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("View Co"));
        User owner = member("channel-owner@example.com", tenant);
        Cookie ownerSession = logIn("channel-owner@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                .cookie(ownerSession)
                .cookie(ownerCsrf)
                .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                .exchange();

        User viewer = userRepository.saveAndFlush(new User("channel-viewer@example.com"));
        TenantMembership viewerMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(viewer, tenant, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(viewerMembership, Permission.SUPPORT_CHANNEL_VIEW));
        Cookie viewerSession = logIn("channel-viewer@example.com");

        var response =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/channel")
                        .cookie(viewerSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }
}
