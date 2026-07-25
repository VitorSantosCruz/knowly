# TASKS — Tenant creation (staff)

> Atomic, sequential, verifiable tasks derived from PLAN.md.

- [x] 1. Write `staff.guard.spec.ts` covering REQ-2/REQ-6 (allow on
      `listAllTenants()` success, redirect to `/select-tenant` on
      error) — Red.
- [x] 2. Implement `staff.guard.ts` — Green.
- [x] 3. Write `active-tenant.service.spec.ts` case for `createTenant()`
      posting `{ name, adminEmail }` to `/api/tenants` — Red.
- [x] 4. Implement `createTenant()` in `active-tenant.service.ts` —
      Green.
- [x] 5. Write `tenant-create-page.component.spec.ts` covering: renders
      form; client-side validation blocks empty/invalid submit (REQ-1);
      successful submit calls the service and navigates to
      `/select-tenant` (REQ-4); service error shows inline error and
      keeps entered values (REQ-5) — Red.
- [x] 6. Implement `TenantCreatePageComponent` — Green.
- [x] 7. Wire the route: add `/tenants/new` to `app.routes.ts` with
      `staffGuard` (REQ-1, REQ-2, REQ-6).
- [x] 8. Write `select-tenant-page.component.spec.ts` case: "create
      tenant" link appears only on the staff (listAllTenants-success)
      path (REQ-3) — Red.
- [x] 9. Implement the link in `select-tenant-page.component.ts` —
      Green.
- [x] 10. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 11. Update `PLAN.md` if any decision changed during
       implementation; update `PROJECT_STATUS.md`'s feature table and
       "Next up" section.
- [x] 12. Commit.
