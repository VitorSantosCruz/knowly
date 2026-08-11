package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    // --- RAG conversation turn-content search (2026-08-11 amendment), REQ-27-REQ-33 ---

    @Test
    void searchByConversationOwnerAndContentReturnsOnlyTheOwnersOwnMessagesInTheActiveTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User owner = userRepository.saveAndFlush(new User("rag-content-owner@example.com"));
        User otherOwner = userRepository.saveAndFlush(new User("rag-content-other@example.com"));

        Conversation ownTenantA =
                conversationRepository.saveAndFlush(new Conversation(tenantA, owner, "Chat"));
        Conversation ownTenantB =
                conversationRepository.saveAndFlush(new Conversation(tenantB, owner, "Chat"));
        Conversation otherOwnerTenantA =
                conversationRepository.saveAndFlush(new Conversation(tenantA, otherOwner, "Chat"));
        Conversation softDeletedTenantA =
                conversationRepository.saveAndFlush(new Conversation(tenantA, owner, "Deleted"));

        Message userMessage =
                messageRepository.saveAndFlush(
                        new Message(ownTenantA, MessageRole.USER, "What time is the meeting?"));
        Message assistantMessage =
                messageRepository.saveAndFlush(
                        new Message(
                                ownTenantA,
                                MessageRole.ASSISTANT,
                                "The meeting is at 3pm about the meeting room."));
        messageRepository.saveAndFlush(
                new Message(ownTenantB, MessageRole.USER, "Different tenant meeting question"));
        messageRepository.saveAndFlush(
                new Message(otherOwnerTenantA, MessageRole.USER, "Another owner's meeting note"));
        messageRepository.saveAndFlush(
                new Message(softDeletedTenantA, MessageRole.USER, "Soft deleted meeting note"));

        softDeletedTenantA.setDeletedAt(java.time.Instant.now());
        conversationRepository.saveAndFlush(softDeletedTenantA);

        var page =
                messageRepository.searchByConversationOwnerAndContent(
                        owner.getId(), tenantA.getId(), "%meeting%", PageRequest.of(0, 10));

        List<Message> content = page.getContent();

        assertThat(content)
                .extracting(Message::getId)
                .containsExactly(assistantMessage.getId(), userMessage.getId());
        assertThat(content)
                .extracting(Message::getRole)
                .containsExactly(MessageRole.ASSISTANT, MessageRole.USER);
    }
}
