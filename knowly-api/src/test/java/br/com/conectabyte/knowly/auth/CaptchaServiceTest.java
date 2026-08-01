package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CaptchaServiceTest {

    private MockRestServiceServer server;
    private CaptchaService captchaService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthProperties properties =
                new AuthProperties(
                        null,
                        null,
                        null,
                        new AuthProperties.Captcha(5, 20, Duration.ofMinutes(5), "test-secret"),
                        null);

        captchaService = new CaptchaService(builder, redisTemplate, properties);
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

    @Test
    void doesNotExceedVelocityBelowTheThreshold() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThat(captchaService.recordRequestAndIsVelocityExceeded("1.2.3.4", "login-request", 5))
                .isFalse();
    }

    @Test
    void exceedsVelocityAboveTheThreshold() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        assertThat(captchaService.recordRequestAndIsVelocityExceeded("1.2.3.4", "login-request", 5))
                .isTrue();
    }
}
