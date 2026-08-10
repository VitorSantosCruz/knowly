package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.dto.ChatEntitySearchResponseDto;
import br.com.conectabyte.knowly.chat.dto.ChatEntitySearchResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatGroupSearchResultDto;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchExpandParamException;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.ConversationService;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatEntitySearchServiceTest {

    @Mock private ChatEligibilityService chatEligibilityService;
    @Mock private ChatConversationService chatConversationService;
    @Mock private SupportTicketService supportTicketService;
    @Mock private ConversationService conversationService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    @Mock private TenantContext tenantContext;

    private ChatEntitySearchService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatEntitySearchService(
                        chatEligibilityService,
                        chatConversationService,
                        supportTicketService,
                        conversationService,
                        conversationRepository,
                        chatMessageSearchLocaleResolver,
                        tenantContext);
    }

    private User user(long id) {
        User user = new User("user" + id + "@example.com");
        user.setId(id);
        return user;
    }

    @Test
    void eachOfTheFourSectionsCallsItsUnderlyingServiceWithTheCallersActualIdentity() {
        User actor = user(1L);
        when(chatEligibilityService.searchEligibleDirectCandidates(actor, "book", 6))
                .thenReturn(List.of());
        when(chatConversationService.searchDiscoverableGroups(actor, "book", 6))
                .thenReturn(List.of());
        when(conversationService.searchOwn(actor, "book", 6)).thenReturn(List.of());
        when(chatMessageSearchLocaleResolver.resolve(any())).thenReturn(ChatSearchLocale.EN);

        var response =
                (ChatEntitySearchResponseDto) service.search(actor, "book", null, null, "en");

        assertThat(response.people().results()).isEmpty();
        verify(chatEligibilityService).searchEligibleDirectCandidates(actor, "book", 6);
        verify(chatConversationService).searchDiscoverableGroups(actor, "book", 6);
        verify(conversationService).searchOwn(actor, "book", 6);
    }

    @Test
    void oneFailingSectionDoesNotBlockTheOtherThreeAndDegradesToEmptyNotA500() {
        User actor = user(1L);
        when(chatEligibilityService.searchEligibleDirectCandidates(any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("boom"));
        when(chatConversationService.searchDiscoverableGroups(any(), anyString(), anyInt()))
                .thenReturn(
                        List.of(
                                new ChatGroupSearchResultDto(
                                        1L, "Group", false, ChatGroupVisibility.PUBLIC)));
        when(conversationService.searchOwn(any(), anyString(), anyInt())).thenReturn(List.of());
        when(chatMessageSearchLocaleResolver.resolve(any())).thenReturn(ChatSearchLocale.EN);

        var response = (ChatEntitySearchResponseDto) service.search(actor, "q", null, null, "en");

        assertThat(response.people().results()).isEmpty();
        assertThat(response.people().hasMore()).isFalse();
        assertThat(response.groups().results()).hasSize(1);
    }

    @Test
    void typeAndOffsetMissingOneOfThePairThrowsBeforeAnyRepositoryCall() {
        User actor = user(1L);

        assertThatThrownBy(() -> service.search(actor, "q", "people", null, "en"))
                .isInstanceOf(ChatInvalidSearchExpandParamException.class);
        assertThatThrownBy(() -> service.search(actor, "q", null, 0, "en"))
                .isInstanceOf(ChatInvalidSearchExpandParamException.class);

        org.mockito.Mockito.verifyNoInteractions(chatEligibilityService, chatConversationService);
    }

    @Test
    void anOutOfEnumTypeThrows() {
        User actor = user(1L);

        assertThatThrownBy(() -> service.search(actor, "q", "bogus", 0, "en"))
                .isInstanceOf(ChatInvalidSearchExpandParamException.class);
    }

    @Test
    void blankQReturnsRecentPlacesMergingChatAndRagConversations() {
        User actor = user(1L);
        when(chatConversationService.listConversations(actor))
                .thenReturn(
                        List.of(
                                new br.com.conectabyte.knowly.chat.dto.ChatConversationSummaryDto(
                                        100L,
                                        ChatConversationKind.PEER_GROUP,
                                        10L,
                                        "Chat Group",
                                        null,
                                        List.of(1L),
                                        java.time.Instant.parse("2026-08-01T00:00:00Z"))));
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var result = (ChatEntitySearchResultDto) service.search(actor, null, null, null, "en");

        assertThat(result.recentPlaces()).extracting("conversationId").containsExactly(100L);
    }

    // --- Support-label match (REQ-21), locale-aware ---

    @Test
    void supportLabelMatchesCaseInsensitivelyForBothEnAndPtBrLocales() {
        User actor = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        when(chatEligibilityService.searchEligibleDirectCandidates(any(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(chatConversationService.searchDiscoverableGroups(any(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(conversationService.searchOwn(any(), anyString(), anyInt())).thenReturn(List.of());
        when(chatMessageSearchLocaleResolver.resolve("en")).thenReturn(ChatSearchLocale.EN);
        when(supportTicketService.findOwnOrClaimableChannel(actor, 10L))
                .thenReturn(Optional.of(channelWithId(500L)));

        var response =
                (ChatEntitySearchResponseDto) service.search(actor, "SUPPORT", null, null, "en");

        assertThat(response.support().channelId()).isEqualTo(500L);
    }

    @Test
    void supportLabelMatchesPtBrLocale() {
        User actor = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        when(chatEligibilityService.searchEligibleDirectCandidates(any(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(chatConversationService.searchDiscoverableGroups(any(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(conversationService.searchOwn(any(), anyString(), anyInt())).thenReturn(List.of());
        when(chatMessageSearchLocaleResolver.resolve("pt-BR")).thenReturn(ChatSearchLocale.PT);
        when(supportTicketService.findOwnOrClaimableChannel(actor, 10L))
                .thenReturn(Optional.of(channelWithId(500L)));

        var response =
                (ChatEntitySearchResponseDto) service.search(actor, "suporte", null, null, "pt-BR");

        assertThat(response.support().channelId()).isEqualTo(500L);
    }

    private ChatConversation channelWithId(long id) {
        ChatConversation channel = new ChatConversation();
        channel.setId(id);
        return channel;
    }
}
