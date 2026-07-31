package br.com.conectabyte.knowly.identity;

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
 * V19 migration coverage: the legacy flat identity columns V17 added to users/users_aud
 * (full_name/address/rg/cpf/phone/rg_blind_index/cpf_blind_index) are gone, now that
 * UserProfile/Address/Contact (V18) are the only code path reading/writing this data. See
 * specify/features/identity-profile-model-v2/TASKS.md item 27.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V19MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private static final List<String> DROPPED_COLUMNS =
            List.of(
                    "full_name",
                    "address",
                    "rg",
                    "cpf",
                    "phone",
                    "rg_blind_index",
                    "cpf_blind_index");

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

    @Test
    void usersTableNoLongerHasAnyLegacyIdentityColumn() {
        assertThat(DROPPED_COLUMNS).noneMatch(column -> columnExists("users", column));
    }

    @Test
    void usersAudTableNoLongerHasAnyLegacyIdentityColumn() {
        assertThat(DROPPED_COLUMNS).noneMatch(column -> columnExists("users_aud", column));
    }
}
