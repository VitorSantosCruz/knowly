# TASKS — global-staff-dashboard-sparklines (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) → Green (minimal code) → subproject verify.

## 1. Repository cumulative-count queries (REQ-1, REQ-2)

- [ ] 1. **Red** — Write `TenantRepositoryTest` cases: seed `Tenant` rows
      across several UTC calendar days, including more than one row on
      the same day, and assert `countCumulativeTenantsByDay()` returns
      one row per day-with-activity where each `count` is the true
      running total as of that day (never decreasing, correctly
      aggregating same-day rows into one bump), sorted chronologically.
- [ ] 2. **Green** — Add `TenantRepository.countCumulativeTenantsByDay():
      List<DailyCountProjection>` (native `@Query`, window-function SQL
      per PLAN.md's "Architectural decisions").
- [ ] 3. Run `./mvnw test -Dtest=TenantRepositoryTest` and confirm green;
      commit (`feat(metrics): add cumulative tenant count query`).
- [ ] 4. **Red** — Write `UserRepositoryTest` cases: seed `User` rows with
      a mix of `STAFF`/`STAFF_ADMIN`/`MEMBER`/no-`GlobalRole` across
      several UTC calendar days, assert `countCumulativeStaffByDay()`
      returns a running total that only counts `STAFF`/`STAFF_ADMIN`
      rows, correctly excluding every other role, sorted chronologically.
- [ ] 5. **Green** — Add `UserRepository.countCumulativeStaffByDay():
      List<DailyCountProjection>` (native `@Query`, same shape, `WHERE
      global_role IN ('STAFF','STAFF_ADMIN')`).
- [ ] 6. Run `./mvnw test -Dtest=UserRepositoryTest` and confirm green;
      commit (`feat(metrics): add cumulative staff count query`).

## 2. `GlobalTrendsDto` shape (REQ-1)

- [ ] 7. Append `totalTenantsPerDay`/`staffCountPerDay` fields to
      `GlobalTrendsDto` per PLAN.md's "API contracts". Update every
      existing positional `new GlobalTrendsDto(...)` call site
      (`GlobalMetricsServiceTest`) to pass the two new arguments (can
      pass empty lists as placeholders here — wired for real in section
      3). No standalone test needed for a plain record; covered
      indirectly by section 3's service tests. Commit
      (`feat(metrics): append cumulative series fields to GlobalTrendsDto`).

## 3. `mergeCarryForwardDays` + `GlobalMetricsService#globalTrends` wiring (REQ-2, REQ-3, REQ-4, REQ-5, REQ-6)

- [ ] 8. **Red** — Write `GlobalMetricsServiceTest` cases for a new
      package-private (or directly-tested-via-`globalTrends`) helper
      `mergeCarryForwardDays`:
      - Bounded period (e.g. `THIRTY_DAYS`): a quiet day within the range
        carries the prior day's total forward, never resets to `0` once
        positive.
      - Bounded period: the *first* displayed day seeds its carry value
        from a cumulative total recorded strictly *before* the display
        range starts (not `0`) — the "tenant created 6 months ago, `7d`
        window" case from PLAN.md.
      - `period=all`: display range spans from the earliest row's day
        through today, same carry-forward rule.
      - `period=all` with zero rows: returns an empty list (not
        zero-filled).
      - Bounded period with zero rows at all: every day in the range is
        `0`.
- [ ] 9. **Green** — Implement `mergeCarryForwardDays(List<DailyCountProjection>,
      MetricsPeriod)` as a private helper on `GlobalMetricsService`,
      exactly per PLAN.md's algorithm.
- [ ] 10. **Red** — Write `GlobalMetricsServiceTest` cases confirming
      `globalTrends(...)` wires `countCumulativeTenantsByDay()`/
      `countCumulativeStaffByDay()` through `mergeCarryForwardDays(...)`
      into `totalTenantsPerDay`/`staffCountPerDay`, and that
      `newTenantsPerDay`/`articlesReadPerDay`/all four
      `PeriodComparisonDto` fields remain byte-for-byte unchanged from
      before this feature (REQ-6 regression guard).
- [ ] 11. **Green** — Wire the two new repository calls and
      `mergeCarryForwardDays(...)` into `globalTrends(MetricsPeriod)`,
      populating the two new `GlobalTrendsDto` fields.
- [ ] 12. Run `./mvnw test -Dtest=GlobalMetricsServiceTest` and confirm
      green; commit
      (`feat(metrics): compute cumulative running-total series in globalTrends`).

## 4. Integration coverage (REQ-1, REQ-7)

- [ ] 13. **Red** — Extend `GlobalMetricsControllerIntegrationTest`: seed
      tenants/staff users across several days (including days before a
      `7d`/`30d` window), assert `GET /api/staff/metrics/global/trends`
      response includes `totalTenantsPerDay`/`staffCountPerDay` with the
      same `date`/`count` shape as the existing two series, correct
      carry-forward values, for `7d`/`30d`/`90d`/`all`. Confirm existing
      `DASHBOARD_VIEW_GLOBAL`/`STAFF_ADMIN`/`403`/`400` assertions still
      pass unmodified against the now-larger response.
- [ ] 14. **Green** — No production code expected to change here if
      sections 1-3 are correct; this task exists to catch any wiring gap
      at the controller/serialization boundary. Fix anything the Red
      step surfaces.
- [ ] 15. Run `./mvnw test -Dtest=GlobalMetricsControllerIntegrationTest`
      and confirm green; commit
      (`test(metrics): cover cumulative trend series at the API boundary`).

## 5. Full regression + doc sync

- [ ] 16. Run `./mvnw spotless:apply && ./mvnw verify` for the whole
      `knowly-api` module and confirm the entire suite is green, not just
      this feature's tests. Cross-check against SPEC.md's acceptance-
      criteria checklist before considering this feature complete.
      Resolve any compiler/IDE warnings in touched files before
      committing (standing rule).
- [ ] 17. Update `PLAN.md` with any decision that changed during
      implementation.
- [ ] 18. Update root `PROJECT_STATUS.md` to record
      `global-staff-dashboard-sparklines` (backend) as implemented.
- [ ] 19. Final commit for any doc-only changes from steps 17/18
      (`docs(metrics): record global-staff-dashboard-sparklines backend completion`).
