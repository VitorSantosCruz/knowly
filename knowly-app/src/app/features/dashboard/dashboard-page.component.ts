import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ArticleCountCardComponent } from './article-count-card.component';
import { ArticleUsageListComponent } from './article-usage-list.component';
import { ConversationsCardComponent } from './conversations-card.component';
import { MembersBreakdownCardComponent } from './members-breakdown-card.component';
import { MessagesCardComponent } from './messages-card.component';
import { Period, PeriodFilterComponent } from './period-filter.component';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    TranslocoPipe,
    RouterLink,
    ArticleCountCardComponent,
    ArticleUsageListComponent,
    ConversationsCardComponent,
    MessagesCardComponent,
    MembersBreakdownCardComponent,
    PeriodFilterComponent,
  ],
  template: `
    <div data-testid="dashboard-page" class="page-shell grid gap-4 sm:grid-cols-2">
      <app-period-filter [(period)]="period" class="sm:col-span-2" />
      <app-article-count-card />
      <app-article-usage-list />
      <app-conversations-card />
      <app-messages-card />
      <app-members-breakdown-card />
      <a
        data-testid="articles-link"
        routerLink="/articles"
        class="text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:text-signal-600 dark:text-ink-300 dark:hover:text-signal-400 sm:col-span-2"
      >
        {{ 'dashboard.articlesLink' | transloco }}
      </a>
    </div>
  `,
})
export class DashboardPageComponent {
  protected readonly period = signal<Period>('30d');
}
