import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MetricTileComponent } from './metric-tile.component';

export interface GlobalMetricsDto {
  tenantCount: number;
  newTenantsThisMonth: number;
  articlesReadTotal: number;
  staffCount: number;
}

type GlobalDashboardError = 'network' | 'permission-denied' | null;

/**
 * Staff, no active tenant: one page-level fetch to GET /api/staff/metrics/global, rendering
 * 4 populated tiles (pre-fetched-value mode) plus 1 disabled "coming soon" support-tickets
 * tile. 403 handling is page-level (app-no-access-state), not per-tile, since the endpoint
 * returns every number in a single call.
 */
@Component({
  selector: 'app-global-dashboard-page',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent, MetricTileComponent],
  template: `
    <div data-testid="global-dashboard-page" class="page-shell grid gap-4 sm:grid-cols-2">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <app-metric-tile
          testId="tenant-count-tile"
          label="{{ 'dashboard.tiles.tenantCount' | transloco }}"
          [value]="metrics()?.tenantCount"
        />
        <app-metric-tile
          testId="new-tenants-tile"
          label="{{ 'dashboard.tiles.newTenantsThisMonth' | transloco }}"
          [value]="metrics()?.newTenantsThisMonth"
        />
        <app-metric-tile
          testId="articles-read-tile"
          label="{{ 'dashboard.tiles.articlesReadTotal' | transloco }}"
          [value]="metrics()?.articlesReadTotal"
        />
        <app-metric-tile
          testId="staff-count-tile"
          label="{{ 'dashboard.tiles.staffCount' | transloco }}"
          [value]="metrics()?.staffCount"
        />
        <app-metric-tile
          testId="support-tickets-tile"
          label="{{ 'dashboard.tiles.supportTickets' | transloco }}"
          [disabled]="true"
        />
      }
    </div>
  `,
})
export class GlobalDashboardPageComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly metrics = signal<GlobalMetricsDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<GlobalDashboardError>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http
      .get<GlobalMetricsDto>('/api/staff/metrics/global')
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
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
}
