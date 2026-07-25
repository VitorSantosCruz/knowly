package br.com.conectabyte.knowly.article.dto;

import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleStatus;

public record ArticleDetailDto(
        Long id,
        String title,
        String text,
        ArticleStatus status,
        String failureReason,
        String originalFileUrl) {

    public static ArticleDetailDto from(Article article, String originalFileUrl) {
        return new ArticleDetailDto(
                article.getId(),
                article.getTitle(),
                article.getText(),
                article.getStatus(),
                article.getFailureReason(),
                originalFileUrl);
    }
}
