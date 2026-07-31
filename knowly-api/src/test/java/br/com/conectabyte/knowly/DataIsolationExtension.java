package br.com.conectabyte.knowly;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Resets shared, Testcontainers-backed state (Postgres rows, Redis keys) before every test method,
 * regardless of whether Spring's test context cache reuses an already-started application context
 * (and its containers) across test classes.
 *
 * <p>None of the ~20 {@code @SpringBootTest} integration test classes in this module clean up after
 * themselves (no {@code @Sql}, no repository {@code deleteAll()}, no {@code @DirtiesContext}), so
 * when the context cache reuses a context between classes with matching bootstrap signatures, data
 * (and Redis-backed login-throttle/lockout state) leaks between classes. Previously this was masked
 * by forcing {@code reuseForks=false} in Surefire, which forced a fresh JVM (and thus a fresh,
 * JVM-local context cache and fresh containers) per test class — at the cost of the test suite's
 * runtime. This extension gives the same isolation without that cost, by resetting state directly
 * instead of relying on process-level isolation.
 *
 * <p>Registered via JUnit 5's extension auto-detection (see {@code
 * src/test/resources/junit-platform.properties}), so every {@code @SpringBootTest} class in this
 * module gets the reset automatically without needing to declare {@code @ExtendWith} individually.
 */
public class DataIsolationExtension implements BeforeEachCallback {

    /**
     * Seeded once per fresh database by {@code V13__create_bootstrap_staff_user.sql} (see {@code
     * src/test/resources/application-test.yaml}'s {@code spring.flyway.placeholders
     * .bootstrap-staff-email}). Truncating the {@code users} table would silently delete it, so
     * it's re-seeded after every reset — matching the row's final post-migration state, i.e. after
     * {@code V14__create_global_permission_tables.sql}'s {@code UPDATE users SET global_role =
     * 'STAFF_ADMIN' WHERE global_role = 'STAFF'}, not V13's original insert value.
     */
    private static final String BOOTSTRAP_STAFF_EMAIL = "bootstrap-test@conectabyte.com";

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext;

        try {
            applicationContext = SpringExtension.getApplicationContext(context);
        } catch (IllegalStateException noSpringContextForThisTest) {
            // Plain unit tests (no @SpringBootTest) have no Spring context to reset.
            return;
        }

        resetPostgres(applicationContext);
        resetRedis(applicationContext);
    }

    private void resetPostgres(ApplicationContext applicationContext) {
        DataSource dataSource;

        try {
            dataSource = applicationContext.getBean(DataSource.class);
        } catch (Exception noDataSourceForThisContext) {
            return;
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        var tableNames =
                jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = 'public'"
                                + " AND table_name NOT IN ('flyway_schema_history', 'revinfo')",
                        String.class);

        if (tableNames.isEmpty()) {
            return;
        }

        jdbcTemplate.execute(
                "TRUNCATE TABLE " + String.join(", ", tableNames) + " RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                "INSERT INTO users (email, global_role, created_by, updated_by)"
                        + " VALUES (?, 'STAFF_ADMIN', 'system', 'system')",
                BOOTSTRAP_STAFF_EMAIL);
    }

    private void resetRedis(ApplicationContext applicationContext) {
        RedisConnectionFactory redisConnectionFactory;

        try {
            redisConnectionFactory = applicationContext.getBean(RedisConnectionFactory.class);
        } catch (Exception noRedisForThisContext) {
            return;
        }

        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }
}
