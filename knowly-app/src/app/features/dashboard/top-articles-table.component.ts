import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
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
  selector: 'app-top-articles-table',
  imports: [ErrorStateComponent, NoAccessStateComponent, TranslocoPipe],
  template: `
    <div
      data-testid="top-articles-table"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <input
          data-testid="article-search"
          type="text"
          class="mb-3 w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
          [placeholder]="'dashboard.searchArticles' | transloco"
          [value]="searchTerm()"
          (input)="searchTerm.set($any($event.target).value)"
        />
        <table class="w-full">
          <tbody>
            @for (article of filteredArticles(); track article.id) {
              <tr data-testid="article-row">
                <td>{{ article.title }}</td>
                <td class="text-right">{{ article.useCount }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class TopArticlesTableComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly searchTerm = signal('');

  protected readonly fetcher = createMetricFetcher<ArticleUsageResponse>(
    this.http,
    '/api/tenants/metrics/articles/usage',
  );

  protected readonly filteredArticles = computed<ArticleUsage[]>(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const articles = this.fetcher.data()?.articles ?? [];
    return term
      ? articles.filter((article) => article.title.toLowerCase().includes(term))
      : articles;
  });

  ngOnInit(): void {
    this.fetcher.load();
  }
}
