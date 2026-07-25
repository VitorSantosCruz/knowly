package br.com.conectabyte.knowly.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginRequestThrottleService {

    private static final String COOLDOWN_KEY_PREFIX = "auth:login-code-cooldown:";
    private static final String REQUEST_COUNT_KEY_PREFIX = "auth:login-request-count:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final FailedAttemptService failedAttemptService;

    public LoginRequestThrottleService(
            StringRedisTemplate redisTemplate,
            AuthProperties properties,
            FailedAttemptService failedAttemptService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.failedAttemptService = failedAttemptService;
    }

    public boolean isInCooldown(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(email)));
    }

    public void recordRequest(String email) {
        redisTemplate
                .opsForValue()
                .set(cooldownKey(email), "1", properties.loginCode().resendCooldown());

        String countKey = requestCountKey(email);
        Long count = redisTemplate.opsForValue().increment(countKey);

        if (count != null && count == 1) {
            redisTemplate.expire(countKey, properties.lockout().abuseDuration());
        }

        if (count != null && count >= properties.lockout().abuseRequestThreshold()) {
            failedAttemptService.lockForAbuse(email);
            redisTemplate.delete(countKey);
        }
    }

    /**
     * A verify attempt (even with a wrong code) shows genuine intent to log in, so it offsets the
     * abuse counter — but only by one, not a full reset. A full reset would let an attacker send N
     * requests just under the threshold, throw away one verify call, and repeat indefinitely
     * without ever triggering the abuse lockout; decrementing means that gaming pattern still
     * converges on the threshold, while a genuine user who requests-then-tries-once per code isn't
     * penalized.
     */
    public void recordVerifyAttempt(String email) {
        String countKey = requestCountKey(email);
        Long count = redisTemplate.opsForValue().decrement(countKey);

        if (count != null && count <= 0) {
            redisTemplate.delete(countKey);
        }
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email.toLowerCase();
    }

    private String requestCountKey(String email) {
        return REQUEST_COUNT_KEY_PREFIX + email.toLowerCase();
    }
}
