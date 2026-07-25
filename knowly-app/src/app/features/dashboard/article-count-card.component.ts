import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

interface ArticleCountResponse {
  totalCount: number;
}

@Component({
  selector: 'app-article-count-card',
  imports: [ErrorStateComponent, NoAccessStateComponent],
  template: `
    <div
      data-testid="article-count-card"
      class="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-slate-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <p class="text-3xl font-bold text-slate-900 dark:text-white">{{ data.totalCount }}</p>
      }
    </div>
  `,
})
export class ArticleCountCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly fetcher = createMetricFetcher<ArticleCountResponse>(
    this.http,
    '/api/tenants/metrics/articles',
  );

  ngOnInit(): void {
    this.fetcher.load();
  }
}
