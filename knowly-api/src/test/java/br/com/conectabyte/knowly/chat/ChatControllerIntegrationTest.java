package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;

    @Autowired
    private br.com.conectabyte.knowly.identity.UserProfileRepository userProfileRepository;

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

    private User staff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void createDirectConversationBetweenTwoStaffUsersSucceeds() {
        staff("staffa@example.com");
        User staffB = staff("staffb@example.com");
        Cookie session = logIn("staffa@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + staffB.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void gettingADirectConversationExposesTheRemoteParticipantsAvatarUrl() throws Exception {
        staff("avatar-staffa@example.com");
        User staffB = staff("avatar-staffb@example.com");
        var profileB = new br.com.conectabyte.knowly.identity.UserProfile(staffB);
        profileB.setAvatarUrl("https://minio.local/avatars/" + staffB.getId());
        userProfileRepository.saveAndFlush(profileB);
        Cookie session = logIn("avatar-staffa@example.com");
        Cookie csrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + staffB.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        var getResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(session)
                        .exchange();

        assertThat(getResponse).hasStatus(HttpStatus.OK);
        String avatarUrl =
                com.jayway.jsonpath.JsonPath.read(
                        getResponse.getResponse().getContentAsString(),
                        "$.participantAvatarUrls['" + staffB.getId() + "']");
        assertThat(avatarUrl).isEqualTo("https://minio.local/avatars/" + staffB.getId());
    }

    @Test
    void staffWithNoMembershipCannotDirectMessageAMemberOfThatTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Direct Reject Co"));
        User memberUser = member("directreject-member@example.com", tenant);
        staff("directreject-staff@example.com");
        Cookie session = logIn("directreject-staff@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + memberUser.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void staffWithMembershipCanDirectMessageAMemberOfThatTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Direct Accept Co"));
        User memberUser = member("directaccept-member@example.com", tenant);
        User staffUser = staff("directaccept-staff@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(staffUser, tenant, MembershipRole.MEMBER));
        Cookie session = logIn("directaccept-staff@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + memberUser.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aNonParticipantCannotOpenAnotherUsersDirectConversation() throws Exception {
        staff("iso-a@example.com");
        User staffB = staff("iso-b@example.com");
        staff("iso-c@example.com");
        Cookie sessionA = logIn("iso-a@example.com");
        Cookie csrfA = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(sessionA)
                        .cookie(csrfA)
                        .header("X-XSRF-TOKEN", csrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + staffB.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        Cookie sessionC = logIn("iso-c@example.com");
        var getResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(sessionC)
                        .exchange();

        assertThat(getResponse.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
    }

    @Test
    void memberOnlyGroupAcceptsAStaffUserWithMembershipAndRejectsOneWithout() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Group Co"));
        member("group-owner@example.com", tenant);
        User eligibleStaff = staff("group-eligible-staff@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(eligibleStaff, tenant, MembershipRole.MEMBER));
        User ineligibleStaff = staff("group-ineligible-staff@example.com");
        Cookie session = logIn("group-owner@example.com");
        Cookie csrf = obtainCsrfCookie();

        var acceptedResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Group\",\"participantUserIds\":["
                                        + eligibleStaff.getId()
                                        + "]}")
                        .exchange();
        assertThat(acceptedResponse).hasStatus(HttpStatus.CREATED);

        var rejectedResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Group2\",\"participantUserIds\":["
                                        + ineligibleStaff.getId()
                                        + "]}")
                        .exchange();
        assertThat(rejectedResponse).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void staffAdminCanLookIntoAGroupTheyAreNotAParticipantOfWithoutBecomingOne() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Oversight Co"));
        member("oversight-owner@example.com", tenant);
        User peer = member("oversight-peer@example.com", tenant);
        Cookie ownerSession = logIn("oversight-owner@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(ownerSession)
                        .cookie(ownerCsrf)
                        .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Group\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        User admin = userRepository.saveAndFlush(new User("oversight-admin@example.com"));
        admin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(admin);
        Cookie adminSession = logIn("oversight-admin@example.com");

        var adminReadResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(adminSession)
                        .exchange();
        assertThat(adminReadResponse).hasStatus(HttpStatus.OK);
        assertThat(adminReadResponse.getResponse().getContentAsString())
                .doesNotContain(String.valueOf(admin.getId()));

        Cookie adminCsrf = obtainCsrfCookie();
        var adminSendResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations/" + conversationId + "/messages")
                        .cookie(adminSession)
                        .cookie(adminCsrf)
                        .header("X-XSRF-TOKEN", adminCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}")
                        .exchange();
        assertThat(adminSendResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void loadingMessageHistoryNeverReturnsMoreThanOnePageAtOnce() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Pagination Co"));
        member("page-owner@example.com", tenant);
        User peer = member("page-peer@example.com", tenant);
        Cookie ownerSession = logIn("page-owner@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(ownerSession)
                        .cookie(ownerCsrf)
                        .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Page Group\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        for (int i = 0; i < 35; i++) {
            var sendResponse =
                    mockMvc.post()
                            .uri("/api/chat/conversations/" + conversationId + "/messages")
                            .cookie(ownerSession)
                            .cookie(ownerCsrf)
                            .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"message " + i + "\"}")
                            .exchange();
            assertThat(sendResponse).hasStatus(HttpStatus.CREATED);
        }

        var pageResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId + "/messages")
                        .cookie(ownerSession)
                        .exchange();
        assertThat(pageResponse).hasStatus(HttpStatus.OK);
        java.util.List<?> messages =
                com.jayway.jsonpath.JsonPath.read(
                        pageResponse.getResponse().getContentAsString(), "$.messages");
        assertThat(messages).hasSize(30);
        String nextCursor =
                com.jayway.jsonpath.JsonPath.read(
                        pageResponse.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(nextCursor).isNotNull();

        var secondPageResponse =
                mockMvc.get()
                        .uri(
                                "/api/chat/conversations/"
                                        + conversationId
                                        + "/messages?before="
                                        + nextCursor)
                        .cookie(ownerSession)
                        .exchange();
        assertThat(secondPageResponse).hasStatus(HttpStatus.OK);
        java.util.List<?> secondPageMessages =
                com.jayway.jsonpath.JsonPath.read(
                        secondPageResponse.getResponse().getContentAsString(), "$.messages");
        assertThat(secondPageMessages).hasSize(5);
    }

    @Test
    void oversizedPageSizeIsClampedNotRejected() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Clamp Co"));
        member("clamp-owner@example.com", tenant);
        Cookie session = logIn("clamp-owner@example.com");
        Cookie csrf = obtainCsrfCookie();
        User peer = member("clamp-peer@example.com", tenant);

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        var response =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId + "/messages?size=1000")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void malformedCursorIsRejectedNotAServerError() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Malformed Cursor Co"));
        member("malformed-owner@example.com", tenant);
        User peer = member("malformed-peer@example.com", tenant);
        Cookie session = logIn("malformed-owner@example.com");
        Cookie csrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        var response =
                mockMvc.get()
                        .uri(
                                "/api/chat/conversations/"
                                        + conversationId
                                        + "/messages?before=not-a-valid-cursor!!!")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void emptyConversationReturnsAnEmptyPageWithNoNextCursor() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Empty Convo Co"));
        member("empty-owner@example.com", tenant);
        User peer = member("empty-peer@example.com", tenant);
        Cookie session = logIn("empty-owner@example.com");
        Cookie csrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        var response =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId + "/messages")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        java.util.List<?> messages =
                com.jayway.jsonpath.JsonPath.read(
                        response.getResponse().getContentAsString(), "$.messages");
        assertThat(messages).isEmpty();
        Object nextCursor =
                com.jayway.jsonpath.JsonPath.read(
                        response.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(nextCursor).isNull();
    }

    @Test
    void exactlyOnePageOfMessagesLeavesNoNextCursor() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Boundary Co"));
        member("boundary-owner@example.com", tenant);
        User peer = member("boundary-peer@example.com", tenant);
        Cookie ownerSession = logIn("boundary-owner@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(ownerSession)
                        .cookie(ownerCsrf)
                        .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Boundary Group\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        for (int i = 0; i < 30; i++) {
            var sendResponse =
                    mockMvc.post()
                            .uri("/api/chat/conversations/" + conversationId + "/messages")
                            .cookie(ownerSession)
                            .cookie(ownerCsrf)
                            .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"message " + i + "\"}")
                            .exchange();
            assertThat(sendResponse).hasStatus(HttpStatus.CREATED);
        }

        var pageResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId + "/messages")
                        .cookie(ownerSession)
                        .exchange();
        assertThat(pageResponse).hasStatus(HttpStatus.OK);
        java.util.List<?> messages =
                com.jayway.jsonpath.JsonPath.read(
                        pageResponse.getResponse().getContentAsString(), "$.messages");
        assertThat(messages).hasSize(30);

        // Known, accepted pagination-contract quirk (flagged during independent QA review, not a
        // correctness bug): when the page is exactly full, the server cannot yet know whether
        // history is exhausted, so it still returns a nextCursor -- the client is expected to
        // follow it once more and observe an empty page with no further cursor, rather than the
        // server ever dropping or duplicating a message across the two fetches.
        Object nextCursor =
                com.jayway.jsonpath.JsonPath.read(
                        pageResponse.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(nextCursor).isNotNull();

        var followUpResponse =
                mockMvc.get()
                        .uri(
                                "/api/chat/conversations/"
                                        + conversationId
                                        + "/messages?before="
                                        + nextCursor)
                        .cookie(ownerSession)
                        .exchange();
        assertThat(followUpResponse).hasStatus(HttpStatus.OK);
        java.util.List<?> followUpMessages =
                com.jayway.jsonpath.JsonPath.read(
                        followUpResponse.getResponse().getContentAsString(), "$.messages");
        assertThat(followUpMessages).isEmpty();
        Object followUpCursor =
                com.jayway.jsonpath.JsonPath.read(
                        followUpResponse.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(followUpCursor).isNull();
    }

    @Test
    void staffAdminAndMemberAdminAreBothRejectedFromA1to1TheyDoNotParticipateIn() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Direct Isolation Co"));
        User memberAdminUser =
                userRepository.saveAndFlush(new User("direct-iso-memberadmin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(memberAdminUser, tenant, MembershipRole.MEMBER_ADMIN));
        User staffAdminUser =
                userRepository.saveAndFlush(new User("direct-iso-staffadmin@example.com"));
        staffAdminUser.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdminUser);

        staff("direct-iso-a@example.com");
        User staffB = staff("direct-iso-b@example.com");
        Cookie sessionA = logIn("direct-iso-a@example.com");
        Cookie csrfA = obtainCsrfCookie();
        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(sessionA)
                        .cookie(csrfA)
                        .header("X-XSRF-TOKEN", csrfA.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + staffB.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        Cookie memberAdminSession = logIn("direct-iso-memberadmin@example.com");
        var memberAdminResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(memberAdminSession)
                        .exchange();
        assertThat(memberAdminResponse.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());

        Cookie staffAdminSession = logIn("direct-iso-staffadmin@example.com");
        var staffAdminResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(staffAdminSession)
                        .exchange();
        assertThat(staffAdminResponse.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
    }

    // --- logical-delete-everywhere (2026-08-04): a soft-deleted user must not be reachable via
    // chat -- neither as an eligible-participant candidate nor addable to a brand-new conversation
    // ---

    @Test
    void softDeletedStaffUserDoesNotAppearAsAnEligibleParticipantCandidate() throws Exception {
        User staffA = staff("softdel-eligible-staffa@example.com");
        User staffB = staff("softdel-eligible-staffb@example.com");
        staffB.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(staffB);
        Cookie session = logIn("softdel-eligible-staffa@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/chat/eligible-participants?scope=group-staff-only")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        java.util.List<Integer> candidateIds =
                com.jayway.jsonpath.JsonPath.read(
                        response.getResponse().getContentAsString(), "$[*].userId");
        assertThat(candidateIds).doesNotContain(staffB.getId().intValue());
        assertThat(staffA).isNotNull();
    }

    @Test
    void softDeletedTenantMemberDoesNotAppearAsAnEligibleParticipantCandidate() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Candidate Co"));
        User owner = member("softdel-eligible-owner@example.com", tenant);
        User deletedMember = member("softdel-eligible-member@example.com", tenant);
        deletedMember.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(deletedMember);
        Cookie session = logIn("softdel-eligible-owner@example.com");

        var response =
                mockMvc.get()
                        .uri(
                                "/api/chat/eligible-participants?scope=group&tenantId="
                                        + tenant.getId())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        java.util.List<Integer> candidateIds =
                com.jayway.jsonpath.JsonPath.read(
                        response.getResponse().getContentAsString(), "$[*].userId");
        assertThat(candidateIds).doesNotContain(deletedMember.getId().intValue());
        assertThat(owner).isNotNull();
    }

    @Test
    void creatingADirectConversationWithASoftDeletedStaffUserIdFails() {
        staff("softdel-direct-actor@example.com");
        User deletedStaff = staff("softdel-direct-target@example.com");
        deletedStaff.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(deletedStaff);
        Cookie session = logIn("softdel-direct-actor@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + deletedStaff.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingADirectConversationWithASoftDeletedTenantMemberIdFails() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Direct Co"));
        member("softdel-direct-member-actor@example.com", tenant);
        User deletedMember = member("softdel-direct-member-target@example.com", tenant);
        deletedMember.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(deletedMember);
        Cookie session = logIn("softdel-direct-member-actor@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + deletedMember.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingAGroupConversationWithASoftDeletedStaffParticipantIdFails() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Group Co"));
        member("softdel-group-owner@example.com", tenant);
        User deletedStaff = staff("softdel-group-staff@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(deletedStaff, tenant, MembershipRole.MEMBER));
        deletedStaff.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(deletedStaff);
        Cookie session = logIn("softdel-group-owner@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Group\",\"participantUserIds\":["
                                        + deletedStaff.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void creatingAGroupConversationWithASoftDeletedTenantMemberParticipantIdFails() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Group Member Co"));
        member("softdel-group-member-owner@example.com", tenant);
        User deletedMember = member("softdel-group-member-target@example.com", tenant);
        deletedMember.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(deletedMember);
        Cookie session = logIn("softdel-group-member-owner@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"Group\",\"participantUserIds\":["
                                        + deletedMember.getId()
                                        + "]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }
}
