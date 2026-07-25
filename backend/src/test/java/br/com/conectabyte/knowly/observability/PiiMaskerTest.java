package br.com.conectabyte.knowly.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void masksTheLocalPartButKeepsTheDomainVisible() {
        String masked = PiiMasker.maskEmail("john.doe@example.com");

        assertThat(masked).startsWith("j***@example.com#");
    }

    @Test
    void isStableForTheSameEmail() {
        assertThat(PiiMasker.maskEmail("john.doe@example.com"))
                .isEqualTo(PiiMasker.maskEmail("john.doe@example.com"));
    }

    @Test
    void isCaseInsensitiveSoTheSameAccountAlwaysMasksTheSameWay() {
        assertThat(PiiMasker.maskEmail("John.Doe@Example.com"))
                .isEqualTo(PiiMasker.maskEmail("john.doe@example.com"));
    }

    @Test
    void differentEmailsWithTheSamePrefixAndDomainDoNotCollide() {
        String first = PiiMasker.maskEmail("john@example.com");
        String second = PiiMasker.maskEmail("jane@example.com");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void neverIncludesTheFullLocalPart() {
        String masked = PiiMasker.maskEmail("john.doe@example.com");

        assertThat(masked).doesNotContain("john.doe");
    }

    @Test
    void handlesBlankInputWithoutThrowing() {
        assertThat(PiiMasker.maskEmail(null)).isEmpty();
        assertThat(PiiMasker.maskEmail("")).isEmpty();
    }

    @Test
    void handlesInputWithoutAnAtSign() {
        String masked = PiiMasker.maskEmail("not-an-email");

        assertThat(masked).doesNotContain("not-an-email");
        assertThat(masked).contains("#");
    }
}
