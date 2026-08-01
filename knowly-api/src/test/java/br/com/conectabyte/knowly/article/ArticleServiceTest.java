package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Regression test for the race where {@link ArticleService#create} published the "article uploaded"
 * AMQP event synchronously, inside the still-open write transaction, letting {@link
 * ArticleExtractionListener} dequeue and look the row up before it was ever committed. The fix
 * defers the notification to an {@link ApplicationEventPublisher} event, consumed only after commit
 * by {@link ArticleUploadedEventListener}'s {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)} - so this test asserts {@code create()} itself never talks to the AMQP publisher
 * directly (it no longer even holds a reference to it), only raises the Spring application event.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ArticleStorageService articleStorageService;
    @Mock private VectorStore vectorStore;
    @Mock private TenantContext tenantContext;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        ArticleProperties articleProperties =
                new ArticleProperties(DataSize.ofMegabytes(10), List.of("application/pdf"));
        articleService =
                new ArticleService(
                        articleRepository,
                        tenantRepository,
                        articleStorageService,
                        vectorStore,
                        tenantContext,
                        articleProperties,
                        applicationEventPublisher);
    }

    @Test
    void createRaisesAnApplicationEventInsteadOfPublishingToAmqpDirectly() {
        Long tenantId = 1L;
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(tenantId));
        Tenant tenant = new Tenant("Tenant");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(
                        invocation -> {
                            Article article = invocation.getArgument(0);
                            article.setId(4L);
                            return article;
                        });
        when(articleRepository.save(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file =
                new MockMultipartFile("file", "iso.pdf", "application/pdf", "content".getBytes());

        articleService.create(tenantId, "ISO PDF", file);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(ArticleUploadedApplicationEvent.class);
        assertThat(((ArticleUploadedApplicationEvent) eventCaptor.getValue()).articleId())
                .isEqualTo(4L);
    }
}
