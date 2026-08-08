package br.com.conectabyte.knowly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration imported by every integration test class.
 *
 * <p>All containers are held in {@code static} fields and started exactly once per JVM (a
 * singleton-container pattern), regardless of how many distinct Spring {@code ApplicationContext}
 * instances get created across the 100+ test classes that import this configuration (e.g. due to
 * differing {@code @MockBean}/{@code @ActiveProfiles}/{@code @TestPropertySource} combinations
 * causing Spring's test context cache to miss). Previously the container beans were created fresh
 * per {@code @Bean} method invocation, meaning every context-cache miss also started a brand new
 * Postgres/RabbitMQ/Redis/LGTM stack — the actual cause of the slow local test suite, not
 * Testcontainers itself.
 *
 * <p>Locally, developers can additionally opt into cross-JVM-run reuse (i.e. containers surviving
 * between separate {@code mvn test} invocations) by adding {@code testcontainers.reuse.enable=true}
 * to {@code ~/.testcontainers.properties} (a file outside this repo, see the backend README for
 * details) and setting the {@code TESTCONTAINERS_REUSE_ENABLE} env var (or {@code
 * -Dtestcontainers.reuse.enable=true}). CI runners are ephemeral and never benefit from cross-run
 * reuse, so that flag is intentionally left off by default here and only enabled when the local
 * opt-in is present.
 *
 * <p><b>Important:</b> Spring Boot's Testcontainers integration ({@code
 * TestcontainersLifecycleApplicationContextInitializer}) auto-stops any {@code Startable} container
 * bean when its owning {@code ApplicationContext} closes -- correct for the usual "one container
 * per context" pattern, but wrong here: it would tear down these *shared* singletons the moment any
 * one context using them closes (which happens routinely, e.g. on Spring's test-context-cache LRU
 * eviction), forcing a full container re-create on next use and defeating the whole point of this
 * class. Each container below therefore overrides {@code stop()}/{@code close()} as a no-op --
 * Testcontainers' own Ryuk reaper (or JVM exit) is what actually cleans these up, matching
 * Testcontainers' documented "singleton containers" pattern.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final boolean REUSE_ENABLED = isReuseEnabled();

    private static final MinIOContainer MINIO_CONTAINER =
            new MinIOContainer("minio/minio:RELEASE.2025-04-08T15-41-24Z") {
                @Override
                public void stop() {
                    // no-op: shared singleton container, see class Javadoc.
                }
            }.withReuse(REUSE_ENABLED);

    private static final LgtmStackContainer LGTM_CONTAINER =
            new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:0.28.0")) {
                @Override
                public void stop() {
                    // no-op: shared singleton container, see class Javadoc.
                }
            }.withReuse(REUSE_ENABLED);

    private static final PostgreSQLContainer PGVECTOR_CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg18")) {
                @Override
                public void stop() {
                    // no-op: shared singleton container, see class Javadoc.
                }
            }.withReuse(REUSE_ENABLED);

    private static final RabbitMQContainer RABBIT_CONTAINER =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3")) {
                @Override
                public void stop() {
                    // no-op: shared singleton container, see class Javadoc.
                }
            }.withReuse(REUSE_ENABLED);

    private static final GenericContainer<?> REDIS_CONTAINER =
            new NonStoppingGenericContainer(DockerImageName.parse("redis:8.8"))
                    .withExposedPorts(6379)
                    .withReuse(REUSE_ENABLED);

    static {
        MINIO_CONTAINER.start();
        LGTM_CONTAINER.start();
        PGVECTOR_CONTAINER.start();
        RABBIT_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    private static boolean isReuseEnabled() {
        return Boolean.getBoolean("testcontainers.reuse.enable")
                || "true".equalsIgnoreCase(System.getenv("TESTCONTAINERS_REUSE_ENABLE"));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties() {
        return registry -> {
            registry.add("knowly.storage.endpoint", MINIO_CONTAINER::getS3URL);
            registry.add("knowly.storage.access-key", MINIO_CONTAINER::getUserName);
            registry.add("knowly.storage.secret-key", MINIO_CONTAINER::getPassword);
        };
    }

    @Bean
    @ServiceConnection
    LgtmStackContainer grafanaLgtmContainer() {
        return LGTM_CONTAINER;
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvectorContainer() {
        return PGVECTOR_CONTAINER;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return RABBIT_CONTAINER;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS_CONTAINER;
    }

    @Bean
    MinIOContainer minioContainer() {
        return MINIO_CONTAINER;
    }

    /**
     * {@link GenericContainer} is generically self-bounded ({@code GenericContainer<SELF extends
     * GenericContainer<SELF>>}), which an anonymous subclass with a diamond type argument cannot
     * satisfy (the anonymous class's own type isn't expressible as a type argument) -- hence this
     * named subclass instead of an anonymous one, purely to override {@code stop()} as a no-op (see
     * class Javadoc).
     */
    private static final class NonStoppingGenericContainer
            extends GenericContainer<NonStoppingGenericContainer> {
        NonStoppingGenericContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        @Override
        public void stop() {
            // no-op: shared singleton container, see class Javadoc.
        }
    }
}
