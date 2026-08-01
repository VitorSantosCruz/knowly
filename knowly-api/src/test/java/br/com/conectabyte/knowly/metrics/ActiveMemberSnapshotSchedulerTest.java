package br.com.conectabyte.knowly.metrics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveMemberSnapshotSchedulerTest {

    @Mock private TenantMembershipRepository tenantMembershipRepository;
    @Mock private ActiveMemberSnapshotRepository activeMemberSnapshotRepository;

    private static TenantActiveCountProjection projection(long tenantId, long count) {
        return new TenantActiveCountProjection() {
            @Override
            public Long getTenantId() {
                return tenantId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    @Test
    void recordsYesterdaysSnapshotForEveryTenantReturnedByTheAggregateQuery() {
        Clock fixedClock =
                Clock.fixed(
                        LocalDate.of(2026, 8, 1)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .plusSeconds(300),
                        ZoneOffset.UTC);
        when(tenantMembershipRepository.countActiveGroupedByTenant())
                .thenReturn(List.of(projection(1L, 3L), projection(2L, 7L)));
        ActiveMemberSnapshotScheduler scheduler =
                new ActiveMemberSnapshotScheduler(
                        tenantMembershipRepository, activeMemberSnapshotRepository, fixedClock);

        scheduler.recordDailySnapshots();

        LocalDate yesterday = LocalDate.of(2026, 7, 31);
        verify(activeMemberSnapshotRepository, times(1))
                .upsert(eq(1L), eq(yesterday), eq(3L), any(String.class));
        verify(activeMemberSnapshotRepository, times(1))
                .upsert(eq(2L), eq(yesterday), eq(7L), any(String.class));
        verify(activeMemberSnapshotRepository, times(2))
                .upsert(anyLong(), eq(yesterday), anyLong(), any(String.class));
    }
}
