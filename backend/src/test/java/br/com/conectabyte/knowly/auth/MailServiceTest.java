package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MailServiceTest {

    @MockitoBean private JavaMailSender mailSender;

    @Autowired private MailService mailService;

    @Test
    void sendsLoginCodeEmailContainingTheCode() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendLoginCode("user@example.com", "123456");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat((String) sent.getContent()).contains("123456");
    }

    @Test
    void sendsNewOneTimePasswordEmailContainingThePassword() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendNewOneTimePassword("user@example.com", "aB3dEfGh4jkm");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat((String) sent.getContent()).contains("aB3dEfGh4jkm");
    }
}
