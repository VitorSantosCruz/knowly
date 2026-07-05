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
class FailedAttemptServiceTest {

    @Autowired private FailedAttemptService failedAttemptService;

    @Test
    void isNotLockedByDefault() {
        assertThat(failedAttemptService.isLocked("fresh@example.com")).isFalse();
    }

    @Test
    void locksAfterReachingTheConfiguredMaxAttempts() {
        String email = "brute-forced@example.com";

        failedAttemptService.recordFailure(email);
        failedAttemptService.recordFailure(email);
        assertThat(failedAttemptService.isLocked(email)).isFalse();

        failedAttemptService.recordFailure(email);
        assertThat(failedAttemptService.isLocked(email)).isTrue();
    }

    @Test
    void sharesTheCounterAcrossDifferentFailureSources() {
        String email = "mixed-attempts@example.com";

        failedAttemptService.recordFailure(email); // e.g. wrong code
        failedAttemptService.recordFailure(email); // e.g. wrong password
        failedAttemptService.recordFailure(email); // e.g. wrong code again

        assertThat(failedAttemptService.isLocked(email)).isTrue();
    }

    @Test
    void successResetsTheCounterAndAnyLockout() {
        String email = "recovers@example.com";

        failedAttemptService.recordFailure(email);
        failedAttemptService.recordFailure(email);
        failedAttemptService.recordFailure(email);
        assertThat(failedAttemptService.isLocked(email)).isTrue();

        failedAttemptService.recordSuccess(email);

        assertThat(failedAttemptService.isLocked(email)).isFalse();
    }
}
