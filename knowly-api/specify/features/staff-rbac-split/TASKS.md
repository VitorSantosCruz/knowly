# TASKS — staff-rbac-split

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. `GlobalRole`: add `STAFF_ADMIN`. Migration
      `V14__create_global_permission_tables.sql`: `UPDATE users SET
      global_role = 'STAFF_ADMIN' WHERE global_role = 'STAFF'` plus the
      four new tables and their `_aud` counterparts (PLAN.md's Data
      schema). Test: a `User` seeded with the old `'STAFF'` value (raw
      SQL, pre-migration state simulated) ends up `STAFF_ADMIN` after
      migration; `staff-bootstrap-user`'s existing integration test
      updated to assert `STAFF_ADMIN` instead of `STAFF`.
- [x] 2. New entities + repositories: `GlobalPermission`,
      `DirectGlobalPermissionGrant`, `GlobalAccessGroup`,
      `GlobalAccessGroupPermission`, `UserGlobalAccessGroup` — plain JPA
      mapping tests only (mirrors the existing tenant-side entity tests),
      no behavior yet.
- [x] 3. `GlobalPermissionService` (`effectivePermissions`,
      `hasPermission`) — unit tests for direct-only, group-only,
      combined, and revoked-access cases (mirrors
      `PermissionServiceTest`).
- [x] 4. `TenantContext.isStaff()` → `isStaffAdmin()`; update every
      caller (`PermissionAspect`, filters, etc.) to the renamed method.
      Existing tests updated for the rename; no behavior change here
      (Red/Green not applicable — pure rename, verified by
      `./mvnw test` staying green).
- [x] 5. `@RequiresGlobalPermission` + `GlobalPermissionAspect` — test:
      a `STAFF_ADMIN`-bypassed call succeeds unconditionally; a `STAFF`
      call without the permission is rejected; a `STAFF` call with a
      direct grant succeeds.
- [x] 6. `TenantService.requireStaff`/`requireAdminOfTenantOrStaff`
      updated per PLAN.md (STAFF_ADMIN unconditional, STAFF gated by the
      matching `GlobalPermission`), applied consistently to every call
      site listed in PLAN.md. Covered by `StaffRbacIntegrationTest`
      against `createTenant`/`listAllTenants` (including cross-permission
      independence: a `TENANT_CREATE`-only grant is rejected from
      `listAllTenants`, which needs `TENANT_ACT_AS_ANY`), **and now also
      individually against the other 10 call sites**
      (`addMember`/`removeMember`/`listMembers`/`createAccessGroup`/
      `listAccessGroups`/`grantPermission`/`revokePermission`/
      `assignAccessGroup`/`unassignAccessGroup`/`getMemberDetail`) — each
      with a denied-without-grant / allowed-with-the-matching-direct-grant
      pair, closing the coverage gap previously flagged here.
- [x] 7. Audit: `@AuditLog` on every new grant/revoke/group-management
      path. Test: expected `AuditEvent` recorded per action.
- [x] 8. `StaffController` + DTOs (`/api/staff/**`, PLAN.md's API
      contracts table), each endpoint `@RequiresGlobalPermission(STAFF_PERMISSION_MANAGE)`.
      Integration tests: `STAFF_ADMIN` can call every endpoint; a `STAFF`
      user holding some *other* permission (not
      `STAFF_PERMISSION_MANAGE`) is rejected from all of them (REQ-7).
- [x] 9. Sweep every remaining `GlobalRole.STAFF` reference outside
      `tenancy` (article-management, conversations, dashboard-metrics —
      see PLAN.md's file list) and update to `STAFF_ADMIN`/
      `isStaffAdmin()`. Existing tests for those features updated
      accordingly; no new behavior.
- [x] 10. Full acceptance-criteria pass: re-verify every checkbox in
      SPEC.md's Acceptance criteria explicitly against the finished
      implementation.
- [x] 11. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
      full suite is green.
- [x] 12. Update `PROJECT_STATUS.md` (feature table + "Next up" pointing
      at the next confirmed roadmap item — login/provisioning flow
      completion) and commit.
