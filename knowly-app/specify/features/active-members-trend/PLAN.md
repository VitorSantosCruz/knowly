# PLAN — active-members-trend (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Consumed API contract

Per `knowly-api/specify/features/active-members-trend/PLAN.md`:

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/tenants/metrics/members/timeseries` | query: `period` (`7d`\|`30d`\|`90d`\|`all`) | `{ days: [{ date: string, count: number }] }` | 200, 400, 403, 409 |

Same `{ days: [{ date, count }] }` shape already consumed by
`/api/tenants/metrics/articles/timeseries` and
`/api/tenants/metrics/conversations/timeseries` — the existing
`DailyCountResponse` interface in `dashboard-page.component.ts` is
reused as-is, no new response type needed.

## Architectural decisions

- **`dashboard-page.component.ts`'s active-members tile switches its
  `url` input from `/api/tenants/metrics/members` to
  `/api/tenants/metrics/members/timeseries`**, and gains a `[period]`
  binding (the other four tiles already pass `[period]="period()"`;
  today's active-members tile omits it because its old endpoint isn't
  period-aware) — this is the minimum change `metric-tile.component.ts`
  needs to actually issue the new period-aware request, since its
  internal `effect()` already re-fetches whenever `url()`/`period()`
  change (see `metric-tile.component.ts`'s constructor), no changes to
  `metric-tile.component.ts` itself are required.
- **`[showSparkline]="false"` is removed** from the tile (default is
  `true` per `MetricTileComponent`'s own `input()` default), per SPEC
  req. 2.
- **New `activeMembersSparklineSelector`**, added alongside the existing
  `dailyCountSparklineSelector`/`userMessagesSparklineSelector`/
  `assistantMessagesSparklineSelector` selectors already on
  `DashboardPageComponent` — reuses `dailyCountSparklineSelector`
  directly rather than duplicating it, since the new response shape
  (`DailyCountResponse`) is identical to the articles/conversations
  tiles' shape (`{ days: [{ date, count }] }`). No new selector function
  is actually written; `[sparklineSelector]="dailyCountSparklineSelector"`
  is passed to the active-members tile the same way it already is to
  the article-count tile. *Why reuse over a new function:* a
  `activeMembersSparklineSelector` that just re-implements
  `(data) => (data as DailyCountResponse).days` would be a byte-for-byte
  duplicate with no behavioral difference — this codebase's convention
  (see `dailyCountSparklineSelector`'s existing reuse across
  articles/conversations) is to share a selector whenever the underlying
  shape is actually the same, not to give every tile its own copy for
  symmetry's sake.
- **`activeMembersValueSelector` changes what it reads, not its
  contract**: today it reads `(data as MembersResponse).activeCount`
  from the old `/members` snapshot shape; it becomes
  `(data as DailyCountResponse).days.at(-1)?.count ?? 0` — the most
  recent day's count, per SPEC req. 3 ("current" = latest day in the
  series, not a sum across the period, unlike
  `dailyCountValueSelector`'s `reduce`-to-sum used by the articles
  tile). This mirrors how the old `/members` endpoint's `activeCount`
  was already a single point-in-time number, just now sourced from the
  new series' last element instead of a dedicated field — no visible
  behavior change for the headline number on a normal day.
- **No new component, no new service, no new route/guard** — this is a
  one-file (`dashboard-page.component.ts`) prop/selector change on an
  existing tile, consistent with the SPEC's "Out of scope" section
  ruling out any layout/component change.
- **Error/no-access fallback**: unchanged — `MetricTileComponent`'s
  existing `fetcher?.error() === 'permission-denied' | 'network'` `@if`
  branches already cover this tile once it's fetching from the new URL;
  no new fallback path (SPEC req. 6).

## Components and routes

No new route, no new guard, no new component. Only
`dashboard-page.component.ts` changes (template's `active-members-tile`
`app-metric-tile` bindings + the `MembersResponse` interface/
`activeMembersValueSelector` implementation).

## Dependencies

None new.

## Package/file structure

- `src/app/features/dashboard/dashboard-page.component.ts` (modify only)
  — no other file changes.

## Testing strategy

- Unit/component test (Vitest, extends the existing
  `dashboard-page.component.spec.ts` if present, or the tile-level spec
  covering `activeMembersValueSelector`/sparkline wiring):
  - Confirms the active-members tile's HTTP call targets
    `/api/tenants/metrics/members/timeseries` with the current `period`
    query param (SPEC req. 1/5).
  - Confirms `showSparkline` is `true` (default, no override) on the
    tile (SPEC req. 2).
  - Confirms `activeMembersValueSelector` returns the last day's `count`
    from a sample `{ days: [...] }` response, not a sum (SPEC req. 3).
  - Confirms the sparkline selector maps `days` straight through to
    `SparklineDay[]` (SPEC req. 4) — covered by reusing
    `dailyCountSparklineSelector`, whose existing test coverage (from
    the articles tile) already exercises this mapping; add a
    tile-specific assertion only if the existing spec doesn't already
    parametrize over multiple tiles.
  - 403/network fallback: no new test needed beyond confirming the tile
    still uses `MetricTileComponent`'s existing fallback machinery
    (already covered by `metric-tile.component.spec.ts`'s existing
    tests, which are shape-agnostic).
- `npm run format:check && npm test && npm run build && npm run lint`
  all pass before the task is considered done.
