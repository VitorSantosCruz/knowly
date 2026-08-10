package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.dto.ChatEntitySearchResponseDto;
import br.com.conectabyte.knowly.chat.dto.ChatEntitySearchResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatEntitySearchSectionDto;
import br.com.conectabyte.knowly.chat.dto.ChatGroupSearchResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatPersonSearchResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatRagConversationSearchResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatRecentPlaceDto;
import br.com.conectabyte.knowly.chat.dto.ChatSupportSearchResultDto;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchExpandParamException;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.ConversationService;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified entity search (2026-08-10 amendment), REQ-16 through REQ-26: orchestrates the four
 * matched-{@code q} sections (people/groups/Support/RAG) and the blank-{@code q} "recent places"
 * case backing {@code GET /api/chat/search}.
 *
 * <p><b>No oversight bypass of any kind (REQ-18):</b> no method in this service ever reads {@code
 * tenantContext.isStaff()}/{@code isStaffAdmin()} to branch into a wider result set -- each
 * delegated call resolves its own tenant scoping independently and fails closed, exactly mirroring
 * the shipped message-search service's posture (REQ-5).
 */
@Service
public class ChatEntitySearchService {

    private static final Logger log = LoggerFactory.getLogger(ChatEntitySearchService.class);
    private static final int SECTION_LIMIT = 5;
    private static final int RECENT_PLACES_LIMIT = 10;

    private final ChatEligibilityService chatEligibilityService;
    private final ChatConversationService chatConversationService;
    private final SupportTicketService supportTicketService;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    private final TenantContext tenantContext;

    public ChatEntitySearchService(
            ChatEligibilityService chatEligibilityService,
            ChatConversationService chatConversationService,
            SupportTicketService supportTicketService,
            ConversationService conversationService,
            ConversationRepository conversationRepository,
            ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver,
            TenantContext tenantContext) {
        this.chatEligibilityService = chatEligibilityService;
        this.chatConversationService = chatConversationService;
        this.supportTicketService = supportTicketService;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.chatMessageSearchLocaleResolver = chatMessageSearchLocaleResolver;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public Object search(
            User actor, String q, String type, Integer offset, String acceptLanguageHeader) {
        if ((type != null) != (offset != null)) {
            throw new ChatInvalidSearchExpandParamException();
        }

        if (type != null) {
            return expandSection(actor, type, q, offset);
        }

        if (q == null || q.isBlank()) {
            return recentPlaces(actor);
        }

        return fullResponse(actor, q, acceptLanguageHeader);
    }

    private ChatEntitySearchSectionDto<?> expandSection(
            User actor, String type, String q, int offset) {
        int fetchLimit = offset + SECTION_LIMIT + 1;
        List<?> all =
                switch (type) {
                    case "people" -> peopleMatches(actor, q, fetchLimit);
                    case "groups" -> groupMatches(actor, q, fetchLimit);
                    case "rag" -> ragMatches(actor, q, fetchLimit);
                    default -> throw new ChatInvalidSearchExpandParamException();
                };

        return page(all, offset);
    }

    private <T> ChatEntitySearchSectionDto<T> page(List<T> all, int offset) {
        if (offset >= all.size()) {
            return new ChatEntitySearchSectionDto<>(List.of(), false);
        }

        int end = Math.min(offset + SECTION_LIMIT, all.size());
        boolean hasMore = all.size() > end;

        return new ChatEntitySearchSectionDto<>(all.subList(offset, end), hasMore);
    }

    private ChatEntitySearchResponseDto fullResponse(
            User actor, String q, String acceptLanguageHeader) {
        ChatEntitySearchSectionDto<ChatPersonSearchResultDto> people =
                section(() -> peopleMatches(actor, q, SECTION_LIMIT + 1), "people", actor);
        ChatEntitySearchSectionDto<ChatGroupSearchResultDto> groups =
                section(() -> groupMatches(actor, q, SECTION_LIMIT + 1), "groups", actor);
        ChatSupportSearchResultDto support = supportMatch(actor, q, acceptLanguageHeader);
        ChatEntitySearchSectionDto<ChatRagConversationSearchResultDto> rag =
                section(() -> ragMatches(actor, q, SECTION_LIMIT + 1), "rag", actor);

        log.info(
                "chat.entity.search actorId={} hasQuery={} peopleCount={} groupsCount={}"
                        + " hasSupport={} ragCount={}",
                actor.getId(),
                true,
                people.results().size(),
                groups.results().size(),
                support != null,
                rag.results().size());

        return new ChatEntitySearchResponseDto(people, groups, support, rag);
    }

    private <T> ChatEntitySearchSectionDto<T> section(
            java.util.function.Supplier<List<T>> supplier, String sectionName, User actor) {
        try {
            List<T> all = supplier.get();
            boolean hasMore = all.size() > SECTION_LIMIT;
            List<T> capped = all.size() > SECTION_LIMIT ? all.subList(0, SECTION_LIMIT) : all;
            return new ChatEntitySearchSectionDto<>(capped, hasMore);
        } catch (RuntimeException ex) {
            log.warn(
                    "chat.entity.search.section_failed actorId={} section={}",
                    actor.getId(),
                    sectionName,
                    ex);
            return new ChatEntitySearchSectionDto<>(List.of(), false);
        }
    }

    private List<ChatPersonSearchResultDto> peopleMatches(User actor, String q, int limit) {
        return chatEligibilityService.searchEligibleDirectCandidates(actor, q, limit).stream()
                .map(c -> new ChatPersonSearchResultDto(c.userId(), c.nickname(), c.avatarUrl()))
                .toList();
    }

    private List<ChatGroupSearchResultDto> groupMatches(User actor, String q, int limit) {
        return chatConversationService.searchDiscoverableGroups(actor, q, limit);
    }

    private List<ChatRagConversationSearchResultDto> ragMatches(User actor, String q, int limit) {
        return conversationService.searchOwn(actor, q, limit);
    }

    /**
     * REQ-21: matches the fixed "Suporte"/"Support" label, locale-aware via the reused {@link
     * ChatMessageSearchLocaleResolver}, then defers entirely to {@link
     * SupportTicketService#findOwnOrClaimableChannel} for the actual visibility check -- fails
     * closed (no Support result) when there is no active tenant, since Support is always
     * tenant-anchored.
     */
    private ChatSupportSearchResultDto supportMatch(User actor, String q, String acceptLanguage) {
        try {
            ChatSearchLocale locale = chatMessageSearchLocaleResolver.resolve(acceptLanguage);
            String label = locale == ChatSearchLocale.PT ? "suporte" : "support";
            String query = q.trim().toLowerCase(Locale.ROOT);

            if (!label.contains(query) && !query.contains(label)) {
                return null;
            }

            Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
            if (activeTenantId.isEmpty()) {
                return null;
            }

            return supportTicketService
                    .findOwnOrClaimableChannel(actor, activeTenantId.get())
                    .map(channel -> new ChatSupportSearchResultDto(channel.getId()))
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.warn(
                    "chat.entity.search.section_failed actorId={} section=support",
                    actor.getId(),
                    ex);
            return null;
        }
    }

    /**
     * REQ-25/26: merges {@code ChatConversationService#listConversations} (chat kinds) with the
     * caller's own RAG conversations, ordered by each item's own recency signal, falling back to id
     * order for no-messages-yet chat conversations (mirrors {@code
     * ChatConversationSummaryDto.from}'s existing null-lastMessageAt handling).
     */
    private ChatEntitySearchResultDto recentPlaces(User actor) {
        List<ChatRecentPlaceDto> places = new ArrayList<>();

        try {
            chatConversationService.listConversations(actor).stream()
                    .map(
                            summary ->
                                    new ChatRecentPlaceDto(
                                            summary.id(),
                                            summary.kind().name(),
                                            summary.title(),
                                            summary.lastMessageAt()))
                    .forEach(places::add);
        } catch (RuntimeException ex) {
            log.warn(
                    "chat.entity.search.section_failed actorId={} section=recentChat",
                    actor.getId(),
                    ex);
        }

        Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        if (activeTenantId.isPresent()) {
            try {
                List<Conversation> ownRagConversations =
                        conversationRepository.findByOwnerIdOrderByCreatedAtDesc(actor.getId());
                for (Conversation conversation : ownRagConversations) {
                    if (conversation.getTenant() == null
                            || !conversation.getTenant().getId().equals(activeTenantId.get())) {
                        continue;
                    }
                    places.add(
                            new ChatRecentPlaceDto(
                                    conversation.getId(),
                                    "RAG",
                                    conversation.getTitle(),
                                    conversation.getCreatedAt()));
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "chat.entity.search.section_failed actorId={} section=recentRag",
                        actor.getId(),
                        ex);
            }
        }

        List<ChatRecentPlaceDto> merged =
                places.stream()
                        .sorted(
                                Comparator.comparing(
                                                ChatRecentPlaceDto::orderingTimestamp,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                ChatRecentPlaceDto::conversationId,
                                                Comparator.reverseOrder()))
                        .limit(RECENT_PLACES_LIMIT)
                        .toList();

        return new ChatEntitySearchResultDto(merged);
    }
}
