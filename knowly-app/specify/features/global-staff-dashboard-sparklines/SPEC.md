# SPEC — global-staff-dashboard-sparklines (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

`GlobalDashboardPageComponent` renders its four stat cards via
`GradientStatCardComponent` — a purely presentational card (value +
percent-change badge) with **no sparkline chart at all**, unlike the
tenant-scoped dashboard's `MetricTileComponent`, which renders a
Chart.js sparkline under the value whenever a daily series is
available. This gap was a deliberate, explicit exclusion recorded in
`knowly-api/specify/features/global-staff-dashboard-trends/SPEC.md`'s
"Out of scope" and is now being closed, matching the companion backend
SPEC at
`knowly-api/specify/features/global-staff-dashboard-sparklines/SPEC.md`
(which adds the two missing cumulative day-bucketed series,
`totalTenantsPerDay`/`staffCountPerDay`, to
`GET /api/staff/metrics/global/trends`).

Two of the four cards ("Novos tenants neste mês", "Total de artigos
lidos") already have their daily series available today
(`newTenantsPerDay`/`articlesReadPerDay`, already fetched by
`GlobalDashboardPageComponent` and currently only rendered in two
separate full-width trend-chart components below the cards,
`NewTenantsTrendChartComponent`/`ArticlesReadTrendChartComponent`) —
this feature wires that same already-fetched data into the
corresponding stat card as a sparkline too. The other two ("Total de
tenants", "Membros da equipe interna") need the two new series the
companion backend SPEC adds.

`MetricTileComponent` already has a sparkline rendering path
(Chart.js, via `sparklineSelector`/`toChartData`, tuned specifically
for this dark gradient-card background) — this feature reuses that
existing chart treatment rather than building a second one, since the
gradient-card visual chrome is already shared between
`GradientStatCardComponent` and `MetricTileComponent` (see
`DECISIONS.md`'s `staff-global-dashboard` entry).

## User stories

- As a `STAFF`/`STAFF_ADMIN` viewing `/dashboard` with no active
  tenant, I want each of the four gradient stat cards to show a
  sparkline of its trend, the same way the tenant dashboard's metric
  tiles do, so the two dashboards feel visually and behaviorally
  consistent.
- As that same staff user, I want the sparkline on each card to reflect
  the currently selected period (`7d`/`30d`/`90d`/`all`), matching the
  card's own percent-change badge, so the chart and the badge always
  describe the same window.
- As that same staff user, if the trends call fails (network/`403`), I
  want the cards to still show their value/badge from the metrics call
  that already succeeded, with no sparkline rather than a broken chart
  — matching this page's existing graceful-degradation behavior for the
  two existing trend charts.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** `GlobalDashboardPageComponent` shall render each of
   its four stat cards with a sparkline chart of its own day-bucketed
   series for the selected period: "Total de tenants" ←
   `totalTenantsPerDay`, "Novos tenants neste mês" ←
   `newTenantsPerDay`, "Total de artigos lidos" ← `articlesReadPerDay`,
   "Membros da equipe interna" ← `staffCountPerDay`.
2. **[Ubiquitous]** The sparkline chart on each card shall use the same
   Chart.js line-sparkline visual treatment (no axes/legend, tuned
   line/point colors) already used by `MetricTileComponent`'s
   sparkline, not a new/differently-styled chart implementation.
3. **[Event-Driven]** When the period filter changes, the system shall
   re-fetch `GET /api/staff/metrics/global/trends` (unchanged existing
   behavior) and update all four sparklines from the new response,
   consistent with how the two existing trend charts and percent-change
   badges already update on period change.
4. **[State-Driven]** While the trends call has not yet succeeded (in
   flight, or has never succeeded for the current session), the system
   shall render each stat card with its value/badge only, no sparkline
   — matching the existing pattern of "badge appears once trends data
   is available."
5. **[Unwanted Behavior]** If the trends call fails (network error or a
   non-403 error) after previously succeeding, then the system shall
   continue showing each card's last-successfully-fetched sparkline and
   value rather than clearing them, matching this page's existing
   `trendsError` graceful-degradation behavior for the percent-change
   badges.
6. **[Unwanted Behavior]** If the trends call has never succeeded (page
   load, first fetch fails), then the system shall render each stat
   card with no sparkline (value/badge from the separate, already
   page-gating `GET /api/staff/metrics/global` call still renders as
   today) rather than an empty/broken chart placeholder.
7. **[Ubiquitous]** The "Suporte" (support tickets) stat card shall be
   unaffected by this feature — it remains the disabled "coming soon"
   card with no value, badge, or sparkline.

## Non-functional requirements

- Accessibility: each sparkline shall carry the same screen-reader-only
  data table fallback (`<table class="sr-only">`, date/value columns)
  already implemented for `MetricTileComponent`'s sparkline, so the
  same trend data is available non-visually.
- Performance: this feature adds zero new HTTP calls — it renders data
  already fetched by the existing single `GET /api/staff/metrics/global/trends`
  call per period change, exactly like the two existing trend charts
  below the cards do today.
- Responsiveness: sparklines render within the existing card's fixed
  height/grid layout at all currently supported breakpoints (`sm`/`lg`),
  with no layout reflow of the 4-card (plus support-tickets) grid.

## Acceptance criteria

- [ ] All four stat cards ("Total de tenants", "Novos tenants neste
      mês", "Total de artigos lidos", "Membros da equipe interna") show
      a sparkline chart matching the tenant dashboard's visual
      treatment.
- [ ] Each sparkline reflects the currently selected period and updates
      when the period filter changes, in sync with that card's
      percent-change badge.
- [ ] Before the trends call first succeeds, cards show value/badge
      with no sparkline (no broken/empty chart).
- [ ] A trends-call failure after a prior success leaves the last
      successfully rendered sparklines/values in place rather than
      clearing them.
- [ ] Each sparkline has an accompanying screen-reader-only data table,
      matching `MetricTileComponent`'s existing pattern.
- [ ] The support-tickets card is unchanged (still disabled,
      value/badge/sparkline-free).
- [ ] `npm run format:check && npm test && npm run build && npm run lint`
      all pass.

## Out of scope

- Removing or changing the two existing standalone trend-chart
  components (`NewTenantsTrendChartComponent`/
  `ArticlesReadTrendChartComponent`) below the stat cards — this
  feature is additive (the same series now also renders inline on its
  corresponding stat card); whether those two full-width charts become
  redundant once their data also appears on the card is a separate,
  not-yet-requested follow-up decision, not made here.
- Any change to which HTTP endpoint(s) this page calls, or to the
  existing 403-vs-network error classification
  (`classifyMetricError`) — unchanged.
- Any change to the tenant-scoped dashboard's `MetricTileComponent`
  call sites or their existing tiles — this feature only changes how
  `GlobalDashboardPageComponent`'s cards render, reusing
  `MetricTileComponent`'s existing sparkline treatment as a visual
  reference/shared implementation detail, not altering its current
  consumers.
- Support-ticket metrics/sparkline — still a placeholder, per the
  backend SPEC's matching exclusion.
- Any change to i18n copy/labels/subtitles already shipped for these
  four cards.

## Dependency

This feature depends on the companion backend SPEC
(`knowly-api/specify/features/global-staff-dashboard-sparklines/SPEC.md`)
shipping `totalTenantsPerDay`/`staffCountPerDay` on
`GET /api/staff/metrics/global/trends` — the "Total de tenants" and
"Membros da equipe interna" cards cannot get a real sparkline before
that data exists. "Novos tenants neste mês"/"Total de artigos lidos"
have no such dependency (their series already exists today) and could
ship independently if the two SPECs are sequenced separately.
