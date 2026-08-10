package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.ChatMessageSearchRepository.ChatMessageSearchRow;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Backs TASKS.md items 13-28: {@link ChatMessageSearchRepository}'s native-query scoping/ filtering
 * behavior, exercised directly (no HTTP layer) since {@code @Filter} does not apply here (see that
 * repository's Javadoc).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ChatMessageSearchRepositoryTest {

    @Autowired private ChatMessageSearchRepository chatMessageSearchRepository;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private Tenant tenant(String name) {
        return tenantRepository.saveAndFlush(new Tenant(name));
    }

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private ChatConversation conversation(ChatConversationKind kind, Tenant tenant, String title) {
        return chatConversationRepository.saveAndFlush(
                new ChatConversation(kind, tenant, title, null));
    }

    private void participate(ChatConversation conversation, User user) {
        chatParticipantRepository.saveAndFlush(new ChatParticipant(conversation, user));
    }

    private ChatMessage message(ChatConversation conversation, User sender, String content) {
        return chatMessageRepository.saveAndFlush(new ChatMessage(conversation, sender, content));
    }

    private List<ChatMessageSearchRow> searchEn(
            Long callerId,
            Long tenantId,
            String q,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            Long cursor) {
        return chatMessageSearchRepository.searchScopedEn(
                callerId,
                tenantId,
                new Long[0],
                q,
                senderId,
                conversationId,
                dateFrom,
                dateTo,
                cursor,
                30);
    }

    // --- task 13/14: tenant scoping ---
    @Test
    void searchOnlyReturnsMatchesFromTheActiveTenant() {
        Tenant tenantOne = tenant("Search Tenant 1");
        Tenant tenantTwo = tenant("Search Tenant 2");
        User caller = user("search-two-tenant@example.com");

        ChatConversation conversationOne =
                conversation(ChatConversationKind.PEER_GROUP, tenantOne, "Group One");
        participate(conversationOne, caller);
        message(conversationOne, caller, "unicornshibboleth alpha message");

        ChatConversation conversationTwo =
                conversation(ChatConversationKind.PEER_GROUP, tenantTwo, "Group Two");
        participate(conversationTwo, caller);
        message(conversationTwo, caller, "unicornshibboleth beta message");

        List<ChatMessageSearchRow> results =
                searchEn(
                        caller.getId(),
                        tenantOne.getId(),
                        "unicornshibboleth",
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConversationId()).isEqualTo(conversationOne.getId());
    }

    // --- task 15/16: former participant excluded, current participant of another conversation OK
    // ---
    @Test
    void formerParticipantNoLongerMatchesWhileCurrentParticipantOfAnotherConversationStillDoes() {
        Tenant t = tenant("Former Participant Co");
        User caller = user("former-participant@example.com");

        ChatConversation left = conversation(ChatConversationKind.PEER_GROUP, t, "Left Group");
        ChatParticipant leftParticipation = new ChatParticipant(left, caller);
        leftParticipation = chatParticipantRepository.saveAndFlush(leftParticipation);
        message(left, caller, "gargantuanplinth departed message");
        leftParticipation.setDeletedAt(Instant.now());
        chatParticipantRepository.saveAndFlush(leftParticipation);

        ChatConversation current =
                conversation(ChatConversationKind.PEER_GROUP, t, "Current Group");
        participate(current, caller);
        message(current, caller, "gargantuanplinth current message");

        List<ChatMessageSearchRow> results =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "gargantuanplinth",
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConversationId()).isEqualTo(current.getId());
    }

    // --- task 17/18: SUPPORT conversations excluded ---
    @Test
    void supportConversationExcludedEvenWithMatchingContentAndParticipantRow() {
        Tenant t = tenant("Support Excluded Co");
        User caller = user("support-excluded@example.com");

        ChatConversation support = conversation(ChatConversationKind.SUPPORT, t, "Support Channel");
        participate(support, caller);
        message(support, caller, "flibbertigibbet support message");

        List<ChatMessageSearchRow> results =
                searchEn(
                        caller.getId(), t.getId(), "flibbertigibbet", null, null, null, null, null);

        assertThat(results).isEmpty();
    }

    // --- task 19/20: archived/soft-deleted conversations excluded ---
    @Test
    void archivedAndSoftDeletedConversationsAreExcludedDespiteStillHoldingAParticipantRow() {
        Tenant t = tenant("Archived Deleted Co");
        User caller = user("archived-deleted@example.com");

        ChatConversation archived =
                conversation(ChatConversationKind.PEER_GROUP, t, "Archived Group");
        participate(archived, caller);
        message(archived, caller, "wobblesnatch archived message");
        archived.setArchivedAt(Instant.now());
        chatConversationRepository.saveAndFlush(archived);

        ChatConversation deleted =
                conversation(ChatConversationKind.PEER_GROUP, t, "Deleted Group");
        participate(deleted, caller);
        message(deleted, caller, "wobblesnatch deleted message");
        deleted.setDeletedAt(Instant.now());
        chatConversationRepository.saveAndFlush(deleted);

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "wobblesnatch", null, null, null, null, null);

        assertThat(results).isEmpty();
    }

    // --- task 21/22: optional filters ---
    @Test
    void optionalFiltersNarrowResultsIndividuallyAndCombined() {
        Tenant t = tenant("Optional Filters Co");
        User caller = user("optional-filters-caller@example.com");
        User other = user("optional-filters-other@example.com");

        ChatConversation conversationOne =
                conversation(ChatConversationKind.PEER_GROUP, t, "Filters Group One");
        participate(conversationOne, caller);
        participate(conversationOne, other);
        ChatConversation conversationTwo =
                conversation(ChatConversationKind.PEER_GROUP, t, "Filters Group Two");
        participate(conversationTwo, caller);

        message(conversationOne, caller, "quixoticzephyr from caller in one");
        ChatMessage otherMessage =
                message(conversationOne, other, "quixoticzephyr from other in one");
        message(conversationTwo, caller, "quixoticzephyr from caller in two");

        // senderId only
        List<ChatMessageSearchRow> bySender =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "quixoticzephyr",
                        other.getId(),
                        null,
                        null,
                        null,
                        null);
        assertThat(bySender).hasSize(1);
        assertThat(bySender.get(0).getSenderUserId()).isEqualTo(other.getId());

        // conversationId only
        List<ChatMessageSearchRow> byConversation =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "quixoticzephyr",
                        null,
                        conversationTwo.getId(),
                        null,
                        null,
                        null);
        assertThat(byConversation).hasSize(1);
        assertThat(byConversation.get(0).getConversationId()).isEqualTo(conversationTwo.getId());

        // combined senderId + conversationId
        List<ChatMessageSearchRow> combined =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "quixoticzephyr",
                        other.getId(),
                        conversationOne.getId(),
                        null,
                        null,
                        null);
        assertThat(combined).hasSize(1);
        assertThat(combined.get(0).getId()).isEqualTo(otherMessage.getId());

        // dateFrom/dateTo excludes everything when set in the future
        List<ChatMessageSearchRow> futureRange =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "quixoticzephyr",
                        null,
                        null,
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        null,
                        null);
        assertThat(futureRange).isEmpty();

        List<ChatMessageSearchRow> pastRangeIncludesAll =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "quixoticzephyr",
                        null,
                        null,
                        Instant.now().minus(1, ChronoUnit.DAYS),
                        Instant.now().plus(1, ChronoUnit.DAYS),
                        null);
        assertThat(pastRangeIncludesAll).hasSize(3);
    }

    // --- task 23/24: staff-only (NULL tenant_id) conversations never surface for a tenant-scoped
    // caller ---
    @Test
    void nullTenantConversationNeverSurfacesForATenantScopedCaller() {
        Tenant t = tenant("Null Tenant Exclusion Co");
        User caller = user("null-tenant-exclusion@example.com");

        ChatConversation staffOnly =
                conversation(ChatConversationKind.PEER_GROUP, null, "Staff Only Group");
        participate(staffOnly, caller);
        message(staffOnly, caller, "hobnobbington staffonly message");

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "hobnobbington", null, null, null, null, null);

        assertThat(results).isEmpty();
    }

    // --- task 25/26: locale-aware matching ---
    @Test
    void portugueseSearchMatchesAConjugatedFormViaSearchPt() {
        Tenant t = tenant("Locale Pt Co");
        User caller = user("locale-pt@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Locale Pt Group");
        participate(conversation, caller);
        message(conversation, caller, "os gatos correm rapido no jardim");

        List<ChatMessageSearchRow> results =
                chatMessageSearchRepository.searchScopedPt(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "gato",
                        null,
                        null,
                        null,
                        null,
                        null,
                        30);

        assertThat(results).hasSize(1);
    }

    @Test
    void englishSearchMatchesAPluralFormViaSearchEn() {
        Tenant t = tenant("Locale En Co");
        User caller = user("locale-en@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Locale En Group");
        participate(conversation, caller);
        message(conversation, caller, "we have several meetings scheduled");

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "meeting", null, null, null, null, null);

        assertThat(results).hasSize(1);
    }

    // --- task 27/28: cursor pagination, no overlap/no gaps, most-recent-first ---
    @Test
    void cursorPaginationHasNoOverlapOrGapsAndOrdersMostRecentFirst() {
        Tenant t = tenant("Cursor Paging Co");
        User caller = user("cursor-paging@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Cursor Paging Group");
        participate(conversation, caller);

        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(message(conversation, caller, "snorklewhiff message " + i).getId());
        }

        List<ChatMessageSearchRow> page1 =
                chatMessageSearchRepository.searchScopedEn(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "snorklewhiff",
                        null,
                        null,
                        null,
                        null,
                        null,
                        3);
        assertThat(page1).hasSize(3);
        assertThat(page1)
                .extracting(ChatMessageSearchRow::getId)
                .isSortedAccordingTo((a, b) -> b.compareTo(a));

        Long cursor = page1.get(page1.size() - 1).getId();
        List<ChatMessageSearchRow> page2 =
                chatMessageSearchRepository.searchScopedEn(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "snorklewhiff",
                        null,
                        null,
                        null,
                        null,
                        cursor,
                        3);
        assertThat(page2).hasSize(2);

        List<Long> combinedIds =
                java.util.stream.Stream.concat(
                                page1.stream().map(ChatMessageSearchRow::getId),
                                page2.stream().map(ChatMessageSearchRow::getId))
                        .toList();
        assertThat(combinedIds).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(new java.util.HashSet<>(combinedIds)).hasSize(5);
    }
}
