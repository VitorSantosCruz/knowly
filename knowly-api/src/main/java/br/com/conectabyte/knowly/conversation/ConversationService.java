package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.dto.ChatRagConversationSearchResultDto;
import br.com.conectabyte.knowly.conversation.dto.ConversationDetailDto;
import br.com.conectabyte.knowly.conversation.dto.ConversationSummaryDto;
import br.com.conectabyte.knowly.conversation.exception.ConversationNotFoundException;
import br.com.conectabyte.knowly.icon.IconKey;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TenantRepository tenantRepository;
    private final TenantContext tenantContext;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            TenantRepository tenantRepository,
            TenantContext tenantContext) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.tenantRepository = tenantRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public ConversationSummaryDto create(User owner, Long tenantId, String title, IconKey icon) {
        requireActiveTenant(tenantId);
        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(ConversationNotFoundException::new);
        Conversation conversation =
                conversationRepository.save(new Conversation(tenant, owner, title, icon));

        return ConversationSummaryDto.from(conversation);
    }

    @Transactional
    public ConversationSummaryDto rename(
            User owner, Long tenantId, Long conversationId, String title, IconKey icon) {
        requireActiveTenant(tenantId);
        Conversation conversation = requireOwnConversation(owner, conversationId);
        conversation.setTitle(title);
        conversation.setIcon(icon);
        conversationRepository.save(conversation);

        return ConversationSummaryDto.from(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> list(User owner, Long tenantId) {
        requireActiveTenant(tenantId);

        return conversationRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(ConversationSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto get(User owner, Long tenantId, Long conversationId) {
        requireActiveTenant(tenantId);
        Conversation conversation = requireOwnConversation(owner, conversationId);
        List<Message> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        return ConversationDetailDto.from(conversation, messages);
    }

    /** RAG conversation turn-content search (2026-08-11 amendment): snippet truncation bound. */
    private static final int SNIPPET_MAX_LENGTH = 150;

    /**
     * Unified entity search (2026-08-10 amendment), REQ-22: RAG conversation title match backing
     * {@code ChatEntitySearchService}.
     *
     * <p><b>AppSec correction (Gap 2, RAG half):</b> resolves {@link
     * TenantContext#getActiveTenantId()} itself, before any repository invocation, and fails closed
     * (empty result, no query run at all) when absent -- {@code
     * ConversationRepository#searchByOwnerAndTitle}'s own explicit {@code tenant.id = :tenantId}
     * predicate (not the session-level {@code @Filter}, which {@code TenantFilterAspect} disables
     * for a staff caller with no active tenant selected) is what actually does the scoping, but
     * this short-circuit is a stronger guarantee still -- no query at all rather than one that
     * merely matches nothing.
     *
     * <p><b>RAG conversation turn-content search (2026-08-11 amendment), REQ-27-REQ-33:</b> merges
     * title hits ({@code searchByOwnerAndTitle}) and turn-content hits ({@code
     * MessageRepository#searchByConversationOwnerAndContent}, same pattern/limit, same
     * ownerId/tenantId predicates applied before the text predicate) into a single list
     * deduplicated by conversation id -- title hits are inserted first, so a conversation matching
     * both keeps its title. For a conversation matched by content, the single most-recent matching
     * {@code Message} wins (REQ-31's tie-break) -- the repository already orders by {@code
     * createdAt DESC}, so the first content hit encountered per conversation id is the most recent.
     */
    @Transactional(readOnly = true)
    public List<ChatRagConversationSearchResultDto> searchOwn(
            User owner, String titleQuery, int limit) {
        Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        if (activeTenantId.isEmpty()) {
            return List.of();
        }

        Long tenantId = activeTenantId.get();
        String pattern = "%" + titleQuery + "%";

        Map<Long, ChatRagConversationSearchResultDto> merged = new LinkedHashMap<>();

        conversationRepository
                .searchByOwnerAndTitle(owner.getId(), tenantId, pattern, PageRequest.of(0, limit))
                .getContent()
                .forEach(
                        conversation ->
                                merged.put(
                                        conversation.getId(),
                                        new ChatRagConversationSearchResultDto(
                                                conversation.getId(), conversation.getTitle())));

        messageRepository
                .searchByConversationOwnerAndContent(
                        owner.getId(), tenantId, pattern, PageRequest.of(0, limit))
                .getContent()
                .forEach(
                        message -> {
                            Long conversationId = message.getConversation().getId();
                            ChatRagConversationSearchResultDto existing =
                                    merged.get(conversationId);
                            String title =
                                    existing != null
                                            ? existing.title()
                                            : message.getConversation().getTitle();
                            if (existing != null && existing.matchedSnippet() != null) {
                                // A more recent matching turn for this conversation was already
                                // recorded (repository orders most-recent-first) -- REQ-31's
                                // tie-break keeps the first one seen.
                                return;
                            }

                            merged.put(
                                    conversationId,
                                    new ChatRagConversationSearchResultDto(
                                            conversationId,
                                            title,
                                            buildSnippet(message.getContent(), titleQuery),
                                            message.getRole().name()));
                        });

        return List.copyOf(merged.values());
    }

    /**
     * Truncates {@code content} to a {@value #SNIPPET_MAX_LENGTH}-character window centered on the
     * first occurrence of {@code query} (case-insensitive), never throwing on an out-of-range
     * substring index and never splitting a UTF-16 surrogate pair.
     */
    static String buildSnippet(String content, String query) {
        if (content.length() <= SNIPPET_MAX_LENGTH) {
            return content;
        }

        int matchIndex = content.toLowerCase().indexOf(query.toLowerCase());
        int center = matchIndex >= 0 ? matchIndex : 0;

        int start = Math.max(0, center - SNIPPET_MAX_LENGTH / 2);
        int end = Math.min(content.length(), start + SNIPPET_MAX_LENGTH);
        start = Math.max(0, end - SNIPPET_MAX_LENGTH);

        // Never split a UTF-16 surrogate pair at either boundary.
        if (start > 0 && Character.isLowSurrogate(content.charAt(start))) {
            start--;
        }
        if (end < content.length() && Character.isLowSurrogate(content.charAt(end))) {
            end--;
        }

        return content.substring(start, end);
    }

    Conversation requireOwnConversation(User owner, Long conversationId) {
        return conversationRepository
                .findByIdAndOwnerId(conversationId, owner.getId())
                .orElseThrow(ConversationNotFoundException::new);
    }

    private void requireActiveTenant(Long tenantId) {
        if (tenantContext.isStaffAdmin()) {
            return;
        }

        if (tenantContext.getActiveTenantId().filter(tenantId::equals).isEmpty()) {
            throw new TenantAccessDeniedException();
        }
    }
}
