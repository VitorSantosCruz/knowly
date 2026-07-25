package br.com.conectabyte.knowly.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Masks personally identifiable information before it reaches logs, while keeping enough of a
 * stable fingerprint to correlate every log line for the same actor — required by the
 * constitution's "fully auditable" mandate. A plain prefix mask (e.g. "j***@example.com") isn't
 * enough on its own: two different users sharing a first letter and domain would be
 * indistinguishable in logs. Appending a short, deterministic hash of the full (lowercased) email
 * disambiguates them without ever printing the email itself.
 */
public final class PiiMasker {

    private static final int HASH_BYTES = 4;

    private PiiMasker() {}

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }

        String normalized = email.toLowerCase();
        String hash = shortHash(normalized);
        int at = normalized.indexOf('@');

        if (at <= 0) {
            return "***#" + hash;
        }

        return normalized.charAt(0) + "***" + normalized.substring(at) + "#" + hash;
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, HASH_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must always be available", e);
        }
    }
}
