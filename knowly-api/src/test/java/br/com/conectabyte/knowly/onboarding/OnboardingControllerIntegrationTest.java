package br.com.conectabyte.knowly.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
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
class OnboardingControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
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

    @Test
    void aFreshUsersStatusReadsAsNotCompleted() throws Exception {
        userRepository.saveAndFlush(new User("fresh@example.com"));
        Cookie session = logIn("fresh@example.com");

        var response =
                mockMvc.get().uri("/api/users/me/onboarding-status").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"completed\":false");
    }

    @Test
    void markingCompleteThenReadingBackReturnsCompleted() throws Exception {
        userRepository.saveAndFlush(new User("complete@example.com"));
        Cookie session = logIn("complete@example.com");

        var markResponse =
                mockMvc.post().uri("/api/users/me/onboarding-complete").cookie(session).exchange();
        assertThat(markResponse).hasStatus(HttpStatus.OK);

        var statusResponse =
                mockMvc.get().uri("/api/users/me/onboarding-status").cookie(session).exchange();
        assertThat(statusResponse.getResponse().getContentAsString())
                .contains("\"completed\":true");
    }

    @Test
    void markingCompleteTwiceDoesNotError() {
        userRepository.saveAndFlush(new User("twice@example.com"));
        Cookie session = logIn("twice@example.com");

        mockMvc.post().uri("/api/users/me/onboarding-complete").cookie(session).exchange();
        var secondResponse =
                mockMvc.post().uri("/api/users/me/onboarding-complete").cookie(session).exchange();

        assertThat(secondResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void bothEndpointsRequireAuthentication() {
        var statusResponse = mockMvc.get().uri("/api/users/me/onboarding-status").exchange();
        var completeResponse = mockMvc.post().uri("/api/users/me/onboarding-complete").exchange();

        assertThat(statusResponse).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(completeResponse).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aUserWithTwoTenantMembershipsHasOneOnboardingStatusNotOnePerTenant() throws Exception {
        User user = userRepository.saveAndFlush(new User("multitenant@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantA, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantB, MembershipRole.MEMBER));

        Cookie session = logIn("multitenant@example.com");
        mockMvc.post().uri("/api/users/me/onboarding-complete").cookie(session).exchange();

        var statusResponse =
                mockMvc.get().uri("/api/users/me/onboarding-status").cookie(session).exchange();
        assertThat(statusResponse.getResponse().getContentAsString())
                .contains("\"completed\":true");
    }

    @Test
    void bothEndpointsProduceAnAuditEvent() {
        User user = userRepository.saveAndFlush(new User("audited@example.com"));
        Cookie session = logIn("audited@example.com");

        mockMvc.get().uri("/api/users/me/onboarding-status").cookie(session).exchange();
        mockMvc.post().uri("/api/users/me/onboarding-complete").cookie(session).exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events)
                .extracting(AuditEvent::getAction)
                .contains("onboarding.status.view", "onboarding.complete");
    }
}
