package br.com.conectabyte.knowly.article;

import br.com.conectabyte.knowly.article.dto.ArticleDetailDto;
import br.com.conectabyte.knowly.article.dto.ArticleSummaryDto;
import br.com.conectabyte.knowly.article.exception.ArticleNotFoundException;
import br.com.conectabyte.knowly.article.exception.FileTooLargeException;
import br.com.conectabyte.knowly.article.exception.UnsupportedFileTypeException;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.io.IOException;
import java.util.List;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final TenantRepository tenantRepository;
    private final ArticleStorageService articleStorageService;
    private final VectorStore vectorStore;
    private final TenantContext tenantContext;
    private final ArticleProperties articleProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ArticleService(
            ArticleRepository articleRepository,
            TenantRepository tenantRepository,
            ArticleStorageService articleStorageService,
            VectorStore vectorStore,
            TenantContext tenantContext,
            ArticleProperties articleProperties,
            ApplicationEventPublisher applicationEventPublisher) {
        this.articleRepository = articleRepository;
        this.tenantRepository = tenantRepository;
        this.articleStorageService = articleStorageService;
        this.vectorStore = vectorStore;
        this.tenantContext = tenantContext;
        this.articleProperties = articleProperties;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public ArticleSummaryDto create(Long tenantId, String title, MultipartFile file) {
        requireActiveTenant(tenantId);
        validateFile(file);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(ArticleNotFoundException::new);
        Article article =
                new Article(
                        tenant,
                        title,
                        "pending",
                        file.getOriginalFilename(),
                        file.getContentType());
        article = articleRepository.saveAndFlush(article);

        String key = "tenants/" + tenantId + "/articles/" + article.getId() + "/original";
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read uploaded file", e);
        }
        articleStorageService.upload(key, content, file.getContentType());
        article.setOriginalFileKey(key);
        article = articleRepository.save(article);

        applicationEventPublisher.publishEvent(
                new ArticleUploadedApplicationEvent(article.getId()));

        return ArticleSummaryDto.from(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> list(Long tenantId) {
        requireActiveTenant(tenantId);

        return articleRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(ArticleSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleDetailDto get(Long tenantId, Long articleId) {
        requireActiveTenant(tenantId);
        Article article = requireArticle(articleId);
        String url = articleStorageService.presignedUrl(article.getOriginalFileKey()).toString();

        return ArticleDetailDto.from(article, url);
    }

    @Transactional
    public ArticleDetailDto update(Long tenantId, Long articleId, String title, String text) {
        requireActiveTenant(tenantId);
        Article article = requireArticle(articleId);
        article.setTitle(title);
        article.setText(text);
        articleRepository.save(article);
        String url = articleStorageService.presignedUrl(article.getOriginalFileKey()).toString();

        return ArticleDetailDto.from(article, url);
    }

    @Transactional
    public void delete(Long tenantId, Long articleId) {
        requireActiveTenant(tenantId);
        Article article = requireArticle(articleId);
        article.setActive(false);
        articleRepository.save(article);

        vectorStore.delete(new FilterExpressionBuilder().eq("article_id", articleId).build());
    }

    private Article requireArticle(Long articleId) {
        return articleRepository.findById(articleId).orElseThrow(ArticleNotFoundException::new);
    }

    private void requireActiveTenant(Long tenantId) {
        if (tenantContext.isStaffAdmin()) {
            return;
        }

        if (tenantContext.getActiveTenantId().filter(tenantId::equals).isEmpty()) {
            throw new TenantAccessDeniedException();
        }
    }

    private void validateFile(MultipartFile file) {
        if (!articleProperties.allowedContentTypes().contains(file.getContentType())) {
            throw new UnsupportedFileTypeException();
        }

        if (file.getSize() > articleProperties.maxFileSize().toBytes()) {
            throw new FileTooLargeException();
        }
    }
}
