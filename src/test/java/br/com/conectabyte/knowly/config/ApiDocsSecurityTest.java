package br.com.conectabyte.knowly.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ApiDocsSecurityTest {

    @Import(TestcontainersConfiguration.class)
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Nested
    class WhenDisabledByDefault {

        @Autowired private MockMvcTester mockMvc;
        @Autowired private UserRepository userRepository;
        @Autowired private LoginCodeService loginCodeService;

        @MockitoBean private JavaMailSender mailSender;

        @Test
        void apiDocsAreNotServedEvenToAnAuthenticatedSession() {
            String cookie = login("docs-disabled-api@example.com");

            assertThat(
                            mockMvc.get()
                                    .uri("/v3/api-docs")
                                    .cookie(new jakarta.servlet.http.Cookie("SESSION", cookie)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void swaggerUiIsNotServedEvenToAnAuthenticatedSession() {
            String cookie = login("docs-disabled-ui@example.com");

            assertThat(
                            mockMvc.get()
                                    .uri("/swagger-ui/index.html")
                                    .cookie(new jakarta.servlet.http.Cookie("SESSION", cookie)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }

        private String login(String email) {
            userRepository.saveAndFlush(new User(email));
            String code = loginCodeService.generate(email);
            when(mailSender.createMimeMessage())
                    .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

            var result =
                    mockMvc.post()
                            .uri("/api/auth/login-code/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                            .exchange();

            return result.getResponse().getCookie("SESSION").getValue();
        }
    }

    @Import(TestcontainersConfiguration.class)
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Nested
    class WhenEnabled {

        @DynamicPropertySource
        static void enableApiDocs(DynamicPropertyRegistry registry) {
            registry.add("springdoc.api-docs.enabled", () -> "true");
            registry.add("springdoc.swagger-ui.enabled", () -> "true");
        }

        @Autowired private MockMvcTester mockMvc;
        @Autowired private UserRepository userRepository;
        @Autowired private LoginCodeService loginCodeService;

        @MockitoBean private JavaMailSender mailSender;

        @Test
        void apiDocsRequireAuthentication() {
            assertThat(mockMvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void swaggerUiRequiresAuthentication() {
            assertThat(mockMvc.get().uri("/swagger-ui/index.html"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void apiDocsAreServedToAnAuthenticatedSession() {
            userRepository.saveAndFlush(new User("docs-enabled@example.com"));
            String code = loginCodeService.generate("docs-enabled@example.com");
            when(mailSender.createMimeMessage())
                    .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

            var loginResult =
                    mockMvc.post()
                            .uri("/api/auth/login-code/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"email\":\"docs-enabled@example.com\",\"code\":\""
                                            + code
                                            + "\"}")
                            .exchange();
            String cookie = loginResult.getResponse().getCookie("SESSION").getValue();

            assertThat(
                            mockMvc.get()
                                    .uri("/v3/api-docs")
                                    .cookie(new jakarta.servlet.http.Cookie("SESSION", cookie)))
                    .hasStatus(HttpStatus.OK);
        }
    }
}
