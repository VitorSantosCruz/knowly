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
