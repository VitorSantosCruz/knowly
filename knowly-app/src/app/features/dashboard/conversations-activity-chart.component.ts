import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MetricFetcher, createMetricFetcher } from '../../core/metric-fetcher';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { Period } from './period-filter.component';

export interface DailyCountRow {
  date: string;
  count: number;
}

export interface ConversationsTimeseriesResponse {
  days: DailyCountRow[];
}

export interface BarChartData {
  labels: string[];
  datasets: { data: number[] }[];
}

export function toBarData(response: ConversationsTimeseriesResponse): BarChartData {
  return {
    labels: response.days.map((day) => day.date),
    datasets: [{ data: response.days.map((day) => day.count) }],
  };
}

/**
 * Bar/axis colors tuned for legibility on the dark gradient card
 * background (`from-ink-900 to-ink-950`) this chart now shares with
 * `gradient-stat-card.component.ts`; Chart.js's default bar/axis colors
 * are dark grays that read as near-invisible on that background.
 */
const BAR_OPTIONS = {
  plugins: { legend: { display: false } },
  scales: {
    x: { ticks: { color: '#c0a9e3' }, grid: { color: 'rgba(192, 169, 227, 0.15)' } },
    y: { ticks: { color: '#c0a9e3' }, grid: { color: 'rgba(192, 169, 227, 0.15)' } },
  },
  elements: { bar: { backgroundColor: '#7c4fb8' } },
};

@Component({
  selector: 'app-conversations-activity-chart',
  imports: [ErrorStateComponent, NoAccessStateComponent, ChartCanvasComponent, TranslocoPipe],
  template: `
    <div
      data-testid="conversations-activity-chart"
      class="enter-fluid relative overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
    >
      <p class="text-sm font-semibold text-white">
        {{ 'dashboard.trends.conversationsActivityChartLabel' | transloco }}
      </p>
      <p class="mb-3 text-xs text-ink-400">
        {{ 'dashboard.trends.conversationsActivityChartSubtitle' | transloco }}
      </p>

      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <app-chart-canvas
          type="bar"
          [data]="toBarData(data)"
          [options]="barOptions"
          height="220px"
        />
        <table class="sr-only">
          <caption>
            Conversations per day
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th>Count</th>
            </tr>
          </thead>
          <tbody>
            @for (day of data.days; track day.date) {
              <tr>
                <td>{{ day.date }}</td>
                <td>{{ day.count }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class ConversationsActivityChartComponent {
  private readonly http = inject(HttpClient);

  readonly period = input.required<Period>();

  protected readonly barOptions = BAR_OPTIONS;

  private fetcherInstance?: MetricFetcher<ConversationsTimeseriesResponse>;

  get fetcher(): MetricFetcher<ConversationsTimeseriesResponse> {
    this.fetcherInstance ??= createMetricFetcher<ConversationsTimeseriesResponse>(
      this.http,
      '/api/tenants/metrics/conversations/timeseries',
    );
    return this.fetcherInstance;
  }

  constructor() {
    effect(() => {
      const period = this.period();
      this.fetcher.load({ period });
    });
  }

  protected toBarData(data: ConversationsTimeseriesResponse): BarChartData {
    return toBarData(data);
  }
}
