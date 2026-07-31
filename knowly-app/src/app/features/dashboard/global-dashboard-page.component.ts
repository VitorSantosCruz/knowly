import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  LucideBookOpenCheck,
  LucideBuilding2,
  LucideLifeBuoy,
  LucideShieldCheck,
  LucideUserPlus,
} from '@lucide/angular';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { GradientStatCardComponent } from './gradient-stat-card.component';
import { NewTenantsTrendChartComponent } from './new-tenants-trend-chart.component';
import { ArticlesReadTrendChartComponent } from './articles-read-trend-chart.component';
import { PeriodFilterComponent, Period } from './period-filter.component';
import { DailyCountRow } from './trend-chart-data';

export interface GlobalMetricsDto {
  tenantCount: number;
  newTenantsThisMonth: number;
  articlesReadTotal: number;
  staffCount: number;
}

export interface PeriodComparisonDto {
  current: number;
  previous: number | null;
  percentChange: number | null;
}

export interface GlobalTrendsDto {
  newTenantsPerDay: DailyCountRow[];
  articlesReadPerDay: DailyCountRow[];
  totalTenants: PeriodComparisonDto;
  newTenants: PeriodComparisonDto;
  totalArticlesRead: PeriodComparisonDto;
  staffCount: PeriodComparisonDto;
}

type GlobalDashboardError = 'network' | 'permission-denied' | null;

/** Shared 403-vs-network classification, mirroring `metric-fetcher.ts`'s inline logic. */
export function classifyMetricError(response: HttpErrorResponse): 'network' | 'permission-denied' {
  return response.status === 403 && response.error?.code === 'PERMISSION_DENIED'
    ? 'permission-denied'
    : 'network';
}

/**
 * Belt-and-suspenders clamp: no badge when trends failed to load, when
 * `period=all` (backend already omits it, REQ-9), or when the backend
 * itself sent a null percentChange (zero previous-period count, REQ-10).
 */
export function percentChangeFor(
  comparison: PeriodComparisonDto | undefined,
  period: Period,
  trendsFailed: boolean,
): number | undefined {
  if (trendsFailed || period === 'all' || comparison === undefined) {
    return undefined;
  }
  return comparison.percentChange ?? undefined;
}

/**
 * Staff, no active tenant: one page-level fetch to GET /api/staff/metrics/global (unchanged
 * REQ-7 behavior), plus a second, period-scoped fetch to
 * GET /api/staff/metrics/global/trends only attempted once the first succeeds. Renders 4
 * gradient stat cards with a % change badge, 2 trend charts, and 1 disabled "coming soon"
 * card — a single failing trends call degrades gracefully (REQ-8) rather than blanking the
 * page.
 */
@Component({
  selector: 'app-global-dashboard-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    GradientStatCardComponent,
    NewTenantsTrendChartComponent,
    ArticlesReadTrendChartComponent,
    PeriodFilterComponent,
    LucideBuilding2,
    LucideUserPlus,
    LucideBookOpenCheck,
    LucideShieldCheck,
    LucideLifeBuoy,
  ],
  template: `
    <div data-testid="global-dashboard-page" class="page-shell space-y-4">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <div class="flex items-center justify-between gap-3">
          <h1 class="sr-only">{{ 'dashboard.tiles.tenantCount' | transloco }}</h1>
          <app-period-filter [(period)]="period" />
        </div>

        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <app-gradient-stat-card
            testId="tenant-count-tile"
            label="{{ 'dashboard.tiles.tenantCount' | transloco }}"
            subtitle="{{ 'dashboard.trends.tenantCountSubtitle' | transloco }}"
            [value]="metrics()?.tenantCount"
            [percentChange]="tenantCountPercentChange()"
          >
            <svg lucideBuilding2 icon aria-hidden="true"></svg>
          </app-gradient-stat-card>
          <app-gradient-stat-card
            testId="new-tenants-tile"
            label="{{ 'dashboard.tiles.newTenantsThisMonth' | transloco }}"
            subtitle="{{ 'dashboard.trends.newTenantsSubtitle' | transloco }}"
            [value]="metrics()?.newTenantsThisMonth"
            [percentChange]="newTenantsPercentChange()"
          >
            <svg lucideUserPlus icon aria-hidden="true"></svg>
          </app-gradient-stat-card>
          <app-gradient-stat-card
            testId="articles-read-tile"
            label="{{ 'dashboard.tiles.articlesReadTotal' | transloco }}"
            subtitle="{{ 'dashboard.trends.articlesReadSubtitle' | transloco }}"
            [value]="metrics()?.articlesReadTotal"
            [percentChange]="articlesReadPercentChange()"
          >
            <svg lucideBookOpenCheck icon aria-hidden="true"></svg>
          </app-gradient-stat-card>
          <app-gradient-stat-card
            testId="staff-count-tile"
            label="{{ 'dashboard.tiles.staffCount' | transloco }}"
            subtitle="{{ 'dashboard.trends.staffCountSubtitle' | transloco }}"
            [value]="metrics()?.staffCount"
            [percentChange]="staffCountPercentChange()"
          >
            <svg lucideShieldCheck icon aria-hidden="true"></svg>
          </app-gradient-stat-card>
          <app-gradient-stat-card
            testId="support-tickets-tile"
            label="{{ 'dashboard.tiles.supportTickets' | transloco }}"
            subtitle="{{ 'dashboard.trends.supportTicketsSubtitle' | transloco }}"
            [disabled]="true"
            [comingSoonLabel]="'dashboard.comingSoon' | transloco"
          >
            <svg lucideLifeBuoy icon aria-hidden="true"></svg>
          </app-gradient-stat-card>
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <app-new-tenants-trend-chart
            [data]="trends()?.newTenantsPerDay ?? []"
            [error]="trendsError() !== null"
          />
          <app-articles-read-trend-chart
            [data]="trends()?.articlesReadPerDay ?? []"
            [error]="trendsError() !== null"
          />
        </div>
      }
    </div>
  `,
})
export class GlobalDashboardPageComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly metrics = signal<GlobalMetricsDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<GlobalDashboardError>(null);

  protected readonly period = signal<Period>('30d');
  protected readonly trends = signal<GlobalTrendsDto | null>(null);
  protected readonly trendsError = signal<GlobalDashboardError>(null);

  constructor() {
    effect(() => {
      const period = this.period();
      if (this.metrics() !== null && this.error() === null) {
        this.loadTrends(period);
      }
    });
  }

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http
      .get<GlobalMetricsDto>('/api/staff/metrics/global')
      .pipe(
        catchError((err) => {
          this.error.set(classifyMetricError(err));
          return of(null);
        }),
      )
      .subscribe((metrics) => {
        this.loading.set(false);
        if (metrics !== null) {
          this.metrics.set(metrics);
        }
      });
  }

  private loadTrends(period: Period): void {
    this.trendsError.set(null);

    this.http
      .get<GlobalTrendsDto>('/api/staff/metrics/global/trends', { params: { period } })
      .pipe(
        catchError((err) => {
          this.trendsError.set(classifyMetricError(err));
          return of(null);
        }),
      )
      .subscribe((trends) => {
        if (trends !== null) {
          this.trends.set(trends);
        }
      });
  }

  protected tenantCountPercentChange(): number | undefined {
    return percentChangeFor(
      this.trends()?.totalTenants,
      this.period(),
      this.trendsError() !== null,
    );
  }

  protected newTenantsPercentChange(): number | undefined {
    return percentChangeFor(this.trends()?.newTenants, this.period(), this.trendsError() !== null);
  }

  protected articlesReadPercentChange(): number | undefined {
    return percentChangeFor(
      this.trends()?.totalArticlesRead,
      this.period(),
      this.trendsError() !== null,
    );
  }

  protected staffCountPercentChange(): number | undefined {
    return percentChangeFor(this.trends()?.staffCount, this.period(), this.trendsError() !== null);
  }
}
