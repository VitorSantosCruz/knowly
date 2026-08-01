# PLAN — global-staff-dashboard-sparklines (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Depends on
> `knowly-api/specify/features/global-staff-dashboard-sparklines/PLAN.md`
> for the `totalTenantsPerDay`/`staffCountPerDay` contract.

## Architectural decisions

- **`GradientStatCardComponent` gains sparkline support directly —
  `GlobalDashboardPageComponent`'s cards are not swapped for
  `MetricTileComponent`.** `MetricTileComponent` is a *self-fetching*
  component (its own `MetricFetcher`, its own `url`/`period` inputs,
  its own per-tile loading/error state) — the right shape for the
  tenant dashboard's five tiles, each backed by an independent
  timeseries endpoint with per-tile failure isolation
  (`dashboard-analytics` REQ-9). `GlobalDashboardPageComponent` is the
  opposite shape: **one** `GET /api/staff/metrics/global/trends` call
  at the page level already backs all four cards, with page-level
  403-vs-network error classification (`classifyMetricError`) already
  implemented directly in the page component (REQ-4/5/6). Swapping to
  `MetricTileComponent` would mean either (a) four redundant
  self-fetches of the same endpoint, undoing the page's existing single-
  call design, or (b) stretching `MetricTileComponent`'s "pre-fetched
  value" mode (`value`/`disabled` inputs, added for exactly this reason
  per `DECISIONS.md`'s `staff-global-dashboard` entry) to also carry a
  pre-fetched sparkline series through a fourth new pass-through input —
  at that point the component is a `GradientStatCardComponent` in
  disguise, and `GlobalDashboardPageComponent` isn't even using
  `MetricTileComponent` for its cards today (it already uses
  `GradientStatCardComponent`, per the shared gradient chrome noted in
  `DECISIONS.md`). **Decision:** extend `GradientStatCardComponent`
  itself with the same additive, optional, presentational sparkline
  inputs that `MetricTileComponent`'s self-fetching path already renders
  — reusing the chart configuration/markup, not forking a second
  component and not forcing this page into a self-fetching model it was
  deliberately built to avoid. This follows the exact reasoning already
  recorded in `DECISIONS.md`'s `staff-global-dashboard` entry ("before
  adding a new self-fetching widget ... check whether the existing
  self-fetching component can be extended with an optional pre-fetched
  mode ... rather than forcing a page-level data shape into N redundant
  per-widget HTTP calls").
- **New optional inputs on `GradientStatCardComponent`:**
  `sparklineData: SparklineDay[] | undefined` (the day-bucketed series
  for this card, already fetched by the page) and `showSparkline: boolean
  = true`. When `sparklineData` is `undefined` or empty, no chart/table
  renders (covers REQ-4's "before trends succeeds" and REQ-6's "trends
  call never succeeded" states) — a card with no data behaves exactly as
  it does today (value/badge only), no new prop needed to explicitly
  suppress it beyond simply not passing `sparklineData`.
  `SparklineDay`/`SparklineChartData`/`toSparklineData` are imported from
  `metric-tile.component.ts` (already exported there) rather than
  redefined — one shared shape for "a day-bucketed chart-ready series,"
  not a second copy in `gradient-stat-card.component.ts`.
- **Sparkline rendering reuses `MetricTileComponent`'s exact Chart.js
  treatment**, including the `SPARKLINE_OPTIONS` constant (currently
  module-private to `metric-tile.component.ts`) — this constant is
  exported (renamed export, no behavior change) so both components
  import the same object rather than duplicating the tuned
  line/point colors, satisfying REQ-2 ("not a new/differently-styled
  chart implementation") and the sr-only `<table>` fallback markup
  (REQ, Accessibility NFR) is copied verbatim into
  `GradientStatCardComponent`'s template, keyed off the same
  `sparklineSelector`-equivalent (here, `sparklineData` is already the
  final `SparklineDay[]`, so no selector function is needed — the page
  passes the exact per-card slice directly, unlike `MetricTileComponent`,
  which extracts a slice out of one larger fetched object via
  `sparklineSelector`).
- **No change to `ChartCanvasComponent`** — both components already
  share it; `GradientStatCardComponent` imports it directly, same as
  `MetricTileComponent` does.
- **Page wiring is direct property binding, no new state/service.**
  `GlobalDashboardPageComponent` already holds `trends: Signal<GlobalTrendsDto
  | null>` and already updates it via `loadTrends(period)` on period
  change (REQ-3, unchanged flow). Each of the four
  `<app-gradient-stat-card>` call sites gains one new binding:
  `[sparklineData]="trends()?.<field>PerDay"` (`totalTenantsPerDay`,
  `newTenantsPerDay`, `articlesReadPerDay`, `staffCountPerDay` per
  REQ-1). No new signal, no new service — this is pure presentational
  data already sitting in the existing `trends` signal, matching this
  app's "state lives in services as signals" convention by not
  introducing a second copy of state the page already owns.
- **Graceful degradation (REQ-5/6) needs no new logic.** Because
  `sparklineData` is bound straight from the `trends` signal (not
  re-fetched independently per card), the existing `trendsError`
  handling already covers this: a failed `loadTrends` call leaves
  `trends()` at its last successful value (existing behavior, unchanged
  by this feature — `loadTrends`'s `catchError` only sets `trendsError`,
  it never clears `trends`), so the sparklines keep showing the last
  good data automatically. Before the first successful fetch, `trends()`
  is `null`, so `sparklineData` is `undefined` and the card renders with
  no chart — exactly REQ-4/6's required behavior, with zero new
  branching needed in the page component.

## Components and routes

- **Changed:** `gradient-stat-card.component.ts` — add `sparklineData`/
  `showSparkline` inputs, sparkline chart block + sr-only data table in
  the template (conditionally rendered, mirroring
  `metric-tile.component.ts`'s existing block), import
  `ChartCanvasComponent`, `SparklineDay`, `toSparklineData`,
  `SPARKLINE_OPTIONS` (exported) from `metric-tile.component.ts`.
- **Changed:** `metric-tile.component.ts` — export `SPARKLINE_OPTIONS`
  (currently a local `const`) so `gradient-stat-card.component.ts` can
  import it; no other change to this file (its own self-fetching
  behavior and existing call sites are untouched, per SPEC's "Out of
  scope").
- **Changed:** `global-dashboard-page.component.ts` — bind
  `[sparklineData]` on each of the four (non-disabled)
  `<app-gradient-stat-card>` elements to the matching field on
  `trends()`. No new imports beyond what's already there
  (`GlobalTrendsDto`'s type already includes `totalTenantsPerDay`/
  `staffCountPerDay` once the companion backend PLAN ships — this
  frontend PLAN's local `GlobalTrendsDto` interface, currently defined
  inline in this same file, gains the same two fields).
- No routing change — this page's route/guards are unaffected.

## Consumed API contracts

`GET /api/staff/metrics/global/trends` (unchanged path/params), response
gains two fields per the companion backend PLAN
(`knowly-api/specify/features/global-staff-dashboard-sparklines/PLAN.md`):

```ts
interface GlobalTrendsDto {
  newTenantsPerDay: DailyCountRow[]; // unchanged
  articlesReadPerDay: DailyCountRow[]; // unchanged
  totalTenants: PeriodComparisonDto; // unchanged
  newTenants: PeriodComparisonDto; // unchanged
  totalArticlesRead: PeriodComparisonDto; // unchanged
  staffCount: PeriodComparisonDto; // unchanged
  totalTenantsPerDay: DailyCountRow[]; // NEW
  staffCountPerDay: DailyCountRow[]; // NEW
}
```

`DailyCountRow` (existing type, `trend-chart-data.ts`) is structurally
identical to `metric-tile.component.ts`'s `SparklineDay` (`{ date:
string; count: number }`) — both already satisfy the same shape, no
conversion needed when passing `trends()?.totalTenantsPerDay` etc.
directly into `[sparklineData]`.

## State and data

No new signal/service. `GlobalDashboardPageComponent`'s existing
`trends = signal<GlobalTrendsDto | null>(null)` remains the single
source of truth; `sparklineData` bindings read from it via the existing
template expressions (`trends()?.totalTenantsPerDay`, etc.) exactly the
way `[value]`/`[percentChange]` already do for the flat metrics.
`GradientStatCardComponent` itself stays purely presentational (no
signals of its own beyond what Angular's `input()` already provides) —
consistent with this component's existing "no fetch, all inputs" design
and this app's "state lives in services as signals, not components"
rule (this component still owns zero state; it's a pure render of
whatever its parent passes in).

## Deviations from this PLAN

- TASKS.md sequenced wiring as two steps (task section 3: `newTenantsPerDay`/
  `articlesReadPerDay`, no backend dependency; section 4:
  `totalTenantsPerDay`/`staffCountPerDay`, depends on the companion backend
  PLAN). By the time implementation started, `main` already carried the
  merged backend contract with all four fields on `GlobalTrendsDto`
  simultaneously, so there was no longer a reason to split the frontend
  wiring into two separate Red/Green/commit cycles — all four
  `[sparklineData]` bindings were added and tested together in one task/
  commit. No behavioral difference from the PLAN, purely a sequencing
  simplification once the dependency was already satisfied.

## Dependencies

None. Chart.js is already a dependency (via `ChartCanvasComponent`,
already used by `MetricTileComponent` and the two existing trend-chart
components on this same page) — no `package.json` change.

## Testing strategy

Vitest, component-level:

- `gradient-stat-card.component.spec.ts`: new cases —
  - Renders a sparkline chart + sr-only data table when `sparklineData`
    is a non-empty array (REQ-1/2, Accessibility NFR).
  - Renders no chart/table when `sparklineData` is `undefined` or `[]`
    (REQ-4/6), while `value`/badge still render normally.
  - `showSparkline="false"` suppresses the chart even when
    `sparklineData` is present (mirrors `MetricTileComponent`'s own
    `showSparkline` input, in case a future card needs the "no chart for
    a point-in-time metric" treatment this SPEC deliberately didn't ask
    for on the two new cards, per SPEC's "Judgment call" section).
  - `disabled()` (support-tickets card) never renders a sparkline
    regardless of `sparklineData` (REQ-7 regression guard).
- `global-dashboard-page.component.spec.ts`: new/updated cases —
  - After a successful `loadTrends`, each of the four cards' rendered
    `app-gradient-stat-card` receives the correct `sparklineData` input
    (`totalTenantsPerDay` → "Total de tenants" card, etc., per REQ-1's
    exact mapping).
  - Changing `period` re-fetches trends and updates all four cards'
    `sparklineData` in sync with their percent-change badges (REQ-3).
  - Before the first successful trends fetch, cards render with no
    sparkline (REQ-4/6) — reuses this spec's existing "trends not yet
    loaded" setup.
  - A trends fetch failing after a prior success leaves the last
    successful `sparklineData` bindings in place (REQ-5) — reuses this
    spec's existing `trendsError` test setup, asserting `trends()` (and
    therefore the sparkline bindings) is unchanged, not just the badge.
  - Support-tickets card: no `sparklineData` binding exists at all on
    that call site (REQ-7).
