package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.tenancy.Tenant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class ArticleEmbeddingListenerTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ArticleEmbeddingListener listener =
            new ArticleEmbeddingListener(articleRepository, vectorStore);

    private Article aReadyArticle(Long articleId, Long tenantId, String text) {
        Tenant tenant = new Tenant("Tenant");
        tenant.setId(tenantId);
        Article article = new Article(tenant, "Title", "key", "file.pdf", "application/pdf");
        article.setId(articleId);
        article.setText(text);
        article.setStatus(ArticleStatus.READY);

        return article;
    }

    @Test
    void chunksAReadyArticlesTextAndAddsItToTheVectorStoreTaggedByTenantAndArticle() {
        Article article = aReadyArticle(7L, 3L, "This is the article's extracted plain text.");
        when(articleRepository.findById(7L)).thenReturn(java.util.Optional.of(article));

        listener.handle(new ArticleReadyForEmbeddingEvent(7L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> chunks = captor.getValue();
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("tenant_id", 3L)
                .containsEntry("article_id", 7L);
        assertThat(article.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.READY);
        verify(articleRepository).save(article);
    }

    @Test
    void aVectorStoreFailureMarksTheArticleFailedWithAReasonAndDoesNotRethrow() {
        Article article = aReadyArticle(8L, 3L, "Some text to embed.");
        when(articleRepository.findById(8L)).thenReturn(java.util.Optional.of(article));
        doThrow(new RuntimeException("embedding provider unavailable"))
                .when(vectorStore)
                .add(any());

        listener.handle(new ArticleReadyForEmbeddingEvent(8L));

        assertThat(article.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.FAILED);
        assertThat(article.getEmbeddingFailureReason()).contains("embedding provider unavailable");
        verify(articleRepository).save(article);
    }

    @Test
    void aMissingArticleIsSkippedWithoutCallingTheVectorStore() {
        when(articleRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        listener.handle(new ArticleReadyForEmbeddingEvent(99L));

        verify(vectorStore, never()).add(any());
        verify(articleRepository, never()).save(any());
    }
}
