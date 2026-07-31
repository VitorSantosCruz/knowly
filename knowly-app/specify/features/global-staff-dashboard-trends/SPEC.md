# SPEC — global-staff-dashboard-trends (frontend)

## Context and motivation

The app owner shared reference images of "Dashdark X" (a dark-theme
admin dashboard template: gradient stat cards with % change badges, a
big trend/area chart, supporting widget cards) and asked for the
staff/global dashboard — `GlobalDashboardPageComponent`, rendered at
`/dashboard` when a staff session has no active tenant, per
`staff-global-dashboard`'s existing "one screen, two contexts" pattern —
to be visually redone in that style, with "gráficos, cards relevantes"
(charts, relevant cards).

The app owner explicitly confirmed: (1) it's this staff/global
dashboard, not the tenant-scoped `DashboardPageComponent`; (2) this
needs a real trend chart with % change badges backed by real data, not
a purely cosmetic restyle. The new data this requires is provided by
the backend SPEC at
`knowly-api/specify/features/global-staff-dashboard-trends/SPEC.md`
(`GET /api/staff/metrics/global/trends`). This SPEC covers only the
frontend consumption and visual redesign — the four existing flat tiles
(`GlobalDashboardPageComponent`, from `staff-global-dashboard`) are
replaced in place by gradient stat cards with % change badges, and two
new trend charts are added, following the tenant-scoped dashboard's
established Chart.js pattern (`chart-canvas.component.ts`: pick a
Chart.js type, write a pure `toXxxData()` mapper, render via
`<app-chart-canvas>` — see `dashboard-page.component.ts` for the
existing precedent) rather than inventing a new charting approach.

**Naming note:** this SPEC does not touch the tenant-scoped
`DashboardPageComponent`, `/welcome`, or any other screen — only
`GlobalDashboardPageComponent` (and its own private child
components/mappers) change.

## User stories

- As a `STAFF`/`STAFF_ADMIN` with no active tenant selected, I want the
  global dashboard's four metric cards to look like polished,
  gradient-styled stat cards with a visible percentage-change badge
  (up/down), so the screen reads as a real product dashboard rather
  than four plain numbers.
- As that same staff user, I want a prominent trend chart showing new
  tenant signups per day, so I can see growth shape at a glance instead
  of a single number.
- As that same staff user, I want a second chart showing articles read
  (usage) per day, so I can see product usage trend at a glance.
- As that same staff user, I want to switch between 7-day/30-day/90-day/
  all-time views of both charts and both sets of change badges, the same
  way the tenant-scoped dashboard already lets me switch periods, so the
  interaction is familiar.
- As a staff user without `DASHBOARD_VIEW_GLOBAL` (and not
  `STAFF_ADMIN`), I want the redesigned screen to keep showing the
  existing non-technical permission-denied state, unchanged from
  today's behavior.
- As a staff user, if the new trends data fails to load but the
  existing flat metrics still loaded fine, I want to still see my four
  stat cards' current values (without their % change badge and without
  the two charts) rather than the whole page going blank — matching
  this project's "don't let one failing data source blank an entire
  page" convention (`dashboard-analytics` REQ-9, `staff-global-dashboard`
  REQ-5's section-scoped precedent).
- As a staff user looking at any card or chart on this screen, I want to
  immediately understand what the number/series represents — a clear
  label, a short explanatory subtitle where the label alone is
  ambiguous, and an icon reinforcing the metric's meaning — instead of
  today's bare numbers with a generic label and no context, which the
  app owner explicitly flagged as unclear ("hoje são números soltos,
  nem dá para saber do que se trata").

## Requirements (EARS/GEARS)

### Layout and visual redesign

- **REQ-1 [Ubiquitous]** `GlobalDashboardPageComponent` shall render its
  four existing metrics (total tenants, new tenants, total articles
  read, staff count) as gradient-styled stat cards — reusing this app's
  existing "Ink & Signal" design tokens/gradients (no new component
  library, no new CSS framework, per `DECISIONS.md`'s PrimeNG-removal
  entry) — replacing the plain `metric-tile.component.ts` presentation
  currently used on this screen only. `metric-tile.component.ts` itself,
  and every other screen that already consumes it
  (`dashboard-page.component.ts`'s five tenant-scoped tiles), are
  unchanged.
- **REQ-2 [Ubiquitous]** Each of the four stat cards shall display a
  percentage-change badge (e.g. an up/down indicator with the signed
  percentage) sourced from
  `GET /api/staff/metrics/global/trends`'s per-metric comparison, for
  the currently selected period.
- **REQ-3 [Ubiquitous]** The screen shall render two trend charts below
  the stat cards: a primary chart for the new-tenants daily series, and
  a secondary chart for the articles-read daily series — both sourced
  from `GET /api/staff/metrics/global/trends`, both following the
  existing `chart-canvas.component.ts` pattern (a pure `toXxxData()`
  mapper per chart, no chart-specific logic embedded in the page
  component).
- **REQ-4 [Ubiquitous]** The screen shall keep the existing fifth,
  visibly-disabled "support tickets — coming soon" tile
  (`staff-global-dashboard` REQ-4), restyled to match the new gradient
  card look but otherwise unchanged in behavior/meaning.
- **REQ-5 [Ubiquitous]** The screen shall provide a period selector
  (`7d`/`30d`/`90d`/`all`) that drives both charts and both stat-card
  badges together — mirroring the tenant-scoped dashboard's existing
  period-selector interaction pattern, not a separate control per
  widget.
- **REQ-11 [Ubiquitous]** Each stat card and each chart shall carry a
  clear, specific label plus a short subtitle explaining what the
  number/series represents in plain language (e.g. "Total tenants —
  companies with an active workspace", "New tenants — signups in the
  selected period", "Articles read — total open/view events across all
  tenants", "Staff — ConectaByte team accounts with platform access",
  "New tenants per day", "Articles read per day") and a small icon
  reinforcing the metric's meaning, so no card or chart reads as a bare
  number/series with no context — this directly addresses the app
  owner's explicit feedback that today's tiles are unclear ("números
  soltos").

### Data loading and failure handling

- **REQ-6 [Event-Driven]** When the screen mounts (or the period
  selector changes), the system shall fetch
  `GET /api/staff/metrics/global/trends?period=<selected>` in addition
  to the existing `GET /api/staff/metrics/global` fetch, independently.
- **REQ-7 [Unwanted Behavior]** If the existing
  `GET /api/staff/metrics/global` call fails (network or 403), then the
  screen shall behave exactly as it does today (`staff-global-dashboard`
  REQ-5: the page-level non-technical error/permission-denied state,
  unchanged) — the new trends call is not attempted or is disregarded in
  that case.
- **REQ-8 [Unwanted Behavior]** If
  `GET /api/staff/metrics/global` succeeds but
  `GET /api/staff/metrics/global/trends` fails (network or 403), then
  the four stat cards shall still render their current values (from the
  successful call) without a % change badge, and both trend charts shall
  render this screen's existing non-technical error state in their place
  — the page as a whole does not blank out over a single failing data
  source.
- **REQ-9 [Unwanted Behavior]** If `period=all` is selected, then the
  stat cards shall render their current value with no % change badge
  (matching the backend's own `period=all` omission, REQ-5 of the
  backend SPEC) rather than showing a broken/placeholder badge.
- **REQ-10 [Unwanted Behavior]** If a given metric's percent change is
  returned as null/undefined (backend's zero-previous-period case), then
  that card shall render with no badge (e.g. "new" or no badge at all,
  not a broken `NaN%`/`Infinity%` value).

## Non-functional requirements

- Design: gradient stat cards use this app's existing Tailwind token
  system (`ink-*`/`signal-*`), not a hardcoded one-off palette — same
  "Ink & Signal" system every other screen already uses.
- Accessibility: both new charts get an `.sr-only` textual/table mirror
  of their data, matching the existing pattern already used by the
  tenant-scoped dashboard's charts (`dashboard-page.component.ts`) —
  charts are never the only way to read the underlying numbers.
- Performance: no new charting library — `chart.js` (already a
  dependency via `chart-canvas.component.ts`) is reused as-is.
- Security: REQ-7/REQ-8's error-state behavior is UI-only; the
  underlying authorization boundary is enforced server-side
  (`@RequiresGlobalPermission`) exactly as today — this SPEC never
  becomes the real security boundary.

## Acceptance criteria

- [x] The four existing metrics render as gradient stat cards with a
      % change badge for every bounded period (`7d`/`30d`/`90d`).
- [x] `period=all` shows the stat cards' current values with no badge.
- [x] A metric with a null/undefined percent change (zero
      previous-period count) shows no badge, never `NaN%`/`Infinity%`.
- [x] Two trend charts (new tenants/day, articles read/day) render below
      the stat cards, each with an `.sr-only` data mirror.
- [x] The period selector changes both charts and both badge sets
      together.
- [x] Every stat card and chart has a clear label, a plain-language
      subtitle explaining what it measures, and an icon — no card or
      chart is a bare number/series with no explanation.
- [x] The "support tickets — coming soon" tile still renders, visually
      restyled, still disabled.
- [x] If `GET /api/staff/metrics/global` fails, the existing page-level
      error/permission-denied behavior is unchanged.
- [x] If only `GET /api/staff/metrics/global/trends` fails, the four
      stat cards still show their current values (no badge), and the two
      charts show an error state — the page does not blank out.
- [x] `npm run format:check && npm test && npm run build && npm run lint`
      all pass.

## Out of scope

- **Any decorative/non-data-backed widget** from the Dashdark X
  reference (e.g. a "Products" or "Team progress"-style widget with no
  real knowly data behind it) — per this project's "never invent scope
  the user didn't ask for" rule, no such widget is added; if the app
  owner wants a specific additional widget, that needs its own follow-up
  confirmation naming the real data it would show.
- **The tenant-scoped `DashboardPageComponent`** — unchanged, out of
  scope, per the app owner's explicit confirmation that this redesign
  targets the staff/global dashboard only.
- **`/welcome`'s quick-link card** (`staff-global-dashboard` REQ-8) —
  unchanged; this SPEC doesn't touch `/welcome`.
- **Support-ticket real data** — the fifth tile stays a disabled
  placeholder (REQ-4); wiring real `SupportTicket` data (which now
  exists per `internal-team-chat`) into this screen is a separate,
  not-yet-confirmed feature.
- **A daily trend chart for "total tenants" or "staff count"** — matches
  the backend SPEC's own scope; only new tenants and articles read get a
  daily series in this iteration.
- **Any change to `metric-tile.component.ts` itself or any other screen
  that consumes it** — the tenant-scoped dashboard's five tiles are
  untouched; the gradient-card look (REQ-1) is new markup specific to
  this screen, not a change to the shared tile component.
- **Light-theme variant of the new gradient cards** — the Dashdark X
  reference is dark-theme; this SPEC assumes the redesigned cards render
  correctly under this app's existing global light/dark toggle using the
  same token system as every other screen, but does not introduce a
  screen-specific "always dark" override. If the app owner wants this
  screen to always render dark regardless of the global theme
  preference, that is a separate, explicit decision, not assumed here.

## Judgment calls (Tier 2 — flagged for explicit confirmation)

1. **Exactly two charts, both daily-bucketed line/area charts** (new
   tenants, articles read) — chosen as the two metrics with real,
   already-decided event-level data suited to a daily series (see the
   backend SPEC's own scoping). "Total tenants" and "staff count" stay
   badge-only cards, no chart, since a daily series of a cumulative
   running total wasn't asked for and has no existing precedent in this
   codebase to follow.
2. **No decorative supporting widgets** beyond the four (now
   gradient-styled) metric cards, the "coming soon" tile, and the two
   charts — the reference image's non-data widgets are deliberately
   skipped rather than approximated with placeholder/fake data.
3. **Percent-change comparison basis is "previous period of equal
   length,"** matching the backend SPEC's own choice — flagged together
   with that SPEC's identical note, since both sides need to agree on
   this if it's ever revisited.

If any of these three should instead be a blocking question rather than
a documented judgment call, flag it before PLAN.md work starts.
