package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class OneTimePasswordServiceTest {

    @Autowired private OneTimePasswordService oneTimePasswordService;

    @Autowired private UserRepository userRepository;

    @Test
    void generatesAndPersistsAHashedPasswordOfTheConfiguredLength() {
        User user = userRepository.saveAndFlush(new User("generate@example.com"));

        String password = oneTimePasswordService.generateFor(user);

        assertThat(password).hasSize(12);
        assertThat(user.getOneTimePasswordHash()).isNotNull().isNotEqualTo(password);
        assertThat(user.getOneTimePasswordIssuedAt()).isNotNull();
    }

    @Test
    void hasNoValidPasswordForAFreshUser() {
        User user = userRepository.saveAndFlush(new User("fresh@example.com"));

        assertThat(oneTimePasswordService.hasValidPassword(user)).isFalse();
    }

    @Test
    void verifyAndRotateSucceedsAndIssuesADifferentPassword() {
        User user = userRepository.saveAndFlush(new User("rotate@example.com"));
        String original = oneTimePasswordService.generateFor(user);

        var rotated = oneTimePasswordService.verifyAndRotate(user, original);

        assertThat(rotated).isPresent();
        assertThat(rotated.get()).isNotEqualTo(original);
        assertThat(oneTimePasswordService.verifyAndRotate(user, original)).isEmpty();
    }

    @Test
    void verifyAndRotateFailsForAWrongPasswordWithoutRotating() {
        User user = userRepository.saveAndFlush(new User("wrong-password@example.com"));
        String original = oneTimePasswordService.generateFor(user);

        assertThat(oneTimePasswordService.verifyAndRotate(user, "not-the-real-one")).isEmpty();
        assertThat(oneTimePasswordService.verifyAndRotate(user, original)).isPresent();
    }

    @Test
    void treatsAPasswordOlderThanTheConfiguredTtlAsInvalid() {
        User user = userRepository.saveAndFlush(new User("expired@example.com"));
        String original = oneTimePasswordService.generateFor(user);
        user.setOneTimePasswordIssuedAt(Instant.now().minus(16, ChronoUnit.DAYS));
        userRepository.saveAndFlush(user);

        assertThat(oneTimePasswordService.hasValidPassword(user)).isFalse();
        assertThat(oneTimePasswordService.verifyAndRotate(user, original)).isEmpty();
    }
}
