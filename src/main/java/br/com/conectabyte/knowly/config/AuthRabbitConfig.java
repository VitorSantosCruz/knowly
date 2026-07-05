package br.com.conectabyte.knowly.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthRabbitConfig {

    public static final String LOGIN_REQUESTED_QUEUE = "auth.login-requested";

    @Bean
    Queue loginRequestedQueue() {
        return new Queue(LOGIN_REQUESTED_QUEUE, true);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
