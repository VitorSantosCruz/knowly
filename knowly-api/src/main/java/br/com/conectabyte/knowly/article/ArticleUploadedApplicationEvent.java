package br.com.conectabyte.knowly.article;

/**
 * Spring application event raised by {@link ArticleService#create} right after the article row is
 * written, but consumed only after the enclosing transaction commits (see {@link
 * ArticleUploadedEventListener}). Deliberately distinct from {@link ArticleUploadedEvent}, which is
 * the AMQP message DTO sent to downstream consumers once the row is actually visible.
 */
public record ArticleUploadedApplicationEvent(Long articleId) {}
