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

    // usersTableHasEveryNewIdentityColumn was removed here: V19
    // (identity-profile-model-v2/TASKS.md 27) drops full_name/address/rg/cpf/phone/
    // rg_blind_index/cpf_blind_index from users once user_profiles/addresses/contacts (V18) took
    // over as the only code path for this data -- see V19MigrationTest for the current-state
    // coverage of that drop.

    // tenantsTableHasEveryNewCompanyRecordColumn was removed here: V23
    // (tenant-creation/TASKS.md 1) drops cnpj/razao_social/nome_fantasia/inscricao_estadual from
    // tenants/tenants_aud in favor of the full identification column set -- see V23MigrationTest
    // for the current-state coverage of that replacement.

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
        // proposed_address is deliberately excluded here -- V18 (identity-profile-model-v2) drops
        // it in favor of the structured proposed_cep/logradouro/... columns, see V18MigrationTest.
        // proposed_rg is excluded -- V26 drops it entirely (RG removed from the data model).
        // proposed_cpf is asserted here under its current name, proposed_tax_id -- V27 renames it
        // (country-agnostic identity/address model amendment), and this test runs against the full
        // migration chain, not just V17's original shape.
        List<String> columns =
                List.of(
                        "requester_user_id",
                        "proposed_full_name",
                        "proposed_tax_id",
                        "proposed_phone",
                        "status",
                        "resolved_by_user_id",
                        "resolved_at");

        assertThat(columns).allMatch(column -> columnExists("profile_edit_requests", column));
    }
}
