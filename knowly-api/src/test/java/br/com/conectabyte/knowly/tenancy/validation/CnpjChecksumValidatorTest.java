package br.com.conectabyte.knowly.tenancy.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** REQ-6c: pure-logic coverage of {@link CnpjChecksumValidator}, exact weights per PLAN.md. */
class CnpjChecksumValidatorTest {

    @Test
    void validNumericCnpj1() {
        assertThat(CnpjChecksumValidator.isValid("11222333000181")).isTrue();
    }

    @Test
    void invalidNumericCnpj1() {
        assertThat(CnpjChecksumValidator.isValid("11222333000180")).isFalse();
    }

    @Test
    void validNumericCnpj2() {
        assertThat(CnpjChecksumValidator.isValid("11444777000161")).isTrue();
    }

    @Test
    void invalidNumericCnpj2() {
        assertThat(CnpjChecksumValidator.isValid("11444777000160")).isFalse();
    }

    @Test
    void validNumericCnpj3() {
        assertThat(CnpjChecksumValidator.isValid("01838723000127")).isTrue();
    }

    @Test
    void invalidNumericCnpj3() {
        assertThat(CnpjChecksumValidator.isValid("01838723000100")).isFalse();
    }

    @Test
    void validAlphanumericCnpj() {
        // Alphanumeric-base CNPJ (letters in the first 12 characters), check digits computed
        // per the mod-11 algorithm above (verified independently, not a claimed real-world CNPJ).
        assertThat(CnpjChecksumValidator.isValid("12ABC34501DE35")).isTrue();
    }

    @Test
    void invalidAlphanumericCnpjChecksum() {
        assertThat(CnpjChecksumValidator.isValid("12ABC34501DE00")).isFalse();
    }

    @Test
    void wrongLengthIsRejectedWithoutThrowing() {
        assertThat(CnpjChecksumValidator.isValid("123")).isFalse();
        assertThat(CnpjChecksumValidator.isValid("")).isFalse();
        assertThat(CnpjChecksumValidator.isValid(null)).isFalse();
    }
}
