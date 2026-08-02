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
 * REQ-6: {@code POST /api/users/me/profile/complete} is the bootstrap account's one-time,
 * no-approval self-completion path, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapProfileCompletionIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap-test@conectabyte.com";

    private static final String COMPLETE_PROFILE_JSON =
            "{\"fullName\":\"Test User\","
                    + "\"taxId\":\"52998224725\",\"countryCode\":\"BR\","
                    + "\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\","
                    + "\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                    + "\"countryCode\":\"BR\"},"
                    + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}";

    private static final String INCOMPLETE_PROFILE_JSON =
            "{\"fullName\":\"Test User\","
                    + "\"taxId\":\"52998224725\","
                    + "\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\","
                    + "\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                    + "\"countryCode\":\"BR\"},"
                    + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}";

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
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
    void completingWithEveryRequiredFieldTransitionsTheAccountAndUnblocksFurtherRequests() {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var completeResponse =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_PROFILE_JSON)
                        .exchange();

        assertThat(completeResponse).hasStatus(HttpStatus.OK);

        var nextResponse = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();
        assertThat(nextResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void completingASecondTimeIsRejectedAsAlreadyComplete() throws Exception {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        mockMvc.post()
                .uri("/api/users/me/profile/complete")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMPLETE_PROFILE_JSON)
                .exchange();

        var secondResponse =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_PROFILE_JSON)
                        .exchange();

        assertThat(secondResponse).hasStatus(HttpStatus.CONFLICT);
        assertThat(secondResponse.getResponse().getContentAsString())
                .contains("PROFILE_ALREADY_COMPLETE");
    }

    @Test
    void submittingAllButOneRequiredFieldIsRejectedAndTheAccountRemainsPending() throws Exception {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INCOMPLETE_PROFILE_JSON)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);

        var stillPendingResponse = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();
        assertThat(stillPendingResponse).hasStatus(HttpStatus.CONFLICT);
        assertThat(stillPendingResponse.getResponse().getContentAsString())
                .contains("PROFILE_COMPLETION_REQUIRED");
    }

    @Test
    void completingTheBootstrapAccountEmitsAnAuditEvent() {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_PROFILE_JSON)
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);

        User bootstrap = userRepository.findByEmailIgnoreCase(BOOTSTRAP_EMAIL).orElseThrow();
        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(bootstrap.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("identity.profile.complete");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }
}
