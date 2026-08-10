package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.dto.ChatMessageSearchPageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageSearchResultDto;
import br.com.conectabyte.knowly.chat.exception.ChatBlankSearchQueryException;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchDateRangeException;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-1 through REQ-15: orchestrates chat-message-search -- input validation (REQ-11/12), locale
 * resolution (REQ-13-15), the fail-closed tenant-scoping step, and delegation to {@link
 * ChatMessageSearchRepository}.
 *
 * <p><b>AppSec correction (see PLAN.md "Architectural decisions"):</b> the active tenant id is
 * resolved via {@link TenantContext#getActiveTenantId()} <b>before</b> the repository is ever
 * invoked, and an empty result short-circuits to an empty {@link ChatMessageSearchPageDto} with
 * <b>no query executed at all</b> -- stronger than {@code TenantFilterAspect}'s own sentinel
 * pattern (which still runs a query). This method deliberately never reads {@code
 * tenantContext.isStaff()}/{@code isStaffAdmin()} -- REQ-5 forbids any oversight bypass for search
 * specifically, so "staff with no active tenant" is treated identically to any other caller with no
 * active tenant.
 */
@Service
public class ChatMessageSearchService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageSearchService.class);

    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    private final TenantContext tenantContext;

    public ChatMessageSearchService(
            ChatMessageSearchRepository chatMessageSearchRepository,
            ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver,
            TenantContext tenantContext) {
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.chatMessageSearchLocaleResolver = chatMessageSearchLocaleResolver;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public ChatMessageSearchPageDto search(
            User actor,
            String q,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            String cursor,
            Integer size,
            String acceptLanguageHeader) {
        if (q == null || q.isBlank()) {
            throw new ChatBlankSearchQueryException();
        }

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ChatInvalidSearchDateRangeException();
        }

        Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        if (activeTenantId.isEmpty()) {
            logSearch(actor, senderId, conversationId, dateFrom, dateTo, 0);
            return new ChatMessageSearchPageDto(List.of(), null);
        }

        int pageSize = ChatCursor.clampSize(size);
        Long decodedCursor = cursor == null ? null : ChatCursor.decode(cursor);
        ChatSearchLocale locale = chatMessageSearchLocaleResolver.resolve(acceptLanguageHeader);

        List<ChatMessageSearchRepository.ChatMessageSearchRow> rows =
                locale == ChatSearchLocale.PT
                        ? chatMessageSearchRepository.searchPt(
                                actor.getId(),
                                activeTenantId.get(),
                                q,
                                senderId,
                                conversationId,
                                dateFrom,
                                dateTo,
                                decodedCursor,
                                pageSize)
                        : chatMessageSearchRepository.searchEn(
                                actor.getId(),
                                activeTenantId.get(),
                                q,
                                senderId,
                                conversationId,
                                dateFrom,
                                dateTo,
                                decodedCursor,
                                pageSize);

        List<ChatMessageSearchResultDto> results =
                rows.stream()
                        .map(
                                row ->
                                        new ChatMessageSearchResultDto(
                                                row.getId(),
                                                row.getConversationId(),
                                                row.getConversationTitle(),
                                                row.getSenderUserId(),
                                                row.getSenderNickname(),
                                                row.getContent(),
                                                row.getCreatedAt()))
                        .toList();

        String nextCursor =
                rows.size() < pageSize
                        ? null
                        : ChatCursor.encode(rows.get(rows.size() - 1).getId());

        logSearch(actor, senderId, conversationId, dateFrom, dateTo, results.size());

        return new ChatMessageSearchPageDto(results, nextCursor);
    }

    /**
     * Structured, non-{@code @AuditLog} logging per PLAN.md's non-functional requirements: actor
     * id, {@code hasQuery}, filter-presence booleans, and result count only. Reminder for future
     * contributors: never add the raw {@code q} string to this log line -- it is user-authored free
     * text that may contain sensitive content.
     */
    private void logSearch(
            User actor,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            int resultCount) {
        log.info(
                "chat.message.search actorId={} hasQuery={} hasSenderFilter={} hasConversationFilter={}"
                        + " hasDateRangeFilter={} resultCount={}",
                actor.getId(),
                true,
                senderId != null,
                conversationId != null,
                dateFrom != null || dateTo != null,
                resultCount);
    }
}
