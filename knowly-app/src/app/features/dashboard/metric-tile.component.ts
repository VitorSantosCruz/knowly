import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
import { UIChart } from 'primeng/chart';
import { MetricFetcher, createMetricFetcher } from '../../core/metric-fetcher';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { Period } from './period-filter.component';

export interface SparklineDay {
  date: string;
  count: number;
}

export interface SparklineChartData {
  labels: string[];
  datasets: { data: number[] }[];
}

export function toSparklineData(days: SparklineDay[]): SparklineChartData {
  return {
    labels: days.map((day) => day.date),
    datasets: [{ data: days.map((day) => day.count) }],
  };
}

const SPARKLINE_OPTIONS = {
  plugins: { legend: { display: false } },
  scales: { x: { display: false }, y: { display: false } },
  elements: { point: { radius: 0 } },
  maintainAspectRatio: false,
};

@Component({
  selector: 'app-metric-tile',
  imports: [ErrorStateComponent, NoAccessStateComponent, UIChart],
  template: `
    <div
      [attr.data-testid]="testId()"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (fetcher?.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher?.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher?.error() === 'network') {
        <app-error-state [traceId]="fetcher?.traceId()" />
      } @else if (fetcher?.data(); as data) {
        <p class="text-sm text-ink-500 dark:text-ink-400">{{ label() }}</p>
        <p data-testid="metric-tile-value" class="text-3xl font-bold text-ink-900 dark:text-white">
          {{ valueSelector()(data) }}
        </p>
        <div class="mt-2 h-12">
          <p-chart
            type="line"
            [data]="toChartData(data)"
            [options]="sparklineOptions"
            height="48px"
          />
        </div>
        <table class="sr-only">
          <caption>
            {{
              label()
            }}
            trend
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            @for (day of sparklineSelector()(data); track day.date) {
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
export class MetricTileComponent {
  private readonly http = inject(HttpClient);

  readonly period = input.required<Period>();
  readonly url = input.required<string>();
  readonly label = input.required<string>();
  readonly testId = input<string>('metric-tile');
  readonly valueSelector = input.required<(data: unknown) => number>();
  readonly sparklineSelector = input.required<(data: unknown) => SparklineDay[]>();

  protected readonly sparklineOptions = SPARKLINE_OPTIONS;
  protected fetcher?: MetricFetcher<unknown>;

  constructor() {
    effect(() => {
      const period = this.period();
      this.fetcher ??= createMetricFetcher(this.http, this.url());
      this.fetcher.load({ period });
    });
  }

  protected toChartData(data: unknown): SparklineChartData {
    return toSparklineData(this.sparklineSelector()(data));
  }
}
