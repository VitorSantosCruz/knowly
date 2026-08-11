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

    // --- bugfix (prefix-match type-ahead): "men" (substring of "mensagem") must match ---
    @Test
    void shortPrefixOfALongerWordMatchesViaPrefixTsQuery() {
        Tenant t = tenant("Prefix Bugfix Co");
        User caller = user("prefix-bugfix@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Prefix Bugfix Group");
        participate(conversation, caller);
        message(conversation, caller, "enviei uma mensagem importante ontem");

        List<ChatMessageSearchRow> results =
                chatMessageSearchRepository.searchScopedPt(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "men",
                        null,
                        null,
                        null,
                        null,
                        null,
                        30);

        assertThat(results).hasSize(1);
    }

    // --- bugfix regression: the previously-working longer-substring/full-word matches still work
    // ---
    @Test
    void longerSubstringAndFullWordStillMatchAfterPrefixFix() {
        Tenant t = tenant("Prefix Regression Co");
        User caller = user("prefix-regression@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Prefix Regression Group");
        participate(conversation, caller);
        message(conversation, caller, "enviei uma mensagem importante ontem");

        List<ChatMessageSearchRow> byLongerSubstring =
                chatMessageSearchRepository.searchScopedPt(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "mensag",
                        null,
                        null,
                        null,
                        null,
                        null,
                        30);
        assertThat(byLongerSubstring).hasSize(1);

        List<ChatMessageSearchRow> byFullWord =
                chatMessageSearchRepository.searchScopedPt(
                        caller.getId(),
                        t.getId(),
                        new Long[0],
                        "mensagem",
                        null,
                        null,
                        null,
                        null,
                        null,
                        30);
        assertThat(byFullWord).hasSize(1);
    }

    // --- bugfix regression: multi-word AND semantics preserved with the last term prefix-matched
    // ---
    @Test
    void multiWordQueryStillRequiresAllTermsWithOnlyLastTermPrefixMatched() {
        Tenant t = tenant("Prefix MultiWord Co");
        User caller = user("prefix-multiword@example.com");
        ChatConversation both = conversation(ChatConversationKind.PEER_GROUP, t, "Both Group");
        participate(both, caller);
        message(both, caller, "important message about the budget");

        ChatConversation onlyOne =
                conversation(ChatConversationKind.PEER_GROUP, t, "Only One Group");
        participate(onlyOne, caller);
        message(onlyOne, caller, "important announcement only");

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "important mess", null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConversationId()).isEqualTo(both.getId());
    }

    // --- bugfix (AppSec): no tsquery-operator injection via crafted user input ---
    @Test
    void craftedInputCannotInjectATsQueryOrOperatorAcrossUnrelatedMessages() {
        Tenant t = tenant("Prefix Injection Co");
        User caller = user("prefix-injection@example.com");
        ChatConversation conversationA =
                conversation(ChatConversationKind.PEER_GROUP, t, "Injection Group A");
        participate(conversationA, caller);
        message(conversationA, caller, "zzzalpha only");

        ChatConversation conversationB =
                conversation(ChatConversationKind.PEER_GROUP, t, "Injection Group B");
        participate(conversationB, caller);
        message(conversationB, caller, "zzzbeta only");

        // If the raw "|" were honored as a tsquery OR operator instead of being stripped/escaped,
        // this would match both conversations; it must instead be treated as AND (or reject/no-op),
        // matching neither since no single message contains both terms.
        List<ChatMessageSearchRow> results =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "zzzalpha | zzzbeta",
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThat(results).isEmpty();
    }

    @Test
    void craftedInputWithTsQuerySpecialCharactersDoesNotThrowAndStillMatchesLiterally() {
        Tenant t = tenant("Prefix Injection Chars Co");
        User caller = user("prefix-injection-chars@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Injection Chars Group");
        participate(conversation, caller);
        message(conversation, caller, "zzzgamma safe message");

        List<ChatMessageSearchRow> results =
                searchEn(
                        caller.getId(),
                        t.getId(),
                        "zzzgamma&()!:*'\"\\",
                        null,
                        null,
                        null,
                        null,
                        null);

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

    // --- tasks 169/170 (REQ-44/45/46): isParticipant/visibility projected columns ---

    @Test
    void participantsOwnPeerGroupMessageReportsIsParticipantTrue() {
        Tenant t = tenant("Participancy PeerGroup Co");
        User caller = user("participancy-peergroup@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Participancy Group");
        participate(conversation, caller);
        message(conversation, caller, "widgetflurry participant message");

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "widgetflurry", null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsParticipant()).isTrue();
    }

    @Test
    void discoverableGroupReachedOnlyViaCarveOutReportsIsParticipantFalseWithRealVisibility() {
        Tenant t = tenant("Participancy Discoverable Co");
        User caller = user("participancy-discoverable@example.com");
        User other = user("participancy-discoverable-other@example.com");
        ChatConversation publicGroup =
                conversation(ChatConversationKind.PEER_GROUP, t, "Participancy Public Group");
        publicGroup.setVisibility(br.com.conectabyte.knowly.chat.ChatGroupVisibility.PUBLIC);
        publicGroup = chatConversationRepository.saveAndFlush(publicGroup);
        participate(publicGroup, other);
        message(publicGroup, other, "widgetflurry discoverable message");

        List<ChatMessageSearchRow> results =
                chatMessageSearchRepository.searchScopedEn(
                        caller.getId(),
                        t.getId(),
                        new Long[] {publicGroup.getId()},
                        "widgetflurry",
                        null,
                        null,
                        null,
                        null,
                        null,
                        30);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsParticipant()).isFalse();
        assertThat(results.get(0).getVisibility())
                .isEqualTo(br.com.conectabyte.knowly.chat.ChatGroupVisibility.PUBLIC);
    }

    @Test
    void peerDirectResultAlwaysReportsIsParticipantTrueAndNullVisibility() {
        Tenant t = tenant("Participancy PeerDirect Co");
        User caller = user("participancy-peerdirect@example.com");
        ChatConversation conversation = conversation(ChatConversationKind.PEER_DIRECT, t, null);
        participate(conversation, caller);
        message(conversation, caller, "widgetflurry direct message");

        List<ChatMessageSearchRow> results =
                searchEn(caller.getId(), t.getId(), "widgetflurry", null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsParticipant()).isTrue();
        assertThat(results.get(0).getVisibility()).isNull();
    }

    @Test
    void
            adminUnrestrictedFragmentRowReportsGroundTruthIsParticipantFalseWhenCallerNotAParticipant() {
        Tenant t = tenant("Participancy Admin Unrestricted Co");
        User admin = user("participancy-admin-unrestricted@example.com");
        User other = user("participancy-admin-unrestricted-other@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, t, "Participancy Admin Group");
        participate(conversation, other);
        message(conversation, other, "widgetflurry admin unrestricted message");

        List<ChatMessageSearchRow> results =
                chatMessageSearchRepository.searchTenantUnrestrictedEn(
                        t.getId(), "widgetflurry", null, null, null, null, null, 30, admin.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsParticipant()).isFalse();
    }

    @Test
    void staffScopeAdminUnrestrictedFragmentRowReportsGroundTruthIsParticipantFalse() {
        User admin = user("participancy-staff-admin-unrestricted@example.com");
        User other = user("participancy-staff-admin-unrestricted-other@example.com");
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, null, "Participancy Staff Group");
        participate(conversation, other);
        message(conversation, other, "widgetflurry staff unrestricted message");

        List<ChatMessageSearchRow> results =
                chatMessageSearchRepository.searchStaffScopeUnrestrictedEn(
                        "widgetflurry", null, null, null, null, null, 30, admin.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsParticipant()).isFalse();
    }
}
