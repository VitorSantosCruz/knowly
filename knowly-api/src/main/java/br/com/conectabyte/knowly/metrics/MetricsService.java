package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.conversation.MessageRepository;
import br.com.conectabyte.knowly.conversation.MessageRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsService {

    private final ArticleRepository articleRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public MetricsService(
            ArticleRepository articleRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            TenantContext tenantContext,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
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

    @Transactional(readOnly = true)
    public ConversationsTimeseriesDto conversationsTimeseries(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        List<DailyCountProjection> rows =
                period.startInstant(clock)
                        .map(
                                from ->
                                        conversationRepository.countByDayForTenantSince(
                                                tenantId, from))
                        .orElseGet(() -> conversationRepository.countByDayForTenant(tenantId));

        return new ConversationsTimeseriesDto(mergeZeroCountDays(rows, period));
    }

    private List<DailyCountDto> mergeZeroCountDays(
            List<DailyCountProjection> rows, MetricsPeriod period) {
        Map<LocalDate, Long> counts =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        DailyCountProjection::getDay,
                                        DailyCountProjection::getCount));
        List<LocalDate> dates =
                period.dateRange(clock).orElseGet(() -> counts.keySet().stream().sorted().toList());

        return dates.stream()
                .map(date -> new DailyCountDto(date, counts.getOrDefault(date, 0L)))
                .toList();
    }

    private Long requireActiveTenant() {
        return tenantContext.getActiveTenantId().orElseThrow(TenantAccessDeniedException::new);
    }
}
