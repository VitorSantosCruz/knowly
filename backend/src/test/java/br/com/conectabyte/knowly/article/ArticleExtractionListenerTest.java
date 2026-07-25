package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ArticleExtractionListenerTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private ArticleStorageService articleStorageService;
    @Autowired private ArticleExtractionPublisher articleExtractionPublisher;
    @MockitoBean private TranscriptionModel transcriptionModel;

    private Article createProcessingArticle(String key, String fileName, String contentType) {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Extraction Tenant"));
        Article article = new Article(tenant, "Test article", key, fileName, contentType);
        return articleRepository.saveAndFlush(article);
    }

    private static byte[] samplePdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void aRealPdfEventuallyReachesReadyWithExtractedText() throws Exception {
        byte[] pdfBytes = samplePdfBytes("Hello knowly extraction");
        Article article =
                createProcessingArticle("test/sample.pdf", "sample.pdf", "application/pdf");
        articleStorageService.upload(article.getOriginalFileKey(), pdfBytes, "application/pdf");

        articleExtractionPublisher.publish(article.getId());

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            Article reloaded =
                                    articleRepository.findById(article.getId()).orElseThrow();
                            assertThat(reloaded.getStatus()).isEqualTo(ArticleStatus.READY);
                            assertThat(reloaded.getText()).contains("Hello knowly extraction");
                        });
    }

    private static byte[] sampleOcrImageBytes(String text) throws Exception {
        BufferedImage image = new BufferedImage(600, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        graphics.drawString(text, 20, 80);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    void aRealImageEventuallyReachesReadyWithOcrdText() throws Exception {
        byte[] imageBytes = sampleOcrImageBytes("Hello knowly OCR");
        Article article = createProcessingArticle("test/sample.png", "sample.png", "image/png");
        articleStorageService.upload(article.getOriginalFileKey(), imageBytes, "image/png");

        articleExtractionPublisher.publish(article.getId());

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            Article reloaded =
                                    articleRepository.findById(article.getId()).orElseThrow();
                            assertThat(reloaded.getStatus()).isEqualTo(ArticleStatus.READY);
                            assertThat(reloaded.getText()).containsIgnoringCase("knowly");
                        });
    }

    @Test
    void anAudioFileEventuallyReachesReadyWithTheMockedTranscription() {
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(
                        new AudioTranscriptionResponse(
                                new AudioTranscription("mocked transcript")));

        Article article = createProcessingArticle("test/sample.mp3", "sample.mp3", "audio/mpeg");
        articleStorageService.upload(
                article.getOriginalFileKey(), "fake audio bytes".getBytes(), "audio/mpeg");

        articleExtractionPublisher.publish(article.getId());

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            Article reloaded =
                                    articleRepository.findById(article.getId()).orElseThrow();
                            assertThat(reloaded.getStatus()).isEqualTo(ArticleStatus.READY);
                            assertThat(reloaded.getText()).isEqualTo("mocked transcript");
                        });
    }

    @Test
    void aCorruptFileReachesFailedWithAReasonInsteadOfStayingProcessingOrGoingToTheDlq() {
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        Article article = createProcessingArticle("test/corrupt.mp3", "corrupt.mp3", "audio/mpeg");
        articleStorageService.upload(
                article.getOriginalFileKey(), "not really audio".getBytes(), "audio/mpeg");

        articleExtractionPublisher.publish(article.getId());

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(
                        () -> {
                            Article reloaded =
                                    articleRepository.findById(article.getId()).orElseThrow();
                            assertThat(reloaded.getStatus()).isEqualTo(ArticleStatus.FAILED);
                            assertThat(reloaded.getFailureReason())
                                    .contains("provider unavailable");
                        });
    }
}
