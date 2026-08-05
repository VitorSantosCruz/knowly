package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.observability.PiiMasker;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @Autowired private AuditEventRepository auditEventRepository;

    @MockitoSpyBean private CaptchaService captchaService;

    @MockitoBean private JavaMailSender mailSender;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthController.class))
                .addAppender(logAppender);

        // The real velocity counter is keyed by IP+action in shared Redis, so it accumulates
        // across every test in this class (all issued from the same loopback address). Defaulting
        // it to "not exceeded" here keeps that shared counter from tipping over as more tests are
        // added; the two dedicated velocity tests below override this per-action as needed.
        doReturn(false)
                .when(captchaService)
                .recordRequestAndIsVelocityExceeded(any(), any(), anyInt());

        // mailSender.send(...) verifications in this class use a generic any(MimeMessage.class)
        // matcher (the mock has no visibility into the message's recipient), so a slow async
        // login-request listener from a previous test that only completes after that test's own
        // await() succeeded can otherwise bleed a stray invocation into this test's count.
        org.mockito.Mockito.clearInvocations(mailSender);
    }

    @AfterEach
    void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthController.class))
                .detachAppender(logAppender);
        org.mockito.Mockito.reset(captchaService);
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
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.contains("SESSION"));
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
    void verifyCodeForASoftDeletedUserIsRejectedLikeInvalidCredentials() throws Exception {
        // Logical-delete-everywhere (2026-08-04): a soft-deleted account must never get a
        // session -- found live when the fix that made staff-user deletion actually work
        // (previously 500'd) surfaced this as the next thing to get right.
        User user = userRepository.saveAndFlush(new User("code-deleted@example.com"));
        user.setDeletedAt(java.time.Instant.now());
        userRepository.saveAndFlush(user);
        String code = loginCodeService.generate("code-deleted@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"code-deleted@example.com\",\"code\":\""
                                        + code
                                        + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(header -> header.contains("SESSION"));
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
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.contains("SESSION"));
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
                                event.getFormattedMessage()
                                                .contains(
                                                        PiiMasker.maskEmail(
                                                                "audit-success@example.com"))
                                        && event.getFormattedMessage().contains("outcome=success"));
        assertThat(logAppender.list)
                .noneMatch(
                        event -> event.getFormattedMessage().contains("audit-success@example.com"));
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
                                event.getFormattedMessage()
                                                .contains(
                                                        PiiMasker.maskEmail(
                                                                "audit-fail@example.com"))
                                        && event.getFormattedMessage()
                                                .contains("outcome=invalid_code"));
        assertThat(logAppender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("audit-fail@example.com"));
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
                                event.getFormattedMessage().contains(PiiMasker.maskEmail(email))
                                        && event.getFormattedMessage().contains("outcome=locked"));
        assertThat(logAppender.list)
                .noneMatch(event -> event.getFormattedMessage().contains(email));
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
    void verifyingACodePartiallyOffsetsTheRequestAbuseCounter() {
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

        // A single verify attempt offsets the abuse counter by one, not a full reset (REQ-4c) —
        // so one more request stays under the threshold, but repeating the request-then-verify
        // cycle would still converge on it.
        loginRequestThrottleService.recordRequest(email);

        assertThat(failedAttemptService.isLocked(email)).isFalse();
    }

    @Test
    void verifyCodeRequiresCaptchaWhenVelocityExceeded() {
        doReturn(true)
                .when(captchaService)
                .recordRequestAndIsVelocityExceeded(any(), eq("login-code-verify"), anyInt());

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"captcha-verify-code@example.com\",\"code\":\"000000\"}");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyCodeChangesTheSessionIdToPreventFixation() {
        userRepository.saveAndFlush(new User("fixation-seed@example.com"));
        userRepository.saveAndFlush(new User("fixation@example.com"));
        String seedCode = loginCodeService.generate("fixation-seed@example.com");
        String code = loginCodeService.generate("fixation@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        // Simulates an attacker who obtained a valid (but unauthenticated) session id and
        // tricked the victim into using it, e.g. via a fixed SESSION cookie.
        var seedResult =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"fixation-seed@example.com\",\"code\":\""
                                        + seedCode
                                        + "\"}")
                        .exchange();
        String preLoginCookie = seedResult.getResponse().getCookie("SESSION").getValue();

        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fixation@example.com\",\"code\":\"" + code + "\"}")
                        .cookie(new Cookie("SESSION", preLoginCookie))
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        String postLoginCookie = result.getResponse().getCookie("SESSION").getValue();
        assertThat(postLoginCookie).isNotEqualTo(preLoginCookie);
    }

    @Test
    void verifyPasswordRequiresCaptchaWhenVelocityExceeded() {
        doReturn(true)
                .when(captchaService)
                .recordRequestAndIsVelocityExceeded(any(), eq("login-password-verify"), anyInt());

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"captcha-verify-password@example.com\",\"password\":\"wrong\"}");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void logoutWithoutAuthenticatedSessionIsUnauthorized() {
        Cookie csrfCookie = obtainCsrfCookie();

        assertThat(
                        mockMvc.post()
                                .uri("/api/auth/logout")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutInvalidatesTheSessionAndClearsTheCookie() {
        userRepository.saveAndFlush(new User("logout@example.com"));
        String code = loginCodeService.generate("logout@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var loginResult =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"logout@example.com\",\"code\":\"" + code + "\"}")
                        .exchange();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        Cookie csrfCookie = obtainCsrfCookie();

        var logoutResult =
                mockMvc.post()
                        .uri("/api/auth/logout")
                        .cookie(sessionCookie)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(logoutResult).hasStatus(HttpStatus.OK);

        // Reusing the invalidated cookie against the same endpoint must now be treated as
        // unauthenticated.
        var protectedResult =
                mockMvc.post()
                        .uri("/api/auth/logout")
                        .cookie(sessionCookie)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .exchange();

        assertThat(protectedResult).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRequestProducesExactlyOneAuditEventForAnExistingEmail() {
        userRepository.saveAndFlush(new User("audit-login-request-known@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        String maskedEmail = PiiMasker.maskEmail("audit-login-request-known@example.com");

        mockMvc.post()
                .uri("/api/auth/login-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"audit-login-request-known@example.com\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_request", maskedEmail);
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getResourceType()).isEqualTo("auth-email");
        assertThat(event.getMetadata()).contains("127.0.0.0");
        assertThat(event.getMetadata()).doesNotContain("127.0.0.1");

        // Drains the async login-request-listener's mail send before this test ends, so it can't
        // straggle into a later test's mailSender verification (the mock has no per-recipient
        // matcher to tell them apart).
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(mailSender).send(any(MimeMessage.class)));
    }

    @Test
    void loginRequestProducesExactlyOneAuditEventForANonExistingEmail() {
        String maskedEmail = PiiMasker.maskEmail("audit-login-request-unknown@example.com");

        mockMvc.post()
                .uri("/api/auth/login-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"audit-login-request-unknown@example.com\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_request", maskedEmail);
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getResourceType()).isEqualTo("auth-email");
    }

    @Test
    void verifyCodeSuccessProducesAnAuditEventWithTheRealActor() {
        String email = "audit-code-success@example.com";
        User user = userRepository.saveAndFlush(new User(email));
        String code = loginCodeService.generate(email);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_code_verify", PiiMasker.maskEmail(email));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events.get(0).getActorUserId()).isEqualTo(user.getId());
        assertThat(events.get(0).getMetadata()).contains("127.0.0.0");
        assertThat(events.get(0).getMetadata()).doesNotContain("127.0.0.1");
    }

    @Test
    void verifyCodeFailureProducesAnAuditEventWithNoActor() {
        String email = "audit-code-failure@example.com";
        userRepository.saveAndFlush(new User(email));
        loginCodeService.generate(email);

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"000000\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_code_verify", PiiMasker.maskEmail(email));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(events.get(0).getActorUserId()).isNull();
    }

    @Test
    void verifyPasswordSuccessProducesAnAuditEventWithTheRealActor() {
        String email = "audit-password-success@example.com";
        User user = userRepository.saveAndFlush(new User(email));
        String password = oneTimePasswordService.generateFor(user);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        mockMvc.post()
                .uri("/api/auth/login-password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_password_verify", PiiMasker.maskEmail(email));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events.get(0).getActorUserId()).isEqualTo(user.getId());
        assertThat(events.get(0).getMetadata()).contains("127.0.0.0");
        assertThat(events.get(0).getMetadata()).doesNotContain("127.0.0.1");
    }

    @Test
    void verifyPasswordFailureProducesAnAuditEventWithNoActor() {
        String email = "audit-password-failure@example.com";
        userRepository.saveAndFlush(new User(email));

        mockMvc.post()
                .uri("/api/auth/login-password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}")
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_password_verify", PiiMasker.maskEmail(email));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(events.get(0).getActorUserId()).isNull();
    }

    @Test
    void lockedOutCodeVerificationProducesALockedOutOutcomeDistinctFromFailure() {
        String email = "audit-code-locked-rejection@example.com";
        String body = "{\"email\":\"" + email + "\",\"code\":\"000000\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.post()
                    .uri("/api/auth/login-code/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .exchange();
        }

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_code_verify", PiiMasker.maskEmail(email));
        assertThat(events).anyMatch(e -> e.getOutcome() == AuditOutcome.LOCKED_OUT);
        assertThat(events)
                .filteredOn(e -> e.getOutcome() == AuditOutcome.LOCKED_OUT)
                .allMatch(e -> e.getActorUserId() == null);
    }

    @Test
    void lockedOutPasswordVerificationProducesALockedOutOutcomeDistinctFromFailure() {
        String email = "audit-password-locked-rejection@example.com";
        userRepository.saveAndFlush(new User(email));
        String body = "{\"email\":\"" + email + "\",\"password\":\"wrong\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.post()
                    .uri("/api/auth/login-password/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .exchange();
        }

        mockMvc.post()
                .uri("/api/auth/login-password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_password_verify", PiiMasker.maskEmail(email));
        assertThat(events).anyMatch(e -> e.getOutcome() == AuditOutcome.LOCKED_OUT);
        assertThat(events)
                .filteredOn(e -> e.getOutcome() == AuditOutcome.LOCKED_OUT)
                .allMatch(e -> e.getActorUserId() == null);
    }

    @Test
    void crossingTheLockoutThresholdProducesASeparateLockoutEvent() {
        String email = "audit-lockout-threshold@example.com";
        String body = "{\"email\":\"" + email + "\",\"code\":\"000000\"}";

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        List<AuditEvent> lockoutEventsAfterFirstFailure =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login.lockout", PiiMasker.maskEmail(email));
        assertThat(lockoutEventsAfterFirstFailure).isEmpty();

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        List<AuditEvent> lockoutEvents =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login.lockout", PiiMasker.maskEmail(email));
        assertThat(lockoutEvents).hasSize(1);
        assertThat(lockoutEvents.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(lockoutEvents.get(0).getMetadata()).contains("127.0.0.0");
        assertThat(lockoutEvents.get(0).getMetadata()).doesNotContain("127.0.0.1");

        List<AuditEvent> failureEvents =
                auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                        "auth.login_code_verify", PiiMasker.maskEmail(email));
        assertThat(failureEvents).hasSize(3);
        assertThat(failureEvents).allMatch(e -> e.getOutcome() == AuditOutcome.FAILURE);
    }

    @Test
    void authenticatedLogoutProducesAnAuditEventWithTheRealActor() {
        String email = "audit-logout@example.com";
        User user = userRepository.saveAndFlush(new User(email));
        String code = loginCodeService.generate(email);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var loginResult =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                        .exchange();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        Cookie csrfCookie = obtainCsrfCookie();

        mockMvc.post()
                .uri("/api/auth/logout")
                .cookie(sessionCookie)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).anyMatch(e -> e.getAction().equals("auth.logout"));
        AuditEvent logoutEvent =
                events.stream().filter(e -> e.getAction().equals("auth.logout")).findFirst().get();
        assertThat(logoutEvent.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(logoutEvent.getActorUserId()).isEqualTo(user.getId());
        assertThat(logoutEvent.getMetadata()).contains("127.0.0.0");
        assertThat(logoutEvent.getMetadata()).doesNotContain("127.0.0.1");
    }

    @Test
    void unauthenticatedLogoutProducesNoAuditEvent() {
        int before =
                auditEventRepository
                        .findByActionAndResourceIdOrderByOccurredAtDesc("auth.logout", null)
                        .size();
        Cookie csrfCookie = obtainCsrfCookie();

        mockMvc.post()
                .uri("/api/auth/logout")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .exchange();

        int after =
                auditEventRepository
                        .findByActionAndResourceIdOrderByOccurredAtDesc("auth.logout", null)
                        .size();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void noAuditEventContainsARawEmailAcrossAllOfThisFeaturesScenarios() {
        String[] emails = {
            "pii-sweep-1@example.com", "pii-sweep-2@example.com", "pii-sweep-3@example.com"
        };
        userRepository.saveAndFlush(new User(emails[0]));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        mockMvc.post()
                .uri("/api/auth/login-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + emails[0] + "\"}")
                .exchange();
        // Drains the async login-request-listener's mail send before moving on, so it can't
        // straggle into a later test's mailSender verification.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(mailSender).send(any(MimeMessage.class)));
        mockMvc.post()
                .uri("/api/auth/login-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + emails[1] + "\",\"code\":\"000000\"}")
                .exchange();
        mockMvc.post()
                .uri("/api/auth/login-password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + emails[2] + "\",\"password\":\"wrong\"}")
                .exchange();

        for (String email : emails) {
            List<AuditEvent> events =
                    auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                            "auth.login_request", PiiMasker.maskEmail(email));
            events.addAll(
                    auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                            "auth.login_code_verify", PiiMasker.maskEmail(email)));
            events.addAll(
                    auditEventRepository.findByActionAndResourceIdOrderByOccurredAtDesc(
                            "auth.login_password_verify", PiiMasker.maskEmail(email)));

            for (AuditEvent event : events) {
                assertThat(event.getResourceId()).doesNotContain(email);
                assertThat(event.getMetadata() == null ? "" : event.getMetadata())
                        .doesNotContain(email);
                // No raw (unmasked) source IP either — only the /24-truncated form is ever
                // written.
                assertThat(event.getMetadata() == null ? "" : event.getMetadata())
                        .doesNotContain("127.0.0.1");
                if (event.getMetadata() != null) {
                    assertThat(event.getMetadata()).contains("127.0.0.0");
                }
            }
        }
    }

    // Deliberately not using SecurityMockMvcRequestPostProcessors.csrf(): it works by
    // reflectively swapping the shared CsrfFilter bean's tokenRepository field for a session-based
    // test stub for the rest of this class's Spring context, which silently breaks the real
    // CookieCsrfTokenRepository for every later test in the suite. Using the real cookie-issuance
    // flow instead is both safer and a more faithful test.
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }
}
