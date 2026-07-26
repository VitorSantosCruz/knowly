package br.com.conectabyte.knowly.metrics.global;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-only, cross-tenant aggregation for internal operational visibility — the global counterpart
 * of {@link br.com.conectabyte.knowly.metrics.MetricsService}, per
 * specify/features/global-staff-dashboard-metrics/SPEC.md. Deliberately never touches {@code
 * TenantContext}/{@code TenantFilter}: every query here is intentionally unscoped (SPEC REQ-11),
 * kept in its own class/package so a future edit can't accidentally scope a global query by mixing
 * it into the tenant-scoped {@code MetricsService}.
 */
@Service
public class GlobalMetricsService {

    private final TenantRepository tenantRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public GlobalMetricsService(
            TenantRepository tenantRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            UserRepository userRepository,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)
    @AuditLog(action = "metrics.global.view", resourceType = "Metrics")
    public GlobalMetricsDto globalMetrics() {
        Instant startOfCurrentUtcMonth =
                LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long tenantCount = tenantRepository.count();
        long newTenantsThisMonth =
                tenantRepository.countByCreatedAtGreaterThanEqual(startOfCurrentUtcMonth);
        long articlesReadTotal = messageArticleCitationRepository.count();
        long staffCount =
                userRepository.countByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        return new GlobalMetricsDto(
                tenantCount, newTenantsThisMonth, articlesReadTotal, staffCount);
    }
}
