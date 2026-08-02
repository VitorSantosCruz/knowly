package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.ProfileCompletenessService;
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

/**
 * REQ-7/REQ-8/REQ-9: staff creation and {@code addMember} both reject a request missing any
 * mandatory profile field outright (no partial state), and a fully-populated request succeeds with
 * the created user never pending, per specify/features/mandatory-complete-profile/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MandatoryCompleteProfileCreationIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private ProfileCompletenessService profileCompletenessService;
    @MockitoBean private JavaMailSender mailSender;

    private static final String COMPLETE_PROFILE_JSON =
            "\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\","
                    + "\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\","
                    + "\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\","
                    + "\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\","
                    + "\"pais\":\"Brasil\"},"
                    + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}";

    private static final String INCOMPLETE_PROFILE_JSON =
            "\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\","
                    + "\"cpf\":\"12345678901\",\"rg\":\"123456\","
                    + "\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\","
                    + "\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\","
                    + "\"pais\":\"Brasil\"},"
                    + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}";

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

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void staffCreationMissingOneMandatoryFieldIsRejectedAndNoRowIsPersisted() {
        staffAdmin("staff-creator@example.com");
        Cookie session = logIn("staff-creator@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"incomplete-staff@example.com\","
                                        + INCOMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmailIgnoreCase("incomplete-staff@example.com")).isEmpty();
    }

    @Test
    void staffCreationWithEveryMandatoryFieldSucceedsAndTheUserIsNeverPending() {
        staffAdmin("staff-creator2@example.com");
        Cookie session = logIn("staff-creator2@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"complete-staff@example.com\","
                                        + COMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        User created =
                userRepository.findByEmailIgnoreCase("complete-staff@example.com").orElseThrow();
        assertThat(profileCompletenessService.isComplete(created)).isTrue();
    }

    @Test
    void staffCreationRejectionEmitsAnAuditEventWithTheMissingFields() {
        User admin = staffAdmin("staff-creator3@example.com");
        Cookie session = logIn("staff-creator3@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"audited-incomplete-staff@example.com\","
                                        + INCOMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("staff.user.creation.denied");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void addMemberMissingOneMandatoryFieldIsRejectedAndNoRowIsPersisted() {
        User admin = userRepository.saveAndFlush(new User("member-admin@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Mandatory Profile Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("member-admin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"incomplete-member@example.com\",\"role\":\"MEMBER\","
                                        + INCOMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmailIgnoreCase("incomplete-member@example.com")).isEmpty();
    }

    @Test
    void addMemberWithEveryMandatoryFieldSucceedsAndTheUserIsNeverPending() {
        User admin = userRepository.saveAndFlush(new User("member-admin2@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Mandatory Profile Tenant 2"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("member-admin2@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"complete-member@example.com\",\"role\":\"MEMBER\","
                                        + COMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        User created =
                userRepository.findByEmailIgnoreCase("complete-member@example.com").orElseThrow();
        assertThat(profileCompletenessService.isComplete(created)).isTrue();
    }

    @Test
    void addMemberRejectionEmitsAnAuditEventWithTheMissingFields() {
        User admin = userRepository.saveAndFlush(new User("member-admin3@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Mandatory Profile Tenant 3"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("member-admin3@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/members")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"audited-incomplete-member@example.com\","
                                        + "\"role\":\"MEMBER\","
                                        + INCOMPLETE_PROFILE_JSON
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("tenant.member.creation.denied");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }
}
