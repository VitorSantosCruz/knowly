package br.com.conectabyte.knowly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final MinIOContainer MINIO_CONTAINER =
            new MinIOContainer("minio/minio:RELEASE.2025-04-08T15-41-24Z");

    static {
        MINIO_CONTAINER.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("knowly.storage.endpoint", MINIO_CONTAINER::getS3URL);
        registry.add("knowly.storage.access-key", MINIO_CONTAINER::getUserName);
        registry.add("knowly.storage.secret-key", MINIO_CONTAINER::getPassword);
    }

    @Bean
    @ServiceConnection
    LgtmStackContainer grafanaLgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:0.28.0"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvectorContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg18"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:8.8"));

        container.withExposedPorts(6379);

        return container;
    }

    @Bean
    MinIOContainer minioContainer() {
        return MINIO_CONTAINER;
    }
}
