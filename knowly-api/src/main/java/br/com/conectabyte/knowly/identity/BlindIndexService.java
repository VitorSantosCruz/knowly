package br.com.conectabyte.knowly.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Computes the keyed HMAC-SHA256 blind index (hex-encoded) used for DB-level uniqueness on {@code
 * cpf}/{@code rg} (REQ-2), per specify/features/identity-profile-model/SPEC.md's "Resolved"
 * section. Normalization strips every non-digit character before hashing so formatted/unformatted
 * input always collides; an empty string after stripping is treated as "not provided" (REQ-2a).
 * Uses a key independent from {@link CpfRgEncryptionConverter}'s encryption key.
 */
@Service
public class BlindIndexService {

    private static final String ALGORITHM = "HmacSHA256";

    private final IdentityCryptoProperties properties;

    public BlindIndexService(IdentityCryptoProperties properties) {
        this.properties = properties;
    }

    /** Strips every non-digit character; empty result after stripping is treated as absent. */
    public String normalize(String value) {
        if (value == null) {
            return null;
        }

        String digitsOnly = value.replaceAll("[^0-9]", "");
        return digitsOnly.isEmpty() ? null : digitsOnly;
    }

    /** Returns the hex-encoded HMAC-SHA256 of the normalized value, or {@code null} if absent. */
    public String hmac(String value) {
        String normalized = normalize(value);

        if (normalized == null) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.hmacKeyBytes(), ALGORITHM));

            byte[] hash = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }
}
