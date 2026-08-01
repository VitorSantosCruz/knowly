# TASKS — active-members-trend (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [ ] 1. Add migration `V22__create_active_member_snapshots_table.sql`
      (table + unique constraint `(tenant_id, snapshot_date)` + index —
      no test needed, this is schema-only; verified by task 2's test
      actually persisting through it).
- [ ] 2. Write a failing repository test asserting
      `ActiveMemberSnapshotRepository`'s upsert query inserts a new row
      when none exists for `(tenantId, day)` (Red). Implement
      `ActiveMemberSnapshot` entity + `ActiveMemberSnapshotRepository`
      with the `INSERT ... ON CONFLICT (tenant_id, snapshot_date) DO
      UPDATE` native upsert method (Green).
- [ ] 3. Write a failing repository test asserting calling the same
      upsert twice for the same `(tenantId, day)` with a different count
      leaves exactly one row, with the latest count (Red — REQ-3).
      Confirm the upsert query already satisfies it (Green — should
      already pass from task 2's implementation; if not, fix the
      `ON CONFLICT` clause).
- [ ] 4. Write a failing repository test for the day-bucketed read query
      (`countByTenantSince`/`countByTenant`, mirrors
      `ArticleRepository.countActiveByDayForTenant(Since)`) asserting it
      returns only the active tenant's rows, ordered chronologically
      (Red). Implement the query (Green).
- [ ] 5. Write a failing unit test for `ActiveMemberSnapshotScheduler`
      asserting it computes "yesterday" from a fixed `Clock` and calls
      the repository's upsert once per tenant returned by the aggregate
      count query (Red — use a mocked repository). Implement
      `ActiveMemberSnapshotScheduler` with `@Scheduled(cron = "0 5 0 * *
      *", zone = "UTC")` (Green). Add `@EnableScheduling` to
      `KnowlyApplication`.
- [ ] 6. Write a failing integration test asserting
      `MetricsService.membersTimeseries(period)` returns zero-filled
      per-day counts for `7d`/`30d`/`90d` and sparse days for `all`,
      scoped to the active tenant, using rows seeded directly via
      `ActiveMemberSnapshotRepository` (Red — REQ-4/6/7/8). Implement
      `MembersTimeseriesDto` + `MetricsService.membersTimeseries` reusing
      the existing `mergeZeroCountDays` helper (Green).
- [ ] 7. Write a failing `MetricsControllerIntegrationTest` case
      asserting `GET /api/tenants/metrics/members/timeseries` returns
      200 with the expected shape for a caller holding `DASHBOARD_VIEW`,
      403 for a caller who doesn't (REQ-9), and 400 for an invalid
      `period` (REQ-5) (Red). Wire the new `@GetMapping("/members/timeseries")`
      + `@RequiresPermission(DASHBOARD_VIEW)` +
      `@AuditLog(action = "metrics.members.timeseries.view", ...)` on
      `MetricsController` (Green).
- [ ] 8. Write a failing integration test asserting tenant A's snapshot
      data is never visible while tenant B is active (Red — REQ-8,
      tenant-isolation regression guard mirroring
      `MetricsControllerIntegrationTest`'s existing pattern). Confirm it
      passes given the explicit `tenant_id` predicate already in the
      read query from task 4 (Green — should already pass; investigate
      if not).
- [ ] 9. Write a failing integration test confirming
      `GET /api/tenants/metrics/members` (existing endpoint) is
      byte-for-byte unchanged in shape/behavior after this feature lands
      (Red — REQ-11 regression guard). Confirm green with no production
      code change needed.
- [ ] 10. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
       full suite is green.
- [ ] 11. Update `PLAN.md`/`PROJECT_STATUS.md`/`DECISIONS.md` if any
       decision changed during implementation.
