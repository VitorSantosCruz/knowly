package br.com.conectabyte.knowly.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FailedAttemptService {

    private static final String ATTEMPTS_KEY_PREFIX = "auth:failed-attempts:";
    private static final String LOCKOUT_KEY_PREFIX = "auth:lockout:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public FailedAttemptService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey(email)));
    }

    /**
     * @return true iff this call is the one that just crossed the lockout threshold.
     */
    public boolean recordFailure(String email) {
        String attemptsKey = attemptsKey(email);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, properties.lockout().duration());
        }

        if (attempts != null && attempts >= properties.lockout().maxAttempts()) {
            redisTemplate
                    .opsForValue()
                    .set(lockoutKey(email), "1", properties.lockout().duration());
            redisTemplate.delete(attemptsKey);
            return true;
        }

        return false;
    }

    public void lockForAbuse(String email) {
        redisTemplate
                .opsForValue()
                .set(lockoutKey(email), "1", properties.lockout().abuseDuration());
    }

    public void recordSuccess(String email) {
        redisTemplate.delete(attemptsKey(email));
        redisTemplate.delete(lockoutKey(email));
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + email.toLowerCase();
    }

    private String lockoutKey(String email) {
        return LOCKOUT_KEY_PREFIX + email.toLowerCase();
    }
}
