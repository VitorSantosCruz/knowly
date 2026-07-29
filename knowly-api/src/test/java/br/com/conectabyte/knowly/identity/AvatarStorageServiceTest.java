package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mirrors {@code ArticleStorageServiceTest}'s shape against the new {@code avatarBucket}, per
 * specify/features/identity-profile-model-v2/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AvatarStorageServiceTest {

    @Autowired private AvatarStorageService avatarStorageService;

    @Test
    void uploadThenPresignedUrlRoundTrips() {
        String key = "users/1/avatar-" + System.nanoTime();
        byte[] content = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);

        avatarStorageService.upload(key, content, "image/png");

        assertThat(avatarStorageService.presignedUrl(key)).isNotNull();
        assertThat(avatarStorageService.presignedUrl(key).toString()).contains(key);
    }
}
