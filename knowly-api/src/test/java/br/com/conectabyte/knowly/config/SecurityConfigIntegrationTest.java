package br.com.conectabyte.knowly.config;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired private MockMvcTester mockMvc;

    @Test
    void actuatorHealthIsPubliclyAccessible() {
        Assertions.assertThat(mockMvc.get().uri("/actuator/health")).hasStatus(HttpStatus.OK);
    }

    @Test
    void anyOtherEndpointRequiresAuthentication() {
        Assertions.assertThat(mockMvc.get().uri("/some-protected-resource"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRequiresAuthenticationDespiteBeingUnderApiAuth() {
        // Deliberately not using SecurityMockMvcRequestPostProcessors.csrf(): it works by
        // reflectively swapping the shared CsrfFilter bean's tokenRepository field for a
        // session-based test stub for the rest of this class's Spring context, which silently
        // breaks the real CookieCsrfTokenRepository for every later test — including
        // everyResponseCarriesAReadableCsrfCookieForBrowserClients below. Using the real
        // cookie-issuance flow instead is both safer and a more faithful test.
        Cookie csrfCookie =
                mockMvc.get()
                        .uri("/actuator/health")
                        .exchange()
                        .getResponse()
                        .getCookie("XSRF-TOKEN");

        Assertions.assertThat(
                        mockMvc.post()
                                .uri("/api/auth/logout")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyResponseCarriesAReadableCsrfCookieForBrowserClients() {
        var result = mockMvc.get().uri("/actuator/health").exchange();

        Assertions.assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        Assertions.assertThat(result.getResponse().getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
    }

    @Test
    void everyResponseCarriesATraceparentHeaderForClientErrorCorrelation() {
        var result = mockMvc.get().uri("/actuator/health").exchange();

        String traceparent = result.getResponse().getHeader("traceparent");
        Assertions.assertThat(traceparent).isNotNull();
        Assertions.assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void unauthenticatedErrorResponsesAlsoCarryATraceparentHeader() {
        var result = mockMvc.get().uri("/some-protected-resource").exchange();

        Assertions.assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        Assertions.assertThat(result.getResponse().getHeader("traceparent")).isNotNull();
    }
}
