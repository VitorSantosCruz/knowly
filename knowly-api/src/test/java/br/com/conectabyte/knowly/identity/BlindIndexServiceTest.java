package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Unit test covering the blind-index normalization/hashing rules from
 * specify/features/identity-profile-model/SPEC.md's "Resolved" section.
 */
class BlindIndexServiceTest {

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

    private final BlindIndexService service =
            new BlindIndexService(new IdentityCryptoProperties(ENCRYPTION_KEY, HMAC_KEY));

    @Test
    void formattedAndUnformattedInputHashIdentically() {
        assertThat(service.hmac("123.456.789-00")).isEqualTo(service.hmac("12345678900"));
    }

    @Test
    void samePlaintextAndKeyAlwaysProduceTheSameHash() {
        assertThat(service.hmac("12345678900")).isEqualTo(service.hmac("12345678900"));
    }

    @Test
    void emptyStringAfterStrippingIsTreatedAsAbsent() {
        assertThat(service.hmac("---")).isNull();
        assertThat(service.hmac("")).isNull();
        assertThat(service.hmac(null)).isNull();
    }

    @Test
    void differentPlaintextProducesADifferentHash() {
        assertThat(service.hmac("12345678900")).isNotEqualTo(service.hmac("98765432100"));
    }
}
