package br.com.conectabyte.knowly.article;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArticleEmbeddingRabbitConfig {

    public static final String ARTICLE_READY_FOR_EMBEDDING_QUEUE = "article.ready-for-embedding";
    public static final String ARTICLE_READY_FOR_EMBEDDING_DEAD_LETTER_QUEUE =
            "article.ready-for-embedding.dlq";
    private static final String DEAD_LETTER_EXCHANGE = "article.embedding.dlx";

    @Bean
    Queue articleReadyForEmbeddingQueue() {
        return QueueBuilder.durable(ARTICLE_READY_FOR_EMBEDDING_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ARTICLE_READY_FOR_EMBEDDING_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    DirectExchange articleEmbeddingDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue articleReadyForEmbeddingDeadLetterQueue() {
        return QueueBuilder.durable(ARTICLE_READY_FOR_EMBEDDING_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding articleReadyForEmbeddingDeadLetterBinding() {
        return BindingBuilder.bind(articleReadyForEmbeddingDeadLetterQueue())
                .to(articleEmbeddingDeadLetterExchange())
                .with(ARTICLE_READY_FOR_EMBEDDING_DEAD_LETTER_QUEUE);
    }
}
