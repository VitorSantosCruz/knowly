package br.com.conectabyte.knowly.observability;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    /**
     * Masks a source IP address for audit/log purposes, truncating it to a /24 (IPv4) or /48 (IPv6)
     * network prefix so enough remains for coarse geo/abuse correlation without recording a raw,
     * individually-identifying address.
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }

        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            byte[] bytes = address.getAddress();

            if (bytes.length == 4) {
                bytes[3] = 0;
            } else if (bytes.length == 16) {
                for (int i = 6; i < bytes.length; i++) {
                    bytes[i] = 0;
                }
            } else {
                return "";
            }

            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
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
