package br.com.conectabyte.knowly.article;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Deliberately not {@code @Transactional}: this listener runs with no active tenant in context
 * (it's a background system process, not a user request), so a @Transactional method here would
 * have TenantFilterAspect enable the tenant filter with the fail-closed sentinel and hide the very
 * row this needs to update. Each repository call below runs through Spring Data's own default
 * per-call transaction instead, unfiltered — safe here since every lookup is scoped by an explicit
 * article id, not a listing.
 */
@Component
public class ArticleExtractionListener {

    private static final Logger log = LoggerFactory.getLogger(ArticleExtractionListener.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final ArticleRepository articleRepository;
    private final ArticleStorageService articleStorageService;
    private final List<TextExtractor> extractors;

    public ArticleExtractionListener(
            ArticleRepository articleRepository,
            ArticleStorageService articleStorageService,
            List<TextExtractor> extractors) {
        this.articleRepository = articleRepository;
        this.articleStorageService = articleStorageService;
        this.extractors = extractors;
    }

    @RabbitListener(queues = ArticleRabbitConfig.ARTICLE_UPLOADED_QUEUE)
    public void handle(ArticleUploadedEvent event) {
        Article article = articleRepository.findById(event.articleId()).orElse(null);

        if (article == null) {
            log.warn("article.extraction_skipped articleId={} reason=not_found", event.articleId());
            return;
        }

        try {
            byte[] content = articleStorageService.download(article.getOriginalFileKey());
            TextExtractor extractor = findExtractor(article.getOriginalContentType());
            String text = extractor.extract(content, article.getOriginalFileName());

            article.setText(text);
            article.setStatus(ArticleStatus.READY);
            log.info("article.extraction_succeeded articleId={}", article.getId());
        } catch (Exception e) {
            article.setStatus(ArticleStatus.FAILED);
            article.setFailureReason(truncate(e.getMessage()));
            log.error(
                    "article.extraction_failed articleId={} reason={}",
                    article.getId(),
                    e.getMessage());
        }

        articleRepository.save(article);
    }

    private TextExtractor findExtractor(String contentType) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(contentType))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No text extractor registered for content type "
                                                + contentType));
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown extraction failure";
        }

        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(0, MAX_FAILURE_REASON_LENGTH)
                : message;
    }
}
