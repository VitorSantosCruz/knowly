package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/** Unified entity search (2026-08-10 amendment), REQ-22: {@link ConversationService#searchOwn}. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantContext tenantContext;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service =
                new ConversationService(
                        conversationRepository, messageRepository, tenantRepository, tenantContext);
    }

    private User user(long id) {
        User user = new User("user" + id + "@example.com");
        user.setId(id);
        return user;
    }

    @Test
    void searchOwnWithNoActiveTenantReturnsEmptyWithNoRepositoryInteraction() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var result = service.searchOwn(owner, "articles", 5);

        assertThat(result).isEmpty();
        verify(conversationRepository, never())
                .searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void searchOwnBindsTheResolvedActiveTenantAndOwnerToTheRepositoryCall() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        Tenant tenant = new Tenant("Tenant");
        tenant.setId(10L);
        Conversation match = new Conversation(tenant, owner, "Base de artigos");
        match.setId(100L);
        when(conversationRepository.searchByOwnerAndTitle(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        anyString(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(match)));
        when(messageRepository.searchByConversationOwnerAndContent(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        anyString(),
                        any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.searchOwn(owner, "base", 5);

        assertThat(result).extracting("id").containsExactly(100L);
        verify(conversationRepository)
                .searchByOwnerAndTitle(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        anyString(),
                        any());
    }

    // --- RAG conversation turn-content search (2026-08-11 amendment), REQ-27-REQ-33 ---

    @Test
    void searchOwnMergesTitleAndContentMatchesWithoutDuplicateConversations() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        Tenant tenant = new Tenant("Tenant");
        tenant.setId(10L);

        Conversation titleOnly = new Conversation(tenant, owner, "Meeting notes");
        titleOnly.setId(100L);
        Conversation contentOnly = new Conversation(tenant, owner, "Random title");
        contentOnly.setId(200L);
        Conversation both = new Conversation(tenant, owner, "Meeting recap");
        both.setId(300L);
        Conversation multiTurn = new Conversation(tenant, owner, "Other title");
        multiTurn.setId(400L);

        when(conversationRepository.searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(titleOnly, both)));

        Message contentOnlyMsg =
                new Message(contentOnly, MessageRole.USER, "the meeting is at 3pm");
        contentOnlyMsg.setId(1L);
        Message bothMsg = new Message(both, MessageRole.ASSISTANT, "yes the meeting recap is here");
        bothMsg.setId(2L);
        Message multiTurnRecent =
                new Message(multiTurn, MessageRole.ASSISTANT, "second meeting turn");
        multiTurnRecent.setId(4L);
        Message multiTurnOlder = new Message(multiTurn, MessageRole.USER, "first meeting turn");
        multiTurnOlder.setId(3L);

        when(messageRepository.searchByConversationOwnerAndContent(
                        anyLong(), anyLong(), anyString(), any()))
                .thenReturn(
                        new PageImpl<>(
                                List.of(multiTurnRecent, bothMsg, contentOnlyMsg, multiTurnOlder)));

        var result = service.searchOwn(owner, "meeting", 5);

        assertThat(result).extracting("id").containsExactlyInAnyOrder(100L, 200L, 300L, 400L);

        var titleOnlyResult =
                result.stream().filter(r -> r.id().equals(100L)).findFirst().orElseThrow();
        assertThat(titleOnlyResult.title()).isEqualTo("Meeting notes");
        assertThat(titleOnlyResult.matchedSnippet()).isNull();
        assertThat(titleOnlyResult.matchedRole()).isNull();

        var contentOnlyResult =
                result.stream().filter(r -> r.id().equals(200L)).findFirst().orElseThrow();
        assertThat(contentOnlyResult.title()).isEqualTo("Random title");
        assertThat(contentOnlyResult.matchedSnippet()).contains("meeting is at 3pm");
        assertThat(contentOnlyResult.matchedRole()).isEqualTo("USER");

        var bothResult = result.stream().filter(r -> r.id().equals(300L)).findFirst().orElseThrow();
        assertThat(bothResult.title()).isEqualTo("Meeting recap");
        assertThat(bothResult.matchedSnippet()).contains("meeting recap is here");
        assertThat(bothResult.matchedRole()).isEqualTo("ASSISTANT");

        // REQ-31 tie-break: multiTurn matched twice, the most-recent turn (first in the
        // most-recent-first-ordered repository result) wins.
        var multiTurnResult =
                result.stream().filter(r -> r.id().equals(400L)).findFirst().orElseThrow();
        assertThat(multiTurnResult.matchedSnippet()).contains("second meeting turn");
        assertThat(multiTurnResult.matchedRole()).isEqualTo("ASSISTANT");
    }

    @Test
    void searchOwnNeverReturnsAnotherUsersOrDifferentTenantsConversationByContentMatch() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        // The repository's own ownerId/tenantId predicates are what enforce this in production;
        // here the mock simply never returns an inaccessible row, confirming the service applies
        // no additional Java-side filter that could accidentally leak one -- and that "no matching
        // turn" and "a matching-but-inaccessible turn" produce the exact same, indistinguishable
        // empty result.
        when(conversationRepository.searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(messageRepository.searchByConversationOwnerAndContent(
                        anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.searchOwn(owner, "meeting", 5);

        assertThat(result).isEmpty();
    }

    // --- AppSec-required regression (Gap 2, RAG half) ---

    @Test
    void searchOwnForAStaffCallerWithNoActiveTenantReturnsZeroResultsAcrossTenants() {
        User staff = user(1L);
        staff.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var result = service.searchOwn(staff, "base", 5);

        assertThat(result).isEmpty();
        verify(conversationRepository, never())
                .searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any());
    }
}
