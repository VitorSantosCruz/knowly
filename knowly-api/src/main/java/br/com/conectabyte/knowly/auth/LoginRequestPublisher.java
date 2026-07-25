package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.config.AuthRabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public LoginRequestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String email) {
        rabbitTemplate.convertAndSend(
                AuthRabbitConfig.LOGIN_REQUESTED_QUEUE, new LoginRequestedEvent(email));
    }
}
