import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

interface MessagesResponse {
  sentCount: number;
  receivedCount: number;
}

@Component({
  selector: 'app-messages-card',
  imports: [ErrorStateComponent, NoAccessStateComponent],
  template: `
    <div
      data-testid="messages-card"
      style="animation-delay: 180ms"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <div class="flex gap-6">
          <p class="text-3xl font-bold text-ink-900 dark:text-white">{{ data.sentCount }}</p>
          <p class="text-3xl font-bold text-ink-900 dark:text-white">
            {{ data.receivedCount }}
          </p>
        </div>
      }
    </div>
  `,
})
export class MessagesCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly fetcher = createMetricFetcher<MessagesResponse>(
    this.http,
    '/api/tenants/metrics/messages',
  );

  ngOnInit(): void {
    this.fetcher.load();
  }
}
