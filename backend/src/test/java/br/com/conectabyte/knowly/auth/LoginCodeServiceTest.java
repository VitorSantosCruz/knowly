package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LoginCodeServiceTest {

    @Autowired private LoginCodeService loginCodeService;

    @MockitoSpyBean private PasswordEncoder passwordEncoder;

    @Test
    void generatesANumericCodeOfTheConfiguredLength() {
        String code = loginCodeService.generate("someone@example.com");

        assertThat(code).hasSize(6).matches("\\d{6}");
    }

    @Test
    void verifiesAndInvalidatesACorrectCode() {
        String code = loginCodeService.generate("someone@example.com");

        assertThat(loginCodeService.verify("someone@example.com", code)).isTrue();
        assertThat(loginCodeService.verify("someone@example.com", code)).isFalse();
    }

    @Test
    void rejectsAWrongCodeWithoutInvalidatingTheRealOne() {
        String code = loginCodeService.generate("someone@example.com");

        assertThat(loginCodeService.verify("someone@example.com", "000000")).isFalse();
        assertThat(loginCodeService.verify("someone@example.com", code)).isTrue();
    }

    @Test
    void rejectsVerificationWhenNoCodeWasGenerated() {
        assertThat(loginCodeService.verify("nobody@example.com", "123456")).isFalse();
    }

    @Test
    void comparesAgainstADummyHashWhenNoCodeExists() {
        loginCodeService.verify("no-code-timing@example.com", "123456");

        verify(passwordEncoder).matches(eq("123456"), any());
    }
}
