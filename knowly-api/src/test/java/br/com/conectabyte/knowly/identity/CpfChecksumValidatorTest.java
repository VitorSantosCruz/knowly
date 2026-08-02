package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link CpfChecksumValidator#isValid}, per
 * specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 amendment (REQ-4a).
 */
class CpfChecksumValidatorTest {

    @Test
    void aKnownValidCpfPasses() {
        assertThat(CpfChecksumValidator.isValid("52998224725")).isTrue();
    }

    @Test
    void aCpfWithAWrongVerifierDigitIsRejected() {
        assertThat(CpfChecksumValidator.isValid("52998224724")).isFalse();
    }

    @Test
    void everyAllRepeatedDigitCpfIsRejectedDespitePassingNaiveMod11() {
        for (int digit = 0; digit <= 9; digit++) {
            String repeated = String.valueOf(digit).repeat(11);
            assertThat(CpfChecksumValidator.isValid(repeated)).as("digit %d", digit).isFalse();
        }
    }

    @Test
    void theClassicAllOnesFixtureIsRejected() {
        assertThat(CpfChecksumValidator.isValid("11111111111")).isFalse();
    }

    @Test
    void aValueThatIsNotExactlyElevenDigitsIsRejected() {
        assertThat(CpfChecksumValidator.isValid("1234567890")).isFalse();
        assertThat(CpfChecksumValidator.isValid("123456789012")).isFalse();
        assertThat(CpfChecksumValidator.isValid("")).isFalse();
    }
}
