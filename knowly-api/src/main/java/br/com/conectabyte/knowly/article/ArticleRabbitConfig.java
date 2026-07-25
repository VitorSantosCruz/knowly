package br.com.conectabyte.knowly.article;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArticleRabbitConfig {

    public static final String ARTICLE_UPLOADED_QUEUE = "article.uploaded";
    public static final String ARTICLE_UPLOADED_DEAD_LETTER_QUEUE = "article.uploaded.dlq";
    private static final String DEAD_LETTER_EXCHANGE = "article.dlx";

    @Bean
    Queue articleUploadedQueue() {
        return QueueBuilder.durable(ARTICLE_UPLOADED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ARTICLE_UPLOADED_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    DirectExchange articleDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue articleUploadedDeadLetterQueue() {
        return QueueBuilder.durable(ARTICLE_UPLOADED_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding articleUploadedDeadLetterBinding() {
        return BindingBuilder.bind(articleUploadedDeadLetterQueue())
                .to(articleDeadLetterExchange())
                .with(ARTICLE_UPLOADED_DEAD_LETTER_QUEUE);
    }
}
