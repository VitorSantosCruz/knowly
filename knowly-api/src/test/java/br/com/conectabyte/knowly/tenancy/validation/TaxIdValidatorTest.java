package br.com.conectabyte.knowly.tenancy.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** REQ-6: pure-logic coverage of {@link TaxIdValidator}'s package-private static rule. */
class TaxIdValidatorTest {

    @Test
    void brazilWith14UnpunctuatedDigitsPasses() {
        assertThat(TaxIdValidator.isValid("BR", "12345678000199")).isTrue();
    }

    @Test
    void brazilWithPunctuatedButStill14DigitsPasses() {
        assertThat(TaxIdValidator.isValid("Brasil", "12.345.678/0001-99")).isTrue();
    }

    @Test
    void brazilWithWrongDigitCountFails() {
        assertThat(TaxIdValidator.isValid("Brazil", "123456789")).isFalse();
    }

    @Test
    void nonBrazilWithAnyNonEmptyStringPasses() {
        assertThat(TaxIdValidator.isValid("US", "EIN-123")).isTrue();
        assertThat(TaxIdValidator.isValid("Portugal", "PT-1")).isTrue();
    }

    @Test
    void blankTaxIdFailsRegardlessOfCountry() {
        assertThat(TaxIdValidator.isValid("BR", "")).isFalse();
        assertThat(TaxIdValidator.isValid("US", "   ")).isFalse();
        assertThat(TaxIdValidator.isValid("US", null)).isFalse();
    }

    @Test
    void countryComparisonIsCaseInsensitiveAndTrimmed() {
        assertThat(TaxIdValidator.isValid(" br ", "12345678000199")).isTrue();
        assertThat(TaxIdValidator.isValid("bR", "not-14-digits")).isFalse();
    }
}
