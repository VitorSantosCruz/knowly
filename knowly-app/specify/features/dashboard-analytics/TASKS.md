# TASKS — dashboard-analytics (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> Each backend-dependent task names the endpoint it needs — do not start
> its Green step before that endpoint exists per
> `knowly-api/specify/features/dashboard-analytics/PLAN.md`.

## 0. Shared fetcher extension (no backend dependency — existing endpoints only)

- [x] 1. Write a test for `metric-fetcher.ts` asserting `load(params)`
      forwards `params` to `HttpClient#get` (Red).
- [x] 2. Extend `createMetricFetcher`'s `load()` signature to accept an
      optional `params: Record<string, string>` and forward it to
      `http.get(url, { params })` (Green).
- [x] 3. Commit: `feat(dashboard): support query params in metric fetcher`.

## (a) Period filter + shared period signal

- [x] 4. Write `period-filter.component.spec.ts`: renders four options
      (`7d`/`30d`/`90d`/`all`), selecting one updates its `period` model
      (Red).
- [x] 5. Implement `period-filter.component.ts` (PrimeNG `SelectButton`,
      `period = model<Period>('30d')`) (Green).
- [x] 6. Wire `period-filter.component.ts` into `dashboard-page.component.ts`,
      owning `protected readonly period = signal<Period>('30d')` bound via
      `[(period)]` — no other widgets changed yet, so
      `dashboard-page.component.spec.ts`'s existing flushes still pass.
- [x] 7. Commit: `feat(dashboard): add period filter with shared period signal`.

## (b) `metric-tile.component.ts` (reused 5x)

**Unblocked 2026-07-26**: user approved adding `chart.js@^4.5.1` as a
real dependency (Tier 3, confirmed) — see PLAN.md's "Dependencies"
section and SPEC.md's "Bundle size" note.

Depends on: existing `/api/tenants/metrics/articles`,
`/api/tenants/metrics/conversations`, `/api/tenants/metrics/messages`
(period support), and NEW `/api/tenants/metrics/conversations/timeseries`,
`/api/tenants/metrics/messages/timeseries`, and
`/api/tenants/metrics/articles/timeseries` — confirm response shapes
against the backend PLAN.md before task 9.

- [x] 8. Write `metric-tile.component.spec.ts` covering: loading state;
      success renders value + sparkline data passed via input; `'network'`
      error renders `app-error-state`; `'permission-denied'` renders
      `app-no-access-state`; changing the `period` input triggers a new
      HTTP call with the updated `period` param (Red).
- [x] 9. Implement `metric-tile.component.ts` (generic over `url`,
      `label`, `valueSelector`, `sparklineSelector` inputs; internal
      `effect()` re-`load()`s on `period` change) (Green).
- [x] 10. Write/implement the pure `toSparklineData()` mapping function
      (plain unit test against sample time-series objects, no rendering)
      (Red/Green). This single function is reused by all four
      time-series-backed tiles (conversations, USER messages, ASSISTANT
      messages, active articles) — no per-tile one-off mapping.
- [x] 11. Replace `article-count-card.component.ts`,
      `conversations-card.component.ts`, `messages-card.component.ts` in
      `dashboard-page.component.ts` with five `<app-metric-tile>` usages
      (articles, conversations, USER messages, ASSISTANT messages, active
      members); delete the three superseded components and their specs;
      update `dashboard-page.component.spec.ts`'s flushed URLs accordingly.
      The active-article-count tile's sparkline is sourced from
      `GET /api/tenants/metrics/articles/timeseries`, wired through
      `metric-tile.component.ts` exactly like the other three
      time-series-backed tiles (reusing `createMetricFetcher` and
      `toSparklineData()` — no one-off implementation for this tile).
      *(The active-members tile also reuses `metric-tile.component.ts`,
      pointed at `/api/tenants/metrics/members`, with a flat single-point
      sparkline per PLAN's "no per-day semantics" note — distinct from
      `members-breakdown-card.component.ts`, which shows active vs.
      inactive rather than a trend.)*
- [x] 12. Commit: `feat(dashboard): replace point-in-time cards with reusable metric tiles`.

## (c) Message-split donut chart

Depends on: NEW `GET /api/tenants/metrics/messages/timeseries`.

- [x] 13. Write the pure `toDonutData()` mapping function's unit test
      against a sample `messages/timeseries` response, asserting the
      `{ labels, datasets }` shape summed across days (Red).
- [x] 14. Implement `toDonutData()` (Green).
- [x] 15. Write `message-split-chart.component.spec.ts`: loading/error/
      no-access states (same pattern as (b)); success renders `p-chart`
      with `type="doughnut"` and a paired `.sr-only` `<table>` mirroring
      the same two data points; period change re-fetches (Red).
- [x] 16. Implement `message-split-chart.component.ts` and wire it into
      `dashboard-page.component.ts` (Green).
- [x] 17. Commit: `feat(dashboard): add message split donut chart`.

## (d) Conversations activity bar chart

Depends on: NEW `GET /api/tenants/metrics/conversations/timeseries`.

- [x] 18. Write the pure `toBarData()` mapping function's unit test
      against a sample `conversations/timeseries` response (Red).
- [x] 19. Implement `toBarData()` (Green).
- [x] 20. Write `conversations-activity-chart.component.spec.ts`: same
      state-coverage pattern as (c), success renders `p-chart` with
      `type="bar"` plus its `.sr-only` mirror table; period change
      re-fetches (Red).
- [x] 21. Implement `conversations-activity-chart.component.ts` and wire
      it into `dashboard-page.component.ts` (Green).
- [x] 22. Commit: `feat(dashboard): add conversations activity bar chart`.

## (e) Members breakdown card

Depends on: NEW `GET /api/tenants/metrics/members`.

- [x] 23. Write `members-breakdown-card.component.spec.ts`: loading/error/
      no-access states; success renders active/inactive counts; **no**
      period-input test (this endpoint is a point-in-time snapshot per
      backend SPEC req. 6, so it is not wired to the `period` signal at
      all) (Red).
- [x] 24. Implement `members-breakdown-card.component.ts` and wire it into
      `dashboard-page.component.ts` (Green).
- [x] 25. Commit: `feat(dashboard): add members active/inactive breakdown card`.

## (f) Top articles table with search filter

Depends on: existing `/api/tenants/metrics/articles/usage` (unchanged shape).

- [x] 26. Write `top-articles-table.component.spec.ts`: loading/error/
      no-access states; success renders a `p-table` row per article;
      typing into the search input filters rendered rows by title (Red).
- [x] 27. Implement `top-articles-table.component.ts` (`p-table` +
      `p-inputtext` bound to global filter) (Green).
- [x] 28. Replace `article-usage-list.component.ts` with
      `top-articles-table.component.ts` in `dashboard-page.component.ts`;
      delete the old component and its spec.
- [x] 29. Commit: `feat(dashboard): replace article usage list with searchable top-articles table`.

## (g) Export button + CSV download

Depends on: NEW `GET /api/tenants/metrics/export?period=<...>`.

- [x] 30. Write `export-button.component.spec.ts`: activating the button
      issues `GET .../export?period=<current period>` with
      `responseType: 'blob'`; a successful flush triggers the
      `URL.createObjectURL`/anchor-click download sequence (spied, not a
      real download); a failing response surfaces the same inline error
      pattern as other widgets (Red).
- [x] 31. Implement `export-button.component.ts` (Green).
- [x] 32. Wire it into `dashboard-page.component.ts`, bound to the shared
      `period` signal.
- [x] 33. Commit: `feat(dashboard): add CSV export button`.

## (h) Full regression

- [x] 34. Update/finish `dashboard-page.component.spec.ts` so its full
      widget-composition test covers every new widget's `data-testid`
      and the complete new set of flushed endpoints. *(Covers all 5
      metric tiles, the donut chart, the bar chart, members breakdown,
      top-articles table, period filter, and export button.)*
- [x] 35. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green. *(210/210 tests green; build succeeds
      — production bundle budget raised again in `angular.json`
      (900kB→1.4MB warning, 1.4MB→1.7MB error) to accommodate
      `chart.js`; final bundle 1.50MB raw / ~310KB estimated transfer,
      under the new error threshold with headroom, warning still shown
      as an informational flag same as before the raise.)*
- [x] 36. Update `PLAN.md` if any decision changed during implementation.
      *(Done: PLAN.md's "Dependencies" section now documents the
      `chart.js` addition and why it was only discovered during
      implementation. The "Consumed API contracts" table's assumed
      shapes matched the backend's actual implementation — no other
      changes needed.)*
- [x] 37. Commit: `test(dashboard): finish full dashboard-analytics widget regression`.
