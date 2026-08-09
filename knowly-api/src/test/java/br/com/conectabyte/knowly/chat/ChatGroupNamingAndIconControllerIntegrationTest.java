package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
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

/** chat-group-naming-and-icon: group creation icon + rename endpoint coverage. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatGroupNamingAndIconControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private ChatConversationRepository chatConversationRepository;
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

    private Long createGroup(Cookie session, Cookie csrf, Long tenantId, String title, String icon)
            throws Exception {
        var response =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenantId
                                        + ",\"title\":\""
                                        + title
                                        + "\",\"participantUserIds\":[]"
                                        + (icon == null ? "" : ",\"icon\":\"" + icon + "\"")
                                        + "}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.CREATED);
        return ((Number)
                        com.jayway.jsonpath.JsonPath.read(
                                response.getResponse().getContentAsString(), "$.id"))
                .longValue();
    }

    @Test
    void creatingAGroupWithAValidIconPersistsIt() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Icon Group Co"));
        member("icon-creator@example.com", tenant);
        Cookie session = logIn("icon-creator@example.com");
        Cookie csrf = obtainCsrfCookie();

        Long conversationId = createGroup(session, csrf, tenant.getId(), "Iconic", "BOOK_OPEN");

        ChatConversation saved = chatConversationRepository.findById(conversationId).orElseThrow();
        assertThat(saved.getIcon()).isEqualTo(br.com.conectabyte.knowly.icon.IconKey.BOOK_OPEN);
    }

    @Test
    void creatingAGroupWithAnInvalidIconReturnsBadRequestAndCreatesNothing() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Bad Icon Group Co"));
        member("badicon-creator@example.com", tenant);
        Cookie session = logIn("badicon-creator@example.com");
        Cookie csrf = obtainCsrfCookie();
        long before = chatConversationRepository.count();

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
                                        + ",\"title\":\"Bad\",\"participantUserIds\":[],\"icon\":\"NOT_REAL\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(chatConversationRepository.count()).isEqualTo(before);
    }

    @Test
    void creatingADirectConversationWithAnIconIgnoresIt() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Direct Icon Co"));
        member("direct-creator@example.com", tenant);
        User target = member("direct-target@example.com", tenant);
        Cookie session = logIn("direct-creator@example.com");
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
                                        + target.getId()
                                        + "],\"icon\":\"STAR\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        response.getResponse().getContentAsString(), "$.id"))
                        .longValue();
        ChatConversation saved = chatConversationRepository.findById(conversationId).orElseThrow();
        assertThat(saved.getIcon()).isNull();
    }

    @Test
    void aCurrentGroupAdminRenamesTitleAndIconAndItPersists() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Rename Group Co"));
        member("rename-admin@example.com", tenant);
        Cookie session = logIn("rename-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        Long conversationId = createGroup(session, csrf, tenant.getId(), "Original", null);

        var renameResponse =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"icon\":\"STAR\"}")
                        .exchange();

        assertThat(renameResponse).hasStatus(HttpStatus.OK);
        assertThat(renameResponse.getResponse().getContentAsString())
                .contains("\"title\":\"Renamed\"")
                .contains("\"icon\":\"STAR\"");

        var getResponse =
                mockMvc.get()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(session)
                        .exchange();
        assertThat(getResponse.getResponse().getContentAsString())
                .contains("\"title\":\"Renamed\"")
                .contains("\"icon\":\"STAR\"");
    }

    @Test
    void aNonAdminParticipantOfTheTargetGroupGetsForbiddenOnRename() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NonAdmin Rename Co"));
        member("rename-owner2@example.com", tenant);
        User invitee = member("rename-invitee@example.com", tenant);
        Cookie ownerSession = logIn("rename-owner2@example.com");
        Cookie ownerCsrf = obtainCsrfCookie();

        Long conversationId = createGroup(ownerSession, ownerCsrf, tenant.getId(), "Group", null);
        mockMvc.post()
                .uri("/api/chat/conversations/{id}/participants", conversationId)
                .cookie(ownerSession)
                .cookie(ownerCsrf)
                .header("X-XSRF-TOKEN", ownerCsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[" + invitee.getId() + "]}")
                .exchange();

        Cookie inviteeSession = logIn("rename-invitee@example.com");
        Cookie inviteeCsrf = obtainCsrfCookie();

        var response =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(inviteeSession)
                        .cookie(inviteeCsrf)
                        .header("X-XSRF-TOKEN", inviteeCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAdminOfADifferentGroupCannotRenameThisOne() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Cross Group Co"));
        member("group-a-admin@example.com", tenant);
        member("group-b-admin@example.com", tenant);
        Cookie sessionA = logIn("group-a-admin@example.com");
        Cookie csrfA = obtainCsrfCookie();
        Long conversationA = createGroup(sessionA, csrfA, tenant.getId(), "Group A", null);

        Cookie sessionB = logIn("group-b-admin@example.com");
        Cookie csrfB = obtainCsrfCookie();
        createGroup(sessionB, csrfB, tenant.getId(), "Group B", null);

        var response =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", conversationA)
                        .cookie(sessionB)
                        .cookie(csrfB)
                        .header("X-XSRF-TOKEN", csrfB.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cross rename\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownConversationIdReturnsNotFoundOnRename() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Unknown Rename Co"));
        member("unknown-rename@example.com", tenant);
        Cookie session = logIn("unknown-rename@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", 999999999L)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void renameWithBlankTitleOrInvalidIconReturnsBadRequestWithNoPartialUpdate() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Rename Validation Co"));
        member("rename-validation@example.com", tenant);
        Cookie session = logIn("rename-validation@example.com");
        Cookie csrf = obtainCsrfCookie();

        Long conversationId =
                createGroup(session, csrf, tenant.getId(), "Original title", "FOLDER");

        var blankTitleResponse =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}")
                        .exchange();
        assertThat(blankTitleResponse).hasStatus(HttpStatus.BAD_REQUEST);

        var invalidIconResponse =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid\",\"icon\":\"NOT_REAL\"}")
                        .exchange();
        assertThat(invalidIconResponse).hasStatus(HttpStatus.BAD_REQUEST);

        ChatConversation reloaded =
                chatConversationRepository.findById(conversationId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Original title");
        assertThat(reloaded.getIcon()).isEqualTo(br.com.conectabyte.knowly.icon.IconKey.FOLDER);
    }
}
