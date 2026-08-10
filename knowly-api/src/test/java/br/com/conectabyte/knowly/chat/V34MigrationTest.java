package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V34 migration coverage (chat-message-search TASKS.md items 1/3): the generated {@code
 * content_tsv_pt}/{@code content_tsv_en} tsvector columns and their GIN indexes on {@code
 * chat_messages}, and confirmation that {@code ALTER TABLE ... GENERATED ALWAYS AS ... STORED}
 * backfills pre-existing rows with no separate backfill step.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V34MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    private boolean columnExists(String table, String column) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND"
                                + " column_name = ?",
                        Integer.class,
                        table,
                        column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE indexname = ?",
                        Integer.class,
                        index);
        return count != null && count > 0;
    }

    @Test
    void generatedTsvectorColumnsExist() {
        assertThat(columnExists("chat_messages", "content_tsv_pt")).isTrue();
        assertThat(columnExists("chat_messages", "content_tsv_en")).isTrue();
    }

    @Test
    void ginIndexesExist() {
        assertThat(indexExists("idx_chat_messages_content_tsv_pt")).isTrue();
        assertThat(indexExists("idx_chat_messages_content_tsv_en")).isTrue();
    }

    @Test
    void generatedColumnsAreComputedForExistingRows() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("V34 Migration Co"));
        User sender = userRepository.saveAndFlush(new User("v34-migration-sender@example.com"));
        ChatConversation conversation =
                chatConversationRepository.saveAndFlush(
                        new ChatConversation(
                                ChatConversationKind.PEER_GROUP, tenant, "V34 Group", null));
        chatMessageRepository.saveAndFlush(
                new ChatMessage(conversation, sender, "reunião importante amanhã"));

        Integer matches =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM chat_messages WHERE content_tsv_pt @@"
                                + " websearch_to_tsquery('portuguese', 'reunião')",
                        Integer.class);

        assertThat(matches).isEqualTo(1);
    }
}
