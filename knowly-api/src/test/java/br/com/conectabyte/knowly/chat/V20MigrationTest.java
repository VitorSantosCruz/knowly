package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** V20 migration coverage: internal-team-chat's tables/indexes/_aud counterparts exist. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V20MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private boolean tableExists(String table) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                        Integer.class,
                        table);
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
    void everyChatTableExists() {
        assertThat(
                        List.of(
                                "chat_conversations",
                                "chat_participants",
                                "chat_messages",
                                "support_tickets"))
                .allMatch(this::tableExists);
    }

    @Test
    void everyChatAudTableExists() {
        assertThat(
                        List.of(
                                "chat_conversations_aud",
                                "chat_participants_aud",
                                "support_tickets_aud"))
                .allMatch(this::tableExists);
    }

    @Test
    void chatMessagesHasNoAudTable() {
        assertThat(tableExists("chat_messages_aud")).isFalse();
    }

    @Test
    void thePartialUniqueIndexesExist() {
        assertThat(indexExists("ux_chat_conversations_support_channel")).isTrue();
        assertThat(indexExists("ux_support_tickets_one_open_per_channel")).isTrue();
    }
}
