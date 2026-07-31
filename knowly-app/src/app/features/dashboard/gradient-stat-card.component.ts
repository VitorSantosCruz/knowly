import { Component, input } from '@angular/core';

/**
 * Presentational gradient-styled stat card replacing the plain
 * `metric-tile.component.ts` presentation on `GlobalDashboardPageComponent`
 * only (`metric-tile.component.ts` itself is unchanged, see
 * specify/features/global-staff-dashboard-trends/SPEC.md's "Out of scope").
 * Purely presentational: label/subtitle/value/percentChange are all
 * inputs, no fetch, no `MetricFetcher`. The icon is supplied by each call
 * site via `<ng-content select="[icon]">` so this component never needs to
 * import all the Lucide icon components itself.
 */
@Component({
  selector: 'app-gradient-stat-card',
  template: `
    <div
      [attr.data-testid]="testId()"
      class="enter-fluid relative overflow-hidden rounded-2xl border border-ink-200/70 bg-gradient-to-br from-ink-900 to-ink-950 p-5 text-white shadow-lg shadow-ink-900/10 transition-shadow duration-base ease-fluid dark:border-ink-800/70"
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

      @if (subtitle()) {
        <p class="mt-2 text-xs text-ink-400">{{ subtitle() }}</p>
      }

      @if (!disabled() && hasBadge()) {
        <span
          data-testid="stat-card-badge"
          [class]="badgeClass()"
          class="mt-3 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold"
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
