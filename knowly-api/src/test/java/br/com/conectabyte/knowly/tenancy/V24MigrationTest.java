package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * permission-granularity-model REQ-9/REQ-10/REQ-11, PLAN.md's "Migration regression" testing
 * strategy: runs Flyway only through V22 against a dedicated (not the shared suite-wide)
 * Testcontainers Postgres instance, seeds bundled-permission rows via raw JDBC (bypassing the Java
 * enum entirely, since the bundled values no longer exist there), then applies V24 and asserts --
 * again via raw JDBC -- that every holder now has exactly the expected granular set and every
 * bundled row is gone. This is the only test class that talks to the database via raw JDBC instead
 * of Spring Data, precisely because the whole point is exercising a state transition through values
 * the enum no longer models.
 */
class V24MigrationTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg18"));

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long insertUser(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO users (email, global_role, created_by, updated_by) VALUES (?,"
                                + " 'STAFF', 'test', 'test') RETURNING id")) {
            statement.setString(1, email);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long insertGlobalAccessGroup(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO global_access_groups (name, created_by, updated_by) VALUES (?,"
                                + " 'test', 'test') RETURNING id")) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void grantDirect(Connection connection, long userId, String permission)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO direct_global_permission_grants (user_id, permission,"
                                + " created_by, updated_by) VALUES (?, ?, 'test', 'test')")) {
            statement.setLong(1, userId);
            statement.setString(2, permission);
            statement.executeUpdate();
        }
    }

    private void grantToAccessGroup(Connection connection, long groupId, String permission)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO global_access_group_permissions (global_access_group_id,"
                                + " permission, created_by, updated_by) VALUES (?, ?, 'test',"
                                + " 'test')")) {
            statement.setLong(1, groupId);
            statement.setString(2, permission);
            statement.executeUpdate();
        }
    }

    private Set<String> directPermissionsOf(Connection connection, long userId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT permission FROM direct_global_permission_grants WHERE user_id = ?")) {
            statement.setLong(1, userId);
            ResultSet resultSet = statement.executeQuery();
            return toSet(resultSet);
        }
    }

    private Set<String> accessGroupPermissionsOf(Connection connection, long groupId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT permission FROM global_access_group_permissions WHERE"
                                + " global_access_group_id = ?")) {
            statement.setLong(1, groupId);
            ResultSet resultSet = statement.executeQuery();
            return toSet(resultSet);
        }
    }

    private Set<String> toSet(ResultSet resultSet) throws SQLException {
        List<String> values = new java.util.ArrayList<>();
        while (resultSet.next()) {
            values.add(resultSet.getString(1));
        }
        return values.stream().collect(Collectors.toSet());
    }

    @Test
    void v24ExpandsEveryBundledPermissionToItsGranularReplacementsAndPreservesAnAlreadyGrantedOne()
            throws SQLException {
        Flyway toV22 =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .placeholders(
                                java.util.Map.of(
                                        "bootstrap-staff-email", "bootstrap-v24-test@example.com"))
                        .target("22")
                        .load();
        toV22.migrate();

        try (Connection connection = connect()) {
            long memberHolder = insertUser(connection, "member-holder@example.com");
            long accessGroupHolder = insertUser(connection, "already-granted@example.com");
            long permissionsHolder = insertUser(connection, "permissions-holder@example.com");
            long group = insertGlobalAccessGroup(connection, "Bundled Group");

            grantDirect(connection, memberHolder, "TENANT_MEMBER_MANAGE_ANY");
            // Already holds one of the granular replacements directly -- exercises ON CONFLICT DO
            // NOTHING.
            grantDirect(connection, accessGroupHolder, "TENANT_ACCESS_GROUP_MANAGE_ANY");
            grantDirect(connection, accessGroupHolder, "TENANT_ACCESS_GROUP_VIEW");
            grantDirect(connection, permissionsHolder, "TENANT_PERMISSION_GRANT_MANAGE_ANY");
            grantToAccessGroup(connection, group, "TENANT_MEMBER_MANAGE_ANY");
            grantToAccessGroup(connection, group, "TENANT_ACCESS_GROUP_MANAGE_ANY");
            grantToAccessGroup(connection, group, "TENANT_PERMISSION_GRANT_MANAGE_ANY");

            Flyway toLatest =
                    Flyway.configure()
                            .dataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword())
                            .placeholders(
                                    java.util.Map.of(
                                            "bootstrap-staff-email",
                                            "bootstrap-v24-test@example.com"))
                            .load();
            toLatest.migrate();

            assertThat(directPermissionsOf(connection, memberHolder))
                    .containsExactlyInAnyOrder(
                            "TENANT_MEMBER_VIEW",
                            "TENANT_MEMBER_CREATE",
                            "TENANT_MEMBER_EDIT",
                            "TENANT_MEMBER_DELETE");

            assertThat(directPermissionsOf(connection, accessGroupHolder))
                    .containsExactlyInAnyOrder(
                            "TENANT_ACCESS_GROUP_VIEW",
                            "TENANT_ACCESS_GROUP_CREATE",
                            "TENANT_ACCESS_GROUP_EDIT",
                            "TENANT_ACCESS_GROUP_DELETE");

            assertThat(directPermissionsOf(connection, permissionsHolder))
                    .containsExactlyInAnyOrder(
                            "TENANT_PERMISSION_GRANT_VIEW",
                            "TENANT_PERMISSION_GRANT_CREATE",
                            "TENANT_PERMISSION_GRANT_DELETE");

            assertThat(accessGroupPermissionsOf(connection, group))
                    .containsExactlyInAnyOrder(
                            "TENANT_MEMBER_VIEW",
                            "TENANT_MEMBER_CREATE",
                            "TENANT_MEMBER_EDIT",
                            "TENANT_MEMBER_DELETE",
                            "TENANT_ACCESS_GROUP_VIEW",
                            "TENANT_ACCESS_GROUP_CREATE",
                            "TENANT_ACCESS_GROUP_EDIT",
                            "TENANT_ACCESS_GROUP_DELETE",
                            "TENANT_PERMISSION_GRANT_VIEW",
                            "TENANT_PERMISSION_GRANT_CREATE",
                            "TENANT_PERMISSION_GRANT_DELETE");
        }
    }
}
