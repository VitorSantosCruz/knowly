import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ArticleCountCardComponent } from './article-count-card.component';
import { ArticleUsageListComponent } from './article-usage-list.component';
import { ConversationsCardComponent } from './conversations-card.component';
import { MessagesCardComponent } from './messages-card.component';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    TranslocoPipe,
    RouterLink,
    ArticleCountCardComponent,
    ArticleUsageListComponent,
    ConversationsCardComponent,
    MessagesCardComponent,
  ],
  template: `
    <div data-testid="dashboard-page" class="grid gap-4 p-6 sm:grid-cols-2">
      <app-article-count-card />
      <app-article-usage-list />
      <app-conversations-card />
      <app-messages-card />
      <a
        data-testid="articles-link"
        routerLink="/articles"
        class="text-sm font-medium text-indigo-600 hover:text-indigo-500 sm:col-span-2"
      >
        {{ 'dashboard.articlesLink' | transloco }}
      </a>
    </div>
  `,
})
export class DashboardPageComponent {}
