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
import java.util.List;
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
     */
    @Transactional(readOnly = true)
    public List<ChatRagConversationSearchResultDto> searchOwn(
            User owner, String titleQuery, int limit) {
        Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        if (activeTenantId.isEmpty()) {
            return List.of();
        }

        String pattern = "%" + titleQuery + "%";
        return conversationRepository
                .searchByOwnerAndTitle(
                        owner.getId(), activeTenantId.get(), pattern, PageRequest.of(0, limit))
                .getContent()
                .stream()
                .map(
                        conversation ->
                                new ChatRagConversationSearchResultDto(
                                        conversation.getId(), conversation.getTitle()))
                .toList();
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
