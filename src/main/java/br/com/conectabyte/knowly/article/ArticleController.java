package br.com.conectabyte.knowly.article;

import br.com.conectabyte.knowly.article.dto.ArticleDetailDto;
import br.com.conectabyte.knowly.article.dto.ArticleSummaryDto;
import br.com.conectabyte.knowly.article.dto.UpdateArticleRequestDto;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresPermission;
import br.com.conectabyte.knowly.tenancy.Permission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tenants/{tenantId}/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping
    @RequiresPermission(Permission.ARTICLE_CREATE)
    @AuditLog(action = "article.create", resourceType = "Article")
    public ResponseEntity<ArticleSummaryDto> upload(
            @PathVariable Long tenantId,
            @RequestParam String title,
            @RequestParam("file") MultipartFile file) {
        ArticleSummaryDto article = articleService.create(tenantId, title, file);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(article);
    }

    @GetMapping
    @RequiresPermission(Permission.ARTICLE_VIEW)
    @AuditLog(action = "article.list", resourceType = "Article")
    public ResponseEntity<List<ArticleSummaryDto>> list(@PathVariable Long tenantId) {
        return ResponseEntity.ok(articleService.list(tenantId));
    }

    @GetMapping("/{articleId}")
    @RequiresPermission(Permission.ARTICLE_VIEW)
    @AuditLog(
            action = "article.view",
            resourceType = "Article",
            resourceIdExpression = "#articleId")
    public ResponseEntity<ArticleDetailDto> get(
            @PathVariable Long tenantId, @PathVariable Long articleId) {
        return ResponseEntity.ok(articleService.get(tenantId, articleId));
    }

    @PutMapping("/{articleId}")
    @RequiresPermission(Permission.ARTICLE_EDIT)
    @AuditLog(
            action = "article.edit",
            resourceType = "Article",
            resourceIdExpression = "#articleId")
    public ResponseEntity<ArticleDetailDto> update(
            @PathVariable Long tenantId,
            @PathVariable Long articleId,
            @Valid @RequestBody UpdateArticleRequestDto request) {
        return ResponseEntity.ok(
                articleService.update(tenantId, articleId, request.title(), request.text()));
    }

    @DeleteMapping("/{articleId}")
    @RequiresPermission(Permission.ARTICLE_DELETE)
    @AuditLog(
            action = "article.delete",
            resourceType = "Article",
            resourceIdExpression = "#articleId")
    public ResponseEntity<Void> delete(@PathVariable Long tenantId, @PathVariable Long articleId) {
        articleService.delete(tenantId, articleId);

        return ResponseEntity.ok().build();
    }
}
