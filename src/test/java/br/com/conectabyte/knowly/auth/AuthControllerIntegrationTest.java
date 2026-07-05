package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
class AuthControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;

    @Autowired private UserRepository userRepository;

    @Autowired private LoginCodeService loginCodeService;

    @Autowired private OneTimePasswordService oneTimePasswordService;

    @Autowired private FailedAttemptService failedAttemptService;

    @Autowired private LoginRequestThrottleService loginRequestThrottleService;

    @MockitoBean private JavaMailSender mailSender;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthController.class))
                .addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthController.class))
                .detachAppender(logAppender);
    }

    @Test
    void loginRequestForAnExistingEmailReturnsGenericSuccessAndSendsACode() throws Exception {
        userRepository.saveAndFlush(new User("known@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"known@example.com\"}");

        // The response returns before the email-existence-dependent work runs (REQ-3a), so the
        // side effect is asserted asynchronously here, not the response itself.
        assertThat(response).hasStatus(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(mailSender).send(any(MimeMessage.class)));
    }

    @Test
    void loginRequestForANonExistingEmailAlsoReturnsGenericSuccessWithNoEmailSent() {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}");

        assertThat(response).hasStatus(HttpStatus.OK);
        verify(mailSender, after(3000).never()).send(any(MimeMessage.class));
    }

    @Test
    void loginRequestRejectsAMalformedEmail() {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyCodeWithCorrectCodeLogsInAndEstablishesASession() throws Exception {
        userRepository.saveAndFlush(new User("code-ok@example.com"));
        String code = loginCodeService.generate("code-ok@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"code-ok@example.com\",\"code\":\"" + code + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SESSION");
        verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verifyCodeWithWrongCodeReturnsInvalidCredentials() {
        userRepository.saveAndFlush(new User("code-wrong@example.com"));
        loginCodeService.generate("code-wrong@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"code-wrong@example.com\",\"code\":\"000000\"}");

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void locksOutAfterThreeWrongCodeAttemptsEvenForANonExistingEmail() {
        String email = "code-lockout@example.com";
        String body = "{\"email\":\"" + email + "\",\"code\":\"000000\"}";

        for (int i = 0; i < 3; i++) {
            assertThat(
                            mockMvc.post()
                                    .uri("/api/auth/login-code/verify")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        var fourthAttempt =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);

        assertThat(fourthAttempt).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void verifyPasswordWithCorrectPasswordLogsInRotatesAndEmailsANewOne() throws Exception {
        User user = userRepository.saveAndFlush(new User("password-ok@example.com"));
        String password = oneTimePasswordService.generateFor(user);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var result =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"password-ok@example.com\",\"password\":\""
                                        + password
                                        + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SESSION");
        verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void verifyPasswordWithWrongPasswordReturnsInvalidCredentials() {
        userRepository.saveAndFlush(new User("password-wrong@example.com"));

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"password-wrong@example.com\",\"password\":\"nope\"}");

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verifyPasswordForANonExistingEmailAlsoReturnsInvalidCredentials() {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-pw@example.com\",\"password\":\"whatever\"}");

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void locksOutAfterThreeWrongPasswordAttempts() {
        userRepository.saveAndFlush(new User("password-lockout@example.com"));
        String body = "{\"email\":\"password-lockout@example.com\",\"password\":\"wrong\"}";

        for (int i = 0; i < 3; i++) {
            assertThat(
                            mockMvc.post()
                                    .uri("/api/auth/login-password/verify")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        var fourthAttempt =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);

        assertThat(fourthAttempt).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void logsAnAuditEventOnSuccessfulCodeVerification() {
        userRepository.saveAndFlush(new User("audit-success@example.com"));
        String code = loginCodeService.generate("audit-success@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"audit-success@example.com\",\"code\":\"" + code + "\"}")
                .exchange();

        assertThat(logAppender.list)
                .anyMatch(
                        event ->
                                event.getFormattedMessage().contains("audit-success@example.com")
                                        && event.getFormattedMessage().contains("outcome=success"));
    }

    @Test
    void logsAnAuditEventOnFailedCodeVerification() {
        userRepository.saveAndFlush(new User("audit-fail@example.com"));
        loginCodeService.generate("audit-fail@example.com");

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"audit-fail@example.com\",\"code\":\"000000\"}")
                .exchange();

        assertThat(logAppender.list)
                .anyMatch(
                        event ->
                                event.getFormattedMessage().contains("audit-fail@example.com")
                                        && event.getFormattedMessage()
                                                .contains("outcome=invalid_code"));
    }

    @Test
    void logsAnAuditEventWhenAccountIsLocked() {
        String email = "audit-locked@example.com";
        String body = "{\"email\":\"" + email + "\",\"code\":\"000000\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.post()
                    .uri("/api/auth/login-code/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .exchange();
        }
        logAppender.list.clear();

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(logAppender.list)
                .anyMatch(
                        event ->
                                event.getFormattedMessage().contains(email)
                                        && event.getFormattedMessage().contains("outcome=locked"));
    }

    @Test
    void aSecondLoginRequestWithinTheCooldownDoesNotSendAnotherCode() throws Exception {
        userRepository.saveAndFlush(new User("cooldown-http@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        String body = "{\"email\":\"cooldown-http@example.com\"}";

        assertThat(
                        mockMvc.post()
                                .uri("/api/auth/login-request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .hasStatus(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(mailSender).send(any(MimeMessage.class)));

        assertThat(
                        mockMvc.post()
                                .uri("/api/auth/login-request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .hasStatus(HttpStatus.OK);

        verify(mailSender, after(3000).times(1)).send(any(MimeMessage.class));
    }

    @Test
    void loginRequestSkipsGeneratingACodeWhenTheEmailIsAlreadyLocked() throws Exception {
        String email = "already-locked@example.com";
        userRepository.saveAndFlush(new User(email));
        failedAttemptService.lockForAbuse(email);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}");

        assertThat(response).hasStatus(HttpStatus.OK);
        verify(mailSender, after(3000).never()).send(any(MimeMessage.class));
    }

    @Test
    void verifyingACodeResetsTheRequestAbuseCounter() {
        String email = "resets-abuse-counter@example.com";
        userRepository.saveAndFlush(new User(email));

        for (int i = 0; i < 4; i++) {
            loginRequestThrottleService.recordRequest(email);
        }

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"000000\"}")
                .exchange();

        for (int i = 0; i < 4; i++) {
            loginRequestThrottleService.recordRequest(email);
        }

        assertThat(failedAttemptService.isLocked(email)).isFalse();
    }
}
