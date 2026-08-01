# PLAN — global-staff-dashboard-sparklines (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Additive fields on the existing `GlobalTrendsDto`, no new endpoint.**
  `GET /api/staff/metrics/global/trends` gains two new fields
  (`totalTenantsPerDay`, `staffCountPerDay`); no new controller mapping,
  no new `@AuditLog` action — matches REQ-7/NFR-Observability and the
  existing precedent of one endpoint growing its response shape rather
  than forking a second one for a related-but-different query shape
  (same reasoning `global-staff-dashboard-trends`'s own PLAN used when
  it added `newTenantsPerDay`/`articlesReadPerDay` to this same DTO
  instead of a separate endpoint).
- **New query/merge shape: cumulative running total, carry-forward,
  zero-fill only before the first row — first instance of this pattern
  in the codebase** (every existing day-bucketed series counts rows
  *created* per bucket). Recorded as a new `DECISIONS.md` entry
  (`global-staff-dashboard-sparklines: cumulative, carry-forward
  day-bucketed series...`) per the SPEC's own Tier 2 flag, since there is
  no existing precedent to just copy.
- **One query per metric, computed over full history, independent of
  `period` — not the existing two-query-variant pattern
  (`*Since(Instant)` + bare `*()`).** `newTenantsPerDay`/
  `articlesReadPerDay` need a bounded-window variant so the DB only
  aggregates rows inside the display window for `7d`/`30d`/`90d`. A
  cumulative running total cannot use that shape: the value for
  displayed day N always depends on **all** history up to that day,
  regardless of which days the caller wants displayed — bounding the
  query by the display window would silently understate the true
  running total (day 1 of a `7d` window would show "rows created in the
  last 7 days," not the true all-time total as of that day). **Decision:**
  a single repository method per metric, always computed over full
  history; the requested display range is applied only in the app-layer
  merge step, never in SQL. This is a deliberate divergence from the two
  sibling series and is called out so it isn't "fixed" into matching
  them later.
- **Running total computed as a window-function sum over already
  day-bucketed counts, not N per-day `count(*) WHERE created_at <= day`
  queries.** One native `@Query` per metric:
  ```sql
  WITH daily AS (
    SELECT date_trunc('day', created_at AT TIME ZONE 'UTC')::date AS day, count(*) AS cnt
    FROM tenants
    GROUP BY day
  )
  SELECT day, sum(cnt) OVER (ORDER BY day) AS count
  FROM daily
  ORDER BY day
  ```
  (same shape for `users WHERE global_role IN ('STAFF','STAFF_ADMIN')`).
  **Why this over N queries:** the SPEC's own NFR explicitly asks for
  "a single grouped aggregate query per metric (e.g. a window-function
  running sum over day-bucketed counts) — no per-row loading ... just to
  count or accumulate," which this satisfies directly; the N-queries
  alternative (one `count(*) WHERE created_at <= :day` per calendar day
  in the range) would mean up to 90 round trips for `90d` and doesn't
  scale to `period=all` (unbounded number of days) at all. The window-
  function approach aggregates once, over `count(*) GROUP BY day` rows
  (one row per calendar day *with at least one insert*, not one row per
  entity), so its cost is bounded by "number of distinct days with
  activity," identical in shape/cost to the existing
  `countTenantsByDay()`/`countCitationsByDay()` queries this codebase
  already runs for `period=all` today.
  - Reuses the existing `DailyCountProjection` interface
    (`getDay()`/`getCount()`) as the projection type — the SQL column is
    aliased `count` specifically so it binds into `getCount()` without a
    new projection type, even though semantically it now holds a
    cumulative value, not a per-day-created value; documented at the
    call site so a future reader isn't confused by the reused name (see
    `DECISIONS.md` entry for the full "why one interface" reasoning).
- **New carry-forward merge helper, distinct from the existing
  `mergeZeroCountDays`.** `mergeZeroCountDays` (existing) zero-fills a
  quiet day to `0`. A cumulative series must instead carry the last
  known running total forward — new private helper
  `mergeCarryForwardDays(List<DailyCountProjection> rows, MetricsPeriod
  period)`:
  - For a bounded period, the display range comes from
    `period.dateRange(clock)` (same as today). The initial carry value
    for the first displayed day is **not** always `0` — it seeds from
    the last cumulative value recorded strictly before the range starts
    (e.g. a tenant created 6 months ago must still show `count=1` on day
    1 of a `7d` window, not `0`, per REQ-2/3). Concretely: `carry =
    rows.stream().filter(r -> r.getDay().isBefore(rangeStart))
    .reduce((first, last) -> last).map(DailyCountProjection::getCount)
    .orElse(0L)` (last value strictly before the range start, `0` if
    none — `rows` is already sorted ascending by `day` from the `ORDER
    BY day` in SQL), then for each date in the range: if a row exists for
    that exact date, update `carry` to that row's value; append
    `(date, carry)`.
  - For `period=all` (REQ-4): if `rows` is empty, return `List.of()`
    (REQ-5's "empty environment" case). Otherwise the display range is
    `[rows.get(0).getDay(), today]` inclusive — **not**
    `period.dateRange(clock)`, which returns `Optional.empty()` for
    `ALL`; this is new range-construction logic local to this helper,
    since `MetricsPeriod.dateRange` has no concept of "start from the
    data's own earliest day," only fixed-N-day bounded ranges.
  - For a bounded period with zero rows at all (REQ-6): every date in
    `period.dateRange(clock)` gets `count = 0` — this falls out of the
    general algorithm above with no special case (empty `rows` means the
    seed-carry step yields `0` and never updates).
- **No new package.** Everything lands in
  `br.com.conectabyte.knowly.metrics.global` alongside this feature's
  siblings, and `TenantRepository`/`UserRepository` (existing packages)
  each gain one new query method — consistent with
  `global-staff-dashboard-trends`'s own package placement.

## Data schema

No migration. Both underlying columns already exist and are already
indexed (per `global-staff-dashboard-trends`'s `V21` migration:
`ix_tenants_created_at`). `users.created_at`/`users.global_role`
(string-backed enum, `length = 20`) already exist and are already
queried by `countByGlobalRoleIn`. No new index is added for the new
`users` query: it groups by `date_trunc('day', created_at)` filtered by
`global_role IN (...)`, the same shape/cost class as the sibling
feature's own `countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`,
which already scans `users` by `created_at` with no dedicated index and
no reported performance issue. If this proves measurably slow at
production data volume, that is a follow-up (same class of decision as
`global-staff-dashboard-trends`'s own non-blocking AppSec index
recommendation), not required by this SPEC.

## API contracts

| Method | Path | Query params | Request | Response DTO | Status codes |
|---|---|---|---|---|---|
| GET | `/api/staff/metrics/global/trends` | `period` (unchanged: `7d`\|`30d`\|`90d`\|`all`, default `all`) | — | `GlobalTrendsDto` (below, now 4 series + 4 comparisons) | 200 OK; 400 (invalid `period`, unchanged); 403 (missing `DASHBOARD_VIEW_GLOBAL`, unchanged) |

`GlobalTrendsDto` (changed — two new fields appended, existing four
fields byte-for-byte unchanged per REQ-6):

```java
record GlobalTrendsDto(
    List<DailyCountDto> newTenantsPerDay,      // unchanged
    List<DailyCountDto> articlesReadPerDay,    // unchanged
    PeriodComparisonDto totalTenants,          // unchanged
    PeriodComparisonDto newTenants,            // unchanged
    PeriodComparisonDto totalArticlesRead,     // unchanged
    PeriodComparisonDto staffCount,            // unchanged
    List<DailyCountDto> totalTenantsPerDay,    // NEW — cumulative, carry-forward
    List<DailyCountDto> staffCountPerDay       // NEW — cumulative, carry-forward
) {}
```

Fields are appended at the end (not interleaved with the existing four)
so every existing positional-constructor call site
(`GlobalMetricsServiceTest`) only needs a mechanical two-argument
addition at the end of its `new GlobalTrendsDto(...)` calls, not a
reordering — minimizes diff noise in already-passing tests.

Example JSON, `period=30d`, a tenant created well before the window:

```json
{
  "newTenantsPerDay": [{ "date": "2026-07-03", "count": 0 }, "..."],
  "articlesReadPerDay": [{ "date": "2026-07-03", "count": 12 }, "..."],
  "totalTenants": { "current": 120, "previous": 110, "percentChange": 9.1 },
  "newTenants": { "current": 5, "previous": 0, "percentChange": null },
  "totalArticlesRead": { "current": 8300, "previous": 7900, "percentChange": 5.1 },
  "staffCount": { "current": 3, "previous": 3, "percentChange": 0.0 },
  "totalTenantsPerDay": [{ "date": "2026-07-03", "count": 115 }, { "date": "2026-07-04", "count": 115 }, "..."],
  "staffCountPerDay": [{ "date": "2026-07-03", "count": 3 }, "..."]
}
```

Note `totalTenantsPerDay[0].count = 115`, not `0` — carry-forward from
before the displayed window, per REQ-2/3.

`period=all`, empty environment (REQ-5):

```json
{
  "newTenantsPerDay": [],
  "articlesReadPerDay": [],
  "totalTenants": { "current": 0, "previous": null, "percentChange": null },
  "newTenants": { "current": 0, "previous": null, "percentChange": null },
  "totalArticlesRead": { "current": 0, "previous": null, "percentChange": null },
  "staffCount": { "current": 0, "previous": null, "percentChange": null },
  "totalTenantsPerDay": [],
  "staffCountPerDay": []
}
```

## Dependencies

None. No `pom.xml` change — a native `@Query` with a window function is
already an established pattern in this codebase (day-bucketed queries
already use native `@Query`; `sum(...) OVER (...)` is standard
PostgreSQL, no new driver/library needed).

## Package/file structure

Changed files:

- `GlobalTrendsDto.java` — append `totalTenantsPerDay`/`staffCountPerDay`
  fields.
- `GlobalMetricsService.java` — in `globalTrends(MetricsPeriod)`, call
  the two new repository methods and `mergeCarryForwardDays(...)` (new
  private helper, alongside the existing `mergeZeroCountDays`); wire the
  two new lists into the `GlobalTrendsDto` constructor call.
- `TenantRepository.java` — add
  `countCumulativeTenantsByDay(): List<DailyCountProjection>` (native
  `@Query`, the window-function SQL above, no `Instant` parameter —
  always full history, per this PLAN's first architectural decision).
- `UserRepository.java` — add
  `countCumulativeStaffByDay(): List<DailyCountProjection>` (native
  `@Query`, same shape, `WHERE global_role IN ('STAFF','STAFF_ADMIN')`
  hardcoded as string literals matching the enum's string persistence —
  consistent with this being a fixed, non-parameterized pair of roles,
  the same way `globalMetrics()`'s own `STAFF_ROLES` constant is fixed,
  not client-supplied).

No changes to `GlobalMetricsController.java` (no new mapping),
`GlobalMetricsDto.java`, `PeriodComparisonDto.java`, or
`previousWindowStart(...)` (unaffected — these two new series have no
period-comparison badge, only a sparkline; REQ-6 confirms the four
`PeriodComparisonDto` fields stay untouched).

## Testing strategy

TDAD, Red → Green, per constitution:

- **Repository tests** (`TenantRepositoryTest`/`UserRepositoryTest`,
  Testcontainers): seed `Tenant`/`User` rows across several UTC calendar
  days (including more than one row on the same day, to confirm the
  window function aggregates per-day counts correctly, not per-row) and
  assert `countCumulativeTenantsByDay()`/`countCumulativeStaffByDay()`
  return a running total that only ever increases day over day, in
  chronological order, and correctly excludes non-`STAFF`/`STAFF_ADMIN`
  users from the staff query.
- **Unit tests** (`GlobalMetricsServiceTest`, mocked repositories, fixed
  `Clock`):
  - `mergeCarryForwardDays` carries the last known total forward across a
    quiet day within a bounded period, never reporting `0` once positive
    (REQ-3).
  - `mergeCarryForwardDays` seeds the first displayed day of a bounded
    period from a cumulative value recorded *before* the range starts
    (the "tenant created 6 months ago, `7d` window" case above) — this is
    the case most likely to be silently wrong if implemented as a naive
    zero-fill copy-paste.
  - `mergeCarryForwardDays` for `period=all` spans from the earliest row's
    day through today (REQ-4), and returns an empty list when there are
    no rows at all (REQ-5).
  - `mergeCarryForwardDays` for a bounded period with zero rows at all
    zero-fills every day in the range at `0` (REQ-6).
  - `globalTrends(...)` leaves `newTenantsPerDay`/`articlesReadPerDay`/all
    four `PeriodComparisonDto`s byte-for-byte unchanged (REQ-6 regression
    guard — existing tests plus one explicit new assertion).
- **Integration tests** (`GlobalMetricsControllerIntegrationTest`,
  extending the existing test class):
  - Response includes `totalTenantsPerDay`/`staffCountPerDay` with the
    same `date`/`count` shape as the existing two series, for
    `7d`/`30d`/`90d`/`all`.
  - Existing `DASHBOARD_VIEW_GLOBAL`/`STAFF_ADMIN`/`403`/`400` assertions
    (from the sibling feature) still pass unmodified — no new
    authorization test needed per REQ-7, just confirmation the existing
    ones still gate the now-larger response.

## Deviations during implementation

- **Prerequisite port, not just a PLAN dependency note.** This worktree
  had branched from `main` before `global-staff-dashboard-trends` (this
  feature's direct backend dependency — the endpoint/DTO/service method
  it appends to) had landed there. Rather than block, the trends
  feature's backend (repository queries, `GlobalTrendsDto`/
  `PeriodComparisonDto`, `GlobalMetricsService#globalTrends`,
  `GlobalMetricsController`'s `/trends` mapping, `V21` migration, and
  every associated test) was ported into this branch verbatim, as its
  own commit, before starting this feature's own TASKS.md — see
  `PROJECT_STATUS.md`'s `global-staff-dashboard-trends` row (marked
  "this branch") for the full description of what was ported. No
  `active-members-trend`/`ActiveMemberSnapshot` code was pulled in
  alongside it — confirmed as a genuinely separate, non-dependency
  sibling feature per the orchestrating agent's instructions, and this
  feature's own repository queries/merge helper have no dependency on
  it.
- Test hook `GlobalMetricsService#mergeCarryForwardDaysForTest(...)`:
  `mergeCarryForwardDays` itself stayed `private` as planned, but a
  package-visible one-line delegator was added so a plain, mocked-
  repository unit test (`GlobalMetricsServiceMergeCarryForwardDaysTest`,
  no Spring context) could exercise the algorithm directly — same
  package-visibility convention this class already uses for
  `previousWindowStart`, not a new pattern.
