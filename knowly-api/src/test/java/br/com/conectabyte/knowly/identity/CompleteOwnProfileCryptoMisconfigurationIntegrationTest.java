package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Reproduces the 2026-08-02 bug report against {@code UserProfileController#completeOwnProfile}: a
 * malformed {@code CPF_RG_ENCRYPTION_KEY} (not valid base64) made {@code
 * IdentityCryptoProperties#encryptionKeyBytes} throw a raw, unmapped {@code
 * IllegalArgumentException} ("Illegal base64 character ...") that bubbled past every
 * {@code @ExceptionHandler} and produced an empty response body -- see {@code
 * IdentityCryptoConfigurationException}'s javadoc for the fix. Uses the exact payload from the bug
 * report (including accented {@code fullName}/{@code address} fields, which turned out to be
 * unrelated to the actual root cause -- the misconfigured key is decoded independently of request
 * content).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "knowly.identity.cpf-rg-encryption-key=not-valid-base64-$-content")
class CompleteOwnProfileCryptoMisconfigurationIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap-test@conectabyte.com";

    private static final String COMPLETE_PROFILE_JSON =
            "{"
                    + "\"fullName\":\"Vítor Santos da Cruz\","
                    + "\"taxId\":\"07192424528\","
                    + "\"countryCode\":\"BR\","
                    + "\"address\":{"
                    + "\"addressLine1\":\"Rua Juazeiro, 706\","
                    + "\"addressLine2\":\"Santa Cruz\","
                    + "\"city\":\"Luís Eduardo Maga\","
                    + "\"stateRegion\":\"BA\","
                    + "\"postalCode\":\"47855248\","
                    + "\"countryCode\":\"BR\""
                    + "},"
                    + "\"contacts\":["
                    + "{\"type\":\"PHONE\",\"value\":\"+5571987577122\",\"label\":\"\",\"isPrimary\":false},"
                    + "{\"type\":\"WHATSAPP\",\"value\":\"+5571987577122\",\"label\":null,\"isPrimary\":true},"
                    + "{\"type\":\"EMAIL\",\"value\":\"vittorcruz18101998@gmail.com\",\"label\":null,\"isPrimary\":false}"
                    + "]}";

    @Autowired private MockMvcTester mockMvc;
    @Autowired private LoginCodeService loginCodeService;
    @MockitoBean private JavaMailSender mailSender;

    private Cookie logIn(String email) {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        String code = loginCodeService.generate(email);
        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void completingWithAMisconfiguredEncryptionKeyReturnsAStructuredServerError() throws Exception {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/complete")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_PROFILE_JSON)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getResponse().getContentAsString())
                .isNotBlank()
                .contains("IDENTITY_CRYPTO_MISCONFIGURED");
    }
}
