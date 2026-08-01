import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MetricFetcher, createMetricFetcher } from '../../core/metric-fetcher';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
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

/**
 * Line/point colors tuned for legibility against the dark gradient card
 * background (`from-ink-900 to-ink-950`) the tile now shares with
 * `gradient-stat-card.component.ts` — the Chart.js defaults were picked
 * for the old plain white/`ink-900` card and read as near-invisible on
 * the darker gradient.
 */
const SPARKLINE_OPTIONS = {
  plugins: { legend: { display: false } },
  scales: { x: { display: false }, y: { display: false } },
  elements: {
    point: { radius: 0 },
    line: { borderColor: '#c0a9e3', backgroundColor: 'rgba(192, 169, 227, 0.2)' },
  },
  maintainAspectRatio: false,
};

@Component({
  selector: 'app-metric-tile',
  imports: [ErrorStateComponent, NoAccessStateComponent, ChartCanvasComponent, TranslocoPipe],
  template: `
    <div
      [attr.data-testid]="testId()"
      class="enter-fluid relative overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
    >
      @if (disabled()) {
        <p class="text-sm text-ink-300">{{ label() }}</p>
        <p data-testid="metric-tile-coming-soon" class="text-lg font-semibold text-ink-400">
          {{ 'dashboard.comingSoon' | transloco }}
        </p>
      } @else if (value() !== undefined) {
        <p class="text-sm text-ink-300">{{ label() }}</p>
        <p data-testid="metric-tile-value" class="text-3xl font-bold text-white">
          {{ value() }}
        </p>
      } @else if (fetcher?.loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (fetcher?.error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (fetcher?.error() === 'network') {
        <app-error-state [traceId]="fetcher?.traceId()" />
      } @else if (fetcher?.data(); as data) {
        <p class="text-sm text-ink-300">{{ label() }}</p>
        <p data-testid="metric-tile-value" class="text-3xl font-bold text-white">
          {{ valueSelector()!(data) }}
        </p>
        <div class="mt-2 h-12">
          <app-chart-canvas
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
            @for (day of sparklineSelector()!(data); track day.date) {
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

  readonly period = input<Period | undefined>(undefined);
  readonly url = input<string | undefined>(undefined);
  readonly label = input.required<string>();
  readonly testId = input<string>('metric-tile');
  readonly valueSelector = input<((data: unknown) => number) | undefined>(undefined);
  readonly sparklineSelector = input<((data: unknown) => SparklineDay[]) | undefined>(undefined);

  /** Additive "pre-fetched value" mode — see DECISIONS.md. When set, the tile renders this
   * number directly and skips its own fetch entirely (no sparkline chart/table either). */
  readonly value = input<number | undefined>(undefined);
  /** Renders a visibly muted "coming soon" label instead of a value; no fetch attempted. */
  readonly disabled = input(false);

  protected readonly sparklineOptions = SPARKLINE_OPTIONS;
  protected fetcher?: MetricFetcher<unknown>;

  constructor() {
    effect(() => {
      const url = this.url();
      const period = this.period();

      if (this.disabled() || this.value() !== undefined || url === undefined) {
        return;
      }

      this.fetcher ??= createMetricFetcher(this.http, url);
      this.fetcher.load(period !== undefined ? { period } : undefined);
    });
  }

  protected toChartData(data: unknown): SparklineChartData {
    return toSparklineData(this.sparklineSelector()!(data));
  }
}
