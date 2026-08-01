import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  LucideBookOpenCheck,
  LucideBotMessageSquare,
  LucideMessagesSquare,
  LucideUser,
  LucideUsers,
} from '@lucide/angular';
import { ConversationsActivityChartComponent } from './conversations-activity-chart.component';
import { ExportButtonComponent } from './export-button.component';
import { MembersBreakdownCardComponent } from './members-breakdown-card.component';
import { MessageSplitChartComponent } from './message-split-chart.component';
import { MetricTileComponent, SparklineDay } from './metric-tile.component';
import { Period, PeriodFilterComponent } from './period-filter.component';
import { TopArticlesTableComponent } from './top-articles-table.component';

interface DailyCountResponse {
  days: { date: string; count: number }[];
}

interface DailyRoleCountResponse {
  days: { date: string; userCount: number; assistantCount: number }[];
}

@Component({
  selector: 'app-dashboard-page',
  imports: [
    TranslocoPipe,
    RouterLink,
    MetricTileComponent,
    TopArticlesTableComponent,
    MembersBreakdownCardComponent,
    PeriodFilterComponent,
    ExportButtonComponent,
    MessageSplitChartComponent,
    ConversationsActivityChartComponent,
    LucideBookOpenCheck,
    LucideMessagesSquare,
    LucideUser,
    LucideBotMessageSquare,
    LucideUsers,
  ],
  template: `
    <div data-testid="dashboard-page" class="page-shell space-y-4">
      <div class="flex items-center justify-between gap-4">
        <app-period-filter [(period)]="period" />
        <app-export-button [period]="period()" />
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <app-metric-tile
          testId="article-count-tile"
          [period]="period()"
          url="/api/tenants/metrics/articles/timeseries"
          label="{{ 'dashboard.tiles.articles' | transloco }}"
          subtitle="{{ 'dashboard.trends.articlesSubtitle' | transloco }}"
          [valueSelector]="dailyCountValueSelector"
          [sparklineSelector]="dailyCountSparklineSelector"
        >
          <svg lucideBookOpenCheck icon aria-hidden="true"></svg>
        </app-metric-tile>
        <app-metric-tile
          testId="conversations-tile"
          [period]="period()"
          url="/api/tenants/metrics/conversations/timeseries"
          label="{{ 'dashboard.tiles.conversations' | transloco }}"
          subtitle="{{ 'dashboard.trends.conversationsSubtitle' | transloco }}"
          [valueSelector]="dailyCountValueSelector"
          [sparklineSelector]="dailyCountSparklineSelector"
        >
          <svg lucideMessagesSquare icon aria-hidden="true"></svg>
        </app-metric-tile>
        <app-metric-tile
          testId="user-messages-tile"
          [period]="period()"
          url="/api/tenants/metrics/messages/timeseries"
          label="{{ 'dashboard.tiles.userMessages' | transloco }}"
          subtitle="{{ 'dashboard.trends.userMessagesSubtitle' | transloco }}"
          [valueSelector]="userMessagesValueSelector"
          [sparklineSelector]="userMessagesSparklineSelector"
        >
          <svg lucideUser icon aria-hidden="true"></svg>
        </app-metric-tile>
        <app-metric-tile
          testId="assistant-messages-tile"
          [period]="period()"
          url="/api/tenants/metrics/messages/timeseries"
          label="{{ 'dashboard.tiles.assistantMessages' | transloco }}"
          subtitle="{{ 'dashboard.trends.assistantMessagesSubtitle' | transloco }}"
          [valueSelector]="assistantMessagesValueSelector"
          [sparklineSelector]="assistantMessagesSparklineSelector"
        >
          <svg lucideBotMessageSquare icon aria-hidden="true"></svg>
        </app-metric-tile>
        <app-metric-tile
          testId="active-members-tile"
          [period]="period()"
          url="/api/tenants/metrics/members/timeseries"
          label="{{ 'dashboard.tiles.activeMembers' | transloco }}"
          subtitle="{{ 'dashboard.trends.activeMembersSubtitle' | transloco }}"
          [valueSelector]="activeMembersValueSelector"
          [sparklineSelector]="dailyCountSparklineSelector"
        >
          <svg lucideUsers icon aria-hidden="true"></svg>
        </app-metric-tile>
      </div>

      <div class="grid gap-4 lg:grid-cols-2">
        <app-message-split-chart [period]="period()" />
        <app-conversations-activity-chart [period]="period()" />
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <app-top-articles-table class="sm:col-span-2" />
        <app-members-breakdown-card class="sm:col-span-2" />
        <a
          data-testid="articles-link"
          routerLink="/articles"
          class="text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:text-signal-600 dark:text-ink-300 dark:hover:text-signal-400 sm:col-span-2"
        >
          {{ 'dashboard.articlesLink' | transloco }}
        </a>
      </div>
    </div>
  `,
})
export class DashboardPageComponent {
  protected readonly period = signal<Period>('30d');

  protected readonly dailyCountValueSelector = (data: unknown) =>
    (data as DailyCountResponse).days.reduce((sum, day) => sum + day.count, 0);

  protected readonly dailyCountSparklineSelector = (data: unknown): SparklineDay[] =>
    (data as DailyCountResponse).days;

  protected readonly userMessagesValueSelector = (data: unknown) =>
    (data as DailyRoleCountResponse).days.reduce((sum, day) => sum + day.userCount, 0);

  protected readonly userMessagesSparklineSelector = (data: unknown): SparklineDay[] =>
    (data as DailyRoleCountResponse).days.map((day) => ({ date: day.date, count: day.userCount }));

  protected readonly assistantMessagesValueSelector = (data: unknown) =>
    (data as DailyRoleCountResponse).days.reduce((sum, day) => sum + day.assistantCount, 0);

  protected readonly assistantMessagesSparklineSelector = (data: unknown): SparklineDay[] =>
    (data as DailyRoleCountResponse).days.map((day) => ({
      date: day.date,
      count: day.assistantCount,
    }));

  protected readonly activeMembersValueSelector = (data: unknown) =>
    (data as DailyCountResponse).days.at(-1)?.count ?? 0;
}
