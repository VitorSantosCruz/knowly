package br.com.conectabyte.knowly.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowly.auth")
public record AuthProperties(
        LoginCode loginCode, OneTimePassword oneTimePassword, Lockout lockout, Captcha captcha) {

    public record LoginCode(int length, Duration ttl) {}

    public record OneTimePassword(int length, Duration ttl) {}

    public record Lockout(int maxAttempts, Duration duration) {}

    public record Captcha(int velocityThreshold, Duration velocityWindow, String turnstileSecret) {}
}
