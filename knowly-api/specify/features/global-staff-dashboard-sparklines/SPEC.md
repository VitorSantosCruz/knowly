# SPEC — global-staff-dashboard-sparklines (backend)

> The what and the why. No technical implementation details.

## Context and motivation

The staff/global dashboard's four gradient stat cards ("Total de
tenants", "Novos tenants neste mês", "Total de artigos lidos", "Membros
da equipe interna") today show only a value and a period-over-period
percent-change badge — no chart, unlike the tenant-scoped dashboard's
`MetricTileComponent` tiles, which each render a sparkline. This was a
deliberate, explicit exclusion recorded in
`global-staff-dashboard-trends/SPEC.md`'s "Out of scope": daily series
were built for "new tenants" and "articles read" only; "total tenants"
(a cumulative headcount) and "staff count" (also a cumulative headcount)
were left with a period-comparison badge only, with that SPEC noting
"a future request for their own trend lines needs its own SPEC
addition." This is that addition.

`GET /api/staff/metrics/global/trends` already returns day-bucketed
`newTenantsPerDay`/`articlesReadPerDay` series (REQ-2 of the prior SPEC)
— these two need no new backend work; the frontend simply isn't
consuming them into a chart on these two cards yet (see the companion
frontend SPEC). The two cumulative metrics ("total tenants", "staff
count") have no day-bucketed series today, in either "count of rows
created that day" shape (the pattern every other series in this
codebase uses) or a cumulative running-total shape — this feature adds
the latter.

## Judgment call this SPEC resolves (read before requirements)

The prior SPEC flagged that a daily series for a cumulative metric ("a
daily series of a cumulative running total is a different \[...\]
chart shape") without committing to build it. This codebase has an
existing, established precedent for the alternative — the tenant
dashboard's "active members" tile (`dashboard-page.component.ts`) is
also a point-in-time/cumulative-shaped metric, and it deliberately
renders **no sparkline at all** (`showSparkline="false"`) rather than
fabricating a "new members per day" series that would misrepresent
what the label says.

This feature does **not** follow that "no chart" precedent, because the
app owner's explicit request here is that all four staff cards get a
real sparkline, matching the tenant dashboard's visual treatment — that
request supersedes the earlier SPEC's assumption for these two specific
metrics. Reframing "Total de tenants"/"Membros da equipe interna" as
"new tenants per day"/"new staff per day" was considered and rejected:
it would render the same undercounting series already excluded once
before, attached to a card whose label explicitly says "Total", which
would be actively misleading (a card titled "Total de tenants" showing
a chart of new signups, not the running total the label promises).

**Decision: both metrics get a true cumulative, day-bucketed running
total** — for each UTC calendar day in the requested period, the count
of `Tenant`/staff-`User` rows that existed as of the end of that day
(not created *during* that day). This is a new query/merge shape for
this codebase (every existing day-bucketed series so far counts rows
created *within* the bucket, zero-filling absent days to `0`; a
cumulative series must instead **carry forward** the last known
running total on a day with no new rows, never resetting to `0`). This
is flagged here explicitly, per this project's Tier 2 authority rules,
because it is the first instance of this query/merge shape in the
codebase and has no existing precedent to simply copy — the reasoning
above is the record of why carry-forward cumulative was chosen over
both alternatives (no chart / misleading "new per day" reframing) for
this specific pair of metrics. This SPEC must be read back to the app
owner for explicit sign-off before PLAN — if "no sparkline for these
two" or the "new per day" reframing is actually preferred once the
cost of a new query/merge shape is visible, that's a one-line SPEC
edit, not a rebuild.

## User stories

- As a `STAFF`/`STAFF_ADMIN` holding `DASHBOARD_VIEW_GLOBAL`, I want to
  see a running-total trend line of tenants on the "Total de tenants"
  card, so I can visually gauge overall platform growth the same way I
  can already gauge new-tenant signups.
- As that same staff user, I want to see a running-total trend line of
  internal staff headcount on the "Membros da equipe interna" card, for
  the same reason.
- As that same staff user, I want the "Novos tenants neste mês" and
  "Total de artigos lidos" cards to show the daily series already
  computed for them, so all four cards look and behave consistently.
- As that same staff user, I want the cumulative trend lines to reflect
  the true running total (never dropping to zero on a day with no new
  rows), so the chart doesn't misrepresent a metric its own card labels
  as a running "Total".

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** `GET /api/staff/metrics/global/trends` shall
   additionally return, for the selected `period`, a day-bucketed
   cumulative running-total series for total tenants
   (`totalTenantsPerDay`) and for staff headcount
   (`staffCountPerDay`) — one entry per UTC calendar day in the range,
   using the same `date`/`count` shape as the existing
   `newTenantsPerDay`/`articlesReadPerDay` series.
2. **[Ubiquitous]** Each entry in `totalTenantsPerDay`/`staffCountPerDay`
   shall report the count of qualifying rows (`Tenant` rows;
   `User` rows with `globalRole` `STAFF` or `STAFF_ADMIN`,
   respectively) whose `createdAt` is less than or equal to the end of
   that UTC calendar day — i.e. a true running total as of that day,
   not a count of rows created only during that day.
3. **[Ubiquitous]** For the bounded periods (`7d`/`30d`/`90d`), a day
   with no new qualifying rows shall carry forward the previous day's
   running total in `totalTenantsPerDay`/`staffCountPerDay`, never
   reporting `0` on a day after the running total is already positive.
4. **[Ubiquitous]** Where `period=all`, `totalTenantsPerDay`/
   `staffCountPerDay` shall span from the UTC calendar day of the
   earliest qualifying row through today, inclusive, with the same
   carry-forward rule as requirement 3 (this differs from
   `newTenantsPerDay`/`articlesReadPerDay`'s existing `period=all`
   behavior, which only includes days with at least one row — a
   cumulative series has no equivalent "day with nothing to report"
   concept once the running total has started).
5. **[Ubiquitous]** If there are zero qualifying rows for the entire
   requested range (e.g. a brand-new environment with no tenants yet),
   then `totalTenantsPerDay`/`staffCountPerDay` shall be an empty list
   for `period=all`, or zero-filled at `0` for every day in the bounded
   range, consistent with there being no rows to carry forward from.
6. **[Ubiquitous]** This feature shall not alter
   `newTenantsPerDay`/`articlesReadPerDay`'s existing values, shape, or
   zero-fill behavior, nor any of the four `PeriodComparisonDto` fields
   already returned by this endpoint.
7. **[Ubiquitous]** The two new series shall be gated by the same
   `GlobalPermission.DASHBOARD_VIEW_GLOBAL` permission already gating
   this endpoint — no new permission, no behavior change to who can
   call it or the existing `403`/`400` responses.

## Non-functional requirements

- Security: no change to this endpoint's existing authorization
  posture (`DASHBOARD_VIEW_GLOBAL`, `STAFF_ADMIN` bypass, no
  `TenantFilter`/`TenantContext` scoping — same deliberate exception as
  `global-staff-dashboard-trends` REQ-11).
- Performance: the cumulative series is computed as a single grouped
  aggregate query per metric (e.g. a window-function running sum over
  day-bucketed counts) — no per-row loading of `Tenant`/`User` into the
  application just to count or accumulate them, matching this
  codebase's existing day-bucketed query performance posture.
- Observability: no new `@AuditLog` action — this is additive fields on
  the existing `metrics.global.trends.view` action/endpoint, not a new
  endpoint.

## Acceptance criteria

- [ ] `GET /api/staff/metrics/global/trends` response includes
      `totalTenantsPerDay` and `staffCountPerDay`, same `date`/`count`
      shape as the two existing series.
- [ ] Each day's count is a true running total (rows with
      `createdAt <=` end of that day), not a per-day-created count.
- [ ] A day with zero new rows carries forward the prior day's total in
      the bounded periods (`7d`/`30d`/`90d`); never resets to `0` once
      positive.
- [ ] `period=all` spans from the earliest qualifying row's UTC day
      through today, same carry-forward rule, or is empty when there
      are zero qualifying rows.
- [ ] A bounded period with zero qualifying rows for the whole range is
      zero-filled at `0` for every day, not omitted.
- [ ] `newTenantsPerDay`/`articlesReadPerDay`/all four
      `PeriodComparisonDto` fields are byte-for-byte unchanged from
      before this feature.
- [ ] Existing `DASHBOARD_VIEW_GLOBAL`/`STAFF_ADMIN`/403/400 behavior on
      this endpoint is unchanged.
- [ ] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- Any change to `GET /api/staff/metrics/global` (the non-trends,
  point-in-time endpoint) — untouched, per that feature's own
  "Out of scope" line, still in force.
- A cumulative running-total series for anything other than these two
  metrics (e.g. cumulative articles read, cumulative conversations) —
  not requested, not added.
- Support-ticket metrics — still a placeholder, unaffected by this
  feature, per `global-staff-dashboard-trends`'s existing exclusion.
- Per-tenant breakdown of either new series.
- Tenant-local timezone bucketing — stays UTC calendar-day, same
  precedent as every other day-bucketed series in this codebase.
- Any frontend change — covered by the companion SPEC at
  `knowly-app/specify/features/global-staff-dashboard-sparklines/SPEC.md`.
- Changing the "no sparkline for a point-in-time metric" precedent set
  by the tenant dashboard's "active members" tile — that tile is
  untouched; this feature's cumulative-series approach applies only to
  these two staff-dashboard cards, per the app owner's specific request
  for this screen.
