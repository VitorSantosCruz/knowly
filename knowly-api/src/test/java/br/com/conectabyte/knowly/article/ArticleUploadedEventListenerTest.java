package br.com.conectabyte.knowly.article;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Confirms {@link ArticleUploadedEventListener} is wired to fire only in response to the {@link
 * ArticleUploadedApplicationEvent} and only in the {@code AFTER_COMMIT} phase - i.e. the AMQP
 * publish never happens before the write transaction that created the article has committed.
 */
@ExtendWith(MockitoExtension.class)
class ArticleUploadedEventListenerTest {

    @Mock private ArticleExtractionPublisher articleExtractionPublisher;

    @Test
    void onArticleUploadedIsAnnotatedToRunOnlyAfterCommit() throws NoSuchMethodException {
        Method handler =
                ArticleUploadedEventListener.class.getDeclaredMethod(
                        "onArticleUploaded", ArticleUploadedApplicationEvent.class);
        TransactionalEventListener annotation =
                handler.getAnnotation(TransactionalEventListener.class);

        org.assertj.core.api.Assertions.assertThat(annotation).isNotNull();
        org.assertj.core.api.Assertions.assertThat(annotation.phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onArticleUploadedPublishesToAmqpForTheGivenArticleId() {
        ArticleUploadedEventListener listener =
                new ArticleUploadedEventListener(articleExtractionPublisher);

        listener.onArticleUploaded(new ArticleUploadedApplicationEvent(4L));

        verify(articleExtractionPublisher).publish(4L);
    }

    @Test
    void constructingTheListenerDoesNotTouchAmqpUntilTheEventArrives() {
        new ArticleUploadedEventListener(articleExtractionPublisher);

        verifyNoInteractions(articleExtractionPublisher);
    }
}
