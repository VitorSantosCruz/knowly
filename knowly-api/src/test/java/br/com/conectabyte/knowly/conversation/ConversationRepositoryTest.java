package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.icon.IconKey;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import({TestcontainersConfiguration.class, ConversationRepositoryTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class ConversationRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private ConversationQueryService conversationQueryService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    @Test
    void persistsAndRoundTripsAConversationAndItsMessages() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        User owner = userRepository.saveAndFlush(new User("owner-1@example.com"));
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "What is in article X?"));
        messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.ASSISTANT, "Here is what I found."));

        List<Message> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        assertThat(messages)
                .extracting(Message::getRole)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
    }

    @Test
    void theTenantFilterIsolatesAConversationFromOtherTenantsQueries() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User owner = userRepository.saveAndFlush(new User("owner-2@example.com"));
        conversationRepository.saveAndFlush(new Conversation(tenantA, owner));
        conversationRepository.saveAndFlush(new Conversation(tenantB, owner));

        tenantContext.setActiveTenantId(tenantA.getId());

        assertThat(conversationQueryService.findAll()).hasSize(1);
    }

    @Test
    void anOwnerNeverSeesAnotherUsersConversationEvenInTheSameTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        User userA = userRepository.saveAndFlush(new User("a@example.com"));
        User userB = userRepository.saveAndFlush(new User("b@example.com"));
        Conversation conversationB =
                conversationRepository.saveAndFlush(new Conversation(tenant, userB));

        assertThat(conversationRepository.findByOwnerIdOrderByCreatedAtDesc(userA.getId()))
                .isEmpty();
        assertThat(conversationRepository.findByIdAndOwnerId(conversationB.getId(), userA.getId()))
                .isEmpty();
        assertThat(conversationRepository.findByIdAndOwnerId(conversationB.getId(), userB.getId()))
                .isPresent();
    }

    @Test
    void conversationIconPersistsAndRoundTripsAsAnIconKey() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Icon Tenant"));
        User owner = userRepository.saveAndFlush(new User("icon-owner@example.com"));
        Conversation saved =
                conversationRepository.saveAndFlush(
                        new Conversation(tenant, owner, "Titled", IconKey.SPARKLES));

        Conversation reloaded = conversationRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getIcon()).isEqualTo(IconKey.SPARKLES);
    }

    static class ConversationQueryService {
        private final ConversationRepository conversationRepository;

        ConversationQueryService(ConversationRepository conversationRepository) {
            this.conversationRepository = conversationRepository;
        }

        @Transactional(readOnly = true)
        List<Conversation> findAll() {
            return conversationRepository.findAll();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ConversationQueryService conversationQueryService(
                ConversationRepository conversationRepository) {
            return new ConversationQueryService(conversationRepository);
        }
    }
}
