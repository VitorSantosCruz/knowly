# TASKS — global-staff-dashboard-metrics

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Preamble — test execution deferred (ONE-TIME exception, not standing policy)

**This is a one-time, dated exception the human product owner gave
directly, in conversation, on 2026-07-26 — it is NOT a change to this
project's standing process** (`constitution.md`'s TDAD Red/Green cycle,
and the "commit each completed task as you go" rule in the root
`CLAUDE.md`/`PROJECT_STATUS.md` conventions, remain the project's actual
policy going forward). It applies only to this specific batch of
backlog features implemented in this one session and must not be read
by any future session/agent as license to skip test execution or batch
commits by default.

For this session only: every task below that touches behavior still
requires a test written **first** (Red before Green) — test-first
authorship is mandatory and not skipped. However, **backend-engineer
must NOT run `./mvnw test`, `./mvnw verify`, or any other test-execution
command while working through these tasks.** Write the test, write the
minimal code to make it pass by inspection/reasoning, move on. All test
execution — this feature's suite and the full project suite — happens
in a single final pass (task 12) once all currently planned backlog work
(not just this feature) is done. Do not skip writing the tests
themselves; only the *running* of them is deferred, and only for this
2026-07-26 batch.

- [x] 0. Acknowledge the above: write tests for every task first, but do
      not execute any test command until task 12.

## GlobalPermission

- [x] 1. Add `DASHBOARD_VIEW_GLOBAL` to the `GlobalPermission` enum. (No
      test — pure enum addition, no behavior yet, same precedent as
      `staff-user-listing` task 1.)

## Repository additions

- [x] 2. Write repository-level test(s) for
      `TenantRepository.countByCreatedAtGreaterThanEqual(Instant)`
      (Red) — cover: a tenant created before the cutoff is excluded, a
      tenant created at/after the cutoff is included, zero tenants
      returns 0.
- [x] 3. Implement `countByCreatedAtGreaterThanEqual(Instant from)` on
      `TenantRepository` (Green, by inspection — do not run tests yet).
- [x] 4. Write repository-level test(s) for
      `UserRepository.countByGlobalRoleIn(List<GlobalRole>)` (Red) —
      cover: counts only `STAFF`/`STAFF_ADMIN` rows, excludes users with
      no `GlobalRole`, zero staff users returns 0.
- [x] 5. Implement `countByGlobalRoleIn(List<GlobalRole> globalRoles)`
      on `UserRepository` (Green, by inspection — do not run tests yet).
      (No new method needed on `TenantRepository.count()` or
      `MessageArticleCitationRepository.count()` — both already exist
      via `JpaRepository`.)

## DTO

- [x] 6. Add `GlobalMetricsDto(long tenantCount, long
      newTenantsThisMonth, long articlesReadTotal, long staffCount)`
      record in new package `br.com.conectabyte.knowly.metrics.global`.
      (No standalone test — a plain data record, exercised indirectly
      by the service test below.)

## GlobalMetricsService

- [x] 7. Write `GlobalMetricsServiceTest` case (Red) for SPEC REQ-1/3/
      4/5/6: given known counts of `Tenant` (mixed createdAt, some in
      current UTC month, some in prior), `MessageArticleCitation`, and
      `User` (mixed `GlobalRole`), `globalMetrics()` returns the correct
      `tenantCount`, `newTenantsThisMonth`, `articlesReadTotal`, and
      `staffCount` — using the injected `Clock`, not the system clock,
      to make the UTC-month boundary deterministic (mirrors
      `MetricsServiceTest`'s existing `Clock` convention).
- [x] 8. Write `GlobalMetricsServiceTest` case (Red) for SPEC REQ-9: a
      `STAFF` caller with no `DASHBOARD_VIEW_GLOBAL` grant calling
      `globalMetrics()` throws `PermissionDeniedException`.
- [x] 9. Write `GlobalMetricsServiceTest` case (Red) for SPEC REQ-8: a
      `STAFF` caller holding `DASHBOARD_VIEW_GLOBAL` (direct grant,
      matching existing `grantPermission` test setup convention)
      succeeds; and a separate case confirming `STAFF_ADMIN` succeeds
      without any explicit grant.
- [x] 10. Implement `GlobalMetricsService.globalMetrics()` in the new
      `metrics.global` package — `@Transactional(readOnly = true)`,
      `@RequiresGlobalPermission(GlobalPermission.DASHBOARD_VIEW_GLOBAL)`,
      `@AuditLog(action = "metrics.global.view", resourceType =
      "Metrics")` — computing `startOfCurrentUtcMonth` from the injected
      `Clock` (`LocalDate.now(clock).withDayOfMonth(1)
      .atStartOfDay(ZoneOffset.UTC).toInstant()`) and calling
      `tenantRepository.count()`,
      `tenantRepository.countByCreatedAtGreaterThanEqual(...)`,
      `messageArticleCitationRepository.count()`,
      `userRepository.countByGlobalRoleIn(List.of(GlobalRole.STAFF,
      GlobalRole.STAFF_ADMIN))` — to make tasks 7–9 green by inspection
      — do not run tests yet.

## Controller

- [x] 11a. Write a controller/integration test (Red) covering the full
      `GET /api/staff/metrics/global` contract per SPEC acceptance
      criteria: 200 with correct counts for `STAFF_ADMIN`; 200 for a
      `STAFF` caller holding `DASHBOARD_VIEW_GLOBAL`; 403 for a `STAFF`
      caller without the grant; 403 for a tenant `MEMBER`/`MEMBER_ADMIN`
      with no `GlobalRole`; "new tenants this month" boundary case
      (previous-UTC-month tenant excluded, current-UTC-month tenant
      included).
- [x] 11b. Implement `GlobalMetricsController`
      (`@RequestMapping("/api/staff/metrics")`,
      `@GetMapping("/global")`, delegates to
      `globalMetricsService.globalMetrics()`, no controller-level
      permission/audit annotations — both live on the service method
      per this feature's PLAN) to make task 11a green by inspection —
      do not run tests yet.

## Final pass (only once all currently planned backlog work, not just
## this feature, is done)

- [x] 12. Run `./mvnw spotless:apply` then `./mvnw verify` for the full
      suite (this feature's tests plus every pre-existing test) and fix
      any regression surfaced.
- [x] 13. Hand off to `qa-test-automation` and `appsec` for review of
      this feature during that same final pass — not before, and not
      as a substitute for task 12's own green run.
- [x] 14. Commit the completed, verified work (Conventional Commits),
      once — and only once — task 12's full suite is green and task 13's
      reviews are addressed.
