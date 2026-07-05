package br.com.conectabyte.knowly.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CaptchaService {

    private static final String SITEVERIFY_URI = "/turnstile/v0/siteverify";
    private static final String VELOCITY_KEY_PREFIX = "auth:login-velocity:";

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public CaptchaService(
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redisTemplate,
            AuthProperties properties) {
        this.restClient = restClientBuilder.baseUrl("https://challenges.cloudflare.com").build();
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Records one login-request attempt from {@code ip} and reports whether the configured
     * request-velocity threshold has been exceeded, meaning a CAPTCHA should now be required.
     */
    public boolean recordRequestAndIsVelocityExceeded(String ip) {
        String key = VELOCITY_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, properties.captcha().velocityWindow());
        }

        return count != null && count > properties.captcha().velocityThreshold();
    }

    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        var response =
                restClient
                        .post()
                        .uri(SITEVERIFY_URI)
                        .body(
                                new TurnstileVerifyRequest(
                                        properties.captcha().turnstileSecret(), token))
                        .retrieve()
                        .body(TurnstileVerifyResponse.class);

        return response != null && response.success();
    }

    private record TurnstileVerifyRequest(String secret, String response) {}

    private record TurnstileVerifyResponse(boolean success) {}
}
