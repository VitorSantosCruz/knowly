# PLAN — global-staff-dashboard-trends (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New endpoint lives on the existing `GlobalMetricsController`
  (`br.com.conectabyte.knowly.metrics.global`) as a second
  `@GetMapping`, not a new controller class — mirrors how
  `MetricsController` (tenant side) holds all of its
  `/api/tenants/metrics/**` endpoints in one class. The permission
  check/audit-log annotation live on the service method, exactly as
  `globalMetrics()` already does — same pattern, no new precedent.
- New method `GlobalMetricsService#globalTrends(MetricsPeriod period)`,
  reusing the existing tenant-side `MetricsPeriod` enum
  (`br.com.conectabyte.knowly.metrics.MetricsPeriod`) directly — it
  already encodes `7d`/`30d`/`90d`/`all`, `startInstant(Clock)`, and
  `dateRange(Clock)` (zero-fill day list), which is exactly what
  REQ-1/2/3/10 need. No new period enum is introduced; **why**: the SPEC
  explicitly says "same accepted values ... matching that existing
  convention" — reusing the type, not just the string parsing, avoids a
  second parallel definition of what `all`/`7d` mean.
- **Previous-period comparison is new logic** (`MetricsPeriod` has no
  existing "previous window" concept — the tenant-scoped dashboard never
  needed one). Added as a small package-private helper,
  `GlobalMetricsService#previousWindowStart(MetricsPeriod, Instant
  currentStart, Clock)`, colocated in this service rather than added to
  `MetricsPeriod` itself. **Why here, not on the enum**: `MetricsPeriod`
  is shared with the tenant dashboard, which has no period-comparison
  feature today (`dashboard-analytics`'s SPEC never asked for one); the
  window-math needed is specific to *this* SPEC's REQ-4, and until a
  second consumer needs it, keeping it local avoids growing the shared
  enum's public API on a single caller's behalf (Tier 2 — reversible if
  the tenant dashboard later grows the same need, at which point this
  helper is the obvious thing to promote).
  - For a bounded period `[now - N days, now]`, the previous window is
    `[now - 2N days, now - N days)` — "immediately preceding period of
    equal length" per SPEC's own judgment call, computed as
    `currentStart.minus(N, DAYS)` through `currentStart` (exclusive of
    `currentStart`, to avoid double-counting a row created exactly at
    the boundary instant in both windows).
  - For `period=all`, there is no previous window; the comparison is
    entirely skipped (REQ-5) — `previousWindowStart` is never called.
- **Cross-tenant daily-bucketing query approach**: two new native
  `@Query` methods, following the exact `date_trunc('day',
  created_at)::date as day, count(*) as count ... group by day order by
  day` pattern already used by `ArticleRepository
  .countActiveByDayForTenantSince`/`ConversationRepository
  .countByDayForTenantSince` — the only difference is dropping the
  `tenant_id = :tenantId` predicate entirely (per REQ-11, no
  `TenantFilter`/tenant scoping anywhere in this endpoint):
  - `TenantRepository.countTenantsByDaySince(Instant from):
    List<DailyCountProjection>` — `select date_trunc('day',
    created_at)::date as day, count(*) as count from tenants where
    created_at >= :from group by day order by day` (native query).
    `TenantRepository.countTenantsByDay(): List<DailyCountProjection>`
    for `period=all` (no `where`).
  - `MessageArticleCitationRepository.countCitationsByDaySince(Instant
    from): List<DailyCountProjection>` — same shape over
    `message_article_citations`, no tenant predicate.
    `.countCitationsByDay()` for `period=all`.
  - Both reuse the existing `DailyCountProjection` interface
    (`br.com.conectabyte.knowly.metrics.DailyCountProjection`) as their
    projection type — no new projection type needed, it's already
    schema-agnostic (`getDay()`/`getCount()`).
  - Zero-fill/merge into `DailyCountDto` reuses the existing
    `mergeZeroCountDays`-style logic from `MetricsService` — since that
    helper is `private` on a different class, this PLAN adds an
    equivalent private helper on `GlobalMetricsService` (small, ~10
    lines, not worth extracting into shared code for two call sites
    across two services — Tier 2, revisit if a third consumer appears).
- **Period-over-period counts for the four flat metrics** (REQ-4) query
  existing repositories with a `createdAt` window predicate:
  - Total tenants / new tenants (same underlying query, per SPEC's own
    note that these intentionally share a definition here):
    `TenantRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant
    from, Instant to)` (new derived-query method, current window) and
    reusing the existing `countByCreatedAtGreaterThanEqual(Instant
    from)` for the current-period count when there's no upper bound
    needed... **decision:** always pass both bounds explicitly (`from`,
    `to = now`) via one new derived method
    `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan`, used for
    both the current window (`from = periodStart`, `to = now`) and the
    previous window (`from = previousStart`, `to = periodStart`) — one
    method instead of two, since both windows are bounded ranges once
    "now" is captured once per request via `Clock`.
  - Total articles read: same shape,
    `MessageArticleCitationRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan`
    (new derived method; the repository currently has no `count*`
    method at all — `MessageArticleCitation` needs a `createdAt` field
    to query on, confirmed present already, see Data schema note).
  - Staff count:
    `UserRepository.countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(List<GlobalRole>
    roles, Instant from, Instant to)` (new derived method) — reuses the
    existing `GlobalRole.STAFF`/`STAFF_ADMIN` list literal already used
    by `globalMetrics()`.
  - All four current/previous window counts are computed with a single
    captured `Instant now = Instant.now(clock)` at the top of
    `globalTrends()`, so all four metrics' "current" and "previous"
    windows are computed against the same instant, not four
    slightly-different snapshots from calling `Instant.now()`
    repeatedly (Tier 2, straightforward correctness call, no existing
    precedent needed).
- **Percent-change serialization** (Tier 2, no existing precedent in
  this codebase for a nullable computed percentage): each of the four
  metrics gets its own small `PeriodComparisonDto(long current, Long
  previous, Double percentChange)` — `previous` and `percentChange` are
  boxed (`Long`/`Double`, not primitive) specifically so Jackson can
  serialize JSON `null` for the `period=all` (REQ-5) and
  zero-previous-period (REQ-6) cases, rather than `0`/`NaN`/`Infinity`.
  `percentChange` is computed as `((current - previous) /
  (double) previous) * 100.0`, rounded to 1 decimal place
  (`Math.round(x * 10) / 10.0`) for a stable, readable JSON number —
  one decimal place matches this codebase's general convention of
  keeping API numbers presentation-ready rather than raw floats (no
  existing precedent to contradict; the frontend PLAN treats this value
  as already display-ready, not re-derived from raw current/previous on
  its side).
- Response DTO composition mirrors `ArticlesTimeseriesDto`/
  `ConversationsTimeseriesDto`'s "one record per concept, nest records"
  shape rather than one flat record with 12+ fields — see exact shape
  below.
- No new package. Everything lands in
  `br.com.conectabyte.knowly.metrics.global`, alongside
  `GlobalMetricsDto`/`GlobalMetricsService`, consistent with that
  package already being "the staff/global metrics feature," not split
  by endpoint.

## Implementation notes (post-hoc, added after task 0/11)

- Task 0.3's `./mvnw flyway:info` could not run standalone in the
  implementation sandbox (no live DB credentials configured outside
  the running app/Testcontainers) — the effective verification used
  instead was the Testcontainers-backed test suite, whose Flyway run
  applied `V21` cleanly to a fresh schema every run (confirmed via the
  `DbMigrate`/`DbValidate` log lines: "Successfully applied 21
  migrations ... now at version v21"). No version conflict encountered.
- `previousWindowStart(MetricsPeriod, Instant, Clock)` ended up not
  using its `Clock` parameter in the implementation (the window-length
  math only needs `currentStart`), but the parameter was kept to match
  this PLAN's signature exactly, since `GlobalMetricsServiceTest` calls
  it directly and the PLAN's rationale for a `Clock`-shaped signature
  (consistency with `MetricsPeriod`'s own `Clock`-taking methods) still
  holds even though this particular case didn't need to read the clock.
- One additional edge case not explicitly required by SPEC REQ-6 but
  needed a concrete choice during implementation: `current == previous
  == 0` (both windows empty) returns `percentChange = 0.0`, not `null`
  — "no change" is the only non-arbitrary answer here (REQ-6 only
  mandates `null` when `previous == 0 AND current > 0`).

## Data schema

No migration. All four underlying tables/columns already exist:

- `tenants.created_at` (`Tenant.createdAt`, `Instant`) — already used by
  `globalMetrics()`'s `newTenantsThisMonth`.
- `message_article_citations.created_at`
  (`MessageArticleCitation.createdAt`, `Instant`, set via `Instant.now()`
  in the entity's own initializer) — already exists, just not yet
  queried by count/window in any repository method.
- `users.created_at` / `users.global_role` — `User.createdAt` already
  exists (checked: same pattern as every other entity in this codebase);
  `countByGlobalRoleIn` already exists on `UserRepository`, this PLAN
  adds the createdAt-windowed sibling.

No new entity, no new table, no Liquibase changeset needed.

## API contracts

| Method | Path | Query params | Request | Response DTO | Status codes |
|---|---|---|---|---|---|
| GET | `/api/staff/metrics/global/trends` | `period` (optional: `7d`\|`30d`\|`90d`\|`all`, default `all`) | — | `GlobalTrendsDto` (below) | 200 OK; 400 (invalid `period`, via existing `InvalidPeriodException`/`MetricsExceptionHandler`); 403 (missing `DASHBOARD_VIEW_GLOBAL`, not `STAFF_ADMIN`) |

`GlobalTrendsDto`:

```java
record GlobalTrendsDto(
    List<DailyCountDto> newTenantsPerDay,      // REQ-2a, zero-filled for bounded periods
    List<DailyCountDto> articlesReadPerDay,    // REQ-2b, zero-filled for bounded periods
    PeriodComparisonDto totalTenants,          // REQ-4
    PeriodComparisonDto newTenants,            // REQ-4 (own window count, not newTenantsThisMonth)
    PeriodComparisonDto totalArticlesRead,     // REQ-4
    PeriodComparisonDto staffCount             // REQ-4
) {}

record PeriodComparisonDto(
    long current,          // count of qualifying rows in the selected window
    Long previous,          // null when period=all (REQ-5); count otherwise
    Double percentChange    // null when period=all, or when previous == 0 (REQ-6)
) {}
```

Reuses the existing `DailyCountDto(LocalDate date, long count)` as-is —
no new per-day record type.

Example JSON, `period=30d`, previous window had zero new tenants:

```json
{
  "newTenantsPerDay": [{ "date": "2026-07-01", "count": 0 }, ...],
  "articlesReadPerDay": [{ "date": "2026-07-01", "count": 42 }, ...],
  "totalTenants": { "current": 120, "previous": 110, "percentChange": 9.1 },
  "newTenants": { "current": 5, "previous": 0, "percentChange": null },
  "totalArticlesRead": { "current": 8300, "previous": 7900, "percentChange": 5.1 },
  "staffCount": { "current": 3, "previous": 3, "percentChange": 0.0 }
}
```

`period=all` (no comparison possible):

```json
{
  "newTenantsPerDay": [{ "date": "2025-01-04", "count": 1 }, ...],
  "articlesReadPerDay": [{ "date": "2025-01-06", "count": 3 }, ...],
  "totalTenants": { "current": 120, "previous": null, "percentChange": null },
  "newTenants": { "current": 120, "previous": null, "percentChange": null },
  "totalArticlesRead": { "current": 8300, "previous": null, "percentChange": null },
  "staffCount": { "current": 6, "previous": null, "percentChange": null }
}
```

Note: for `period=all`, "current" for `totalTenants`/`newTenants`/
`totalArticlesRead`/`staffCount` is simply the all-time count (no lower
bound) — consistent with REQ-5 ("current-period count is still
returned").

## Dependencies

None. No `pom.xml` change — everything uses Spring Data JPA derived
queries and native `@Query` methods already established in this
codebase.

## Package/file structure

New files, all in `br.com.conectabyte.knowly.metrics.global`:

- `GlobalTrendsDto.java`
- `PeriodComparisonDto.java`

Changed files:

- `GlobalMetricsController.java` — add `GET /trends` mapping.
- `GlobalMetricsService.java` — add `globalTrends(MetricsPeriod)`,
  `previousWindowStart(...)` helper, `mergeZeroCountDays(...)` helper
  (private, mirrors `MetricsService`'s own).
- `TenantRepository.java` — add `countTenantsByDay()` /
  `countTenantsByDaySince(Instant)` (native, `@Query`),
  `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant,
  Instant)` (derived).
- `MessageArticleCitationRepository.java` — add
  `countCitationsByDay()` / `countCitationsByDaySince(Instant)`
  (native, `@Query`),
  `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant,
  Instant)` (derived).
- `UserRepository.java` — add
  `countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(List<GlobalRole>,
  Instant, Instant)` (derived).

No changes to `GlobalMetricsDto`, `globalMetrics()`, or any file
belonging to `global-staff-dashboard-metrics` (per SPEC's "Out of
scope").

## Testing strategy

TDAD, Red → Green, per constitution:

- **Unit tests** (`GlobalMetricsServiceTest`, mocked repositories):
  - `globalTrends(SEVEN_DAYS)` zero-fills days with no rows in both
    series (REQ-2/3).
  - `globalTrends(ALL)` returns only days with rows, no zero-fill, sorted
    chronologically (REQ-3).
  - `globalTrends(THIRTY_DAYS)` computes `percentChange` correctly for a
    representative case (current > previous, current < previous, current
    == previous == 0).
  - `globalTrends` returns `previous = null, percentChange = null` for
    all four metrics when `period == ALL` (REQ-5).
  - `globalTrends` returns `percentChange = null` (not `NaN`/`Infinity`)
    when a metric's previous-window count is 0 and current > 0 (REQ-6).
  - `previousWindowStart` computes the correct non-overlapping window
    bounds for `7d`/`30d`/`90d` (boundary-exclusive check).
- **Integration tests** (`GlobalMetricsControllerIntegrationTest`,
  Testcontainers, extending the existing
  `global-staff-dashboard-metrics` integration test class/pattern):
  - `STAFF_ADMIN` gets `200` with a well-formed body for each of
    `7d`/`30d`/`90d`/`all` and no `period` (defaults to `all`).
  - `STAFF` holding `DASHBOARD_VIEW_GLOBAL` gets `200`; `STAFF` lacking
    it gets `403` (REQ-8).
  - Tenant `MEMBER`/`MEMBER_ADMIN` with no `GlobalRole` gets `403`
    (REQ-9).
  - Invalid `period` value gets `400` (REQ-10).
  - Seed data across two+ tenants confirms the response reflects *all*
    tenants' rows, not scoped to any single active tenant (REQ-11) —
    same seeding/assertion style as `global-staff-dashboard-metrics`'s
    existing cross-tenant test.
  - `@AuditLog` action `metrics.global.trends.view` is recorded (mirrors
    the existing audit-log integration-test pattern for
    `metrics.global.view`).
