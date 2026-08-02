package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs Flyway migrations up to V17 against a fresh Postgres, seeds pre-migration users/
 * profile_edit_requests rows the same shape identity-profile-model shipped with, then migrates to
 * V18 and asserts the backfill/cancel/no-address-carry-forward behavior from
 * specify/features/identity-profile-model-v2/PLAN.md's "Data schema" section (TASKS.md 3-5). Uses a
 * dedicated container (not {@code TestcontainersConfiguration}'s shared one) because it needs to
 * control the Flyway target version, which the Spring-managed datasource does not allow.
 */
class V18MigrationBackfillTest {

    private static final PostgreSQLContainer CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg18"));

    private static Connection connection;

    @BeforeAll
    static void migrateToV17AndSeed() throws Exception {
        CONTAINER.start();

        Flyway.configure()
                .dataSource(
                        CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                .locations("classpath:db/migration")
                .placeholders(
                        java.util.Map.of("bootstrap-staff-email", "bootstrap-test@conectabyte.com"))
                .target("17")
                .load()
                .migrate();

        connection =
                DriverManager.getConnection(
                        CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        connection.setAutoCommit(true);

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO users (id, email, full_name, cpf, cpf_blind_index, rg,"
                            + " rg_blind_index, phone, address, created_by, updated_by) VALUES"
                            + " (9001, 'seeded-full@example.com', 'Seeded Full Name',"
                            + " 'cpf-ciphertext', 'cpf-blind-index', 'rg-ciphertext',"
                            + " 'rg-blind-index', '+5511999990000', '123 Free Text Ave',"
                            + " 'seed', 'seed')");
            statement.execute(
                    "INSERT INTO users (id, email, created_by, updated_by) VALUES"
                            + " (9002, 'seeded-empty@example.com', 'seed', 'seed')");
            statement.execute(
                    "INSERT INTO profile_edit_requests (id, requester_user_id, status,"
                            + " created_by, updated_by) VALUES (9101, 9002, 'PENDING', 'seed',"
                            + " 'seed')");
        }

        Flyway.configure()
                .dataSource(
                        CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                .locations("classpath:db/migration")
                .placeholders(
                        java.util.Map.of("bootstrap-staff-email", "bootstrap-test@conectabyte.com"))
                .load()
                .migrate();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        CONTAINER.stop();
    }

    @Test
    void backfillsFullNameAndTaxIdFromUsersIntoUserProfiles() throws Exception {
        // NOTE: this test's second migrate() call above runs the *full* migration chain (through
        // the latest migration, not just V18) -- V26 drops rg/rg_blind_index entirely and V27
        // renames cpf/cpf_blind_index to tax_id/tax_id_blind_index, so this asserts against the
        // post-V27 column names, not V18's original ones.
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT full_name, tax_id, tax_id_blind_index FROM"
                                + " user_profiles WHERE user_id = 9001")) {
            ResultSet rs = statement.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("full_name")).isEqualTo("Seeded Full Name");
            assertThat(rs.getString("tax_id")).isEqualTo("cpf-ciphertext");
            assertThat(rs.getString("tax_id_blind_index")).isEqualTo("cpf-blind-index");
        }
    }

    @Test
    void backfillsPhoneIntoOnePhoneContactRow() throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT type, value, is_primary FROM contacts WHERE user_id = 9001")) {
            ResultSet rs = statement.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("type")).isEqualTo("PHONE");
            assertThat(rs.getString("value")).isEqualTo("+5511999990000");
            assertThat(rs.getBoolean("is_primary")).isTrue();
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void everyUserGetsAnEagerUserProfileRowEvenWithNoPersonalDataAtAll() throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT count(*) FROM user_profiles WHERE user_id = 9002")) {
            ResultSet rs = statement.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void usersEmailIsNeverBackfilledIntoContacts() throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT count(*) FROM contacts WHERE value = 'seeded-full@example.com' OR"
                                + " value = 'seeded-empty@example.com'")) {
            ResultSet rs = statement.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }

    @Test
    void pendingProfileEditRequestIsCancelledWithResolvedAtSet() throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT status, resolved_at FROM profile_edit_requests WHERE id = 9101")) {
            ResultSet rs = statement.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("CANCELLED");
            assertThat(rs.getTimestamp("resolved_at")).isNotNull();
        }
    }

    @Test
    void usersAddressFreeTextIsNeverCarriedIntoAddresses() throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT count(*) FROM addresses WHERE user_id IN (9001, 9002)")) {
            ResultSet rs = statement.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }
}
