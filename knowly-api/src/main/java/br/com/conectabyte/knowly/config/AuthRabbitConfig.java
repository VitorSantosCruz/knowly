package br.com.conectabyte.knowly.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthRabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthRabbitConfig.class);

    public static final String LOGIN_REQUESTED_QUEUE = "auth.login-requested";
    public static final String LOGIN_REQUESTED_DEAD_LETTER_QUEUE = "auth.login-requested.dlq";
    private static final String DEAD_LETTER_EXCHANGE = "auth.dlx";

    /**
     * Dead-lettered on exhausted retries (see spring.rabbitmq.listener.simple.retry in
     * application.yaml) so a poison message (e.g. a bug in the listener, a transient outage) lands
     * in {@link #loginRequestedDeadLetterQueue()} instead of being requeued and redelivered forever
     * — the default behavior, which would otherwise pin a consumer thread at 100% CPU and flood the
     * logs without ever draining the queue.
     */
    @Bean
    Queue loginRequestedQueue() {
        return QueueBuilder.durable(LOGIN_REQUESTED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(LOGIN_REQUESTED_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    DirectExchange authDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue loginRequestedDeadLetterQueue() {
        return QueueBuilder.durable(LOGIN_REQUESTED_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding loginRequestedDeadLetterBinding() {
        return BindingBuilder.bind(loginRequestedDeadLetterQueue())
                .to(authDeadLetterExchange())
                .with(LOGIN_REQUESTED_DEAD_LETTER_QUEUE);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Without publisher confirms/returns, a rejected or unroutable publish (broker overloaded,
     * queue not bound) fails completely silently — convertAndSend returns normally and the user
     * just never receives their login code, with nothing in the logs to explain why.
     */
    @Bean
    RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return template -> {
            template.setMandatory(true);
            template.setConfirmCallback(
                    (correlationData, ack, cause) -> {
                        if (!ack) {
                            log.error("auth.rabbitmq publish_not_confirmed cause={}", cause);
                        }
                    });
            template.setReturnsCallback(
                    returned ->
                            log.error(
                                    "auth.rabbitmq message_returned replyCode={} replyText={}"
                                            + " exchange={} routingKey={}",
                                    returned.getReplyCode(),
                                    returned.getReplyText(),
                                    returned.getExchange(),
                                    returned.getRoutingKey()));
        };
    }
}
