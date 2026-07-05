package br.com.conectabyte.knowly.auth;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginCodeService {

    private static final String KEY_PREFIX = "auth:login-code:";

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public LoginCodeService(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public String generate(String email) {
        String code = generateNumericCode(properties.loginCode().length());

        redisTemplate
                .opsForValue()
                .set(
                        key(email),
                        passwordEncoder.encode(code),
                        properties.loginCode().ttl().toMillis(),
                        TimeUnit.MILLISECONDS);

        return code;
    }

    public boolean verify(String email, String code) {
        String hash = redisTemplate.opsForValue().get(key(email));

        if (hash == null) {
            return false;
        }

        boolean matches = passwordEncoder.matches(code, hash);

        if (matches) {
            redisTemplate.delete(key(email));
        }

        return matches;
    }

    private String key(String email) {
        return KEY_PREFIX + email.toLowerCase();
    }

    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }

        return code.toString();
    }
}
