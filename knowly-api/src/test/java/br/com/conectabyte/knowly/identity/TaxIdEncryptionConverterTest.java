package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Unit test (no Spring context needed) covering REQ-3's AES-256-GCM round trip, randomized-IV
 * property, and tamper detection -- see specify/features/identity-profile-model/PLAN.md's "Testing
 * strategy".
 */
class TaxIdEncryptionConverterTest {

    private static final String ENCRYPTION_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[] {
                                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                                1, 1, 1, 1, 1, 1, 1, 1, 1
                            });
    private static final String HMAC_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            new byte[] {
                                2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                                2, 2, 2, 2, 2, 2, 2, 2, 2
                            });

    private final TaxIdEncryptionConverter converter =
            new TaxIdEncryptionConverter(new IdentityCryptoProperties(ENCRYPTION_KEY, HMAC_KEY));

    @Test
    void roundTripReturnsTheOriginalPlaintext() {
        String ciphertext = converter.convertToDatabaseColumn("12345678900");

        assertThat(ciphertext).isNotNull().doesNotContain("12345678900");
        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("12345678900");
    }

    @Test
    void identicalPlaintextProducesDifferentCiphertextAcrossCalls() {
        String first = converter.convertToDatabaseColumn("12345678900");
        String second = converter.convertToDatabaseColumn("12345678900");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aTamperedCiphertextByteFailsToDecrypt() {
        String ciphertext = converter.convertToDatabaseColumn("12345678900");
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullPassesThroughUnchanged() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
