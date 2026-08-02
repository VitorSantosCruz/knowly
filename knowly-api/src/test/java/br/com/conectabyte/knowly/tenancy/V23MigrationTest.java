package br.com.conectabyte.knowly.tenancy;

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
 * V23 migration coverage: the legacy cnpj/razaoSocial/nomeFantasia/inscricaoEstadual columns are
 * gone from both {@code tenants} and {@code tenants_aud}, and the new full-identification column
 * set exists and is {@code NOT NULL}, per specify/features/tenant-creation/PLAN.md's "Data schema"
 * section. By the time this test runs, Flyway has already applied V23 against a fresh test
 * database, so this only asserts the post-migration schema shape (mirrors V17MigrationTest's own
 * after-the-fact assertion style, since re-running a migration against a seeded pre-migration row
 * isn't practical with a Flyway-managed Testcontainers database shared across the whole suite).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V23MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;

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

    private boolean isNotNull(String table, String column) {
        Boolean notNullable =
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable = 'NO' FROM information_schema.columns WHERE table_name"
                                + " = ? AND column_name = ?",
                        Boolean.class,
                        table,
                        column);
        return Boolean.TRUE.equals(notNullable);
    }

    @Test
    void legacyCompanyRecordColumnsAreDroppedFromBothTenantsAndTenantsAud() {
        List<String> legacyColumns =
                List.of("cnpj", "razao_social", "nome_fantasia", "inscricao_estadual");

        assertThat(legacyColumns).noneMatch(column -> columnExists("tenants", column));
        assertThat(legacyColumns).noneMatch(column -> columnExists("tenants_aud", column));
    }

    @Test
    void tenantsTableHasEveryNewIdentificationColumn() {
        List<String> mandatoryColumns =
                List.of(
                        "legal_name",
                        "tax_id",
                        "country",
                        "contact_email",
                        "contact_phone",
                        "postal_code",
                        "street",
                        "number",
                        "neighborhood",
                        "city",
                        "state");

        assertThat(mandatoryColumns).allMatch(column -> columnExists("tenants", column));
        assertThat(mandatoryColumns).allMatch(column -> isNotNull("tenants", column));
        assertThat(columnExists("tenants", "complement")).isTrue();
        assertThat(isNotNull("tenants", "complement")).isFalse();
    }

    @Test
    void tenantsAudTableHasEveryNewIdentificationColumn() {
        List<String> newColumns =
                List.of(
                        "legal_name",
                        "tax_id",
                        "country",
                        "contact_email",
                        "contact_phone",
                        "postal_code",
                        "street",
                        "number",
                        "complement",
                        "neighborhood",
                        "city",
                        "state");

        assertThat(newColumns).allMatch(column -> columnExists("tenants_aud", column));
    }

    @Test
    void taxIdHasAUniqueIndex() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 'tenants' AND indexname ="
                                + " 'ux_tenants_tax_id'",
                        Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void tenantRepositoryIsUsableAfterMigration() {
        assertThat(tenantRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
