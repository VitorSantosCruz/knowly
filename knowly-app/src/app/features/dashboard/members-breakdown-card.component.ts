import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

interface MembersResponse {
  activeCount: number;
  inactiveCount: number;
}

@Component({
  selector: 'app-members-breakdown-card',
  imports: [ErrorStateComponent, NoAccessStateComponent, TranslocoPipe],
  template: `
    <div
      data-testid="members-breakdown-card"
      class="enter-fluid relative overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <div class="flex gap-6">
          <div>
            <p class="text-sm text-ink-300">{{ 'dashboard.tiles.activeMembers' | transloco }}</p>
            <p data-testid="active-count" class="text-3xl font-bold text-white">
              {{ data.activeCount }}
            </p>
          </div>
          <div>
            <p class="text-sm text-ink-300">{{ 'dashboard.inactiveMembers' | transloco }}</p>
            <p data-testid="inactive-count" class="text-3xl font-bold text-white">
              {{ data.inactiveCount }}
            </p>
          </div>
        </div>
      }
    </div>
  `,
})
export class MembersBreakdownCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly fetcher = createMetricFetcher<MembersResponse>(
    this.http,
    '/api/tenants/metrics/members',
  );

  ngOnInit(): void {
    this.fetcher.load();
  }
}
