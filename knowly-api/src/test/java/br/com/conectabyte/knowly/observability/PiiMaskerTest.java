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

    @Test
    void masksAnIpv4AddressToItsSlash24() {
        assertThat(PiiMasker.maskIp("203.0.113.45")).isEqualTo("203.0.113.0");
    }

    @Test
    void masksAnIpv6AddressToItsSlash48() {
        assertThat(PiiMasker.maskIp("2001:db8:85a3::8a2e:370:7334"))
                .isEqualTo("2001:db8:85a3:0:0:0:0:0");
    }

    @Test
    void handlesBlankIpInputWithoutThrowing() {
        assertThat(PiiMasker.maskIp(null)).isEmpty();
        assertThat(PiiMasker.maskIp("")).isEmpty();
        assertThat(PiiMasker.maskIp("   ")).isEmpty();
    }

    @Test
    void handlesUnparsableIpInputWithoutThrowing() {
        assertThat(PiiMasker.maskIp("not-an-ip")).isEmpty();
    }
}
