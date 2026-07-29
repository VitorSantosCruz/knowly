package br.com.conectabyte.knowly.identity;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Mirrors {@code ArticleProperties}' shape for the avatar upload endpoint (REQ-10) -- reuses the
 * same "content type allow-list + max size" validation pattern, just scoped to images, per
 * specify/features/identity-profile-model-v2/PLAN.md.
 */
@ConfigurationProperties(prefix = "knowly.avatar")
public record AvatarProperties(DataSize maxFileSize, List<String> allowedContentTypes) {}
