import { Component, input } from '@angular/core';
import { ChartCanvasComponent } from '../../shared/chart-canvas.component';
import {
  SPARKLINE_OPTIONS,
  SparklineChartData,
  SparklineDay,
  toSparklineData,
} from './metric-tile.component';

/**
 * Presentational gradient-styled stat card, originally built for
 * `GlobalDashboardPageComponent` only (see
 * specify/features/global-staff-dashboard-trends/SPEC.md's "Out of scope",
 * amended 2026-07-31: `metric-tile.component.ts` now shares this same
 * gradient chrome on the tenant dashboard too — see DECISIONS.md).
 * Purely presentational: label/subtitle/value/percentChange are all
 * inputs, no fetch, no `MetricFetcher`. The icon is supplied by each call
 * site via `<ng-content select="[icon]">` so this component never needs to
 * import all the Lucide icon components itself.
 */
@Component({
  selector: 'app-gradient-stat-card',
  imports: [ChartCanvasComponent],
  host: {
    class: 'block h-full',
  },
  template: `
    <div
      [attr.data-testid]="testId()"
      class="enter-fluid relative flex h-full flex-col overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
    >
      <div class="flex items-start justify-between gap-3">
        <div>
          <p class="text-sm text-ink-300">{{ label() }}</p>
          @if (disabled()) {
            <p data-testid="stat-card-coming-soon" class="mt-1 text-lg font-semibold text-ink-400">
              {{ comingSoonLabel() }}
            </p>
          } @else {
            <p data-testid="stat-card-value" class="mt-1 text-3xl font-bold text-white">
              {{ value() }}
            </p>
          }
        </div>
        <div
          aria-hidden="true"
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-signal-500 to-signal-600 text-white"
        >
          <ng-content select="[icon]" />
        </div>
      </div>

      @if (!disabled() && showSparkline() && hasSparklineData()) {
        <div class="mt-2 h-12">
          <app-chart-canvas
            type="line"
            [data]="chartData()"
            [options]="sparklineOptions"
            height="48px"
          />
        </div>
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
              @for (day of sparklineData(); track day.date) {
                <tr>
                  <td>{{ day.date }}</td>
                  <td>{{ day.count }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (subtitle()) {
        <p class="mt-2 text-xs text-ink-400">{{ subtitle() }}</p>
      }

      @if (!disabled() && hasBadge()) {
        <span
          data-testid="stat-card-badge"
          [class]="badgeClass()"
          class="mt-3 inline-flex w-fit items-center gap-1 self-start rounded-full px-2 py-0.5 text-xs font-semibold"
        >
          {{ badgeText() }}
        </span>
      }
    </div>
  `,
})
export class GradientStatCardComponent {
  readonly testId = input<string>('stat-card');
  readonly label = input.required<string>();
  readonly subtitle = input<string | undefined>(undefined);
  readonly value = input<number | undefined>(undefined);
  readonly percentChange = input<number | null | undefined>(undefined);
  /** Renders a muted "coming soon" label instead of a value; no badge. */
  readonly disabled = input(false);
  readonly comingSoonLabel = input<string>('Coming soon');
  /** Day-bucketed series for this card's sparkline, already fetched by the parent page.
   * `undefined`/empty renders no chart (before the owning fetch has succeeded). */
  readonly sparklineData = input<SparklineDay[] | undefined>(undefined);
  readonly showSparkline = input<boolean>(true);

  protected readonly sparklineOptions = SPARKLINE_OPTIONS;

  protected hasSparklineData(): boolean {
    const data = this.sparklineData();
    return data !== undefined && data.length > 0;
  }

  protected chartData(): SparklineChartData {
    return toSparklineData(this.sparklineData() ?? []);
  }

  protected hasBadge(): boolean {
    const change = this.percentChange();
    return change !== null && change !== undefined;
  }

  protected badgeText(): string {
    const change = this.percentChange();
    if (change === null || change === undefined) {
      return '';
    }
    const sign = change >= 0 ? '+' : '-';
    return `${sign}${Math.abs(change)}%`;
  }

  protected badgeClass(): string {
    const change = this.percentChange();
    if (change === null || change === undefined) {
      return '';
    }
    return change >= 0 ? 'bg-emerald-500/15 text-emerald-400' : 'bg-red-500/15 text-red-400';
  }
}
