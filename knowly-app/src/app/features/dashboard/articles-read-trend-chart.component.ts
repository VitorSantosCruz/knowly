import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { DailyCountRow, TrendChartData } from './trend-chart-data';

export function toArticlesReadChartData(rows: DailyCountRow[]): TrendChartData {
  return {
    labels: rows.map((row) => row.date),
    datasets: [{ data: rows.map((row) => row.count) }],
  };
}

// Single, unlabeled dataset — the card title above already says what this is, so a legend would
// only ever show a "undefined" entry for the dataset's missing `label`.
const CHART_OPTIONS = { plugins: { legend: { display: false } } };

/**
 * Presentational "articles read per day" trend chart. Pre-fetched-data
 * mode only (`data`/`error` inputs) — see
 * `new-tenants-trend-chart.component.ts`'s equivalent header comment.
 */
@Component({
  selector: 'app-articles-read-trend-chart',
  imports: [ChartCanvasComponent, ErrorStateComponent, TranslocoPipe],
  template: `
    <div
      data-testid="articles-read-trend-chart"
      class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      <p class="text-sm font-semibold text-ink-900 dark:text-white">
        {{ 'dashboard.trends.articlesReadChartLabel' | transloco }}
      </p>
      <p class="mb-3 text-xs text-ink-500 dark:text-ink-400">
        {{ 'dashboard.trends.articlesReadChartSubtitle' | transloco }}
      </p>

      @if (error()) {
        <app-error-state />
      } @else {
        <app-chart-canvas
          type="line"
          [data]="toArticlesReadChartData(data())"
          [options]="chartOptions"
          height="220px"
        />
        <!-- position:fixed, not the sr-only utility's position:absolute: browser table-layout
             fixup synthesizes an anonymous, unclipped table around a table-role descendant
             (tbody/tr) whenever its parent stops being display:table, and that synthesized
             wrapper can retain a stale content height across a resize even inside a properly
             clipped (position:absolute) div. position:fixed removes this whole subtree from
             every ancestor's layout/scroll computation outright, sidestepping the fixup
             entirely rather than relying on it being clipped correctly. -->
        <div
          data-testid="a11y-table"
          class="fixed h-px w-px overflow-hidden border-0 p-0 whitespace-nowrap opacity-0 [clip-path:inset(50%)]"
        >
          <table>
            <caption>
              {{
                'dashboard.trends.articlesReadChartLabel' | transloco
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
        </div>
      }
    </div>
  `,
})
export class ArticlesReadTrendChartComponent {
  readonly data = input.required<DailyCountRow[]>();
  readonly error = input<boolean>(false);

  protected readonly chartOptions = CHART_OPTIONS;

  protected toArticlesReadChartData(rows: DailyCountRow[]): TrendChartData {
    return toArticlesReadChartData(rows);
  }
}
