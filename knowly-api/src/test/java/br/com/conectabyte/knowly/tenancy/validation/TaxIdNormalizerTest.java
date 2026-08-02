package br.com.conectabyte.knowly.tenancy.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** REQ-6a: pure-logic coverage of {@link TaxIdNormalizer}. */
class TaxIdNormalizerTest {

    @Test
    void stripsPunctuationFromCnpj() {
        assertThat(TaxIdNormalizer.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @Test
    void alreadyNormalizedInputIsUnchanged() {
        assertThat(TaxIdNormalizer.normalize("11222333000181")).isEqualTo("11222333000181");
    }

    @Test
    void nullIsHandledWithoutThrowing() {
        assertThat(TaxIdNormalizer.normalize(null)).isNull();
    }

    @Test
    void blankIsHandledWithoutThrowing() {
        assertThat(TaxIdNormalizer.normalize("   ")).isEqualTo("   ");
    }
}
