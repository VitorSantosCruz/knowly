package br.com.conectabyte.knowly.article;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Deliberately not {@code @Transactional} — same reasoning as {@link ArticleExtractionListener}:
 * this runs with no active tenant in context, and every repository call is scoped by an explicit
 * article id.
 */
@Component
public class ArticleEmbeddingListener {

    private static final Logger log = LoggerFactory.getLogger(ArticleEmbeddingListener.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final ArticleRepository articleRepository;
    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public ArticleEmbeddingListener(ArticleRepository articleRepository, VectorStore vectorStore) {
        this.articleRepository = articleRepository;
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder().build();
    }

    @RabbitListener(queues = ArticleEmbeddingRabbitConfig.ARTICLE_READY_FOR_EMBEDDING_QUEUE)
    public void handle(ArticleReadyForEmbeddingEvent event) {
        Article article = articleRepository.findById(event.articleId()).orElse(null);

        if (article == null) {
            log.warn("article.embedding_skipped articleId={} reason=not_found", event.articleId());
            return;
        }

        try {
            Document document =
                    Document.builder()
                            .text(article.getText())
                            .metadata("tenant_id", article.getTenant().getId())
                            .metadata("article_id", article.getId())
                            .build();
            List<Document> chunks = textSplitter.split(document);

            // Delete before add so redelivery of this event (e.g. after a crash between
            // vectorStore.add() succeeding and the RabbitMQ ack) is idempotent instead of
            // accumulating duplicate embeddings for the same article.
            vectorStore.delete(
                    new FilterExpressionBuilder().eq("article_id", article.getId()).build());
            vectorStore.add(chunks);
            article.setEmbeddingStatus(EmbeddingStatus.READY);
            log.info(
                    "article.embedding_succeeded articleId={} chunks={}",
                    article.getId(),
                    chunks.size());
        } catch (Exception e) {
            article.setEmbeddingStatus(EmbeddingStatus.FAILED);
            article.setEmbeddingFailureReason(truncate(e.getMessage()));
            log.error(
                    "article.embedding_failed articleId={} reason={}",
                    article.getId(),
                    e.getMessage());
        }

        articleRepository.save(article);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown embedding failure";
        }

        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(0, MAX_FAILURE_REASON_LENGTH)
                : message;
    }
}
