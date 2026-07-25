package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ArticleStorageServiceTest {

    @Autowired private ArticleStorageService articleStorageService;

    @Test
    void uploadedBytesAreRetrievableByKey() {
        byte[] content = "hello knowly".getBytes(StandardCharsets.UTF_8);

        articleStorageService.upload("test/round-trip.txt", content, "text/plain");
        byte[] downloaded = articleStorageService.download("test/round-trip.txt");

        assertThat(downloaded).isEqualTo(content);
    }

    @Test
    void presignedUrlServesTheContentWithoutAdditionalCredentials() throws Exception {
        byte[] content = "presigned content".getBytes(StandardCharsets.UTF_8);
        articleStorageService.upload("test/presigned.txt", content, "text/plain");

        URL url = articleStorageService.presignedUrl("test/presigned.txt");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        assertThat(connection.getResponseCode()).isEqualTo(200);
        assertThat(connection.getInputStream().readAllBytes()).isEqualTo(content);
    }
}
