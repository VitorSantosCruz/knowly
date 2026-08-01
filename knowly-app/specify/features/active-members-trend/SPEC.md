# SPEC — active-members-trend (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

The tenant dashboard's "Membros ativos" (active members) metric tile
(`dashboard-page.component.ts`) is currently the only one of the five
metric tiles rendered without a trend sparkline
(`[showSparkline]="false"`) — it shows only today's active-member count,
fetched from `GET /api/tenants/metrics/members`. A new backend endpoint,
`GET /api/tenants/metrics/members/timeseries` (see
`knowly-api/specify/features/active-members-trend/SPEC.md`), now exposes
a real day-bucketed active-member count, in the exact same
`{ days: [{ date, count }] }` shape already used by
`/api/tenants/metrics/articles/timeseries` and
`/api/tenants/metrics/conversations/timeseries`. This SPEC wires the
existing tile to that endpoint so it shows a real trend sparkline like
its siblings, instead of a bare number.

## User stories

- As a tenant admin/manager viewing the dashboard, I want the active
  members tile to show a trend sparkline (like every other metric
  tile), so I can see at a glance whether active membership is growing,
  shrinking, or flat over the selected period, not just today's count.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The active-members tile on `DashboardPageComponent`
   shall fetch its data from `GET /api/tenants/metrics/members/timeseries`
   instead of `GET /api/tenants/metrics/members`.

2. **[Ubiquitous]** The active-members tile shall set `showSparkline` to
   `true` (or omit it, since `true` is `MetricTileComponent`'s default),
   removing the current `[showSparkline]="false"` override.

3. **[Ubiquitous]** The active-members tile's `valueSelector` shall
   compute the displayed headline number as the active-member count on
   the most recent day present in the timeseries response (the tile's
   "current" value), consistent with what the tile showed before this
   change (today's active-member count).

4. **[Ubiquitous]** The active-members tile shall use a
   `sparklineSelector` that maps the timeseries response's `days` array
   directly to `SparklineDay[]` (`{ date, count }`), the same shape
   already used by the articles/conversations tiles'
   `dailyCountSparklineSelector`.

5. **[State-Driven]** While the tile's period filter is set to `7d`,
   `30d`, or `90d`, the active-members tile shall pass that `period`
   value through to the new endpoint exactly as the other four tiles
   already do, so the sparkline respects the dashboard's shared period
   filter.

6. **[Unwanted Behavior]** If the new endpoint responds with a
   permission-denied (`403`) or network error, then the active-members
   tile shall render the same existing `app-no-access-state`/
   `app-error-state` fallback already used by every other self-fetching
   tile — no new error-handling path is introduced for this tile.

## Non-functional requirements

- Accessibility: the sparkline's existing screen-reader-only data table
  fallback (already built into `MetricTileComponent` for every
  sparkline-enabled tile) applies unchanged to the active-members tile
  once `showSparkline` is `true` — no additional accessibility work is
  needed beyond what the shared component already provides.
- Performance: no change — this is a single additional/replacement HTTP
  call per tile, same cost profile as the tile's current
  `/api/tenants/metrics/members` call.
- Responsiveness: no layout change — the tile's grid placement, sizing,
  and card styling (`metric-tile.component.ts`) are unchanged; only its
  data source and `showSparkline` input change.

## Acceptance criteria

- [ ] The active-members tile calls
      `GET /api/tenants/metrics/members/timeseries` (not
      `GET /api/tenants/metrics/members`), including the current
      `period` filter value.
- [ ] The active-members tile renders a trend sparkline, matching the
      visual treatment of the articles/conversations/messages tiles.
- [ ] The tile's headline number continues to reflect the current
      active-member count (most recent day in the series), not a sum
      across the period.
- [ ] Changing the dashboard's period filter (`7d`/`30d`/`90d`/`all`)
      updates the active-members tile's sparkline the same way it
      already updates the other four tiles.
- [ ] A `403`/network failure on the new endpoint renders the tile's
      existing no-access/error fallback, unchanged from today's
      behavior on every other tile.
- [ ] `npm run format:check && npm test && npm run build && npm run lint`
      all pass.

## Out of scope

- Any change to the tile's card layout, icon, label, or subtitle
  copy — this SPEC only changes the tile's data source and
  `showSparkline` value.
- Any change to the other four metric tiles, the donut/bar charts, the
  members-breakdown card, the top-articles table, or CSV export — this
  SPEC touches only the active-members tile.
- Any new period-filter value or new UI control — this SPEC reuses the
  dashboard's existing `PeriodFilterComponent`/`Period` type unchanged.
- Any client-side computation of "active members over time" from
  membership data the frontend already has locally (e.g. from
  `MembersPageComponent`) — the sparkline is sourced exclusively from
  the new backend timeseries endpoint, consistent with how every other
  tile is sourced.
- Backfilled/historical data before the backend feature's rollout date
  for a given tenant — per the backend SPEC, the timeseries endpoint
  has no data before its own rollout, and the frontend renders whatever
  the backend returns (zero-filled for `7d`/`30d`/`90d`, sparse for
  `all`) without trying to compensate for the missing history.
