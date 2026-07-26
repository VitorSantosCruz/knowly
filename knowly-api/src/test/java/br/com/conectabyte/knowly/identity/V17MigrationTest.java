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
 * V17 migration coverage: the new users/tenants/notifications/profile_edit_requests columns
 * actually exist, per specify/features/identity-profile-model/PLAN.md's "Data schema" section.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V17MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

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
    void usersTableHasEveryNewIdentityColumn() {
        List<String> columns =
                List.of(
                        "full_name",
                        "address",
                        "rg",
                        "cpf",
                        "phone",
                        "rg_blind_index",
                        "cpf_blind_index");

        assertThat(columns).allMatch(column -> columnExists("users", column));
    }

    @Test
    void tenantsTableHasEveryNewCompanyRecordColumn() {
        List<String> columns =
                List.of("cnpj", "razao_social", "nome_fantasia", "inscricao_estadual");

        assertThat(columns).allMatch(column -> columnExists("tenants", column));
    }

    @Test
    void notificationsTableHasTheNewNullableFkAndTenantMembershipIdIsNowNullable() {
        assertThat(columnExists("notifications", "profile_edit_request_id")).isTrue();

        Boolean isNullable =
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable = 'YES' FROM information_schema.columns WHERE table_name ="
                                + " 'notifications' AND column_name = 'tenant_membership_id'",
                        Boolean.class);
        assertThat(isNullable).isTrue();
    }

    @Test
    void profileEditRequestsTableExistsWithItsExpectedColumns() {
        List<String> columns =
                List.of(
                        "requester_user_id",
                        "proposed_full_name",
                        "proposed_address",
                        "proposed_rg",
                        "proposed_cpf",
                        "proposed_phone",
                        "status",
                        "resolved_by_user_id",
                        "resolved_at");

        assertThat(columns).allMatch(column -> columnExists("profile_edit_requests", column));
    }
}
