package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresPermission;
import br.com.conectabyte.knowly.tenancy.Permission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/articles")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.articles.view", resourceType = "Metrics")
    public ArticleCountDto articleCount() {
        return metricsService.articleCount();
    }

    @GetMapping("/articles/usage")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.articles.usage.view", resourceType = "Metrics")
    public ArticleUsageResponseDto articleUsage() {
        return metricsService.articleUsage();
    }

    @GetMapping("/conversations")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.conversations.view", resourceType = "Metrics")
    public ConversationsMetricDto conversationsMetric() {
        return metricsService.conversationsMetric();
    }

    @GetMapping("/messages")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.messages.view", resourceType = "Metrics")
    public MessagesMetricDto messagesMetric() {
        return metricsService.messagesMetric();
    }

    @GetMapping("/conversations/timeseries")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.conversations.timeseries.view", resourceType = "Metrics")
    public ConversationsTimeseriesDto conversationsTimeseries(
            @RequestParam(required = false) String period) {
        return metricsService.conversationsTimeseries(MetricsPeriod.from(period));
    }

    @GetMapping("/messages/timeseries")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.messages.timeseries.view", resourceType = "Metrics")
    public MessagesTimeseriesDto messagesTimeseries(@RequestParam(required = false) String period) {
        return metricsService.messagesTimeseries(MetricsPeriod.from(period));
    }

    @GetMapping("/articles/timeseries")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.articles.timeseries.view", resourceType = "Metrics")
    public ArticlesTimeseriesDto articlesTimeseries(@RequestParam(required = false) String period) {
        return metricsService.articlesTimeseries(MetricsPeriod.from(period));
    }

    @GetMapping("/members")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    @AuditLog(action = "metrics.members.view", resourceType = "Metrics")
    public MembersMetricDto membersMetric() {
        return metricsService.membersMetric();
    }
}
