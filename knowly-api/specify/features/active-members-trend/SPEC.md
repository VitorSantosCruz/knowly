# SPEC — active-members-trend (backend)

> The what and the why. No technical implementation details.

## Context and motivation

`dashboard-analytics`'s "Membros ativos" (active members) tile is the
only one of the tenant dashboard's five metric tiles that ships without
a trend sparkline (`showSparkline="false"` on the frontend) — every
other tile (articles, conversations, user messages, assistant messages)
is backed by a day-bucketed time-series endpoint
(`GET /api/tenants/metrics/{articles,conversations,messages}/timeseries`),
but `TenantMembership.active` is a point-in-time boolean toggle with no
history (no `activatedAt`/`deactivatedAt` columns, and Envers — while
present on the entity — has no precedent in this codebase for querying
revisions as a day-bucketed aggregate, and would be a worse fit than a
purpose-built table).

The product owner has decided to close this gap with a real
"active members per day" trend, backed by a new table that records a
daily snapshot of the tenant's active-membership count. This table only
has history from the day it starts recording — there is no backfill for
tenants' pre-existing membership history, and that is an accepted
tradeoff of this SPEC, not a gap to flag further (see "Out of scope").

This SPEC covers only the backend: the new snapshot mechanism, its
schema, and a new day-bucketed timeseries endpoint mirroring the shape
of `GET /api/tenants/metrics/messages/timeseries`. The corresponding
frontend change (wiring the existing tile back to `showSparkline=true`
with a real sparkline selector) is a separate SPEC at
`knowly-app/specify/features/active-members-trend/SPEC.md`, per this
project's cross-folder SPEC placement rule.

## User stories

- As a tenant admin/manager with `DASHBOARD_VIEW`, I want to see how my
  tenant's active-member count has trended day by day (not just today's
  snapshot), so I can tell whether adoption is growing, shrinking, or
  flat — the same way I already can for articles, conversations, and
  messages.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall maintain, per tenant and per
   calendar UTC day, a recorded snapshot of the count of that tenant's
   `TenantMembership` rows with `active = true` at the time the snapshot
   for that day is taken.

2. **[Event-Driven]** When a calendar UTC day completes (or, at minimum,
   once during that day, per the mechanism chosen in the implementation
   plan), the system shall record exactly one snapshot row per tenant
   for that day, reflecting the tenant's active-membership count as of
   that snapshot.

3. **[Unwanted Behavior]** If a snapshot for a given tenant and day is
   recorded more than once (e.g. a retried job run, or a second
   membership-state change on the same day, depending on the mechanism
   chosen), then the system shall ensure at most one snapshot row exists
   per tenant per day — the latest recorded value for that day
   overwrites any earlier one for the same day, never producing
   duplicate rows for the same (tenant, day) pair.

4. **[Event-Driven]** When
   `GET /api/tenants/metrics/members/timeseries?period=<period>` is
   called by a caller holding `DASHBOARD_VIEW` for the active tenant,
   the system shall return one active-member count per calendar UTC day
   within the requested period, for the active tenant only, ordered
   chronologically, using only recorded snapshot rows (no fabricated
   history for days before this feature started recording snapshots for
   that tenant).

5. **[Ubiquitous]** The system shall accept the same `period` query
   parameter values already defined for every other metrics
   timeseries endpoint (`7d`, `30d`, `90d`, `all`), applying the same
   validation rule (`400` on an invalid value) already established by
   `dashboard-analytics`'s SPEC.

6. **[Optional Feature]** Where `period` is `7d`, `30d`, or `90d`, the
   system shall zero-fill any day within that bounded range that has no
   recorded snapshot — for this metric specifically, this only ever
   affects days at or before this feature's rollout for a given tenant
   (no snapshot exists yet), which correctly render as `0` rather than
   omitting the day, consistent with the zero-fill behavior already
   established for the other timeseries endpoints.

7. **[Optional Feature]** Where `period` is `all`, the system shall
   return only the calendar days that have an actual recorded snapshot,
   sorted chronologically, with no zero-fill — matching the existing
   `period=all` behavior on the other timeseries endpoints (see
   `DECISIONS.md`'s `dashboard-analytics` entry).

8. **[Ubiquitous]** The system shall scope every read of this new
   snapshot data to the caller's single active tenant, via the same
   tenant-resolution mechanism already used by every other metrics
   endpoint — never a mechanism that queries across tenants.

9. **[Unwanted Behavior]** If a caller does not hold `DASHBOARD_VIEW`
   for the active tenant, then
   `GET /api/tenants/metrics/members/timeseries` shall respond `403
   Forbidden`, consistent with every other metrics endpoint's existing
   gating.

10. **[Unwanted Behavior]** If no tenant is active in the caller's
    session, then `GET /api/tenants/metrics/members/timeseries` shall
    respond with the same tenant-access-denied behavior already used by
    `requireActiveTenant()`, never expose another tenant's data.

11. **[Ubiquitous]** The existing `GET /api/tenants/metrics/members`
    point-in-time endpoint (active/inactive counts) shall remain
    unchanged by this feature — this SPEC adds a new, additional
    timeseries endpoint; it does not replace or alter the existing one.

## Non-functional requirements

- Security: the new endpoint is gated by the existing
  `Permission.DASHBOARD_VIEW` — no new `Permission` enum value is
  introduced by this feature, consistent with `dashboard-analytics`'s
  established convention for every other metrics endpoint.
- Performance/SLA: the timeseries query is a single, day-bucketed
  aggregate read against the new snapshot table (mirroring
  `TenantRepository.countTenantsByDay()`'s native-query shape), not N+1
  per-day queries. Whatever mechanism records the daily snapshot must
  not add a per-request cost to any existing tenant-membership mutation
  path (`addMember`/`removeMember`/activation toggling) that a caller
  would notice — the snapshot-writing mechanism's own performance
  characteristics are a PLAN-level decision.
- Observability: the new endpoint follows the existing `@AuditLog`
  convention (`metrics.members.timeseries.view`, mirroring
  `metrics.members.view`/`metrics.conversations.timeseries.view`'s
  naming pattern).

## Acceptance criteria

- [ ] A new table records one active-membership-count snapshot per
      tenant per calendar UTC day, with no duplicate rows for the same
      (tenant, day) pair.
- [ ] `GET /api/tenants/metrics/members/timeseries` returns per-day
      active-member counts for the active tenant only, honoring
      `period`, zero-filling `7d`/`30d`/`90d` and returning only
      snapshot-backed days for `all`.
- [ ] An invalid `period` value returns `400`, never a silent fallback
      or stack trace.
- [ ] The new endpoint is denied with `403` to a caller lacking
      `DASHBOARD_VIEW`, and is tenant-isolated (verified with an
      integration test asserting tenant A's data is never visible while
      tenant B is active), matching `MetricsControllerIntegrationTest`'s
      existing pattern.
- [ ] The existing `GET /api/tenants/metrics/members` endpoint's
      response shape and behavior are unchanged.
- [ ] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- **Backfilling history for tenants that existed before this feature
  ships.** The snapshot table only has data from the day it starts
  recording onward — there is no reconstruction of historical
  active-member counts from Envers revisions or any other source. This
  is an accepted, deliberate tradeoff (confirmed by the product owner),
  not a gap to raise again in a later review.
- **Any change to how `TenantMembership.active` is set/unset.** This
  feature only reads that field (directly or via the new snapshot
  mechanism) — it does not add new ways to activate/deactivate a
  membership.
- **Tenant-local timezone bucketing.** Snapshots and the timeseries
  endpoint use UTC calendar-day bucketing, consistent with
  `DECISIONS.md`'s standing `dashboard-analytics` entry on this — no
  `Tenant.timezone` concept is introduced here.
- **Any new `Permission`/`GlobalPermission` value** — this feature
  reuses `DASHBOARD_VIEW` exclusively, same as every other metrics
  endpoint.
- **Cross-tenant or global (all-tenants) aggregation of this metric,
  for staff or otherwise** — remains explicitly excluded per
  `VISION.md` and `dashboard-analytics`'s own "Out of scope" section.
- **Any new time granularity other than "per day"** (no hourly/weekly
  snapshots or bucketing).
- **Historical/point-in-time behavior of `GET /api/tenants/metrics/members`**
  is untouched — this SPEC is purely additive.

## Confirmed by the user

1. **Build a real "active members per day" trend, with a new
   go-forward-only snapshot table — no backfill for existing tenants.**
   Confirmed as an accepted tradeoff, not an open gap.
2. **Daily snapshot approach** (recording the count of currently-active
   `TenantMembership` rows per tenant per calendar day) is the confirmed
   shape, reusing the existing `DailyCountProjection`/`DailyCountDto`/
   `MetricsPeriod.dateRange`/zero-fill pattern already used by
   tenants/articles/conversations/citations. The exact mechanism
   (scheduled job vs. computed/upserted whenever a membership's `active`
   flag changes) is left to the PLAN, per the product owner's explicit
   delegation of that implementation-detail choice.
