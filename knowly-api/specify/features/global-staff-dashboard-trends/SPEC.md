# SPEC — global-staff-dashboard-trends (backend)

## Context and motivation

The app owner wants the staff/global dashboard (`/dashboard` for a staff
session with no active tenant, gated by
`GlobalPermission.DASHBOARD_VIEW_GLOBAL`) redesigned to look like a
polished admin-dashboard template ("Dashdark X" reference: dark theme,
gradient stat cards with % change badges, a big trend chart). The app
owner confirmed explicitly: it's this staff/global dashboard (not the
tenant dashboard), and a real trend chart with % change badges requires
new backend work — a timeseries/comparison endpoint — not just a
frontend restyle.

Today, `GET /api/staff/metrics/global`
(`global-staff-dashboard-metrics`, already shipped) returns four flat,
point-in-time counts (total tenants, new tenants this calendar month,
total articles read, total staff count) with no history and no
prior-period comparison. This feature adds exactly the missing data
this redesign needs — daily-bucketed series for two of those metrics,
plus a period-over-period comparison for all four — without touching
`global-staff-dashboard-metrics`'s existing endpoint, fields, or
"Out of scope" line. This is a new, additive feature folder rather than
a reopening of that closed SPEC, per this project's SDD convention (one
feature folder per SPEC/PLAN/TASKS unit) and because it extends rather
than replaces the earlier feature's committed scope.

The tenant-scoped `MetricsController`
(`/api/tenants/metrics/**`, permission `DASHBOARD_VIEW`) already
established the period-filter convention this feature reuses
(`7d`/`30d`/`90d`/`all`, UTC calendar-day bucketing, zero-fill for
bounded periods — see `DECISIONS.md`'s `dashboard-analytics` entries).
This feature is the staff-only, cross-tenant counterpart: same period
convention, but reading across every tenant, gated by
`GlobalPermission.DASHBOARD_VIEW_GLOBAL` (not a tenant `Permission`),
and never scoped through `TenantFilter`/`TenantContext` — the same
deliberate exception `global-staff-dashboard-metrics` already
documents.

## User stories

- As a `STAFF`/`STAFF_ADMIN` holding `DASHBOARD_VIEW_GLOBAL`, I want to
  see a daily trend of new tenant signups over a selectable period, so
  I can visually gauge growth instead of reading one flat number.
- As that same staff user, I want to see a daily trend of articles read
  (citations) across every tenant over the same selectable period, so I
  can gauge overall product usage over time, not just a running total.
- As that same staff user, I want each of the four existing global
  metrics to show a percentage change versus the immediately preceding
  period of equal length, so I can tell whether the platform is growing,
  shrinking, or flat — not just its current absolute value.
- As that same staff user, I want the comparison period to follow the
  same 7-day/30-day/90-day/all-time selector already used on the
  tenant-scoped dashboard, so the interaction is familiar rather than a
  one-off.
- As a staff user without `DASHBOARD_VIEW_GLOBAL` (and not
  `STAFF_ADMIN`), I want this endpoint to reject me the same way the
  existing global metrics endpoint does, so no new metric leaks to an
  unauthorized caller.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall expose
   `GET /api/staff/metrics/global/trends`, accepting an optional
   `period` query parameter with the same accepted values as the
   tenant-scoped `MetricsController` (`7d`, `30d`, `90d`, `all`; omitted
   defaults to `all`, matching that existing convention).
2. **[Ubiquitous]** The response shall include, for the selected period:
   a) a daily-bucketed series of new-tenant counts (one count per UTC
   calendar day, zero-filled for days with no new tenant, for the
   bounded periods `7d`/`30d`/`90d` — matching the existing UTC
   calendar-day/zero-fill convention), and
   b) a daily-bucketed series of articles-read (citation) counts, built
   the same way.
3. **[Ubiquitous]** Where `period=all`, the daily series in requirement
   2 shall contain only calendar days that actually have at least one
   qualifying row, sorted chronologically, with no zero-fill — matching
   `dashboard-analytics`'s existing `period=all` convention.
4. **[Ubiquitous]** The response shall include, for each of the four
   metrics already exposed by `GET /api/staff/metrics/global` (total
   tenants, new tenants, total articles read, staff count), a
   period-over-period comparison: the count of qualifying rows created
   within the selected period, the count of qualifying rows created
   within the immediately preceding period of equal length, and the
   percentage change between them.
   - "Total tenants" comparison counts `Tenant` rows created in each
     window.
   - "New tenants" comparison counts the same `Tenant` rows (this is the
     period-driven generalization of `global-staff-dashboard-metrics`'s
     calendar-month field, computed independently by this endpoint — it
     does not read or change that other endpoint's own
     `newTenantsThisMonth` field).
   - "Total articles read" comparison counts `MessageArticleCitation`
     rows created in each window.
   - "Staff count" comparison counts `User` rows with `globalRole`
     `STAFF` or `STAFF_ADMIN` created in each window.
5. **[Unwanted Behavior]** If `period=all`, then the response shall omit
   the period-over-period percentage change for all four metrics (there
   is no well-defined "immediately preceding period" for an unbounded
   range) — the current-period count is still returned, but with no
   previous-period count and no percentage change value.
6. **[Unwanted Behavior]** If the previous-period count for a given
   metric is zero and the current-period count is greater than zero,
   then the system shall return the percentage change as undefined/null
   rather than dividing by zero or fabricating a number.
7. **[Ubiquitous]** The system shall gate this endpoint with the same
   `GlobalPermission.DASHBOARD_VIEW_GLOBAL` permission that already
   gates `GET /api/staff/metrics/global` (via the existing
   `@RequiresGlobalPermission` mechanism — `STAFF_ADMIN` always passes).
8. **[Unwanted Behavior]** If a caller lacks `DASHBOARD_VIEW_GLOBAL` (and
   isn't `STAFF_ADMIN`), then the system shall respond `403 Forbidden`,
   identically to `GET /api/staff/metrics/global`'s existing behavior.
9. **[Unwanted Behavior]** If a caller is a tenant `MEMBER`/
   `MEMBER_ADMIN` with no `GlobalRole`, then the system shall respond
   `403 Forbidden` regardless of tenant-side permissions.
10. **[Unwanted Behavior]** If `period` is present but not one of
    `7d`/`30d`/`90d`/`all`, then the system shall respond `400 Bad
    Request`, matching the tenant-scoped `MetricsController`'s existing
    invalid-period behavior.
11. **[Ubiquitous]** The system shall never scope this endpoint's queries
    through `TenantFilter`/`TenantContext.getActiveTenantId()` — this is
    the same deliberate, documented exception already established by
    `global-staff-dashboard-metrics` REQ-11, extended to this endpoint's
    own queries.

## Non-functional requirements

- Security: gated exclusively by `GlobalPermission.DASHBOARD_VIEW_GLOBAL`
  — no tenant `Permission` involved anywhere, identical posture to
  `global-staff-dashboard-metrics`.
- Performance: each metric's current/previous window counts and each
  daily series are straightforward aggregate/grouped queries — no
  loading of individual `Tenant`/`MessageArticleCitation`/`User` rows
  into the application just to count or bucket them.
- Observability: `@AuditLog`, action `metrics.global.trends.view`,
  `resourceType = "Metrics"` — same pattern as the existing
  `metrics.global.view` action, a distinct action name so the two calls
  are independently auditable.

## Acceptance criteria

- [x] `GET /api/staff/metrics/global/trends` exists and accepts
      `period` in `{7d, 30d, 90d, all}` (omitted = `all`).
- [x] Response includes daily-bucketed new-tenant and articles-read
      series, zero-filled for `7d`/`30d`/`90d`, not zero-filled for
      `all`.
- [x] Response includes current/previous-period counts and percent
      change for all four metrics (total tenants, new tenants, total
      articles read, staff count) for every bounded period.
- [x] For `period=all`, percent change is omitted (not present/null),
      current-period count is still present.
- [x] A metric whose previous-period count is zero returns a
      null/undefined percent change, never a divide-by-zero error or a
      fabricated value.
- [x] A caller without `DASHBOARD_VIEW_GLOBAL` (and not `STAFF_ADMIN`)
      gets `403`; `STAFF_ADMIN` always succeeds without an explicit
      grant.
- [x] An invalid `period` value gets `400`.
- [x] Queries reflect all tenants, no `TenantFilter` scoping.
- [x] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- Any change to `GET /api/staff/metrics/global`'s existing fields,
  definitions, or behavior (`global-staff-dashboard-metrics` stays
  exactly as shipped; this is a new, additive endpoint).
- Support-ticket metrics/counts — even though `internal-team-chat` has
  since introduced a real `SupportTicket` entity, wiring it into either
  global metrics endpoint is a separate, not-yet-confirmed scope
  decision; the existing "coming soon" placeholder tile is untouched by
  this feature.
- Daily-bucketed series for "total tenants" (cumulative) or "staff
  count" — only "new tenants" and "articles read" get a daily series
  (REQ-2); the other two metrics get a period-over-period comparison
  only (REQ-4), no chart-ready series. A future request for their own
  trend lines needs its own SPEC addition.
- Per-tenant breakdown of any of these metrics.
- Any customer-facing cross-tenant benchmarking — remains excluded per
  `VISION.md`, same boundary `global-staff-dashboard-metrics` already
  drew.
- Tenant-local timezone bucketing — stays UTC calendar-day, per the
  existing `dashboard-analytics` precedent; introducing tenant
  timezones is a separate Tier 3 decision.
- Any change to the frontend — covered by a separate SPEC,
  `knowly-app/specify/features/global-staff-dashboard-trends/SPEC.md`.

## Notes (judgment calls, Tier 2 — flagged for explicit confirmation)

- **"New tenants" comparison generalizes, but does not replace,**
  `global-staff-dashboard-metrics`'s calendar-month-scoped
  `newTenantsThisMonth` field — that field is untouched on its own
  endpoint; this endpoint computes its own period-driven "new tenants in
  the selected window" independently, for this redesigned screen only.
  If having two slightly different "new tenants" definitions living on
  two sibling endpoints is undesirable, that's worth a follow-up
  decision, not something silently resolved here.
- **Comparison basis is "immediately preceding period of equal
  length,"** not "same period last month/year" — chosen because it's
  the only comparison basis with any existing precedent in this
  codebase (the tenant dashboard's period selector has no "same period
  last month" concept anywhere to mirror), and it works uniformly across
  `7d`/`30d`/`90d` without needing calendar-aware logic.
- **Only two of the four metrics get a daily series** (new tenants,
  articles read) — the other two (total tenants, staff count) are
  cumulative headline numbers better served by the period-comparison
  badge alone; a daily series of a cumulative running total is a
  different (and not-yet-requested) chart shape.
