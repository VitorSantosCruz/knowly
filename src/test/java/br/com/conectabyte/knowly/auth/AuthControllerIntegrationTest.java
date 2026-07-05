package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;

    @Autowired private UserRepository userRepository;

    @MockitoBean private JavaMailSender mailSender;

    @Test
    void loginRequestForAnExistingEmailReturnsGenericSuccessAndSendsACode() throws Exception {
        userRepository.saveAndFlush(new User("known@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"known@example.com\"}");

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void loginRequestForANonExistingEmailAlsoReturnsGenericSuccessWithNoEmailSent() {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}");

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void loginRequestRejectsAMalformedEmail() {
        var response =
                mockMvc.post()
                        .uri("/api/auth/login-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
