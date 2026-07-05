package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.config.AuthRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LoginRequestListener {

    private static final Logger log = LoggerFactory.getLogger(LoginRequestListener.class);

    private final UserRepository userRepository;
    private final LoginCodeService loginCodeService;
    private final MailService mailService;

    public LoginRequestListener(
            UserRepository userRepository,
            LoginCodeService loginCodeService,
            MailService mailService) {
        this.userRepository = userRepository;
        this.loginCodeService = loginCodeService;
        this.mailService = mailService;
    }

    @RabbitListener(queues = AuthRabbitConfig.LOGIN_REQUESTED_QUEUE)
    public void handle(LoginRequestedEvent event) {
        boolean accountExists =
                userRepository
                        .findByEmailIgnoreCase(event.email())
                        .map(
                                user -> {
                                    String code = loginCodeService.generate(user.getEmail());
                                    mailService.sendLoginCode(user.getEmail(), code);
                                    return true;
                                })
                        .orElse(false);

        log.info("auth.login_request email={} accountExists={}", event.email(), accountExists);
    }
}
