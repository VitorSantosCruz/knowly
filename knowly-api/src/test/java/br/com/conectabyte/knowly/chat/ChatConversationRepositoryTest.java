package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unified entity search (2026-08-10 amendment), REQ-19: {@link
 * ChatConversationRepository#findDiscoverableByTitle}'s explicit tenant predicate.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ChatConversationRepositoryTest {

    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    @Test
    void findDiscoverableByTitleOnlyMatchesGroupsWithinTheGivenTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));

        ChatConversation groupA =
                new ChatConversation(ChatConversationKind.PEER_GROUP, tenantA, "Book Club", null);
        groupA.setVisibility(ChatGroupVisibility.PUBLIC);
        chatConversationRepository.saveAndFlush(groupA);

        ChatConversation groupB =
                new ChatConversation(ChatConversationKind.PEER_GROUP, tenantB, "Book Club", null);
        groupB.setVisibility(ChatGroupVisibility.PUBLIC);
        chatConversationRepository.saveAndFlush(groupB);

        var page =
                chatConversationRepository.findDiscoverableByTitle(
                        "%book%", tenantA.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(ChatConversation::getId)
                .containsExactly(groupA.getId());
    }
}
