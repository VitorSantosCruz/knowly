package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.conversation.MessageRepository;
import br.com.conectabyte.knowly.conversation.MessageRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.exception.TenantSelectionRequiredException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsService {

    private final ArticleRepository articleRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final ActiveMemberSnapshotRepository activeMemberSnapshotRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public MetricsService(
            ArticleRepository articleRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            TenantMembershipRepository tenantMembershipRepository,
            ActiveMemberSnapshotRepository activeMemberSnapshotRepository,
            TenantContext tenantContext,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.activeMemberSnapshotRepository = activeMemberSnapshotRepository;
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
    public ConversationsMetricDto conversationsMetric(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        long startedCount =
                period.startInstant(clock)
                        .map(
                                from ->
                                        conversationRepository
                                                .countByTenantIdAndCreatedAtGreaterThanEqual(
                                                        tenantId, from))
                        .orElseGet(() -> conversationRepository.countByTenantId(tenantId));

        return new ConversationsMetricDto(startedCount);
    }

    @Transactional(readOnly = true)
    public MessagesMetricDto messagesMetric(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        var from = period.startInstant(clock);
        long sentCount =
                from.map(
                                f ->
                                        messageRepository
                                                .countByConversation_Tenant_IdAndRoleAndCreatedAtGreaterThanEqual(
                                                        tenantId, MessageRole.USER, f))
                        .orElseGet(
                                () ->
                                        messageRepository.countByConversation_Tenant_IdAndRole(
                                                tenantId, MessageRole.USER));
        long receivedCount =
                from.map(
                                f ->
                                        messageRepository
                                                .countByConversation_Tenant_IdAndRoleAndCreatedAtGreaterThanEqual(
                                                        tenantId, MessageRole.ASSISTANT, f))
                        .orElseGet(
                                () ->
                                        messageRepository.countByConversation_Tenant_IdAndRole(
                                                tenantId, MessageRole.ASSISTANT));

        return new MessagesMetricDto(sentCount, receivedCount);
    }

    @Transactional(readOnly = true)
    public ArticlesTimeseriesDto articlesTimeseries(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        List<DailyCountProjection> rows =
                period.startInstant(clock)
                        .map(
                                from ->
                                        articleRepository.countActiveByDayForTenantSince(
                                                tenantId, from))
                        .orElseGet(() -> articleRepository.countActiveByDayForTenant(tenantId));

        return new ArticlesTimeseriesDto(mergeZeroCountDays(rows, period));
    }

    @Transactional(readOnly = true)
    public MessagesTimeseriesDto messagesTimeseries(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        List<DailyRoleCountProjection> rows =
                period.startInstant(clock)
                        .map(
                                from ->
                                        messageRepository.countByDayAndRoleForTenantSince(
                                                tenantId, from))
                        .orElseGet(() -> messageRepository.countByDayAndRoleForTenant(tenantId));

        return new MessagesTimeseriesDto(mergeZeroCountDaysByRole(rows, period));
    }

    private List<DailyRoleCountDto> mergeZeroCountDaysByRole(
            List<DailyRoleCountProjection> rows, MetricsPeriod period) {
        Map<LocalDate, Long> userCounts = new HashMap<>();
        Map<LocalDate, Long> assistantCounts = new HashMap<>();
        for (DailyRoleCountProjection row : rows) {
            if (MessageRole.USER.name().equals(row.getRole())) {
                userCounts.put(row.getDay(), row.getCount());
            } else if (MessageRole.ASSISTANT.name().equals(row.getRole())) {
                assistantCounts.put(row.getDay(), row.getCount());
            }
        }

        List<LocalDate> dates =
                period.dateRange(clock)
                        .orElseGet(
                                () ->
                                        Stream.concat(
                                                        userCounts.keySet().stream(),
                                                        assistantCounts.keySet().stream())
                                                .distinct()
                                                .sorted()
                                                .toList());

        return dates.stream()
                .map(
                        date ->
                                new DailyRoleCountDto(
                                        date,
                                        userCounts.getOrDefault(date, 0L),
                                        assistantCounts.getOrDefault(date, 0L)))
                .toList();
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

    @Transactional(readOnly = true)
    public MembersTimeseriesDto membersTimeseries(MetricsPeriod period) {
        Long tenantId = requireActiveTenant();
        List<DailyCountProjection> rows =
                period.startInstant(clock)
                        .map(
                                from ->
                                        activeMemberSnapshotRepository.countByTenantSince(
                                                tenantId, from))
                        .orElseGet(() -> activeMemberSnapshotRepository.countByTenant(tenantId));

        return new MembersTimeseriesDto(mergeZeroCountDays(rows, period));
    }

    @Transactional(readOnly = true)
    public MembersMetricDto membersMetric() {
        Long tenantId = requireActiveTenant();
        long activeCount = tenantMembershipRepository.countByTenantIdAndActive(tenantId, true);
        long inactiveCount = tenantMembershipRepository.countByTenantIdAndActive(tenantId, false);

        return new MembersMetricDto(activeCount, inactiveCount);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(MetricsPeriod period) {
        requireActiveTenant();
        ArticleCountDto articleCount = articleCount();
        ConversationsMetricDto conversations = conversationsMetric(period);
        MessagesMetricDto messages = messagesMetric(period);
        MembersMetricDto members = membersMetric();
        ArticlesTimeseriesDto articlesTs = articlesTimeseries(period);
        ConversationsTimeseriesDto conversationsTs = conversationsTimeseries(period);
        MessagesTimeseriesDto messagesTs = messagesTimeseries(period);

        StringBuilder csv = new StringBuilder();
        csv.append("metric,value\n");
        csv.append("active_article_count,").append(articleCount.totalCount()).append('\n');
        csv.append("conversation_count,").append(conversations.startedCount()).append('\n');
        csv.append("user_message_count,").append(messages.sentCount()).append('\n');
        csv.append("assistant_message_count,").append(messages.receivedCount()).append('\n');
        csv.append("member_active_count,").append(members.activeCount()).append('\n');
        csv.append("member_inactive_count,").append(members.inactiveCount()).append('\n');
        csv.append('\n');

        csv.append("date,article_count\n");
        for (DailyCountDto day : articlesTs.days()) {
            csv.append(day.date()).append(',').append(day.count()).append('\n');
        }
        csv.append('\n');

        csv.append("date,conversation_count\n");
        for (DailyCountDto day : conversationsTs.days()) {
            csv.append(day.date()).append(',').append(day.count()).append('\n');
        }
        csv.append('\n');

        csv.append("date,user_message_count,assistant_message_count\n");
        for (DailyRoleCountDto day : messagesTs.days()) {
            csv.append(day.date())
                    .append(',')
                    .append(day.userCount())
                    .append(',')
                    .append(day.assistantCount())
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * No active tenant (staff who hasn't switched into one, or a member whose selection lapsed) is
     * a "pick a tenant first" state, not "you don't have access to this tenant" -- {@link
     * TenantSelectionRequiredException} (409) matches the exact case this project already uses it
     * for elsewhere (e.g. tenant-scoped route guards), instead of the misleading {@code
     * TenantAccessDeniedException} (403) this used to throw here.
     */
    private Long requireActiveTenant() {
        return tenantContext.getActiveTenantId().orElseThrow(TenantSelectionRequiredException::new);
    }
}
