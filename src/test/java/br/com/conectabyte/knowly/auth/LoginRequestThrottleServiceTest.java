package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LoginRequestThrottleServiceTest {

    @Autowired private LoginRequestThrottleService throttleService;

    @Autowired private FailedAttemptService failedAttemptService;

    @Test
    void isNotInCooldownByDefault() {
        assertThat(throttleService.isInCooldown("fresh@example.com")).isFalse();
    }

    @Test
    void entersCooldownAfterARequestIsRecorded() {
        String email = "cooldown@example.com";

        throttleService.recordRequest(email);

        assertThat(throttleService.isInCooldown(email)).isTrue();
    }

    @Test
    void locksForAbuseAfterTheConfiguredRequestThresholdWithNoVerification() {
        String email = "abuser@example.com";

        for (int i = 0; i < 5; i++) {
            throttleService.recordRequest(email);
        }

        assertThat(failedAttemptService.isLocked(email)).isTrue();
    }

    @Test
    void aVerificationAttemptResetsTheAbuseCounter() {
        String email = "legitimate-retry@example.com";

        for (int i = 0; i < 4; i++) {
            throttleService.recordRequest(email);
        }
        throttleService.recordVerifyAttempt(email);

        for (int i = 0; i < 4; i++) {
            throttleService.recordRequest(email);
        }

        assertThat(failedAttemptService.isLocked(email)).isFalse();
    }
}
