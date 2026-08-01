# TASKS — active-members-trend (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

**Sequencing note**: do not start task 1's Green step until
`knowly-api`'s `active-members-trend` feature has actually landed
`GET /api/tenants/metrics/members/timeseries` matching its PLAN.md's
contract — this frontend feature has no fallback if the endpoint
doesn't exist yet.

- [ ] 1. Write a failing test asserting the active-members tile's
      `app-metric-tile` binds `url="/api/tenants/metrics/members/timeseries"`
      and `[period]="period()"` (Red — REQ-1/5). Update
      `dashboard-page.component.ts`'s template accordingly (Green).
- [ ] 2. Write a failing test asserting the active-members tile no
      longer sets `[showSparkline]="false"` (Red — REQ-2). Remove the
      binding (Green).
- [ ] 3. Write a failing unit test for `activeMembersValueSelector`
      asserting it returns the last day's `count` from a sample
      `{ days: [{date, count}, ...] }` payload, not a sum (Red — REQ-3).
      Update the selector's implementation from
      `(data as MembersResponse).activeCount` to
      `(data as DailyCountResponse).days.at(-1)?.count ?? 0` (Green).
      Remove the now-unused `MembersResponse` interface.
- [ ] 4. Write a failing test asserting the active-members tile passes
      `[sparklineSelector]="dailyCountSparklineSelector"` (Red — REQ-4).
      Add the binding (Green).
- [ ] 5. Confirm (no new test expected to fail) that a `403`/network
      error on the new endpoint still renders
      `app-no-access-state`/`app-error-state` via
      `MetricTileComponent`'s existing fallback (REQ-6) — add a targeted
      assertion only if no existing test already covers this
      shape-agnostically.
- [ ] 6. Run `npm run format:check && npm test && npm run build && npm run lint`
      and confirm all four pass.
- [ ] 7. Update `PLAN.md`/`PROJECT_STATUS.md` if any decision changed
      during implementation.
