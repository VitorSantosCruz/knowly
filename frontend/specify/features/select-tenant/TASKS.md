# TASKS — Select tenant

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

- [x] 1. Test: `ActiveTenantService.list()` fetches memberships without
      mutating the active-tenant signals; `selectTenant()` posts the
      choice and updates them (Red).
- [x] 2. Implement `list()`/`selectTenant()` (Green).
- [x] 3. Test: `tenantSelectionGuard` allows navigation when a
      membership is active or there's only one; redirects to
      `/select-tenant` otherwise (Red).
- [x] 4. Implement `tenantSelectionGuard` (Green).
- [x] 5. Test: `SelectTenantPageComponent` lists memberships; selecting
      one posts the choice and navigates to `/dashboard` (Red).
- [x] 6. Implement `SelectTenantPageComponent` + route (Green).
- [x] 7. Apply `canActivate: [tenantSelectionGuard]` to
      `dashboard`/`members`/`conversations`/`articles` routes.
- [x] 8. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 9. Update `SPEC.md`'s acceptance-criteria checkboxes.

## Emergent: staff act-as-any-tenant fallback (REQ-5/REQ-6, added when a
    real staff account with zero memberships hit `TENANT_SELECTION_REQUIRED`
    on every tenant-scoped call — the backend's `tenancy` feature grew a
    `GET /api/tenants` staff-only all-tenants endpoint to support this)

- [x] 10. Test: `tenantSelectionGuard` redirects to `/select-tenant` for
       0 memberships too, not just >1 (Red).
- [x] 11. Fix the guard's `memberships.length <= 1` condition (Green).
- [x] 12. Test: `ActiveTenantService` gets a `listAllTenants()` method
       hitting `GET /api/tenants` (Red).
- [x] 13. Implement `listAllTenants()` (Green).
- [x] 14. Test: `SelectTenantPageComponent` falls back to
       `listAllTenants()` when `list()` returns an empty array, and
       shows an empty state if that fallback itself errors (Red).
- [x] 15. Implement the fallback in `SelectTenantPageComponent` (Green).
- [x] 16. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
