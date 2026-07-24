package br.com.conectabyte.knowly.article;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ArticleExtractionPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ArticleExtractionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long articleId) {
        rabbitTemplate.convertAndSend(
                ArticleRabbitConfig.ARTICLE_UPLOADED_QUEUE, new ArticleUploadedEvent(articleId));
    }
}
