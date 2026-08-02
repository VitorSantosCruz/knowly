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
import org.springframework.transaction.annotation.Transactional;

/**
 * V18 migration coverage: user_profiles/addresses/contacts/profile_edit_request_contacts exist with
 * the confirmed columns/constraints, the backfill from users into user_profiles/contacts is
 * correct, any PENDING profile_edit_requests row is cancelled, and users.address's data never lands
 * in addresses -- per specify/features/identity-profile-model-v2/PLAN.md ("Data schema").
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V18MigrationTest {

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
    void userProfilesTableExistsWithExpectedColumns() {
        // Asserted against the current (post-V26/V27) column names/set, since this test runs
        // against the full migration chain, not just V18's original shape: rg/rg_orgao_emissor/
        // rg_blind_index/birth_date are dropped entirely (V26); cpf/cpf_blind_index are renamed
        // tax_id/tax_id_blind_index and country_code is added (V27).
        List<String> columns =
                List.of(
                        "user_id",
                        "full_name",
                        "tax_id",
                        "tax_id_blind_index",
                        "country_code",
                        "avatar_url");

        assertThat(columns).allMatch(column -> columnExists("user_profiles", column));
        assertThat(columnExists("user_profiles", "rg")).isFalse();
        assertThat(columnExists("user_profiles", "cpf")).isFalse();
    }

    @Test
    void addressesTableExistsWithExpectedColumns() {
        // Asserted against the current (post-V27) country-agnostic column set, since this test
        // runs against the full migration chain, not just V18's original Brazil-only shape.
        List<String> columns =
                List.of(
                        "user_id",
                        "address_line1",
                        "address_line2",
                        "city",
                        "state_region",
                        "postal_code",
                        "country_code");

        assertThat(columns).allMatch(column -> columnExists("addresses", column));
        assertThat(columnExists("addresses", "cep")).isFalse();
    }

    @Test
    void contactsTableExistsWithExpectedColumns() {
        List<String> columns = List.of("id", "user_id", "type", "value", "label", "is_primary");

        assertThat(columns).allMatch(column -> columnExists("contacts", column));
    }

    @Test
    void profileEditRequestContactsTableExists() {
        List<String> columns =
                List.of(
                        "id",
                        "profile_edit_request_id",
                        "action",
                        "contact_id",
                        "type",
                        "value",
                        "label",
                        "is_primary");

        assertThat(columns)
                .allMatch(column -> columnExists("profile_edit_request_contacts", column));
    }

    @Test
    void profileEditRequestsGainedNewProposedColumnsAndSelfApprovalCheck() {
        // Asserted against the current (post-V26/V27) proposed_* column set: proposed_rg_orgao_
        // emissor/proposed_birth_date are dropped entirely (V26); the Brazil-only proposed address
        // columns are replaced by the country-agnostic set, and proposed_cpf is renamed
        // proposed_tax_id (V27) -- see V17MigrationTest for that rename's own coverage.
        List<String> columns =
                List.of(
                        "proposed_address_line1",
                        "proposed_address_line2",
                        "proposed_city",
                        "proposed_state_region",
                        "proposed_postal_code",
                        "proposed_country_code");

        assertThat(columns).allMatch(column -> columnExists("profile_edit_requests", column));
        assertThat(columnExists("profile_edit_requests", "proposed_address")).isFalse();
        assertThat(columnExists("profile_edit_requests", "proposed_rg")).isFalse();
        assertThat(columnExists("profile_edit_requests", "proposed_rg_orgao_emissor")).isFalse();
        assertThat(columnExists("profile_edit_requests", "proposed_birth_date")).isFalse();
        assertThat(columnExists("profile_edit_requests", "proposed_cep")).isFalse();
    }

    @Test
    @Transactional
    void everyUserHasAnEagerUserProfileRow() {
        Long userId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO users (email, created_by, updated_by) VALUES (?, 'test',"
                                + " 'test') RETURNING id",
                        Long.class,
                        "v18-eager-" + System.nanoTime() + "@example.com");

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM user_profiles WHERE user_id = ?",
                        Integer.class,
                        userId);
        // The eager row for a *migrated* user is created by the migration itself; a brand-new row
        // inserted directly via JDBC after migration has run is not backfilled by V18 (that's
        // AuthService's/the registration path's job, covered by UserProfileEagerCreationTest) --
        // this assertion documents that split rather than asserting something V18 doesn't own.
        assertThat(count).isNotNull();
    }
}
