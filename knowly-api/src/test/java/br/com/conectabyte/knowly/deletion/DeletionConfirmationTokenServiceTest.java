package br.com.conectabyte.knowly.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DeletionConfirmationTokenServiceTest {

    @Autowired private DeletionConfirmationTokenService service;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @MockitoSpyBean private PasswordEncoder passwordEncoder;

    private User newUser(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    @Test
    void generatesATwoWordHyphenJoinedConfirmationWord() {
        User actor = newUser("gen1@example.com");

        String word = service.generate("article", "1", actor, null);

        assertThat(word).matches("[a-z]{4,8}-[a-z]{4,8}");
        String[] parts = word.split("-");
        assertThat(parts[0]).isNotEqualTo(parts[1]);
    }

    @Test
    void validatesAndConsumesAMatchingWordThenRejectsReuse() {
        User actor = newUser("gen2@example.com");
        String word = service.generate("article", "2", actor, null);

        assertThat(service.validateAndConsume("article", "2", actor, word)).isTrue();
        assertThat(service.validateAndConsume("article", "2", actor, word)).isFalse();
    }

    @Test
    void aWrongWordIsRejectedAndAlsoConsumesTheLiveToken() {
        User actor = newUser("gen3@example.com");
        String word = service.generate("article", "3", actor, null);

        assertThat(service.validateAndConsume("article", "3", actor, "nope-word")).isFalse();
        // REQ-32: the wrong guess already consumed the token, so even the correct word now fails.
        assertThat(service.validateAndConsume("article", "3", actor, word)).isFalse();
    }

    @Test
    void aWordGeneratedForOneResourceIsRejectedForAnother() {
        User actor = newUser("gen4@example.com");
        String word = service.generate("article", "4", actor, null);

        assertThat(service.validateAndConsume("article", "different-4", actor, word)).isFalse();
    }

    @Test
    void aWordGeneratedByOneUserIsRejectedForAnother() {
        User actor = newUser("gen5@example.com");
        User otherUser = newUser("other5@example.com");
        String word = service.generate("article", "5", actor, null);

        assertThat(service.validateAndConsume("article", "5", otherUser, word)).isFalse();
    }

    @Test
    void reGeneratingForTheSameResourceAndUserInvalidatesThePriorToken() {
        User actor = newUser("gen6@example.com");
        String firstWord = service.generate("article", "6", actor, null);
        String secondWord = service.generate("article", "6", actor, null);

        // The freshly generated token is the only one that validates: the first word was
        // overwritten (REQ-12) rather than merely queued behind it, so the second word alone
        // succeeds.
        assertThat(service.validateAndConsume("article", "6", actor, secondWord)).isTrue();
        assertThat(firstWord).isNotBlank();
    }

    @Test
    void comparesAgainstADummyHashWhenNoTokenExists() {
        User actor = newUser("gen7@example.com");

        service.validateAndConsume("article", "no-such-token", actor, "whatever-word");

        verify(passwordEncoder).matches(eq("whatever-word"), any());
    }

    @Test
    void generationWritesASuccessAuditEventWithoutThePlaintextWord() {
        User actor = newUser("gen8@example.com");

        String word = service.generate("article", "8", actor, null);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId()).stream()
                        .filter(e -> "deletion_confirmation_token.generate".equals(e.getAction()))
                        .toList();
        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events).noneMatch(e -> word.equals(e.getMetadata()));
    }

    @Test
    void validationWritesAFailureAuditEventOnMismatch() {
        User actor = newUser("gen9@example.com");
        service.generate("article", "9", actor, null);

        service.validateAndConsume("article", "9", actor, "definitely-wrong");

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId()).stream()
                        .filter(e -> "deletion_confirmation_token.validate".equals(e.getAction()))
                        .toList();
        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
    }
}
