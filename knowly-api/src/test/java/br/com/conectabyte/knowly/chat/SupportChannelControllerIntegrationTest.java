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
        return member(user, tenant);
    }

    private User member(User user, Tenant tenant) {
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

        staffWithSupportHandle("assignee@example.com");
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
    void getActiveTicketReHydratesStatusAfterClaimWithoutRequiringTheClaimResponse()
            throws Exception {
        // Regression test: activeTicket()'s only source of truth used to be the
        // claim/transfer/close response bodies, so a frontend page reload after claiming a
        // ticket had no way to re-fetch its status. Found live (2026-08-04).
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Ticket Ghost Co"));
        User owner = member("ticket-ghost-member@example.com", tenant);
        Cookie memberSession = logIn("ticket-ghost-member@example.com");
        Cookie memberCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                .cookie(memberSession)
                .cookie(memberCsrf)
                .header("X-XSRF-TOKEN", memberCsrf.getValue())
                .exchange();

        var beforeClaim =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/ticket")
                        .cookie(memberSession)
                        .exchange();
        assertThat(beforeClaim).hasStatus(HttpStatus.OK);
        assertThat(beforeClaim.getResponse().getContentAsString()).contains("\"status\":\"OPEN\"");

        User assignee = staffWithSupportHandle("ticket-ghost-assignee@example.com");
        Cookie assigneeSession = logIn("ticket-ghost-assignee@example.com");
        Cookie assigneeCsrf = obtainCsrfCookie();
        Long ticketId =
                (long)
                        (int)
                                com.jayway.jsonpath.JsonPath.read(
                                        mockMvc.get()
                                                .uri(
                                                        "/api/tenants/"
                                                                + tenant.getId()
                                                                + "/support/tickets/unclaimed")
                                                .cookie(assigneeSession)
                                                .exchange()
                                                .getResponse()
                                                .getContentAsString(),
                                        "$[0].id");
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/support/tickets/" + ticketId + "/claim")
                .cookie(assigneeSession)
                .cookie(assigneeCsrf)
                .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                .exchange();

        // Simulate a fresh page load for the assignee: a brand-new GET, no claim response reused.
        var afterClaim =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/ticket")
                        .cookie(assigneeSession)
                        .exchange();
        assertThat(afterClaim).hasStatus(HttpStatus.OK);
        assertThat(afterClaim.getResponse().getContentAsString())
                .contains("\"status\":\"ASSIGNED\"")
                .contains("\"assignedStaffUserId\":" + assignee.getId());
    }

    @Test
    void getActiveTicketReturns404WhenTheChannelHasNoNonClosedTicket() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Ticketless Co"));
        User owner = member("ticketless-member@example.com", tenant);
        Cookie memberSession = logIn("ticketless-member@example.com");

        var response =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/ticket")
                        .cookie(memberSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
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

    @Test
    void staffCanReadASupportChannelForATicketBelongingToADifferentTenantThanTheirActiveOne()
            throws Exception {
        // Regression test: TenantFilterAspect scopes Hibernate's tenant @Filter to the staff's
        // *currently-active* tenant, but SupportTicketService#findChannel already takes and
        // filters by the ticket's own explicit tenantId -- without @BypassTenantFilterForOversight,
        // the ambient filter ANDs in a second, unrelated tenant_id condition and the channel
        // permanently 404s (CHAT_CONVERSATION_NOT_FOUND) while the staff member is acting as any
        // tenant other than the ticket's own. Found live (2026-08-04).
        Tenant ownTenant = tenantRepository.saveAndFlush(new Tenant("Ticket Owner Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Unrelated Active Co"));
        User owner = member("cross-tenant-member@example.com", ownTenant);
        Cookie ownerSession = logIn("cross-tenant-member@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + ownTenant.getId() + "/support/tickets")
                .cookie(ownerSession)
                .cookie(ownerCsrf)
                .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                .exchange();

        User staff = staffWithSupportHandle("cross-tenant-staff@example.com");
        member(staff, otherTenant);
        Cookie staffSession = logIn("cross-tenant-staff@example.com");
        Cookie staffCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/active")
                .cookie(staffSession)
                .cookie(staffCsrf)
                .header("X-XSRF-TOKEN", staffCsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":" + otherTenant.getId() + "}")
                .exchange();

        var response =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + ownTenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/channel")
                        .cookie(staffSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void sendingToAClosedTicketIsRejected() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Closed Terminal Co"));
        User owner = member("closed-terminal-member@example.com", tenant);
        Cookie memberSession = logIn("closed-terminal-member@example.com");
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
        Long channelId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        openResponse.getResponse().getContentAsString(),
                                        "$.supportChannelId"))
                        .longValue();

        staffWithSupportHandle("closed-terminal-assignee@example.com");
        Cookie assigneeSession = logIn("closed-terminal-assignee@example.com");
        Cookie assigneeCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri("/api/tenants/" + tenant.getId() + "/support/tickets/" + ticketId + "/claim")
                .cookie(assigneeSession)
                .cookie(assigneeCsrf)
                .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                .exchange();

        var closeResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + ticketId
                                        + "/close")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .exchange();
        assertThat(closeResponse).hasStatus(HttpStatus.OK);

        Cookie memberCsrf2 = obtainCsrfCookie();
        var memberSendResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/channel/messages")
                        .cookie(memberSession)
                        .cookie(memberCsrf2)
                        .header("X-XSRF-TOKEN", memberCsrf2.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"can I still write?\"}")
                        .exchange();
        assertThat(memberSendResponse.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.CONFLICT.value());

        var assigneeSendResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/members/"
                                        + owner.getId()
                                        + "/channel/messages")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"still assigned?\"}")
                        .exchange();
        assertThat(assigneeSendResponse.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.CONFLICT.value());

        assertThat(channelId).isNotNull();
    }

    @Test
    void reopeningAndOpeningANewTicketAfterCloseReusesTheSameChannel() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Same Channel Co"));
        member("same-channel-member@example.com", tenant);
        Cookie memberSession = logIn("same-channel-member@example.com");
        Cookie memberCsrf = obtainCsrfCookie();

        var firstOpen =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(memberSession)
                        .cookie(memberCsrf)
                        .header("X-XSRF-TOKEN", memberCsrf.getValue())
                        .exchange();
        assertThat(firstOpen).hasStatus(HttpStatus.CREATED);
        Long firstTicketId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        firstOpen.getResponse().getContentAsString(), "$.id"))
                        .longValue();
        Long firstChannelId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        firstOpen.getResponse().getContentAsString(),
                                        "$.supportChannelId"))
                        .longValue();

        staffWithSupportHandle("same-channel-assignee@example.com");
        Cookie assigneeSession = logIn("same-channel-assignee@example.com");
        Cookie assigneeCsrf = obtainCsrfCookie();
        mockMvc.post()
                .uri(
                        "/api/tenants/"
                                + tenant.getId()
                                + "/support/tickets/"
                                + firstTicketId
                                + "/claim")
                .cookie(assigneeSession)
                .cookie(assigneeCsrf)
                .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                .exchange();
        var closeResponse =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + firstTicketId
                                        + "/close")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .exchange();
        assertThat(closeResponse).hasStatus(HttpStatus.OK);

        // No reopen endpoint exists -- attempting to claim/close the now-CLOSED ticket again must
        // be rejected as a conflict, never silently reopening it.
        var reopenAttempt =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/support/tickets/"
                                        + firstTicketId
                                        + "/claim")
                        .cookie(assigneeSession)
                        .cookie(assigneeCsrf)
                        .header("X-XSRF-TOKEN", assigneeCsrf.getValue())
                        .exchange();
        assertThat(reopenAttempt).hasStatus(HttpStatus.CONFLICT);

        Cookie memberCsrf2 = obtainCsrfCookie();
        var secondOpen =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/support/tickets")
                        .cookie(memberSession)
                        .cookie(memberCsrf2)
                        .header("X-XSRF-TOKEN", memberCsrf2.getValue())
                        .exchange();
        assertThat(secondOpen).hasStatus(HttpStatus.CREATED);
        Long secondTicketId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        secondOpen.getResponse().getContentAsString(), "$.id"))
                        .longValue();
        Long secondChannelId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        secondOpen.getResponse().getContentAsString(),
                                        "$.supportChannelId"))
                        .longValue();

        assertThat(secondTicketId).isNotEqualTo(firstTicketId);
        assertThat(secondChannelId).isEqualTo(firstChannelId);
    }
}
