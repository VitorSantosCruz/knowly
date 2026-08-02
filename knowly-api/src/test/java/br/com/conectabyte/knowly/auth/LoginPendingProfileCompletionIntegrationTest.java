package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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
 * REQ-5: the login-code-verify response for the (still-pending) bootstrap account includes {@code
 * pendingProfileCompletion: true}; after completion, a fresh login shows {@code false}, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginPendingProfileCompletionIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap-test@conectabyte.com";

    private static final String COMPLETE_PROFILE_JSON =
            "{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\","
                    + "\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\","
                    + "\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\","
                    + "\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\","
                    + "\"pais\":\"Brasil\"},"
                    + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}";

    @Autowired private MockMvcTester mockMvc;
    @Autowired private LoginCodeService loginCodeService;
    @MockitoBean private JavaMailSender mailSender;

    private void stubMail() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult verifyLogin(String email) {
        stubMail();
        String code = loginCodeService.generate(email);
        return mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                .exchange();
    }

    @Test
    void loginResponseReportsPendingTrueForTheStillPendingBootstrapAccount() throws Exception {
        var result = verifyLogin(BOOTSTRAP_EMAIL);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"pendingProfileCompletion\":true");
    }

    @Test
    void loginResponseReportsPendingFalseAfterCompletion() throws Exception {
        var session = verifyLogin(BOOTSTRAP_EMAIL).getResponse().getCookie("SESSION");

        mockMvc.post()
                .uri("/api/users/me/profile/complete")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMPLETE_PROFILE_JSON)
                .exchange();

        var result = verifyLogin(BOOTSTRAP_EMAIL);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"pendingProfileCompletion\":false");
    }
}
