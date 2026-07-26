package br.com.conectabyte.knowly.identity;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Two independent, externally-sourced base64-encoded 256-bit keys backing {@link
 * CpfRgEncryptionConverter} (encryption) and {@link BlindIndexService} (HMAC) -- never the same
 * key, per specify/features/identity-profile-model/SPEC.md's "Resolved" section. Both are bare
 * {@code ${VAR}} in application.yaml, same convention as {@code OPENAI_API_KEY}/{@code
 * TURNSTILE_SECRET_KEY}.
 */
@ConfigurationProperties(prefix = "knowly.identity")
public record IdentityCryptoProperties(String cpfRgEncryptionKey, String cpfRgHmacKey) {

    public byte[] encryptionKeyBytes() {
        return Base64.getDecoder().decode(cpfRgEncryptionKey);
    }

    public byte[] hmacKeyBytes() {
        return Base64.getDecoder().decode(cpfRgHmacKey);
    }
}
