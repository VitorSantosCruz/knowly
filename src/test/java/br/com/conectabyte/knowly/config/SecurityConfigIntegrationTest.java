package br.com.conectabyte.knowly.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
        Assertions.assertThat(mockMvc.post().uri("/api/auth/logout").with(csrf()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyResponseCarriesAReadableCsrfCookieForBrowserClients() {
        var result = mockMvc.get().uri("/actuator/health").exchange();

        Assertions.assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        Assertions.assertThat(result.getResponse().getCookie("XSRF-TOKEN").isHttpOnly()).isFalse();
    }
}
