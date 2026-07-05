package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CaptchaServiceTest {

    private MockRestServiceServer server;
    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        AuthProperties properties =
                new AuthProperties(
                        null,
                        null,
                        null,
                        new AuthProperties.Captcha(5, Duration.ofMinutes(5), "test-secret"));

        captchaService = new CaptchaService(builder, properties);
    }

    @Test
    void returnsTrueWhenTurnstileConfirmsSuccess() {
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

        assertThat(captchaService.verify("valid-token")).isTrue();
    }

    @Test
    void returnsFalseWhenTurnstileRejectsTheToken() {
        server.expect(requestTo("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .andRespond(withSuccess("{\"success\": false}", MediaType.APPLICATION_JSON));

        assertThat(captchaService.verify("bad-token")).isFalse();
    }

    @Test
    void returnsFalseForABlankOrMissingTokenWithoutCallingTurnstile() {
        assertThat(captchaService.verify("")).isFalse();
        assertThat(captchaService.verify(null)).isFalse();
    }
}
