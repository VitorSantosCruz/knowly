package br.com.conectabyte.knowly.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginRequestPublisher;
import br.com.conectabyte.knowly.auth.MailService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class AuthRabbitConfigTest {

    @Test
    void loginRequestedQueueDeadLettersToADedicatedQueueOnRejection() {
        Queue queue = new AuthRabbitConfig().loginRequestedQueue();

        assertThat(queue.getArguments()).containsKey("x-dead-letter-exchange");
        assertThat(queue.getArguments())
                .containsEntry(
                        "x-dead-letter-routing-key",
                        AuthRabbitConfig.LOGIN_REQUESTED_DEAD_LETTER_QUEUE);
    }

    @Nested
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest
    @ActiveProfiles("test")
    class DeadLetteringBehavior {

        @Autowired private RabbitAdmin rabbitAdmin;
        @Autowired private UserRepository userRepository;
        @Autowired private LoginRequestPublisher loginRequestPublisher;

        @MockitoSpyBean private MailService mailService;

        @Test
        void aRepeatedlyFailingMessageEndsUpInTheDeadLetterQueue() {
            String email = "poison-message@example.com";
            userRepository.saveAndFlush(new User(email));
            doThrow(new RuntimeException("simulated failure"))
                    .when(mailService)
                    .sendLoginCode(eq(email), any());

            loginRequestPublisher.publish(email);

            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(
                            () -> {
                                Long count =
                                        (Long)
                                                rabbitAdmin
                                                        .getQueueProperties(
                                                                AuthRabbitConfig
                                                                        .LOGIN_REQUESTED_DEAD_LETTER_QUEUE)
                                                        .get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                                assertThat(count).isEqualTo(1L);
                            });
        }
    }

    @Nested
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest
    @ActiveProfiles("test")
    class PublisherReturnsBehavior {

        @Autowired private RabbitTemplate rabbitTemplate;

        private ListAppender<ILoggingEvent> logAppender;

        @BeforeEach
        void attachLogAppender() {
            logAppender = new ListAppender<>();
            logAppender.start();
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthRabbitConfig.class))
                    .addAppender(logAppender);
        }

        @AfterEach
        void detachLogAppender() {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthRabbitConfig.class))
                    .detachAppender(logAppender);
        }

        @Test
        void logsWhenAPublishedMessageIsUnroutable() {
            rabbitTemplate.convertAndSend("no-such-routing-key", "payload");

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () ->
                                    assertThat(logAppender.list)
                                            .anyMatch(
                                                    event ->
                                                            event.getFormattedMessage()
                                                                    .contains("message_returned")));
        }
    }
}
