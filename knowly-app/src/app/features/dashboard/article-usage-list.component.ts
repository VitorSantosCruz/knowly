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
      style="animation-delay: 60ms"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <ul>
          @for (article of data.articles; track article.id) {
            <li
              data-testid="usage-item"
              class="flex justify-between rounded-lg px-1 py-1.5 text-sm transition-colors duration-fast ease-fluid hover:bg-ink-50 dark:hover:bg-ink-800/60"
            >
              <span class="text-ink-800 dark:text-ink-100">{{ article.title }}</span>
              <span class="text-ink-500 dark:text-ink-400">{{ article.useCount }}</span>
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
