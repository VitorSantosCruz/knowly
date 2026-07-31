package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
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
import java.util.List;
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

/** TASKS.md items 94-95, 37/62 (oversight + ticket-lifecycle audit trail). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatTenantIsolationIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private AuditEventRepository auditEventRepository;
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
    void memberOfTenantBCannotReachAMemberOnlyGroupOfTenantA() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Isolation Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Isolation Tenant B"));
        member("iso-a-owner@example.com", tenantA);
        User peerA = member("iso-a-peer@example.com", tenantA);
        member("iso-b-member@example.com", tenantB);

        Cookie ownerSession = logIn("iso-a-owner@example.com");
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
                                        + tenantA.getId()
                                        + ",\"title\":\"A Group\",\"participantUserIds\":["
                                        + peerA.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        Cookie tenantBSession = logIn("iso-b-member@example.com");
        var response =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(tenantBSession)
                        .exchange();

        assertThat(response.getResponse().getStatus())
                .isIn(HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
        assertThat(response.getResponse().getContentAsString()).doesNotContain("A Group");
    }

    @Test
    void staffOnlyConversationIsInvisibleToATenantMember() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Staff Only Isolation Co"));
        member("staffonlyiso-member@example.com", tenant);
        User staffA = userRepository.saveAndFlush(new User("staffonlyiso-a@example.com"));
        staffA.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffA);
        User staffB = userRepository.saveAndFlush(new User("staffonlyiso-b@example.com"));
        staffB.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffB);

        Cookie staffSession = logIn("staffonlyiso-a@example.com");
        Cookie staffCsrf = obtainCsrfCookie();
        var createResponse =
                mockMvc.post()
                        .uri("/api/chat/conversations")
                        .cookie(staffSession)
                        .cookie(staffCsrf)
                        .header("X-XSRF-TOKEN", staffCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"kind\":\"DIRECT\",\"participantUserIds\":["
                                        + staffB.getId()
                                        + "]}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);

        Cookie memberSession = logIn("staffonlyiso-member@example.com");
        var listResponse =
                mockMvc.get().uri("/api/chat/conversations").cookie(memberSession).exchange();

        assertThat(listResponse).hasStatus(HttpStatus.OK);
        assertThat(listResponse.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void staffAdminOversightReadIsAudited() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Oversight Co"));
        member("audit-owner@example.com", tenant);
        User peer = member("audit-peer@example.com", tenant);
        Cookie ownerSession = logIn("audit-owner@example.com");
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
                                        + ",\"title\":\"Audited Group\",\"participantUserIds\":["
                                        + peer.getId()
                                        + "]}")
                        .exchange();
        Long conversationId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        createResponse.getResponse().getContentAsString(), "$.id"))
                        .longValue();

        User admin = userRepository.saveAndFlush(new User("audit-admin@example.com"));
        admin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(admin);
        Cookie adminSession = logIn("audit-admin@example.com");
        var response =
                mockMvc.get()
                        .uri("/api/chat/conversations/" + conversationId)
                        .cookie(adminSession)
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(events).extracting(AuditEvent::getAction).contains("chat.group.oversight_view");
    }
}
