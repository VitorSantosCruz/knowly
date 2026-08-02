package br.com.conectabyte.knowly.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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
 * Regression coverage: {@code CreationValidationAuditAdvice} used to be the sole
 * {@code @ExceptionHandler(MethodArgumentNotValidException.class)} registered app-wide, and
 * returned an empty 400 body for every endpoint it wasn't scoped to audit -- leaving every other
 * {@code @Valid} failure in the app with no structured error the frontend could parse. Asserts the
 * response shape the frontend's {@code complete-profile-page.component.ts}/{@code
 * tenant-create-page.component.ts} already expect: {@code {"errors": [{"field": ..., "message":
 * ...}, ...]}}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidationErrorResponseBodyIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
    @MockitoBean private JavaMailSender mailSender;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Test
    void loginRequestWithInvalidEmailReturnsStructuredFieldErrors() throws Exception {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode body = OBJECT_MAPPER.readTree(response.getResponse().getContentAsString());
        JsonNode errors = body.get("errors");
        assertThat(errors).isNotNull();
        assertThat(errors.isArray()).isTrue();
        assertThat(errors.size()).isGreaterThan(0);
        assertThat(errors.get(0).get("field").asText()).isEqualTo("email");
    }

    @Test
    void completeOwnProfileWithBlankCountryCodeReturnsStructuredFieldErrors() throws Exception {
        userRepository.saveAndFlush(new User("bootstrap-user@example.com"));
        Cookie session = logIn("bootstrap-user@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Test User\",\"taxId\":\"52998224725\","
                                        + "\"countryCode\":\"\","
                                        + "\"address\":{\"addressLine1\":\"Rua Um, 100\","
                                        + "\"addressLine2\":\"Centro\",\"city\":\"Sao Paulo\","
                                        + "\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                                        + "\"countryCode\":\"BR\"},"
                                        + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\","
                                        + "\"isPrimary\":false}]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode body = OBJECT_MAPPER.readTree(response.getResponse().getContentAsString());
        JsonNode errors = body.get("errors");
        assertThat(errors).isNotNull();
        assertThat(errors.isArray()).isTrue();
        boolean hasCountryCodeError = false;
        for (JsonNode error : errors) {
            if ("countryCode".equals(error.get("field").asText())) {
                hasCountryCodeError = true;
            }
        }
        assertThat(hasCountryCodeError).isTrue();
    }
}
