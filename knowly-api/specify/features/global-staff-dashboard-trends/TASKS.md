# TASKS — global-staff-dashboard-trends (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) → Green (minimal code) → subproject verify.

## 0. Index verification (AppSec non-blocking recommendation)

- [x] 0.1. Check existing Flyway migrations
      (`src/main/resources/db/migration/V*.sql`, e.g.
      `V*__create_tenants_table.sql` and
      `V12__create_message_article_citations_table.sql`) for any existing
      index covering `tenants.created_at` or
      `message_article_citations.created_at`. Confirmed as of this PLAN:
      `message_article_citations` currently has only
      `ix_message_article_citations_article` (on `article_id`), and no
      migration creates an index on either table's `created_at` column —
      re-verify this hasn't changed since, don't assume the earlier check
      still holds.
- [x] 0.2. If no such index exists (expected outcome per 0.1), add a new
      Flyway migration `V21__add_created_at_indexes_for_global_trends.sql`
      creating `ix_tenants_created_at ON tenants (created_at)` and
      `ix_message_article_citations_created_at ON message_article_citations
      (created_at)` — these back the `period=all` unbounded
      `date_trunc('day', created_at)` `GROUP BY` queries this feature adds
      (`TenantRepository.countTenantsByDay()`,
      `MessageArticleCitationRepository.countCitationsByDay()`), per
      AppSec's non-blocking recommendation on this feature's PLAN review.
      If 0.1 finds an index already covering one or both columns, skip
      adding a redundant migration for that column and note why in this
      task's checkbox comment.
- [x] 0.3. Run `./mvnw flyway:info` (or run the app locally against the
      dev `compose.yaml` Postgres) to confirm the new migration applies
      cleanly with no version conflict; commit
      (`feat(metrics): add created_at indexes for global trends queries`).

## 1. Repository query methods (REQ-2, REQ-4, REQ-11)

- [x] 1. **Red** — Write `TenantRepositoryTest`/`MessageArticleCitationRepositoryTest`/
      `UserRepositoryTest` (Testcontainers `@DataJpaTest` or equivalent,
      matching this codebase's existing repository-test precedent for
      `ArticleRepository.countActiveByDayForTenantSince`-style methods):
      seed rows across two+ tenants/users with controlled `createdAt`
      values, assert `TenantRepository.countTenantsByDaySince(Instant)` /
      `.countTenantsByDay()`,
      `MessageArticleCitationRepository.countCitationsByDaySince(Instant)` /
      `.countCitationsByDay()`, and the derived
      `countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(...)` methods
      on `TenantRepository`/`MessageArticleCitationRepository`, and
      `UserRepository.countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(...)`,
      all return counts reflecting *every* tenant's rows (no
      `TenantFilter` scoping applied), with day-bucketed results sorted
      chronologically and window-bounded counts respecting the
      half-open `[from, to)` semantics PLAN.md specifies.
- [x] 2. **Green** — Add the native `@Query` day-bucketed methods and the
      derived window-count methods to `TenantRepository`,
      `MessageArticleCitationRepository`, and `UserRepository` exactly as
      specified in PLAN.md's "Package/file structure" section, reusing
      the existing `DailyCountProjection` interface.
- [x] 3. Run `./mvnw test -Dtest=TenantRepositoryTest,MessageArticleCitationRepositoryTest,UserRepositoryTest`
      and confirm green; commit
      (`feat(metrics): add cross-tenant repository queries for global trends`).

## 2. DTOs (REQ-2, REQ-4, REQ-5, REQ-6)

- [x] 4. Add `GlobalTrendsDto` and `PeriodComparisonDto` records to
      `br.com.conectabyte.knowly.metrics.global`, exactly matching
      PLAN.md's "API contracts" shapes (`Long`/`Double` boxed fields on
      `PeriodComparisonDto` so Jackson serializes `null`, not `0`/`NaN`).
      No test needed for a plain record with no logic; covered indirectly
      by the service tests in section 3.

## 3. `GlobalMetricsService#globalTrends` (REQ-2, REQ-3, REQ-4, REQ-5, REQ-6)

- [x] 5. **Red** — Write `GlobalMetricsServiceTest` cases (mocked
      repositories, injected fixed `Clock`):
      - `globalTrends(SEVEN_DAYS)` zero-fills both `newTenantsPerDay` and
        `articlesReadPerDay` for days with no rows (REQ-2/3).
      - `globalTrends(ALL)` returns only days with rows in both series, no
        zero-fill, sorted chronologically (REQ-3).
      - `globalTrends(THIRTY_DAYS)` computes `percentChange` correctly for
        current > previous, current < previous, and current == previous
        == 0.
      - `globalTrends(ALL)` returns `previous = null, percentChange = null`
        for all four `PeriodComparisonDto`s (REQ-5).
      - `globalTrends(<bounded>)` returns `percentChange = null` (never
        `NaN`/`Infinity`) when a metric's previous-window count is 0 and
        current > 0 (REQ-6).
      - `previousWindowStart(period, currentStart, clock)` computes the
        correct non-overlapping `[now - 2N, now - N)` bounds for
        `7d`/`30d`/`90d`, boundary-exclusive.
- [x] 6. **Green** — Implement `GlobalMetricsService#globalTrends(MetricsPeriod)`,
      the package-private `previousWindowStart(...)` helper, and the
      private `mergeZeroCountDays(...)` helper (mirroring
      `MetricsService`'s own), wiring in the repository methods from
      section 1 and the DTOs from section 2, per PLAN.md's "Architectural
      decisions".
- [x] 7. Run `./mvnw test -Dtest=GlobalMetricsServiceTest` and confirm
      green; commit
      (`feat(metrics): add globalTrends service method with period comparison`).

## 4. `GET /api/staff/metrics/global/trends` endpoint (REQ-1, REQ-7, REQ-8, REQ-9, REQ-10, REQ-11)

- [x] 8. **Red** — Extend `GlobalMetricsControllerIntegrationTest`
      (Testcontainers, extending the existing
      `global-staff-dashboard-metrics` integration test class/pattern):
      - `STAFF_ADMIN` gets `200` with a well-formed `GlobalTrendsDto` body
        for each of `7d`/`30d`/`90d`/`all` and no `period` (defaults to
        `all`) (REQ-1).
      - `STAFF` holding `DASHBOARD_VIEW_GLOBAL` gets `200`; `STAFF` lacking
        it gets `403` (REQ-7/8).
      - Tenant `MEMBER`/`MEMBER_ADMIN` with no `GlobalRole` gets `403`
        (REQ-9).
      - Invalid `period` value gets `400` (REQ-10).
      - Seed data across two+ tenants confirms the response reflects
        *all* tenants' rows, not scoped to any single active tenant
        (REQ-11), same seeding/assertion style as
        `global-staff-dashboard-metrics`'s existing cross-tenant test.
      - `@AuditLog` action `metrics.global.trends.view` is recorded.
- [x] 9. **Green** — Add the `GET /trends` mapping to
      `GlobalMetricsController`, gated by the same
      `@RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)`
      mechanism and `@AuditLog(action = "metrics.global.trends.view",
      resourceType = "Metrics")` annotation `globalMetrics()` already
      uses, delegating to `GlobalMetricsService#globalTrends(MetricsPeriod)`.
- [x] 10. Run `./mvnw test -Dtest=GlobalMetricsControllerIntegrationTest`
      and confirm green; commit
      (`feat(metrics): add GET /api/staff/metrics/global/trends endpoint`).

## 5. Full regression + doc sync

- [x] 11. Run `./mvnw spotless:apply && ./mvnw verify` for the whole
      `knowly-api` module and confirm the entire suite is green, not just
      this feature's tests. Cross-check against SPEC.md's acceptance-
      criteria checklist before considering this feature complete.
      Resolve any compiler/IDE warnings in touched files before
      committing (standing rule).
- [x] 12. Update `PLAN.md` with any decision that changed during
      implementation (e.g. task 0.1's actual findings, any query approach
      that turned out simpler/different than planned).
- [x] 13. Update root `PROJECT_STATUS.md` to record
      `global-staff-dashboard-trends` (backend) as implemented.
- [x] 14. Final commit for any doc-only changes from steps 12/13
      (`docs(metrics): record global-staff-dashboard-trends backend completion`).
