import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideUserMinus, LucideUsers } from '@lucide/angular';
import { createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

interface MembersResponse {
  activeCount: number;
  inactiveCount: number;
}

@Component({
  selector: 'app-members-breakdown-card',
  imports: [
    ErrorStateComponent,
    NoAccessStateComponent,
    TranslocoPipe,
    LucideUsers,
    LucideUserMinus,
  ],
  host: {
    class: 'grid gap-4 sm:grid-cols-2',
    'data-testid': 'members-breakdown-card',
  },
  template: `
    @if (fetcher.loading()) {
      <p data-testid="loading-state" class="text-sm text-ink-400 sm:col-span-2">…</p>
    } @else if (fetcher.error() === 'permission-denied') {
      <app-no-access-state />
    } @else if (fetcher.error() === 'network') {
      <app-error-state [traceId]="fetcher.traceId()" />
    } @else if (fetcher.data(); as data) {
      <div
        class="enter-fluid relative flex h-full flex-col overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
      >
        <div class="flex items-start justify-between gap-3">
          <p class="text-sm text-ink-300">{{ 'dashboard.tiles.activeMembers' | transloco }}</p>
          <div
            aria-hidden="true"
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-signal-500 to-signal-600 text-white"
          >
            <svg lucideUsers aria-hidden="true"></svg>
          </div>
        </div>
        <p data-testid="active-count" class="mt-1 text-3xl font-bold text-white">
          {{ data.activeCount }}
        </p>
      </div>
      <div
        class="enter-fluid relative flex h-full flex-col overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
      >
        <div class="flex items-start justify-between gap-3">
          <p class="text-sm text-ink-300">{{ 'dashboard.inactiveMembers' | transloco }}</p>
          <div
            aria-hidden="true"
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-signal-500 to-signal-600 text-white"
          >
            <svg lucideUserMinus aria-hidden="true"></svg>
          </div>
        </div>
        <p data-testid="inactive-count" class="mt-1 text-3xl font-bold text-white">
          {{ data.inactiveCount }}
        </p>
      </div>
    }
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
