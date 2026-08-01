package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * specify/features/active-members-trend/PLAN.md: the first {@code @Scheduled} job in this codebase.
 * Runs once daily at 00:05 UTC, recording the UTC calendar day that just completed for every
 * tenant, via an idempotent {@code ON CONFLICT} upsert (REQ-2/3) -- unconditional, so a tenant with
 * no membership changes that day still gets a row.
 */
@Component
public class ActiveMemberSnapshotScheduler {

    private static final String ACTOR = "system:active-member-snapshot-scheduler";

    private final TenantMembershipRepository tenantMembershipRepository;
    private final ActiveMemberSnapshotRepository activeMemberSnapshotRepository;
    private final Clock clock;

    public ActiveMemberSnapshotScheduler(
            TenantMembershipRepository tenantMembershipRepository,
            ActiveMemberSnapshotRepository activeMemberSnapshotRepository,
            Clock clock) {
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.activeMemberSnapshotRepository = activeMemberSnapshotRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void recordDailySnapshots() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);

        for (TenantActiveCountProjection row :
                tenantMembershipRepository.countActiveGroupedByTenant()) {
            activeMemberSnapshotRepository.upsert(
                    row.getTenantId(), yesterday, row.getCount(), ACTOR);
        }
    }
}
