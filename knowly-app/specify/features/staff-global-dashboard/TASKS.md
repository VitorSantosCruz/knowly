# TASKS — staff-global-dashboard

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 1. Shared prerequisites

- [x] 1. Verify `core/global-permission.ts` does not yet contain
      `DASHBOARD_VIEW_GLOBAL`/`AUDIT_TRAIL_VIEW` (confirm current state
      per PLAN's own note before editing).
- [x] 2. Add `'DASHBOARD_VIEW_GLOBAL'` and `'AUDIT_TRAIL_VIEW'` to
      `GlobalPermission` and `ALL_GLOBAL_PERMISSIONS` in
      `core/global-permission.ts`.

## 2. `metric-tile.component.ts` additive extension (REQ-3, REQ-4)

- [x] 3. Test: `MetricTileComponent` with `[value]` set renders `label()`
      + `value()`, issues no HTTP call, and renders no sparkline
      chart/table (Red).
- [x] 4. Test: `MetricTileComponent` with `[disabled]="true"` renders a
      "coming soon" state, issues no HTTP call regardless of other
      inputs (Red).
- [x] 5. Test: the five existing self-fetching cases (loading, success,
      `'network'` error, `'permission-denied'` error, sparkline
      chart/table rendering) still pass unmodified, proving the
      extension is backward-compatible (Red only if any regress;
      otherwise this is the existing suite re-run as a checkpoint).
- [x] 6. Implement the `value`/`loading`/`disabled` inputs and make
      `url`/`valueSelector`/`period` optional, gating the self-fetch
      `effect()` on `url()` being defined (Green).

## 3. Staff-user audit-trail service method (REQ-10)

- [x] 7. Test: `StaffUserService.getAuditTrail(userId)` calls
      `GET /api/staff/users/{userId}/audit-trail` and returns
      `AuditEvent[]` (Red).
- [x] 8. Implement `AuditEvent` interface + `getAuditTrail` on
      `StaffUserService` (Green).

## 4. Global dashboard screen switch (REQ-1, REQ-2, REQ-7)

- [x] 9. Test: `DashboardWrapperPageComponent` shows a loading state
      while `!activeTenantService.activeTenantResolved()` (Red).
- [x] 10. Test: once resolved, it renders `DashboardPageComponent` when
       `activeTenantId()` is non-null, and `GlobalDashboardPageComponent`
       when it's null; changing from null to non-null swaps the rendered
       child (REQ-7) (Red).
- [x] 11. Implement `DashboardWrapperPageComponent` (Green).
- [x] 12. Point the `/dashboard` route at `DashboardWrapperPageComponent`
       instead of `DashboardPageComponent` in `app.routes.ts`
       (`tenantSelectionGuard` unchanged).

## 5. Global metrics view (REQ-3, REQ-4, REQ-5)

- [x] 13. Test: `GlobalDashboardPageComponent` renders 4 populated tiles
       (total tenants, new tenants this month, total articles read,
       staff count) plus 1 disabled "support tickets — coming soon" tile
       after a successful `GET /api/staff/metrics/global` (Red).
- [x] 14. Implement `GlobalDashboardPageComponent`'s success rendering
       (Green).
- [x] 15. Test: a 403 from `GET /api/staff/metrics/global` renders
       `app-no-access-state` once, at the page level, not per-tile (Red).
- [x] 16. Test: a non-403 error renders `app-error-state` (Red).
- [x] 17. Implement the page-level loading/error handling (Green).

## 6. Welcome-screen quick link (REQ-8, REQ-9)

- [x] 18. Test: `WelcomePageComponent` shows a quick-link card to
       `/dashboard` when there's no active tenant and the caller holds
       `DASHBOARD_VIEW_GLOBAL` or is `STAFF_ADMIN`-shaped (Red).
- [x] 19. Test: the card is absent when the caller holds neither, and
       absent whenever `tenantName()` is set (existing tenant-member
       case unaffected) (Red).
- [x] 20. Implement `showGlobalDashboard` computed + the new card +
       injecting/fetching `GlobalPermissionsService` in
       `WelcomePageComponent` (Green).

## 7. Audit-trail section on the staff detail panel (REQ-10, REQ-11, REQ-12, REQ-13, REQ-14)

- [x] 21. Test: opening a staff user's detail panel fetches and renders
       the audit trail (timestamp, action, resource type/id, tenant id
       or "global", outcome) reverse-chronological, alongside the
       existing permissions/access-groups/effective-permissions sections
       (Red).
- [x] 22. Implement the audit-trail section + `loadAuditTrail()` wired
       into `ngOnChanges()` (Green).
- [x] 23. Test: a 403 from the audit-trail call renders
       `app-no-access-state` only inside the audit-trail section, while
       the other three sections continue to render normally when their
       own calls succeed (Red).
- [x] 24. Implement the section-scoped error handling (`auditTrailError`
       signal, independent of the panel's existing `error` signal)
       (Green).
- [x] 25. Test: zero audit events renders a distinct "no audit history"
       message, not an empty table (Red).
- [x] 26. Implement that empty state (Green).
- [x] 27. Test: the section renders no more rows than the backend
       response contains (no client-side pagination/truncation added)
       (Red — asserts absence of any added pagination control).
- [x] 28. Confirm task 22's implementation already satisfies task 27
       (no extra code expected — render exactly what's returned).

## 8. Nav entry gating (REQ-6)

- [x] 29. Test: the `dashboard` nav entry appears for
       `DASHBOARD_VIEW_GLOBAL` alone (staff, no active tenant) and for a
       `STAFF_ADMIN`-shaped ("all permissions") response even without
       `DASHBOARD_VIEW`; existing `DASHBOARD_VIEW`-only case (tenant
       member) still passes unchanged (Red).
- [x] 30. Update `nav-menu.component.ts`'s `overviewGroups` dashboard-link
       condition to `permissionsService.has('DASHBOARD_VIEW') ||
       globalPermissionsService.has('DASHBOARD_VIEW_GLOBAL')` (Green).

## 9. i18n and design

- [x] 31. Add global-dashboard/audit-trail i18n keys to
       `public/i18n/en.json` / `pt-BR.json` (tile labels, "coming soon",
       welcome quick-link card copy, audit-trail column headers, "no
       audit history" message), reusing `dashboard.*`/`staffDirectory.*`
       -style keys where the copy is generic enough.
- [x] 32. Apply the established "Ink & Signal" design-system standard to
       the global dashboard view and the audit-trail section, matching
       `dashboard-page.component.ts`'s/`staff-user-detail-panel.component.ts`'s
       existing look.

## 10. Final verification

- [x] 33. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 34. Update `PLAN.md`'s decisions if anything changed during
       implementation (add an "Emergent decisions" section if needed,
       matching `user-management-screens`' PLAN precedent).
- [x] 35. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
