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

/**
 * V31 migration coverage: chat-group-membership-management's is_admin/visibility/archived_at/
 * deleted_at columns and the new chat_join_requests(_aud) tables/indexes exist.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V31MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private boolean tableExists(String table) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                        Integer.class,
                        table);
        return count != null && count > 0;
    }

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
    void newColumnsExist() {
        assertThat(columnExists("chat_participants", "is_admin")).isTrue();
        assertThat(columnExists("chat_participants_aud", "is_admin")).isTrue();
        assertThat(columnExists("chat_conversations", "visibility")).isTrue();
        assertThat(columnExists("chat_conversations", "archived_at")).isTrue();
        assertThat(columnExists("chat_conversations_aud", "visibility")).isTrue();
        assertThat(columnExists("chat_conversations_aud", "archived_at")).isTrue();
        assertThat(columnExists("chat_conversations", "deleted_at")).isTrue();
        assertThat(columnExists("chat_conversations_aud", "deleted_at")).isTrue();
        assertThat(columnExists("chat_participants", "deleted_at")).isTrue();
        assertThat(columnExists("chat_participants_aud", "deleted_at")).isTrue();
        assertThat(columnExists("chat_messages", "deleted_at")).isTrue();
    }

    @Test
    void joinRequestTablesExist() {
        assertThat(List.of("chat_join_requests", "chat_join_requests_aud"))
                .allMatch(this::tableExists);
    }

    @Test
    void indexesExist() {
        assertThat(indexExists("ux_chat_join_requests_pending")).isTrue();
        assertThat(indexExists("ix_chat_join_requests_conversation")).isTrue();
        assertThat(indexExists("ix_chat_conversations_discovery")).isTrue();
    }
}
