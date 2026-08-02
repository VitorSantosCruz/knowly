package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V25 migration coverage (specify/features/tenant-crud/PLAN.md "Data schema"): {@code deleted_at}
 * exists nullable on both {@code tenants} and {@code tenants_aud}, the old unconditional {@code
 * ux_tenants_tax_id} index is gone, and the new partial index (only over rows where {@code
 * deleted_at IS NULL}) actually permits two rows sharing the same {@code tax_id} where one is
 * soft-deleted and the other isn't (REQ-12). Mirrors V23MigrationTest's after-the-fact assertion
 * style.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class V25MigrationTest {

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

    private boolean isNullable(String table, String column) {
        Boolean nullable =
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable = 'YES' FROM information_schema.columns WHERE table_name"
                                + " = ? AND column_name = ?",
                        Boolean.class,
                        table,
                        column);
        return Boolean.TRUE.equals(nullable);
    }

    @Test
    void deletedAtColumnExistsNullableOnTenantsAndTenantsAud() {
        assertThat(columnExists("tenants", "deleted_at")).isTrue();
        assertThat(isNullable("tenants", "deleted_at")).isTrue();
        assertThat(columnExists("tenants_aud", "deleted_at")).isTrue();
    }

    @Test
    void taxIdIndexIsPartialNotUnconditional() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 'tenants' AND indexname ="
                                + " 'ux_tenants_tax_id'",
                        Integer.class);
        assertThat(count).isEqualTo(1);

        String indexDef =
                jdbcTemplate.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = 'tenants' AND indexname ="
                                + " 'ux_tenants_tax_id'",
                        String.class);
        assertThat(indexDef).containsIgnoringCase("WHERE").contains("deleted_at IS NULL");
    }

    @Test
    void aSoftDeletedTenantsTaxIdCanBeReusedByAnActiveTenant() {
        String taxId = "V25-REUSE-" + System.nanoTime();

        jdbcTemplate.update(
                "INSERT INTO tenants (name, legal_name, tax_id, country, contact_email,"
                        + " contact_phone, postal_code, street, number, neighborhood, city, state,"
                        + " created_at, created_by, updated_at, updated_by, deleted_at)"
                        + " VALUES (?, ?, ?, 'BR', 'a@example.com', '0000000000', '00000000',"
                        + " 'unset', 'unset', 'unset', 'unset', 'unset', now(), 'test', now(),"
                        + " 'test', now())",
                "Deleted Owner",
                "Deleted Owner Ltda",
                taxId);

        assertThatCode(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO tenants (name, legal_name, tax_id, country,"
                                                + " contact_email, contact_phone, postal_code,"
                                                + " street, number, neighborhood, city, state,"
                                                + " created_at, created_by, updated_at, updated_by,"
                                                + " deleted_at) VALUES (?, ?, ?, 'BR',"
                                                + " 'b@example.com', '0000000000', '00000000',"
                                                + " 'unset', 'unset', 'unset', 'unset', 'unset',"
                                                + " now(), 'test', now(), 'test', NULL)",
                                        "Active Owner",
                                        "Active Owner Ltda",
                                        taxId))
                .doesNotThrowAnyException();
    }

    @Test
    void tenantRepositoryIsUsableAfterMigration() {
        assertThat(tenantRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
