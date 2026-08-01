import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MetricFetcher, createMetricFetcher } from '../../core/metric-fetcher';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { Period } from './period-filter.component';

export interface DailyRoleCountRow {
  date: string;
  userCount: number;
  assistantCount: number;
}

export interface MessagesTimeseriesResponse {
  days: DailyRoleCountRow[];
}

export interface DonutChartData {
  labels: string[];
  datasets: { data: number[] }[];
}

export function toDonutData(response: MessagesTimeseriesResponse): DonutChartData {
  const userTotal = response.days.reduce((sum, day) => sum + day.userCount, 0);
  const assistantTotal = response.days.reduce((sum, day) => sum + day.assistantCount, 0);

  return {
    labels: ['USER', 'ASSISTANT'],
    datasets: [{ data: [userTotal, assistantTotal] }],
  };
}

/**
 * Legend/label color tuned for legibility on the dark gradient card
 * background (`from-ink-900 to-ink-950`) this chart now shares with
 * `gradient-stat-card.component.ts`; Chart.js's default legend text color
 * is a dark gray that reads as near-invisible on that background.
 */
const DONUT_OPTIONS = {
  plugins: { legend: { labels: { color: '#c0a9e3' } } },
};

@Component({
  selector: 'app-message-split-chart',
  imports: [ErrorStateComponent, NoAccessStateComponent, ChartCanvasComponent, TranslocoPipe],
  template: `
    <div
      data-testid="message-split-chart"
      class="enter-fluid relative overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
    >
      <p class="text-sm font-semibold text-white">
        {{ 'dashboard.trends.messageSplitChartLabel' | transloco }}
      </p>
      <p class="mb-3 text-xs text-ink-400">
        {{ 'dashboard.trends.messageSplitChartSubtitle' | transloco }}
      </p>

      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <app-chart-canvas
          type="doughnut"
          [data]="toDonutData(data)"
          [options]="donutOptions"
          height="220px"
        />
        <table class="sr-only">
          <caption>
            Message split
          </caption>
          <thead>
            <tr>
              <th>Role</th>
              <th>Count</th>
            </tr>
          </thead>
          <tbody>
            @for (row of toRows(data); track row.label) {
              <tr>
                <td>{{ row.label }}</td>
                <td>{{ row.count }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class MessageSplitChartComponent {
  private readonly http = inject(HttpClient);

  readonly period = input.required<Period>();

  protected readonly donutOptions = DONUT_OPTIONS;

  private fetcherInstance?: MetricFetcher<MessagesTimeseriesResponse>;

  get fetcher(): MetricFetcher<MessagesTimeseriesResponse> {
    this.fetcherInstance ??= createMetricFetcher<MessagesTimeseriesResponse>(
      this.http,
      '/api/tenants/metrics/messages/timeseries',
    );
    return this.fetcherInstance;
  }

  constructor() {
    effect(() => {
      const period = this.period();
      this.fetcher.load({ period });
    });
  }

  protected toDonutData(data: MessagesTimeseriesResponse): DonutChartData {
    return toDonutData(data);
  }

  protected toRows(data: MessagesTimeseriesResponse): { label: string; count: number }[] {
    const chartData = toDonutData(data);
    return chartData.labels.map((label, index) => {
      const count = chartData.datasets[0]?.data.at(index) ?? 0;
      return { label, count };
    });
  }
}
