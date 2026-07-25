package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "knowly.auth.login-code.length=6",
                            "knowly.auth.login-code.ttl=10m",
                            "knowly.auth.login-code.resend-cooldown=30s",
                            "knowly.auth.one-time-password.length=12",
                            "knowly.auth.one-time-password.ttl=15d",
                            "knowly.auth.lockout.max-attempts=3",
                            "knowly.auth.lockout.duration=15m",
                            "knowly.auth.lockout.abuse-request-threshold=5",
                            "knowly.auth.lockout.abuse-duration=1h",
                            "knowly.auth.captcha.velocity-threshold=5",
                            "knowly.auth.captcha.verify-velocity-threshold=20",
                            "knowly.auth.captcha.velocity-window=5m",
                            "knowly.auth.captcha.turnstile-secret=test-secret");

    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfig {}

    @Test
    void bindsAllPropertiesFromConfiguration() {
        contextRunner.run(
                context -> {
                    AuthProperties properties = context.getBean(AuthProperties.class);

                    assertThat(properties.loginCode().length()).isEqualTo(6);
                    assertThat(properties.loginCode().ttl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.loginCode().resendCooldown())
                            .isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.oneTimePassword().length()).isEqualTo(12);
                    assertThat(properties.oneTimePassword().ttl()).isEqualTo(Duration.ofDays(15));
                    assertThat(properties.lockout().maxAttempts()).isEqualTo(3);
                    assertThat(properties.lockout().duration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.lockout().abuseRequestThreshold()).isEqualTo(5);
                    assertThat(properties.lockout().abuseDuration()).isEqualTo(Duration.ofHours(1));
                    assertThat(properties.captcha().velocityThreshold()).isEqualTo(5);
                    assertThat(properties.captcha().verifyVelocityThreshold()).isEqualTo(20);
                    assertThat(properties.captcha().velocityWindow())
                            .isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.captcha().turnstileSecret()).isEqualTo("test-secret");
                });
    }
}
