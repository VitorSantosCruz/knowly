package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link IdentityFieldNormalizer#stripFormatting}, per
 * specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 amendment (REQ-4a).
 */
class IdentityFieldNormalizerTest {

    @Test
    void stripsFormattingFromAMaskedCpf() {
        assertThat(IdentityFieldNormalizer.stripFormatting("123.456.789-00"))
                .isEqualTo("12345678900");
    }

    @Test
    void stripsFormattingFromAMaskedRg() {
        assertThat(IdentityFieldNormalizer.stripFormatting("12.345.678-9")).isEqualTo("123456789");
    }

    @Test
    void stripsFormattingFromAMaskedCep() {
        assertThat(IdentityFieldNormalizer.stripFormatting("12345-678")).isEqualTo("12345678");
    }

    @Test
    void stripsFormattingFromAMaskedPhonePreservingLeadingPlus() {
        assertThat(IdentityFieldNormalizer.stripFormatting("(11) 91234-5678"))
                .isEqualTo("11912345678");
        assertThat(IdentityFieldNormalizer.stripFormatting("+55 (11) 91234-5678"))
                .isEqualTo("+5511912345678");
    }

    @Test
    void nullInputReturnsNull() {
        assertThat(IdentityFieldNormalizer.stripFormatting(null)).isNull();
    }

    @Test
    void alreadyPlainValueIsReturnedUnchanged() {
        assertThat(IdentityFieldNormalizer.stripFormatting("12345678900")).isEqualTo("12345678900");
    }
}
