# TASKS — role-permission-revoke

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> TDAD: test first (Red), then minimal code (Green).
> Run scoped `./mvnw test -Dtest=ClassName` per task, not full `./mvnw
> verify` — that's reserved for the final task. Run `./mvnw
> spotless:apply` immediately before each commit. Commit each completed
> task separately (Conventional Commits).

## 0. Migration groundwork

- [ ] 1. Inspect `V4__create_tenancy_envers_audit_tables.sql` and any
      later Envers migration touching `global_access_group_permissions`
      to confirm the exact `_aud` table name for that entity (per PLAN's
      "confirm before writing" caution). Also re-read
      `V29__access_group_soft_delete.sql` in full as the pattern to
      mirror. No code change — note the confirmed table name in the
      migration written in task 2.

## 1. V30 migration — schema

- [ ] 2. Write `V30__global_access_group_permission_soft_delete.sql`:
      add `deleted_at TIMESTAMPTZ` to `global_access_group_permissions`
      and to its confirmed `_aud` table; drop the existing table-level
      `UNIQUE (global_access_group_id, permission)` constraint; add
      `CREATE UNIQUE INDEX ux_global_access_group_permissions_group_permission
      ON global_access_group_permissions (global_access_group_id, permission)
      WHERE deleted_at IS NULL;` — mirroring V29 exactly. Verify Flyway
      picks it up cleanly (`./mvnw test -Dtest=FlywayMigrationTest` or
      equivalent existing migration-validation test if one exists;
      otherwise verify via the entity test in task 3-4).
- [ ] 3. Update the `GlobalAccessGroupPermission` entity: add
      `deletedAt` field with the same annotations/Javadoc as
      `AccessGroupPermission#deletedAt`; remove the now-redundant
      `@Table(uniqueConstraints = ...)` attribute. No behavior test yet
      (covered by task 4's repository test) — compile and run existing
      `GlobalAccessGroupPermissionRepositoryTest` (if present) to confirm
      no regression.
- [ ] 4. Repository test (Red): add a test to
      `GlobalAccessGroupPermissionRepositoryTest` (create the class if
      it doesn't exist) proving `findByGlobalAccessGroupAndPermission`
      is unfiltered by `deletedAt` (returns a soft-deleted row) and
      documenting why via Javadoc, mirroring
      `AccessGroupPermissionRepository`'s existing Javadoc pattern.
      Green: add the Javadoc to
      `GlobalAccessGroupPermissionRepository#findByGlobalAccessGroupAndPermission`
      confirming intentional non-filtering; no method-signature change
      needed since the column now exists.
      Commit: `feat(role-permission-revoke): add deleted_at to
      global_access_group_permissions (V30)`.

## 2. Bug fix 1 — tenant-side grant not clearing deletedAt on regrant

- [ ] 5. Test (Red) in `TenantServiceTest`: grant a permission, revoke it
      at the repository/entity level directly (set `deletedAt` on the
      saved row and save), then call
      `grantAccessGroupPermission` again for the same `(role,
      permission)` pair; assert the existing row id is reused and its
      `deletedAt` is now `null` (not a second row inserted). This test
      must fail against current code (the `orElseGet` path never clears
      `deletedAt`).
- [ ] 6. Implement (Green): change
      `TenantService#grantAccessGroupPermission`'s
      `.orElseGet(() -> save(new ...))` to
      `.map(existing -> { existing.setDeletedAt(null); return
      save(existing); }).orElseGet(...)`. Run
      `./mvnw test -Dtest=TenantServiceTest`.
      Commit: `fix(role-permission-revoke): reactivate soft-deleted
      AccessGroupPermission on regrant`.

## 3. Bug fix 2 — GlobalPermissionService unfiltered query

- [ ] 7. Test (Red) in `GlobalPermissionServiceTest`: grant a
      `GlobalPermission` to a `GlobalAccessGroup`, soft-delete the
      resulting `GlobalAccessGroupPermission` row directly, then assert
      the staff member's effective permissions via
      `GlobalPermissionService` no longer include it. Must fail against
      current code (uses unfiltered `findByGlobalAccessGroupIn`).
- [ ] 8. Implement (Green): add
      `GlobalAccessGroupPermissionRepository#findByGlobalAccessGroupInAndDeletedAtIsNull`
      (mirroring `AccessGroupPermissionRepository#findByAccessGroupInAndDeletedAtIsNull`);
      swap `GlobalPermissionService`'s call to the unfiltered
      `findByGlobalAccessGroupIn` for this new variant. Run
      `./mvnw test -Dtest=GlobalPermissionServiceTest,GlobalAccessGroupPermissionRepositoryTest`.
      Also apply the same staff-side reactivate-on-regrant fix as task 6
      to `StaffService#grantAccessGroupPermission` (same `.map(...)`
      pattern) with its own assertion added to
      `StaffServiceTest` (grant → soft-delete row → regrant → same row
      id, `deletedAt` null). Run
      `./mvnw test -Dtest=StaffServiceTest`.
      Commit: `fix(role-permission-revoke): filter deleted rows in
      GlobalPermissionService and reactivate on staff regrant`.

## 4. New exception + handler

- [ ] 9. Test (Red): add a `TenancyExceptionHandlerTest` (or extend the
      existing one) case asserting
      `AccessGroupPermissionNotGrantedException` maps to HTTP 400 with
      body `{"error": "ACCESS_GROUP_PERMISSION_NOT_GRANTED"}`.
- [ ] 10. Implement (Green): create
      `AccessGroupPermissionNotGrantedException` (`RuntimeException`, no
      fields, mirrors `AccessGroupNotFoundException`); register a new
      `@ExceptionHandler` in `TenancyExceptionHandler` returning
      `HttpStatus.BAD_REQUEST` / `"ACCESS_GROUP_PERMISSION_NOT_GRANTED"`,
      following `InvalidAccessGroupBatchException`'s handler shape. Run
      `./mvnw test -Dtest=TenancyExceptionHandlerTest`.
      Commit: `feat(role-permission-revoke): add
      AccessGroupPermissionNotGrantedException`.

## 5. Tenant-scope revoke endpoint

- [ ] 11. Test (Red) in `TenantServiceTest`: `revokeAccessGroupPermission`
      on an unknown/deleted `AccessGroup` throws
      `TenantAccessDeniedException`; on a not-currently-granted
      permission (never granted, and already-revoked) throws
      `AccessGroupPermissionNotGrantedException`; on a valid grant sets
      `deletedAt` on the row without removing it.
- [ ] 12. Implement (Green): add
      `TenantService#revokeAccessGroupPermission`, reusing the same
      role lookup as `grantAccessGroupPermission`
      (`TenantAccessDeniedException` on miss), looking up the active
      `AccessGroupPermission` row and throwing
      `AccessGroupPermissionNotGrantedException` if absent, else setting
      `deletedAt` and saving. Run
      `./mvnw test -Dtest=TenantServiceTest`.
- [ ] 13. Test (Red) in `TenantControllerIntegrationTest` (or the
      relevant existing access-group integration test class): add
      `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}`
      round-trip (grant → revoke → re-grant, asserting the same
      underlying row id persists), unauthorized-caller test (missing
      `TENANT_ACCESS_GROUP_EDIT` → 403), and the two "nothing to revoke"
      rejection tests (never granted; already revoked → both 400).
- [ ] 14. Implement (Green): add `TenantController#deleteAccessGroupPermission`
      (`@DeleteMapping(".../permissions/{permission}")`), gated
      identically to the existing grant handler, with `@AuditLog`
      action `tenant.access_group.revoke_permission` /
      resourceType `AccessGroupPermission` (audit covered fully in
      section 8 below — wire the annotation now). Run the integration
      test class from task 13.
      Commit: `feat(role-permission-revoke): add tenant access-group
      permission revoke endpoint`.

## 6. Staff/global-scope revoke endpoint

- [ ] 15. Test (Red) in `StaffServiceTest`: same three cases as task 11,
      staff-scoped (`GlobalAccessGroup`/`GlobalPermission`).
- [ ] 16. Implement (Green): add
      `StaffService#revokeAccessGroupPermission`, annotated
      `@RequiresGlobalPermission(STAFF_PERMISSION_MANAGE)`, mirroring
      task 12's shape on the global side. Run
      `./mvnw test -Dtest=StaffServiceTest`.
- [ ] 17. Test (Red) in the relevant `StaffControllerIntegrationTest`:
      `DELETE /api/staff/access-groups/{accessGroupId}/permissions/{permission}`
      round-trip (grant → revoke → re-grant, same row id),
      unauthorized-caller test (missing `STAFF_PERMISSION_MANAGE` → 403
      `PERMISSION_DENIED`), unknown/deleted role → 403
      `TENANT_ACCESS_DENIED`, and the two "nothing to revoke" 400 cases.
- [ ] 18. Implement (Green): add `StaffController#deleteAccessGroupPermission`,
      with `@AuditLog` action `staff.access_group.revoke_permission` /
      resourceType `GlobalAccessGroupPermission`. Run the integration
      test class from task 17.
      Commit: `feat(role-permission-revoke): add staff access-group
      permission revoke endpoint`.

## 7. DTO extension — list endpoints return permissions

- [ ] 19. Test (Red): extend the existing `AccessGroupDto`-mapping test
      (unit or the tenant `listAccessGroups` integration test) to assert
      the returned DTO includes `permissions: List<Permission>`
      reflecting only currently-active grants (a revoked permission
      must not appear). Add the equivalent for
      `GlobalAccessGroupDto`/`GET /api/staff/access-groups`.
- [ ] 20. Implement (Green): add `permissions` field to `AccessGroupDto`
      and `GlobalAccessGroupDto`; update their `from(...)` factories to
      accept a pre-fetched permission list; update
      `TenantService#listAccessGroups` to bulk-fetch via
      `findByAccessGroupInAndDeletedAtIsNull(allGroups)`, group by role
      id in memory, and pass into the DTO factory (no N+1); update
      `StaffService#listAccessGroups` identically using the new
      `findByGlobalAccessGroupInAndDeletedAtIsNull`. Run
      `./mvnw test -Dtest=TenantServiceTest,StaffServiceTest` plus the
      integration test class(es) touched in task 19.
      Commit: `feat(role-permission-revoke): expose granted permissions
      on access-group list endpoints`.

## 8. Audit log verification

- [ ] 21. Test (Red): add/extend an audit-log assertion (per existing
      `@AuditLog` test pattern used for the grant endpoints — e.g. an
      `AuditLogServiceTest`/integration assertion querying the audit
      trail after a revoke call) confirming a
      `tenant.access_group.revoke_permission` entry is recorded with
      actor, role, permission, and outcome for the tenant-scope revoke,
      and `staff.access_group.revoke_permission` for the staff-scope
      revoke.
- [ ] 22. Implement (Green): fix any gap found in task 21 (e.g. missing
      resourceId/outcome field on the `@AuditLog` annotations added in
      tasks 14/18) — if both annotations are already fully correct from
      tasks 14/18, this task closes with no code change beyond
      confirming the test passes. Run
      `./mvnw test -Dtest=AuditLogServiceTest` (or the actual test class
      used in task 21).
      Commit: `test(role-permission-revoke): verify audit logging on
      both revoke endpoints`.

## 9. Final verification

- [ ] 23. Run `./mvnw spotless:apply` then the full `./mvnw verify` and
      confirm the entire suite is green, including all new/modified
      tests above and no regression in existing `AccessGroupPermission`/
      `GlobalAccessGroupPermission`/`PermissionService`/
      `GlobalPermissionService` coverage. Commit any final formatting
      fixups: `chore(role-permission-revoke): final verify pass`.
- [ ] 24. Update `PLAN.md`/`../../../../PROJECT_STATUS.md` if any
      decision changed during implementation (per template step 6);
      otherwise confirm no drift and note that explicitly in the PR/
      commit description.
</content>
</invoke>
