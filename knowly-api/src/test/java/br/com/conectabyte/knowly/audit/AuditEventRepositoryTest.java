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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * specify/features/paginated-audit-trail/PLAN.md: {@code
 * findByActorUserIdOrderByOccurredAtDesc(Long, Pageable)} pushes offset/limit into the generated
 * SQL, backed by the existing {@code ix_audit_events_actor_time (actor_user_id, occurred_at)}
 * composite index (backward index scan, no new migration needed).
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

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by("occurredAt").descending());
    }

    @Test
    void returnsResultsOrderedByOccurredAtDescendingAcrossAMultiPageSeed() {
        User actor = user("audit-page-actor@example.com");
        User otherActor = user("audit-page-other-actor@example.com");
        Instant base = Instant.now().minus(1000, ChronoUnit.MINUTES);

        for (int i = 0; i < 25; i++) {
            insertEvent(actor.getId(), base.plus(i, ChronoUnit.MINUTES), "action-" + i);
        }
        insertEvent(otherActor.getId(), base.plus(1000, ChronoUnit.MINUTES), "other-actor-action");

        Page<AuditEvent> firstPage =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        actor.getId(), pageable(0, 10));
        Page<AuditEvent> secondPage =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        actor.getId(), pageable(1, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getContent().get(0).getAction()).isEqualTo("action-24");
        assertThat(firstPage.getContent().get(9).getAction()).isEqualTo("action-15");
        assertThat(secondPage.getContent()).hasSize(10);
        assertThat(secondPage.getContent().get(0).getAction()).isEqualTo("action-14");
        assertThat(firstPage.getContent())
                .allSatisfy(e -> assertThat(e.getActorUserId()).isEqualTo(actor.getId()));
    }

    @Test
    void aPageBeyondAvailableRowsReturnsEmptyContentWithCorrectTotals() {
        User actor = user("audit-page-beyond-actor@example.com");

        for (int i = 0; i < 5; i++) {
            insertEvent(actor.getId(), Instant.now().minus(i, ChronoUnit.MINUTES), "action-" + i);
        }

        Page<AuditEvent> beyond =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        actor.getId(), pageable(3, 10));

        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(5);
        assertThat(beyond.getTotalPages()).isEqualTo(1);
    }

    @Test
    void pageContentSizeNeverExceedsTheRequestedPageableSize() {
        User actor = user("audit-page-bound-actor@example.com");

        for (int i = 0; i < 30; i++) {
            insertEvent(actor.getId(), Instant.now().minus(i, ChronoUnit.MINUTES), "action-" + i);
        }

        Page<AuditEvent> page =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        actor.getId(), pageable(0, 5));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(30);
        assertThat(page.getTotalPages()).isEqualTo(6);
    }

    @Test
    void unpaginatedFinderStillReturnsAllRowsOrderedDescending() {
        User actor = user("audit-unpaginated-actor@example.com");
        insertEvent(actor.getId(), Instant.now().minus(2, ChronoUnit.MINUTES), "older");
        insertEvent(actor.getId(), Instant.now().minus(1, ChronoUnit.MINUTES), "newer");

        List<AuditEvent> result =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId());

        assertThat(result.get(0).getAction()).isEqualTo("newer");
        assertThat(result.get(1).getAction()).isEqualTo("older");
    }
}
