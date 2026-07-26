import { HttpClient } from '@angular/common/http';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { InputText } from 'primeng/inputtext';
import { Table } from 'primeng/table';
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
  imports: [ErrorStateComponent, NoAccessStateComponent, Table, InputText, TranslocoPipe],
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
          pInputText
          class="mb-3 w-full"
          [placeholder]="'dashboard.searchArticles' | transloco"
          (input)="dt.filterGlobal($any($event.target).value, 'contains')"
        />
        <p-table #dt [value]="data.articles" [globalFilterFields]="['title']">
          <ng-template #body let-article>
            <tr data-testid="article-row">
              <td>{{ article.title }}</td>
              <td class="text-right">{{ article.useCount }}</td>
            </tr>
          </ng-template>
        </p-table>
      }
    </div>
  `,
})
export class TopArticlesTableComponent implements OnInit {
  private readonly http = inject(HttpClient);

  @ViewChild('dt') protected dt!: Table;

  protected readonly fetcher = createMetricFetcher<ArticleUsageResponse>(
    this.http,
    '/api/tenants/metrics/articles/usage',
  );

  ngOnInit(): void {
    this.fetcher.load();
  }
}
