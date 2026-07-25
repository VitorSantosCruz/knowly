package br.com.conectabyte.knowly.article;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "knowly.article")
public record ArticleProperties(DataSize maxFileSize, List<String> allowedContentTypes) {}
