package br.com.conectabyte.knowly.article.dto;

import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleStatus;

public record ArticleSummaryDto(Long id, String title, ArticleStatus status) {

    public static ArticleSummaryDto from(Article article) {
        return new ArticleSummaryDto(article.getId(), article.getTitle(), article.getStatus());
    }
}
