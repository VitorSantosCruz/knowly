package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.conversation.MessageRepository;
import br.com.conectabyte.knowly.conversation.MessageRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsService {

    private final ArticleRepository articleRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final TenantContext tenantContext;

    public MetricsService(
            ArticleRepository articleRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            TenantContext tenantContext) {
        this.articleRepository = articleRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public ArticleCountDto articleCount() {
        Long tenantId = requireActiveTenant();

        return new ArticleCountDto(articleRepository.countByTenantIdAndActiveTrue(tenantId));
    }

    @Transactional(readOnly = true)
    public ArticleUsageResponseDto articleUsage() {
        Long tenantId = requireActiveTenant();

        return new ArticleUsageResponseDto(
                messageArticleCitationRepository.usageByTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public ConversationsMetricDto conversationsMetric() {
        Long tenantId = requireActiveTenant();

        return new ConversationsMetricDto(conversationRepository.countByTenantId(tenantId));
    }

    @Transactional(readOnly = true)
    public MessagesMetricDto messagesMetric() {
        Long tenantId = requireActiveTenant();

        long sentCount =
                messageRepository.countByConversation_Tenant_IdAndRole(tenantId, MessageRole.USER);
        long receivedCount =
                messageRepository.countByConversation_Tenant_IdAndRole(
                        tenantId, MessageRole.ASSISTANT);

        return new MessagesMetricDto(sentCount, receivedCount);
    }

    private Long requireActiveTenant() {
        return tenantContext.getActiveTenantId().orElseThrow(TenantAccessDeniedException::new);
    }
}
