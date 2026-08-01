import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { DailyCountRow, TrendChartData } from './trend-chart-data';

export function toNewTenantsChartData(rows: DailyCountRow[]): TrendChartData {
  return {
    labels: rows.map((row) => row.date),
    datasets: [{ data: rows.map((row) => row.count) }],
  };
}

// Single, unlabeled dataset — the card title above already says what this is, so a legend would
// only ever show a "undefined" entry for the dataset's missing `label`.
const CHART_OPTIONS = { plugins: { legend: { display: false } } };

/**
 * Presentational "new tenants per day" trend chart. Pre-fetched-data mode
 * only (`data`/`error` inputs) — `GlobalDashboardPageComponent` owns the
 * single `/trends` fetch, per PLAN.md's "single page-level call" decision.
 */
@Component({
  selector: 'app-new-tenants-trend-chart',
  imports: [ChartCanvasComponent, ErrorStateComponent, TranslocoPipe],
  template: `
    <div
      data-testid="new-tenants-trend-chart"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      <p class="text-sm font-semibold text-ink-900 dark:text-white">
        {{ 'dashboard.trends.newTenantsChartLabel' | transloco }}
      </p>
      <p class="mb-3 text-xs text-ink-500 dark:text-ink-400">
        {{ 'dashboard.trends.newTenantsChartSubtitle' | transloco }}
      </p>

      @if (error()) {
        <app-error-state />
      } @else {
        <app-chart-canvas
          type="line"
          [data]="toNewTenantsChartData(data())"
          [options]="chartOptions"
          height="220px"
        />
        <table class="sr-only">
          <caption>
            {{
              'dashboard.trends.newTenantsChartLabel' | transloco
            }}
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th>Count</th>
            </tr>
          </thead>
          <tbody>
            @for (row of data(); track row.date) {
              <tr>
                <td>{{ row.date }}</td>
                <td>{{ row.count }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
})
export class NewTenantsTrendChartComponent {
  readonly data = input.required<DailyCountRow[]>();
  readonly error = input<boolean>(false);

  protected readonly chartOptions = CHART_OPTIONS;

  protected toNewTenantsChartData(rows: DailyCountRow[]): TrendChartData {
    return toNewTenantsChartData(rows);
  }
}
