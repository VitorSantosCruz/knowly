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

/**
 * chat-group-membership-management: controller/CSRF-layer coverage for the new endpoints. Focused
 * on end-to-end wiring (routes, request/response shapes, CSRF) -- the full authorization matrix is
 * already covered at the service level by {@link ChatGroupMembershipServiceTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatGroupMembershipControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
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

    @Test
    void fullGroupLifecycleAcrossTheNewEndpoints() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Group Lifecycle Co"));
        User creator = member("group-creator@example.com", tenant);
        User invitee = member("group-invitee@example.com", tenant);

        Cookie session = logIn("group-creator@example.com");
        Cookie csrf = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"GROUP\",\"tenantId\":"
                                        + tenant.getId()
                                        + ",\"title\":\"g\",\"participantUserIds\":["
                                        + invitee.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        // Promote to admin (REQ-2).
        var promoteResponse =
                mockMvc.post()
                        .uri(
                                "/api/chat/conversations/{id}/admins/{userId}",
                                conversationId,
                                invitee.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();
        assertThat(promoteResponse).hasStatus(HttpStatus.OK);

        // Change visibility (REQ-23).
        var visibilityResponse =
                mockMvc.put()
                        .uri("/api/chat/conversations/{id}/visibility", conversationId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}")
                        .exchange();
        assertThat(visibilityResponse).hasStatus(HttpStatus.OK);

        // Discovery no longer includes this group for the creator (already a participant).
        var discoveryResponse =
                mockMvc.get().uri("/api/chat/discoverable-groups").cookie(session).exchange();
        assertThat(discoveryResponse).hasStatus(HttpStatus.OK);

        // Leave (REQ-18) as the original creator; invitee remains.
        var leaveResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations/{id}/leave", conversationId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();
        assertThat(leaveResponse).hasStatus(HttpStatus.NO_CONTENT);

        // Delete as the remaining group admin (invitee).
        Cookie inviteeSession = logIn("group-invitee@example.com");
        Cookie inviteeCsrf = obtainCsrfCookie();
        var deleteResponse =
                mockMvc.delete()
                        .uri("/api/chat/conversations/{id}", conversationId)
                        .cookie(inviteeSession)
                        .cookie(inviteeCsrf)
                        .header("X-XSRF-TOKEN", inviteeCsrf.getValue())
                        .exchange();
        assertThat(deleteResponse).hasStatus(HttpStatus.NO_CONTENT);
    }
}
