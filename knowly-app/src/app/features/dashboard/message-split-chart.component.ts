import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
import { UIChart } from 'primeng/chart';
import { MetricFetcher, createMetricFetcher } from '../../core/metric-fetcher';
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

@Component({
  selector: 'app-message-split-chart',
  imports: [ErrorStateComponent, NoAccessStateComponent, UIChart],
  template: `
    <div
      data-testid="message-split-chart"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher.error() === 'network') {
        <app-error-state [traceId]="fetcher.traceId()" />
      } @else if (fetcher.data(); as data) {
        <p-chart type="doughnut" [data]="toDonutData(data)" height="220px" />
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
    return chartData.labels.map((label, index) => ({
      label,
      count: chartData.datasets[0].data[index],
    }));
  }
}
