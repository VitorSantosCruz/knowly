package br.com.conectabyte.knowly.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * specify/features/staff-audit-trail-view/PLAN.md: {@code
 * findTop500ByActorUserIdOrderByOccurredAtDesc} caps the result at the DB layer (not a post-fetch
 * {@code .subList}), so a target user with an unbounded number of audit events never has more than
 * 500 rows pulled into memory.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AuditEventRepositoryTest {

    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private void insertEvent(Long actorUserId, Instant occurredAt, String action) {
        jdbcTemplate.update(
                "insert into audit_events (occurred_at, actor_user_id, action, outcome) values (?,"
                        + " ?, ?, ?)",
                java.sql.Timestamp.from(occurredAt),
                actorUserId,
                action,
                AuditOutcome.SUCCESS.name());
    }

    @Test
    void capsAt500RowsMostRecentFirstAndOnlyForTheGivenActor() {
        User actor = user("audit-cap-actor@example.com");
        User otherActor = user("audit-cap-other-actor@example.com");
        Instant base = Instant.now().minus(1000, ChronoUnit.MINUTES);

        for (int i = 0; i < 510; i++) {
            insertEvent(actor.getId(), base.plus(i, ChronoUnit.MINUTES), "action-" + i);
        }
        insertEvent(otherActor.getId(), base.plus(1000, ChronoUnit.MINUTES), "other-actor-action");

        List<AuditEvent> result =
                auditEventRepository.findTop500ByActorUserIdOrderByOccurredAtDesc(actor.getId());

        assertThat(result).hasSize(500);
        assertThat(result).allSatisfy(e -> assertThat(e.getActorUserId()).isEqualTo(actor.getId()));
        assertThat(result.get(0).getAction()).isEqualTo("action-509");
        assertThat(result.get(499).getAction()).isEqualTo("action-10");
    }
}
