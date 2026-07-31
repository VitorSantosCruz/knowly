package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * specify/features/global-staff-dashboard-trends/SPEC.md REQ-2b/4/11: cross-tenant, day-bucketed
 * and windowed citation counts for the global trends endpoint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MessageArticleCitationRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageArticleCitationRepository messageArticleCitationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private void backdateCitation(MessageArticleCitation citation, Instant createdAt) {
        jdbcTemplate.update(
                "update message_article_citations set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                citation.getId());
    }

    private MessageArticleCitation citationFor(Tenant tenant) {
        Article article =
                articleRepository.saveAndFlush(
                        new Article(
                                tenant,
                                "Trends Article " + tenant.getId(),
                                "key-" + tenant.getId(),
                                "file.pdf",
                                "application/pdf"));
        User owner =
                userRepository.saveAndFlush(
                        new User("citation-owner-" + System.nanoTime() + "@example.com"));
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        Message message =
                messageRepository.saveAndFlush(
                        new Message(conversation, MessageRole.ASSISTANT, "hi"));

        return messageArticleCitationRepository.saveAndFlush(
                new MessageArticleCitation(message, article));
    }

    @Test
    void countCitationsByDaySinceReturnsRowsSortedChronologicallyAcrossAllTenants() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Citation Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Citation Tenant B"));
        Instant cutoff = Instant.now().minus(3, ChronoUnit.DAYS);

        MessageArticleCitation dayOne = citationFor(tenantA);
        backdateCitation(dayOne, cutoff.plus(1, ChronoUnit.HOURS));
        MessageArticleCitation dayTwo = citationFor(tenantB);
        backdateCitation(dayTwo, cutoff.plus(1, ChronoUnit.DAYS));
        MessageArticleCitation beforeCutoff = citationFor(tenantA);
        backdateCitation(beforeCutoff, cutoff.minus(1, ChronoUnit.DAYS));

        List<DailyCountProjection> rows =
                messageArticleCitationRepository.countCitationsByDaySince(cutoff);

        assertThat(rows)
                .extracting(DailyCountProjection::getDay)
                .contains(
                        LocalDate.ofInstant(cutoff.plus(1, ChronoUnit.HOURS), ZoneOffset.UTC),
                        LocalDate.ofInstant(cutoff.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC));
        assertThat(rows).isSortedAccordingTo(java.util.Comparator.comparing(r -> r.getDay()));
    }

    @Test
    void countCitationsByDayReturnsAllRowsWithNoLowerBound() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Citation All Time Tenant"));
        MessageArticleCitation citation = citationFor(tenant);

        List<DailyCountProjection> rows = messageArticleCitationRepository.countCitationsByDay();

        assertThat(rows)
                .extracting(DailyCountProjection::getDay)
                .contains(LocalDate.ofInstant(citation.getCreatedAt(), ZoneOffset.UTC));
    }

    @Test
    void countByCreatedAtWindowRespectsHalfOpenBounds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Citation Window Tenant"));
        Instant windowStart = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant windowEnd = Instant.now().minus(5, ChronoUnit.DAYS);

        MessageArticleCitation insideWindow = citationFor(tenant);
        backdateCitation(insideWindow, windowStart.plus(1, ChronoUnit.HOURS));

        MessageArticleCitation atLowerBound = citationFor(tenant);
        backdateCitation(atLowerBound, windowStart);

        MessageArticleCitation atUpperBound = citationFor(tenant);
        backdateCitation(atUpperBound, windowEnd);

        MessageArticleCitation outsideWindow = citationFor(tenant);
        backdateCitation(outsideWindow, windowEnd.plus(1, ChronoUnit.HOURS));

        long count =
                messageArticleCitationRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                windowStart, windowEnd);

        assertThat(count).isEqualTo(2);
    }
}
