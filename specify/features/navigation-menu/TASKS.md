# TASKS — navigation-menu

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. `core/global-permission.ts` (`GlobalPermission` type +
      `ALL_GLOBAL_PERMISSIONS`, mirrors `permission.ts`).
- [x] 2. `core/global-permissions.service.ts` (mirrors
      `permissions.service.ts`, calls `GET /api/staff/permissions`).
      Unit test: `has()` false before `fetch()`, true/false correctly
      after, per PLAN.md's testing strategy.
- [x] 3. Fix `core/staff.guard.ts` (REQ-6): use
      `GlobalPermissionsService#fetch()` + `.has('TENANT_CREATE')`
      instead of the `GET /api/tenants` success heuristic. Test: a
      `STAFF` user granted only `TENANT_CREATE` (not
      `TENANT_ACT_AS_ANY`) is now allowed through — the regression this
      SPEC exists to fix.
- [x] 4. Fix `select-tenant-page.component.ts`'s "Create tenant" link
      (same bug, second instance): replace the `isStaff` signal (set
      from `listAllTenants()` success) with
      `GlobalPermissionsService#has('TENANT_CREATE')`. Test updated
      accordingly.
- [x] 5. `layout/nav-menu.component.ts`: renders `Dashboard`/
      `Conversations`/`Articles`/`Members` links gated by
      `PermissionsService` (only when there's an active tenant) and
      `Create tenant` gated by `GlobalPermissionsService` — REQ-1/2/3/5.
      Test: each link's visibility per permission combination,
      including the "no active tenant → no tenant-scoped links, but
      global ones still evaluated" case.
- [x] 6. Add "Switch tenant" link (REQ-4): visible when
      `ActiveTenantService#listOwnMemberships()`/`list()` returns more
      than one membership, navigates to `/select-tenant`. Test per
      PLAN.md.
- [x] 7. Wire `<app-nav-menu />` into `app-shell.component.ts` alongside
      the existing corner cluster (not replacing it).
- [x] 8. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 9. Update `PROJECT_STATUS.md` (this repo's feature table + "Next
      up") and the backend's `PROJECT_STATUS.md` (mark roadmap item 4
      done, point "Next up" at item 5 — user management screens), then
      commit both repos.
