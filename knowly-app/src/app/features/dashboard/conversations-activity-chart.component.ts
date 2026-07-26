import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
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

@Component({
  selector: 'app-conversations-activity-chart',
  imports: [ErrorStateComponent, NoAccessStateComponent, ChartCanvasComponent],
  template: `
    <div
      data-testid="conversations-activity-chart"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <app-chart-canvas type="bar" [data]="toBarData(data)" height="220px" />
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
