# TASKS — dashboard-analytics (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: Red (failing test) → Green (minimal code) → subproject verify.

## 0. Groundwork (read-before-write, no test yet)

- [ ] 0.1. Confirm this module's exception/DTO subpackage convention (does
      `metrics` need its own `exception/`/`dto/` subpackages like `article`,
      `conversation`, `tenancy` already have, or do existing `metrics` DTOs
      sit flat in the package?) and whether a `java.time.Clock` bean already
      exists anywhere in `br.com.conectabyte.knowly`. Adjust PLAN.md's
      "Package/file structure" section if either assumption was wrong.

## 1. Shared `period` parsing/validation (REQ-2, REQ-3)

- [ ] 1. **Red** — Write `MetricsPeriodTest` (plain JUnit): `from("7d")`,
      `from("30d")`, `from("90d")`, `from("all")`, `from(null)` all parse to
      the expected enum constant; `from("bogus")` throws
      `InvalidPeriodException`; `startInstant(fixedClock)` returns the
      correct UTC instant for each non-`all` value and `Optional.empty()`
      for `all`.
- [ ] 2. **Green** — Implement `MetricsPeriod` enum + `InvalidPeriodException`
      (+ `ClockConfig` only if task 0.1 found no existing `Clock` bean) to
      make task 1 pass.
- [ ] 3. **Red** — Write a `MetricsExceptionHandlerTest` (or extend an
      existing handler test if this module already has one) asserting
      `InvalidPeriodException` maps to `400` + `MetricsErrorResponseDto("INVALID_PERIOD")`.
- [ ] 4. **Green** — Implement `MetricsExceptionHandler` + `MetricsErrorResponseDto`.
- [ ] 5. Run `./mvnw test -Dtest=MetricsPeriodTest,MetricsExceptionHandlerTest`
      and confirm green; commit
      (`feat(metrics): add shared period parsing and validation`).

## 2. Conversations time-series endpoint (REQ-4)

- [ ] 6. **Red** — In `MetricsControllerIntegrationTest`, add a test seeding
      conversations on specific days within a 7-day window for tenant A
      (using a fixed/injected `Clock` or directly persisting rows with
      controlled `createdAt` values, e.g. via `saveAndFlush` then
      overwriting `createdAt` and re-saving, matching however this test
      class already handles time-sensitive setup — check for precedent
      first) and tenant B, then asserts
      `GET /api/tenants/metrics/conversations/timeseries?period=7d`
      returns exactly 7 chronological entries for tenant A only, with
      zero-count days present and tenant B's conversations absent.
- [ ] 7. **Green** — Add `ConversationRepository` day-bucketed aggregate
      query method, `ConversationsTimeseriesDto`/`DailyCountDto`,
      `MetricsService#conversationsTimeseries(MetricsPeriod)` (merging
      zero-count days in Java per PLAN.md), and the controller endpoint
      with `@RequiresPermission(Permission.DASHBOARD_VIEW)` +
      `@AuditLog(action = "metrics.conversations.timeseries.view", resourceType = "Metrics")`.
- [ ] 8. **Red** — Add a period-validation test:
      `GET .../conversations/timeseries?period=bogus` returns 400
      `INVALID_PERIOD`. Add a permission-denial case to
      `eachMetricsEndpointRequiresDashboardViewPermissionIndependently`.
- [ ] 9. **Green** — Confirm both pass with existing wiring (should already
      be green from tasks 2 and 4's Green steps — this task only adds the
      assertions and fixes anything not already covered).
- [ ] 10. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit
      (`feat(metrics): add conversations time-series endpoint`).

## 3. Messages time-series endpoint (REQ-5)

- [ ] 11. **Red** — Add a test seeding USER/ASSISTANT messages on specific
      days within a 7-day window for tenant A and tenant B, asserting
      `GET /api/tenants/metrics/messages/timeseries?period=7d` returns 7
      chronological entries with correct per-role counts, zero-count days
      included, tenant B excluded.
- [ ] 12. **Green** — Add `MessageRepository` day-bucketed aggregate query
      (grouped by day and role, or two queries merged in the service —
      whichever keeps the query simplest; still a single query per role is
      acceptable since it's still O(1) queries, not O(days)),
      `MessagesTimeseriesDto`/`DailyRoleCountDto`,
      `MetricsService#messagesTimeseries(MetricsPeriod)`, and the controller
      endpoint (`metrics.messages.timeseries.view`).
- [ ] 13. **Red** — Add period-validation and permission-denial assertions
      for this endpoint (extend the shared tests from step 8's pattern).
- [ ] 14. **Green** — Confirm green.
- [ ] 15. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit
      (`feat(metrics): add messages time-series endpoint`).

## 4. Articles time-series endpoint (REQ-6)

- [ ] 16. **Red** — Add a test seeding active articles on specific days
      within a 7-day window for tenant A (plus at least one inactive
      article, to assert it's excluded, matching
      `countByTenantIdAndActiveTrue`'s existing filter) and unrelated
      articles for tenant B, asserting
      `GET /api/tenants/metrics/articles/timeseries?period=7d` returns 7
      chronological entries with correct counts, zero-count days included,
      tenant B excluded, inactive articles excluded.
- [ ] 17. **Green** — Add `ArticleRepository` day-bucketed aggregate query
      (filtered to `active = true`, same shape as the conversations
      time-series query from task 7), reuse `ArticlesTimeseriesDto`
      wrapping the existing `DailyCountDto`,
      `MetricsService#articlesTimeseries(MetricsPeriod)` (merging
      zero-count days in Java, same helper/approach as
      `conversationsTimeseries`), and the controller endpoint with
      `@RequiresPermission(Permission.DASHBOARD_VIEW)` +
      `@AuditLog(action = "metrics.articles.timeseries.view", resourceType = "Metrics")`.
- [ ] 18. **Red** — Add a period-validation test
      (`GET .../articles/timeseries?period=bogus` → 400 `INVALID_PERIOD`)
      and extend `eachMetricsEndpointRequiresDashboardViewPermissionIndependently`
      with this new path.
- [ ] 19. **Green** — Confirm both pass with existing wiring.
- [ ] 20. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit
      (`feat(metrics): add articles time-series endpoint`).

## 5. Members active/inactive endpoint (REQ-7)

- [ ] 21. **Red** — Add a test seeding active and inactive
      `TenantMembership` rows for tenant A and tenant B, asserting
      `GET /api/tenants/metrics/members` returns the correct
      `activeCount`/`inactiveCount` for tenant A only.
- [ ] 22. **Green** — Add `TenantMembershipRepository#countByTenantIdAndActive`,
      `MembersMetricDto`, `MetricsService#membersMetric()`, and the
      controller endpoint (`metrics.members.view`).
- [ ] 23. **Red** — Add a permission-denial assertion for this endpoint
      (extend the shared enumeration test).
- [ ] 24. **Green** — Confirm green.
- [ ] 25. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit
      (`feat(metrics): add tenant membership active/inactive metric`).

## 6. `period` on existing point-in-time endpoints (REQ-9)

- [ ] 26. **Red** — Add tests: seed conversations both inside and outside a
      30-day window, assert `GET /api/tenants/metrics/conversations?period=30d`
      counts only the in-window ones, while `?period=all`/no param preserves
      today's existing all-time behavior (reuse
      `conversationsMetricIsTenantWideNotJustTheCallersOwn`'s setup style,
      don't replace it — add a new test alongside it).
- [ ] 27. **Green** — Add `ConversationRepository#countByTenantIdAndCreatedAtGreaterThanEqual`,
      update `MetricsService#conversationsMetric()` to accept
      `MetricsPeriod` and branch to the unfiltered method only for `ALL`,
      update the controller signature to accept `period`.
- [ ] 28. **Red** — Same pattern for messages:
      `GET /api/tenants/metrics/messages?period=30d` test.
- [ ] 29. **Green** — Add
      `MessageRepository#countByConversation_Tenant_IdAndRoleAndCreatedAtGreaterThanEqual`,
      update `MetricsService#messagesMetric()` and the controller signature.
- [ ] 30. **Red** — Add period-validation assertions
      (`?period=bogus` → 400) for both endpoints if not already covered by
      step 8's shared test extension.
- [ ] 31. **Green** — Confirm green.
- [ ] 32. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit
      (`feat(metrics): add period filtering to existing point-in-time endpoints`).

## 7. CSV export endpoint (REQ-8)

- [ ] 33. **Red** — Add a test seeding a small, known dataset (active
      article, conversation, USER/ASSISTANT messages across a couple of
      days, active/inactive memberships) for tenant A and unrelated data for
      tenant B, asserting `GET /api/tenants/metrics/export?period=all`
      returns `200`, `Content-Type` starting with `text/csv`, a
      `Content-Disposition: attachment` header, a body containing the
      expected aggregate lines and per-day rows for tenant A — including
      the articles/day section alongside conversations/day and
      messages/day — and NOT containing tenant B's article title or any
      message `content` string used in the test.
- [ ] 34. **Green** — Implement `MetricsService#exportCsv(MetricsPeriod)`
      (reusing the existing/new metric methods, including
      `articlesTimeseries`, rather than re-querying) and the controller
      endpoint returning `ResponseEntity<byte[]>` with the headers above,
      `@AuditLog(action = "metrics.export.view", resourceType = "Metrics")`.
- [ ] 35. **Red** — Add a permission-denial assertion for `/export`
      (extend the shared enumeration test) and a period-validation
      assertion.
- [ ] 36. **Green** — Confirm green.
- [ ] 37. Run `./mvnw test -Dtest=MetricsControllerIntegrationTest` and
      confirm green; commit (`feat(metrics): add CSV export endpoint`).

## 8. Full regression + doc sync

- [ ] 38. Run `./mvnw spotless:apply && ./mvnw verify` for the whole
      `knowly-api` module and confirm the entire suite is green, not just
      the metrics tests. Cross-check against SPEC.md's acceptance-criteria
      checklist (now including the articles-time-series bullet) before
      considering this feature complete.
- [ ] 39. Update `PLAN.md` with any decision that changed during
      implementation (e.g. task 0.1's findings, any query approach that
      turned out simpler/different than planned).
- [ ] 40. Update root `PROJECT_STATUS.md` to record
      `dashboard-analytics` (backend) as implemented, and add a
      `DECISIONS.md` entry for the UTC-bucketing choice (see PLAN.md's
      "Architectural decisions" — first bullet — for the content), per this
      repo's standing instruction to keep both updated as work lands.
- [ ] 41. Final commit for any doc-only changes from steps 39/40
      (`docs(metrics): record dashboard-analytics completion and UTC bucketing decision`).
