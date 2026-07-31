# PLAN — global-staff-dashboard-trends (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Consumes
> `knowly-api/specify/features/global-staff-dashboard-trends/PLAN.md`'s
> `GET /api/staff/metrics/global/trends` contract.

## Architectural decisions

- **`GlobalDashboardPageComponent` becomes the single owner of both
  fetches**, `GET /api/staff/metrics/global` (existing) and
  `GET /api/staff/metrics/global/trends` (new), driven by one
  page-level `period` signal. This is a deliberate deviation from
  `chart-canvas.component.ts`'s sibling `ConversationsActivityChartComponent`
  pattern (which self-fetches its own endpoint per chart instance) —
  **why**: REQ-6 ties both new charts and all four badges to *one*
  `/trends` response, and REQ-8 requires page-level knowledge of whether
  that single call failed (cards still show current values, charts show
  the error state) — four/six independent self-fetching widgets would
  mean redundant HTTP calls for data that's already one response, and
  would need to coordinate a shared error flag across widgets anyway.
  This mirrors the exact reasoning already recorded in `DECISIONS.md`
  for why `staff-global-dashboard`'s four tiles use `metric-tile.
  component.ts`'s pre-fetched-value mode instead of self-fetching — the
  new charts extend that same "single page-level call, pass pre-fetched
  data down" shape rather than reusing `chart-canvas.component.ts`'s
  self-fetching sibling pattern.
- **Two new presentational child components**,
  `NewTenantsTrendChartComponent` and `ArticlesReadTrendChartComponent`
  (`src/app/features/dashboard/`), each following
  `chart-canvas.component.ts`'s established shape: an exported pure
  `toXxxData()` mapper function + a component that receives pre-fetched
  data via `input.required<DailyCountRow[]>()` and an explicit
  `error = input<boolean>(false)` flag (no `MetricFetcher`/HTTP
  injection at all — data ownership stays with the page, per the
  decision above). This is the one deliberate difference from
  `ConversationsActivityChartComponent` (which does own a
  `MetricFetcher`): these two components are "dumb" chart renderers,
  the closest existing precedent for a *non-self-fetching* chart is
  `chart-canvas.component.ts` itself (already presentational, receives
  `data`/`type` as inputs).
- **Gradient stat cards are new markup, not a new component class** —
  a single new presentational component, `GradientStatCardComponent`
  (`src/app/features/dashboard/gradient-stat-card.component.ts`),
  parameterized by `label`, `subtitle`, an `svg` template-ref-style icon
  passed via `<ng-content select="[icon]">` (so each call site supplies
  its own imported Lucide icon component without `GradientStatCardComponent`
  itself importing all six), `value`, and an optional
  `percentChange: number | null | undefined` (badge omitted when
  `null`/`undefined`, per REQ-9/10). One component (not four ad-hoc
  card blocks) because the four cards share 100% of their layout/badge
  logic and only differ in content — avoids duplicating the "no
  badge when null" branching four times. `metric-tile.component.ts`
  itself is untouched (SPEC's explicit "Out of scope").
- **Period selector reuses `period-filter.component.ts` as-is** — its
  API (`model<Period>()`, same `Period` union `'7d'|'30d'|'90d'|'all'`)
  already fits exactly (REQ-5: one selector drives both charts and both
  badge sets). No new component needed here.
- **Icons via `@lucide/angular`, attribute-selector pattern**, following
  `nav-menu.component.ts`'s exact convention (`<svg lucideXxx
  aria-hidden="true">`, each icon imported directly into the consuming
  component's `imports` array — no central registration). Icon choices
  (Tier 2, no existing precedent to follow beyond "pick something
  semantically reasonable and don't leave it as a TODO"):

  | Card/chart | Icon | Import |
  |---|---|---|
  | Total tenants | building | `LucideBuilding2` |
  | New tenants | user-plus | `LucideUserPlus` |
  | Total articles read | book-open-check | `LucideBookOpenCheck` |
  | Staff count | shield-check | `LucideShieldCheck` |
  | New tenants per day (chart) | trending-up | `LucideTrendingUp` |
  | Articles read per day (chart) | activity | `LucideActivity` |

  The existing fifth "coming soon" tile also gets an icon for visual
  consistency with the other four now-iconed cards (`LucideLifeBuoy`) —
  this is a small addition beyond REQ-11's literal scope (REQ-11 only
  names the four cards + two charts), included because leaving exactly
  one restyled card without an icon while everything around it has one
  would look like an oversight rather than a decision; flagged here
  rather than silently done, since it's a one-line judgment call, not a
  blocking question.
- **Copy is drafted now, in pt-BR, added to `public/i18n/pt-BR.json`
  under a new `dashboard.trends.*` namespace** (alongside the existing
  `dashboard.tiles.*` keys, not replacing them — REQ-1's four metrics
  keep their existing translation keys for the numeric label; `trends.*`
  adds the new subtitle strings only):

  ```json
  "dashboard": {
    "trends": {
      "tenantCountSubtitle": "Empresas com um workspace ativo na plataforma",
      "newTenantsSubtitle": "Cadastros no período selecionado",
      "articlesReadSubtitle": "Total de artigos abertos/citados em todos os tenants",
      "staffCountSubtitle": "Contas da equipe ConectaByte com acesso à plataforma",
      "newTenantsChartLabel": "Novos tenants por dia",
      "newTenantsChartSubtitle": "Evolução diária de novos cadastros no período selecionado",
      "articlesReadChartLabel": "Artigos lidos por dia",
      "articlesReadChartSubtitle": "Evolução diária de aberturas/citações de artigos em todos os tenants",
      "supportTicketsSubtitle": "Chamados de suporte — recurso ainda não disponível"
    }
  }
  ```

  The four stat-card *labels* reuse the existing
  `dashboard.tiles.tenantCount` / `newTenantsThisMonth` /
  `articlesReadTotal` / `staffCount` keys as-is (REQ-1 restyles
  presentation, not label copy) — `newTenantsThisMonth`'s existing label
  text ("Novos tenants neste mês") already reads fine as a stat-card
  title even though the trends endpoint computes a period-driven count
  rather than a calendar-month one; no new label key needed for that
  mismatch since the visible copy is generic enough ("novos tenants")
  to describe either definition without being wrong. If this distinction
  ever needs to be user-visible, that's a copy-only follow-up, not
  something this PLAN blocks on.
- **Error-state handling (REQ-7/8/9/10)** — `GlobalDashboardPageComponent`
  tracks two independent signals: `metricsError` (existing page-level
  behavior, unchanged) and `trendsError: 'network' | 'permission-denied'
  | null`, populated only when the *first* call already succeeded (per
  REQ-7: trends is "not attempted or is disregarded" if metrics itself
  failed — implemented by only issuing the trends HTTP call inside the
  metrics call's success branch, not calling it unconditionally on
  mount). When `trendsError` is set: the four `GradientStatCardComponent`s
  render with `percentChange` bound to `undefined` (no badge, REQ-8);
  both chart components render `<app-error-state>`/`<app-no-access-state>`
  in place of the chart canvas (same shared components
  `chart-canvas.component.ts`'s sibling charts already use). When
  `period() === 'all'`, `percentChange` is likewise bound to `undefined`
  for all four cards regardless of what the backend returns (REQ-9;
  belt-and-suspenders alongside the backend's own omission) —
  implemented as a small pure function,
  `percentChangeFor(comparison: PeriodComparisonDto | undefined, period:
  Period): number | undefined`, so the "period=all or trends failed or
  backend sent null → no badge" logic lives in one place instead of
  being repeated at each of the four card bindings.

## Components and routes

No routing change — `GlobalDashboardPageComponent` is already mounted at
`/dashboard` for a no-active-tenant staff session
(`staff-global-dashboard`'s existing guard/routing, untouched).

New files:

- `src/app/features/dashboard/gradient-stat-card.component.ts` (+ spec)
- `src/app/features/dashboard/new-tenants-trend-chart.component.ts`
  (+ spec)
- `src/app/features/dashboard/articles-read-trend-chart.component.ts`
  (+ spec)

Changed files:

- `src/app/features/dashboard/global-dashboard-page.component.ts` (+
  spec) — restructured per above; still the only file that knows about
  both endpoints.
- `public/i18n/pt-BR.json` (and the `en-US`/other locale file(s) already
  present in this repo, if any — check `public/i18n/` for siblings
  before assuming pt-BR is the only locale to update).

Unchanged (per SPEC's explicit "Out of scope"):
`metric-tile.component.ts`, `dashboard-page.component.ts`,
`period-filter.component.ts`, `chart-canvas.component.ts`,
`conversations-activity-chart.component.ts`, welcome page, any routing
guard.

## Consumed API contracts

From `knowly-api/specify/features/global-staff-dashboard-trends/PLAN.md`:

`GET /api/staff/metrics/global/trends?period=7d|30d|90d|all` →

```ts
interface DailyCountRow {
  date: string; // ISO LocalDate
  count: number;
}

interface PeriodComparisonDto {
  current: number;
  previous: number | null;
  percentChange: number | null;
}

interface GlobalTrendsDto {
  newTenantsPerDay: DailyCountRow[];
  articlesReadPerDay: DailyCountRow[];
  totalTenants: PeriodComparisonDto;
  newTenants: PeriodComparisonDto;
  totalArticlesRead: PeriodComparisonDto;
  staffCount: PeriodComparisonDto;
}
```

`GET /api/staff/metrics/global` — unchanged (`GlobalMetricsDto`, already
consumed today).

## State and data

- `GlobalDashboardPageComponent` keeps its existing `metrics`, `loading`,
  `error` signals (unchanged shape/behavior, REQ-7) and adds:
  `period = signal<Period>('30d')`, `trends = signal<GlobalTrendsDto |
  null>(null)`, `trendsError = signal<'network' | 'permission-denied' |
  null>(null)`.
- No new shared/injectable service — this data is page-specific,
  fetched once per page instance, not shared app-wide state; doesn't fit
  the `PermissionsService`/`ActiveTenantService`-style "shared state
  service" shape (those exist because multiple unrelated components need
  the same state; nothing else in this app needs `/trends`'s data).
- Both HTTP calls use the plain `HttpClient` already injected in this
  component today (no `MetricFetcher` reuse here — `MetricFetcher`'s
  `error`/`traceId` signal shape is designed for a *self-fetching*
  widget; this component needs both calls' results combined into its own
  two-signal error model per REQ-7/8, so it inlines the same
  403-vs-network distinction `MetricFetcher`/`metric-fetcher.ts` already
  encodes, via a small shared `classifyMetricError(response):
  'network'|'permission-denied'` helper extracted from that file's
  existing inline logic — Tier 2, small dedup, not a new pattern).
- `period` changes re-trigger both fetches via the same `effect()`-based
  reactivity `MetricTileComponent`/`ConversationsActivityChartComponent`
  already use (`effect(() => { const p = this.period(); this.load(p);
  })`), not a manual event handler on `(periodChange)`.

## Dependencies

None new. `@lucide/angular` (already a dependency), `chart.js` (already
a dependency via `chart-canvas.component.ts`) — no `package.json`
change.

## Testing strategy

Vitest, TDAD:

- `gradient-stat-card.component.spec.ts`: renders label/subtitle/icon
  slot; renders badge with correct sign/color for positive/negative
  `percentChange`; renders **no** badge element when `percentChange` is
  `null`/`undefined` (REQ-9/10 regression guard — assert absence, not
  just absence of `NaN%`/`Infinity%` text).
- `new-tenants-trend-chart.component.spec.ts` /
  `articles-read-trend-chart.component.spec.ts`: `toXxxData()` pure
  mapper unit-tested directly (labels/dataset shape from a sample
  `DailyCountRow[]`); component renders `<app-chart-canvas>` with mapped
  data when `data` input is set; renders `<app-error-state>` when
  `error` input is `true`; `.sr-only` table mirrors the same rows
  (assert row count and cell text match input data, mirroring
  `conversations-activity-chart.component.spec.ts`'s existing
  assertions).
- `global-dashboard-page.component.spec.ts` (extends existing spec):
  - REQ-7: metrics call fails → existing permission-denied/network state
    renders; trends endpoint is never called (assert
    `HttpTestingController` has no outstanding request to
    `/trends`).
  - REQ-8: metrics succeeds, trends fails → four cards render current
    values with no badge; both charts render error state; page is not
    blank (some card content is present).
  - REQ-9: `period=all` → all four cards render with no badge even when
    the mocked response includes a non-null `percentChange` (guards the
    frontend-side belt-and-suspenders clamp, not just trusting the
    backend).
  - REQ-10: a metric with `percentChange: null` in the mocked response
    renders that one card with no badge, others with theirs.
  - Period-selector change triggers both a new `/global` request... no —
    only a new `/trends` request (per REQ-6, metrics itself isn't
    period-scoped) — assert exactly that, not a redundant re-fetch of
    `/global`.
  - "Coming soon" tile still renders disabled, now inside/as a
    `GradientStatCardComponent` variant (REQ-4).
