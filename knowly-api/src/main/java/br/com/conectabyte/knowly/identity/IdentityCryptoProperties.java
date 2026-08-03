package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.identity.exception.IdentityCryptoConfigurationException;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Two independent, externally-sourced base64-encoded 256-bit keys backing {@link
 * TaxIdEncryptionConverter} (encryption) and {@link BlindIndexService} (HMAC) -- never the same
 * key, per specify/features/identity-profile-model/SPEC.md's "Resolved" section. Both are bare
 * {@code ${VAR}} in application.yaml, same convention as {@code OPENAI_API_KEY}/{@code
 * TURNSTILE_SECRET_KEY}.
 */
@ConfigurationProperties(prefix = "knowly.identity")
public record IdentityCryptoProperties(String cpfRgEncryptionKey, String cpfRgHmacKey) {

    public byte[] encryptionKeyBytes() {
        return decode(cpfRgEncryptionKey, "CPF_RG_ENCRYPTION_KEY");
    }

    public byte[] hmacKeyBytes() {
        return decode(cpfRgHmacKey, "CPF_RG_HMAC_KEY");
    }

    /**
     * Wraps a malformed key's raw {@code IllegalArgumentException} ("Illegal base64 character ...")
     * in a clear, mapped exception identifying which environment variable is broken -- never the
     * value itself, to avoid leaking a partial secret into logs/responses.
     */
    private static byte[] decode(String value, String propertyName) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IdentityCryptoConfigurationException(propertyName, e);
        }
    }
}
