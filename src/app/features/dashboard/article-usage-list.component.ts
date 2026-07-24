import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

interface ArticleUsage {
  id: number;
  title: string;
  useCount: number;
}

interface ArticleUsageResponse {
  articles: ArticleUsage[];
}

@Component({
  selector: 'app-article-usage-list',
  imports: [ErrorStateComponent, NoAccessStateComponent],
  template: `
    <div
      data-testid="article-usage-list"
      class="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-slate-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <ul>
          @for (article of data.articles; track article.id) {
            <li data-testid="usage-item" class="flex justify-between py-1 text-sm">
              <span>{{ article.title }}</span>
              <span class="text-slate-500">{{ article.useCount }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class ArticleUsageListComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly fetcher = createMetricFetcher<ArticleUsageResponse>(
    this.http,
    '/api/tenants/metrics/articles/usage',
  );

  ngOnInit(): void {
    this.fetcher.load();
  }
}
