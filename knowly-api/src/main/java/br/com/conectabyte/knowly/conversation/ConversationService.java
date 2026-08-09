package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.conversation.dto.ConversationDetailDto;
import br.com.conectabyte.knowly.conversation.dto.ConversationSummaryDto;
import br.com.conectabyte.knowly.conversation.exception.ConversationNotFoundException;
import br.com.conectabyte.knowly.icon.IconKey;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
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
