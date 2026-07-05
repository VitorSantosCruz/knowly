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

    public void recordVerifyAttempt(String email) {
        redisTemplate.delete(requestCountKey(email));
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email.toLowerCase();
    }

    private String requestCountKey(String email) {
        return REQUEST_COUNT_KEY_PREFIX + email.toLowerCase();
    }
}
