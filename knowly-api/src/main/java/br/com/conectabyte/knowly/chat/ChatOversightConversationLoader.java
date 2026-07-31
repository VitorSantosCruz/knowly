package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.tenancy.BypassTenantFilterForOversight;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated bean (not a method on ChatConversationService itself) so the
 * {@code @BypassTenantFilterForOversight}-annotated method is invoked through a real Spring proxy
 * -- self-invocation from within the same class would silently skip TenantFilterAspect's advice,
 * per Spring AOP's well-known same-class-call limitation. This method only widens what the query
 * can see; ChatConversationService still re-derives and verifies the caller's oversight
 * authorization before treating any row it returns as readable.
 */
@Component
public class ChatOversightConversationLoader {

    private final ChatConversationRepository chatConversationRepository;

    public ChatOversightConversationLoader(ChatConversationRepository chatConversationRepository) {
        this.chatConversationRepository = chatConversationRepository;
    }

    @Transactional(readOnly = true)
    @BypassTenantFilterForOversight
    public Optional<ChatConversation> loadIgnoringTenantFilter(Long conversationId) {
        return chatConversationRepository.findByIdRespectingFilter(conversationId);
    }

    /**
     * Normal, filter-respecting counterpart -- exists purely so tests can exercise the same query
     * through a real {@code @Transactional} proxy boundary with and without the bypass annotation,
     * proving the annotation (not just "no filter at all because this ran outside a transaction")
     * is what changes visibility.
     */
    @Transactional(readOnly = true)
    public Optional<ChatConversation> loadRespectingTenantFilter(Long conversationId) {
        return chatConversationRepository.findByIdRespectingFilter(conversationId);
    }

    @Transactional
    public ChatConversation save(ChatConversation conversation) {
        return chatConversationRepository.save(conversation);
    }
}
