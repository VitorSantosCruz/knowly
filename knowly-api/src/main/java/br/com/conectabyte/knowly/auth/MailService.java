package br.com.conectabyte.knowly.auth;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;

    public MailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${knowly.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    public void sendLoginCode(String to, String code) {
        send(to, "Your knowly login code", "mail/login-code.jte", code);
    }

    public void sendNewOneTimePassword(String to, String password) {
        send(to, "Your new knowly one-time password", "mail/new-one-time-password.jte", password);
    }

    private void send(String to, String subject, String template, Object param) {
        StringOutput output = new StringOutput();
        templateEngine.render(template, param, output);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(output.toString(), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to build email message", e);
        }
    }
}
