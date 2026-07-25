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
     * Records one attempt of the given {@code action} from {@code ip} and reports whether {@code
     * threshold} has been exceeded for that action, meaning a CAPTCHA should now be required. Each
     * action has its own counter — a burst on one endpoint (e.g. retrying a code) must not trigger
     * CAPTCHA on an unrelated one (e.g. requesting a new login). The threshold is a parameter, not
     * a single shared property, because verify endpoints are legitimately called far more often per
     * session than the initial login request.
     */
    public boolean recordRequestAndIsVelocityExceeded(String ip, String action, int threshold) {
        String key = VELOCITY_KEY_PREFIX + action + ":" + ip;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, properties.captcha().velocityWindow());
        }

        return count != null && count > threshold;
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
