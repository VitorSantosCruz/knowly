# SPEC — dashboard-analytics (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

The current dashboard (`knowly-app/src/app/features/dashboard/`) renders
four plain, chart-less cards in a grid. The user described this as
"seco e lixo visual" compared to a reference enterprise analytics
dashboard (Linear/Stripe-style: metric tiles with trend sparklines, a
big donut/ring activity chart, a bar chart with a time-range switcher,
an active-members breakdown list, and a searchable/exportable table).
This SPEC redesigns the dashboard screen to consume the new backend
endpoints from `knowly-api/specify/features/dashboard-analytics/SPEC.md`
(time-series, membership counts, CSV export, period filter).

**Revision note:** an earlier draft of this SPEC assumed **ngx-charts**
as the charting library, since knowly-app had no component/charting
library at the time. Since then, the frontend fully migrated to
**PrimeNG** as its component library (see
`knowly-app/specify/features/primeng-migration/`), which ships its own
`Chart` component (a Chart.js wrapper) and `Table` component. This
revision drops ngx-charts entirely — no new charting dependency is
introduced by this feature; the donut and bar charts use PrimeNG
`p-chart`, and the top-articles table uses `p-table` (`Table` is
already used elsewhere post-migration, e.g. `members-page.component.ts`).

## User stories

- As a tenant admin/manager, I want to see metric tiles (active
  articles, conversations, messages, active members) each with a small
  trend sparkline, so I get an at-a-glance sense of direction, not just a
  static number.
- As a tenant admin/manager, I want a donut/ring chart showing the
  USER-vs-ASSISTANT message split, so I can see engagement composition
  at a glance.
- As a tenant admin/manager, I want a bar chart of conversation/message
  activity per day, with a time-range switcher (7d/30d/90d/all), so I
  can see trends over the period I care about.
- As a tenant admin/manager, I want to see an active-vs-inactive members
  breakdown, so I know how much of the tenant's headcount is actually
  using the product.
- As a tenant admin/manager, I want a table of the most-cited articles
  with a search box, so I can quickly find how a specific article is
  performing.
- As a tenant admin/manager, I want to export the current dashboard view
  (for the currently selected period) as a CSV file, so I can share or
  archive it.
- As a tenant admin/manager, I want a period filter (7d/30d/90d/all) that
  applies consistently across every card/chart on the dashboard, so all
  the numbers I'm looking at describe the same time window.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The dashboard screen shall render one period filter
   control (PrimeNG `SelectButton` or `Select`), offering exactly the
   values `7d`, `30d`, `90d`, `all`, that applies to every metric card
   and chart on the screen simultaneously.

2. **[State-Driven]** While a period value is selected, the dashboard
   shall pass that value as the `period` query parameter to every
   backend metrics call the screen makes, and shall re-fetch all
   period-dependent cards/charts whenever the selection changes.

3. **[Ubiquitous]** The dashboard shall render one metric tile (PrimeNG
   `Card`) each for: active article count, conversation count, USER
   message count, ASSISTANT message count, and active member count —
   each tile showing its current value plus a small trend sparkline
   (PrimeNG `p-chart` with `type="line"`, minimal/axis-less styling)
   built from the corresponding time-series/period data. The active
   article count tile's sparkline is sourced from
   `GET /api/tenants/metrics/articles/timeseries` (added to the backend
   SPEC specifically so this tile isn't the one exception without a
   real trend).

4. **[Ubiquitous]** The dashboard shall render one donut/ring chart
   (`p-chart` `type="doughnut"`) showing the USER-vs-ASSISTANT message
   split for the selected period, using `GET /api/tenants/metrics/messages`
   (period-filtered).

5. **[Ubiquitous]** The dashboard shall render one bar chart (`p-chart`
   `type="bar"`) showing per-day conversation activity for the selected
   period, sourced from `GET /api/tenants/metrics/conversations/timeseries`,
   using the same period-filter control described in requirement 1 as its
   time-range switcher (no separate, second time-range control on the
   chart itself).

6. **[Ubiquitous]** The dashboard shall render one active-members
   breakdown (active vs. inactive counts) sourced from
   `GET /api/tenants/metrics/members`.

7. **[Ubiquitous]** The dashboard shall render the existing top-used
   articles data (`GET /api/tenants/metrics/articles/usage`) as a
   `p-table` with a search input (bound to `Table`'s built-in global
   filter) filtering the rendered rows by article name.

8. **[Event-Driven]** When the user activates the "export" control
   (PrimeNG `Button`), the dashboard shall call
   `GET /api/tenants/metrics/export` with the currently selected period
   and trigger a browser download of the returned CSV file, without
   navigating away from the dashboard.

9. **[Unwanted Behavior]** If any dashboard metrics call fails (network
   error or non-2xx response), then the affected card/chart shall show
   an inline error state (the existing `error-state.component.ts`,
   already migrated to PrimeNG `Message`) with the response's trace id
   surfaced (per the root constitution's "Frontend" observability rule),
   while the rest of the dashboard's cards continue to render
   independently — one failed widget shall not blank the whole screen.

10. **[Unwanted Behavior]** If a user lacks `DASHBOARD_VIEW` (a `403`
    from any of these endpoints), then the dashboard shall show the same
    permission-denied handling already used elsewhere in the app
    (`no-access-state.component.ts`), consistently across every
    card/chart, not a partial/mixed state.

11. **[Optional Feature]** Where the viewport is a small/mobile
    breakpoint, the dashboard shall stack all cards/charts in a single
    column (reusing the `.page-shell` spacing convention) and keep the
    period filter and export control reachable without horizontal
    scrolling.

## Non-functional requirements

- Accessibility: WCAG AA — `p-chart` renders to `<canvas>`, which is not
  inherently screen-reader-friendly; each chart needs an accessible text
  alternative (a visually-hidden data summary/table, or an
  `aria-label`/`aria-describedby` summarizing the data) for screen-reader
  users. The period filter and export control must be fully
  keyboard-operable (PrimeNG components are keyboard-accessible
  out of the box; verify, don't assume).
- Performance: chart rendering must not block the rest of the page; each
  card/chart fetches and renders independently (per requirement 9)
  rather than the whole screen waiting on the slowest call.
- Responsiveness: supports the breakpoints already used elsewhere in the
  app (see requirement 11); `p-chart` must resize with its container
  instead of overflowing.
- Bundle size: this feature adds no new npm dependency (PrimeNG's
  `Chart`/`Table` are already installed) — keep it that way; watch the
  production bundle budget in `angular.json`, already raised twice
  during the PrimeNG migration.

## Acceptance criteria

- [ ] Selecting a period value refetches and updates every card/chart on
      the dashboard to that period.
- [ ] All five metric tiles render with a value and a sparkline sourced
      from real backend data (no mocked/hardcoded numbers).
- [ ] The donut chart renders the USER/ASSISTANT split for the selected
      period.
- [ ] The bar chart renders per-day conversation activity for the
      selected period and updates when the period changes.
- [ ] The active-members breakdown renders active/inactive counts.
- [ ] The top-used-articles table supports search-by-name filtering
      via `p-table`'s built-in global filter.
- [ ] Activating export downloads a CSV file for the currently selected
      period.
- [ ] A failing card/chart shows its own inline error (with trace id)
      without blanking unrelated cards.
- [ ] `npm run format:check && npm test && npm run build` passes.
- [ ] No new npm dependency is introduced (PrimeNG's existing `Chart`/
      `Table` components are reused).

## Out of scope

- PDF export (CSV only — see the backend SPEC's "Out of scope").
- Any charting library other than PrimeNG's `Chart` component (ngx-charts,
  a bespoke Chart.js integration, or a hand-rolled SVG chart).
- Cross-tenant comparison views, or any staff-only "all tenants" view of
  this dashboard.
- A UI for managing membership state (activating/deactivating members)
  — this feature only displays the active/inactive counts.
- Real-time/live-updating charts (e.g. websocket push) — data is fetched
  on period-change/page-load only, no polling or live refresh.
- Any new onboarding-tour step referencing these new widgets.

## Open items — need explicit user confirmation before PLAN.md

1. Confirm the ngx-charts → PrimeNG `Chart`/`Table` swap above matches
   your intent (no new charting dependency, since PrimeNG already covers
   it).
2. Chart-type mapping: donut → USER/ASSISTANT message split, bar chart →
   conversations/day, sparklines → each metric tile's trend. Confirm this
   matches the reference screenshot's intent, particularly whether the
   donut should instead represent something else (e.g. article-usage
   share).
