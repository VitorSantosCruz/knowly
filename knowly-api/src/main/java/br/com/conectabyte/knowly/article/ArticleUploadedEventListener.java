package br.com.conectabyte.knowly.article;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes the "article uploaded" AMQP event only after the transaction that inserted the article
 * row has actually committed - fixes a race where {@link ArticleExtractionListener} could dequeue
 * and call {@code findById} before the INSERT from {@link ArticleService#create} was visible
 * outside its transaction, silently skipping processing (article stuck in PROCESSING forever).
 */
@Component
public class ArticleUploadedEventListener {

    private final ArticleExtractionPublisher articleExtractionPublisher;

    public ArticleUploadedEventListener(ArticleExtractionPublisher articleExtractionPublisher) {
        this.articleExtractionPublisher = articleExtractionPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleUploaded(ArticleUploadedApplicationEvent event) {
        articleExtractionPublisher.publish(event.articleId());
    }
}
