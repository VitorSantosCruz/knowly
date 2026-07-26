# PLAN — dashboard-analytics (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Sequencing note (Tier 1, not a scope decision)

This PLAN's endpoints are specified in
`knowly-api/specify/features/dashboard-analytics/SPEC.md`, whose PLAN.md
does not exist yet at the time of writing. Each task below names the
backend endpoint it depends on; **do not start a task's Green step until
that endpoint exists and matches the shape assumed here.** If the
backend PLAN pins a different response shape than assumed below, update
this PLAN's "Consumed API contracts" table first (Tier 2, not a silent
divergence).

## Architectural decisions

- **One small standalone component per widget**, matching the existing
  `article-count-card.component.ts`/`conversations-card.component.ts`
  pattern (a card wrapper + `createMetricFetcher` + the existing
  `ErrorStateComponent`/`NoAccessStateComponent` `@if` chain) — no
  monolithic dashboard-page rewrite, so each widget stays independently
  testable and a failing one can't blank the rest (SPEC req. 9).
- **`createMetricFetcher` (existing shared helper,
  `src/app/core/metric-fetcher.ts`) is extended, not replaced**: `load()`
  becomes `load(params?: Record<string, string>)`, forwarded to
  `HttpClient#get`'s `params` option. This is the smallest change that
  lets every widget pass `{ period }` while keeping the existing
  loading/error/permission-denied/traceId contract and all five existing
  card components' behavior unchanged (they simply call `load()` with no
  params, same as today). *Why extend rather than fork a second fetcher:*
  a second near-identical helper would violate this app's "state lives in
  services with one shape" convention and double the surface to keep in
  sync with backend error-shape changes.
- **Period state is owned by `dashboard-page.component.ts` as a signal**
  (`protected readonly period = signal<Period>('30d')`) and passed down
  via `input.required<Period>()` on every period-dependent widget — no
  new shared service. *Why:* matches this app's existing signal-ownership
  convention (`ActiveTenantService`-style: owner holds the writable
  signal, consumers get read-only access), and period is page-local UI
  state with no cross-route consumer, so a service would be
  over-engineering for a single page.
- **Each period-dependent widget reacts to its `period` input via
  `effect()`** (calling `fetcher.load({ period: this.period() })` whenever
  the input changes), rather than the page re-creating child components
  on period change — keeps each widget's own loading/error state
  independent per SPEC req. 9, and avoids a full child-component
  teardown/recreate on every filter change.
- **`metric-tile.component.ts` is one reusable component for all five
  tiles** (active articles, conversations, USER messages, ASSISTANT
  messages, active members), parameterized by `input()`s: `url` (or a
  pre-fetched time-series array — see below), `label`, and a
  `valueSelector`/`sparklineSelector` pure function pair — rather than
  five near-duplicate components. *Why:* the five tiles differ only in
  data source and a couple of labels; templating that difference via
  inputs avoids five copies of the same loading/error/sparkline
  scaffolding to maintain.
- **Sparklines are derived from per-day time-series endpoints**, not a
  separate ad-hoc source per tile: the conversations tile reads
  `conversations/timeseries`, the two message tiles both read
  `messages/timeseries` (summing/selecting the relevant role field), the
  article-count tile reads `articles/timeseries`, and the members tile
  reads the point-in-time `members` snapshot rendered as a flat/no-trend
  sparkline (members has no per-day semantics per the backend SPEC's
  req. 6). All four time-series-backed tiles share the same
  `toSparklineData()` pure mapping function (see below) — no one-off
  mapping per tile.
- **`p-chart` data-shape mapping lives in one pure function per chart
  component** (e.g. `toDonutData(messages: MessagesTimeseriesResponse):
  ChartData`, `toBarData(rows: ConversationTimeseriesRow[]):
  ChartData`), exported from the component file and unit-tested directly
  with plain objects — no DOM/canvas assertions, consistent with this
  ecosystem's convention of not deep-testing Chart.js internals.
- **CSV export uses `HttpClient` with `responseType: 'blob'`**, then a
  temporary `<a>` + `URL.createObjectURL`/`revokeObjectURL` trigger — the
  standard browser download pattern, no new dependency. The filename is
  read from the response's `Content-Disposition` header when present,
  falling back to a generated `dashboard-<period>.csv`.
- **Chart accessibility**: each `p-chart` gets a paired visually-hidden
  (`.sr-only`, Tailwind's existing utility) `<table>` mirroring the same
  data the chart renders, generated from the same pure mapping function
  used for the chart itself — not a hand-written `aria-label` summary.
  *Why a table over an aria-label:* the SPEC's donut/bar charts are
  multi-series (USER/ASSISTANT split, per-day counts), which a single
  summarized string can't represent losslessly for a screen-reader user;
  a real `<table>` gives full per-category/per-day values with native
  table semantics, and it's generated from the exact same data structure
  already unit-tested for the chart mapping, so there's no second
  data-shaping path to keep in sync.
- **`top-articles-table.component.ts` replaces (not extends)
  `article-usage-list.component.ts`** as the dashboard's article-usage
  widget, using `p-table` with its built-in global filter bound to a
  search `input()` (a `p-inputtext` above the table, per the existing
  `members-page.component.ts` pattern) — the old plain-`<ul>` component is
  deleted once the new one is wired into `dashboard-page.component.ts`,
  rather than kept alongside a near-duplicate.
- **`export-button.component.ts`** is a thin wrapper around a `p-Button`
  + the blob-download call above, taking `period` as an `input()`; it
  does not own the period signal itself.
- **`period-filter.component.ts`** wraps a PrimeNG `SelectButton` with a
  fixed `['7d', '30d', '90d', 'all']` option list and a
  `period = model<Period>('30d')` two-way-bindable signal, consistent
  with the "small composable component, no new pattern" convention.

## Components and routes

No new route: the existing `/dashboard` route and its guards
(`tenantSelectionGuard`, already in place) are unchanged — this feature
only changes what renders inside `dashboard-page.component.ts`.

```
dashboard-page.component.ts                 (owns `period` signal; composes all widgets below)
├── period-filter.component.ts              (NEW — SelectButton, period model())
├── export-button.component.ts              (NEW — Button + CSV blob download)
├── metric-tile.component.ts                (NEW — reused 5x: articles/conversations/user-msgs/assistant-msgs/members)
├── message-split-chart.component.ts        (NEW — donut, GET messages/timeseries)
├── conversations-activity-chart.component.ts (NEW — bar, GET conversations/timeseries)
├── members-breakdown-card.component.ts     (NEW — GET members)
└── top-articles-table.component.ts         (NEW — p-table, replaces article-usage-list.component.ts)

REMOVED: article-count-card.component.ts, conversations-card.component.ts,
         messages-card.component.ts, article-usage-list.component.ts
         (superseded by metric-tile.component.ts x5 and top-articles-table.component.ts)
```

`metric-fetcher.ts` (`src/app/core/`) — modified, not new: `load()` gains
an optional `params` argument (see "Architectural decisions" above).

## Consumed API contracts

Cross-referencing `knowly-api/specify/features/dashboard-analytics/SPEC.md`
(backend PLAN.md not yet written — shapes below are the SPEC's stated
response semantics; confirm against the backend PLAN.md before
implementing each task and update this table if it differs).

| Method | Path | Request | Response (assumed shape) | Status codes |
|---|---|---|---|---|
| GET | `/api/tenants/metrics/articles` | — | `{ totalCount: number }` (existing, unchanged) | 200, 403 |
| GET | `/api/tenants/metrics/articles/timeseries?period=<...>` | query: `period` | `{ days: { date: string, count: number }[] }` (NEW — same shape as `conversations/timeseries`) | 200, 400, 403 |
| GET | `/api/tenants/metrics/articles/usage` | — | `{ articles: { id, title, useCount }[] }` (existing, unchanged) | 200, 403 |
| GET | `/api/tenants/metrics/conversations?period=<7d\|30d\|90d\|all>` | query: `period` | `{ startedCount: number }` (existing, `period` now honored) | 200, 400, 403 |
| GET | `/api/tenants/metrics/messages?period=<...>` | query: `period` | `{ sentCount: number, receivedCount: number }` (existing, `period` now honored) | 200, 400, 403 |
| GET | `/api/tenants/metrics/conversations/timeseries?period=<...>` | query: `period` | `{ days: { date: string, count: number }[] }` (NEW) | 200, 400, 403 |
| GET | `/api/tenants/metrics/messages/timeseries?period=<...>` | query: `period` | `{ days: { date: string, userCount: number, assistantCount: number }[] }` (NEW) | 200, 400, 403 |
| GET | `/api/tenants/metrics/members` | — | `{ activeCount: number, inactiveCount: number }` (NEW, not period-filtered per backend SPEC req. 6) | 200, 403 |
| GET | `/api/tenants/metrics/export?period=<...>` | query: `period` | CSV file (`Content-Type: text/csv`, `Content-Disposition: attachment`) (NEW) | 200, 400, 403 |

`403` responses use the existing `{ code: 'PERMISSION_DENIED' }` shape
consumed by `createMetricFetcher` today; `400` (invalid `period`) is not
expected to occur from this UI since `period-filter.component.ts` only
ever emits one of the four fixed values, so no dedicated `400` handling
is added beyond falling into the existing generic `'network'` error
branch.

## State and data

- `dashboard-page.component.ts`: `protected readonly period =
  signal<Period>('30d')`, where `type Period = '7d' | '30d' | '90d' |
  'all'` (new shared type, colocated in `period-filter.component.ts` and
  re-exported).
- Every period-dependent widget: `period = input.required<Period>()`,
  reacts via `effect()` in the constructor calling
  `this.fetcher.load({ period: this.period() })`.
- No new service — `metric-fetcher.ts`'s existing per-widget-instance
  fetcher pattern is reused as-is (each widget still owns its own
  `MetricFetcher` instance; nothing becomes cross-widget shared state
  beyond the `period` input already flowing through `@Input`/signal
  props, consistent with this app's "no new shared-state pattern without
  reason" convention).
- `top-articles-table.component.ts`: local `searchTerm =
  signal('')` bound to `p-table`'s `[globalFilterFields]`/`filterGlobal`,
  no period dependency change to the existing `/articles/usage` endpoint
  (out of scope per backend SPEC — that endpoint's `period` support is
  listed only for `/conversations` and `/messages`, not `/articles/usage`;
  flagging this as consistent with the backend SPEC's own scope, not an
  oversight here).

## Dependencies

None. PrimeNG's `Chart`, `Table`, `SelectButton`, `Button`, `InputText`,
and `Message` components are already installed post-`primeng-migration`.
No new `package.json` entry.

## Testing strategy

Per the existing house style (`conversations-card.component.spec.ts`,
using `createMetricWidgetHarness`):

- **Each new widget component**: loading state renders before response;
  success state renders fetched data; `'network'` error renders
  `app-error-state` with `traceId`; `'permission-denied'` renders
  `app-no-access-state`. For period-dependent widgets, an additional test
  asserts that changing the `period` input triggers a second HTTP call
  with the new `period` query param (via `httpMock.expectOne` matching
  on URL+params).
- **Pure mapping functions** (`toDonutData`, `toBarData`, and the
  sparkline data-shape function used by `metric-tile.component.ts`):
  unit-tested directly against plain response objects — assert the
  returned `{ labels, datasets }` shape, not anything rendered to canvas.
  This is the primary way SPEC acceptance criteria for chart *content*
  (not chart *rendering*) get verified, consistent with this ecosystem's
  convention of not deep-testing Chart.js/canvas internals.
- **Accessibility fallback table**: since it's generated from the same
  mapping function already unit-tested above, its test only asserts the
  `<table>` element exists with the expected row count and is marked
  `.sr-only` — no separate data-correctness assertion needed.
- **`export-button.component.ts`**: test that activating it issues a
  `GET` with `responseType: 'blob'` and the current `period`, and that a
  successful flush triggers `URL.createObjectURL` (spy-based, no real
  download in jsdom).
- **`period-filter.component.ts`**: test that selecting each of the four
  values updates its `period` model.
- **`dashboard-page.component.ts`**: updated to compose the new widget
  set (replacing the four flushed URLs in its existing spec with the new
  widgets' URLs) and to assert the `period` signal change re-triggers
  every period-dependent widget's request — mirroring
  `dashboard-page.component.spec.ts`'s existing `flushMetricRequests()`
  pattern, extended for the new endpoint set.
- `metric-fetcher.ts`'s existing spec (if any) gets a new case: `load(params)`
  forwards `params` to `HttpClient#get`.
