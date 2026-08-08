# TASKS — tenant-access-group-bulk-and-delete

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `./mvnw spotless:apply && ./mvnw verify`
> and a small Conventional Commit before moving on.

## 0. Schema (REQ-8, REQ-9)

- [ ] 1. Add `src/main/resources/db/migration/V29__access_group_soft_delete.sql`
      — `deleted_at` on `access_groups` and `access_group_permissions`;
      drop each table's existing unique constraint; add
      `ux_access_groups_tenant_name` and
      `ux_access_group_permissions_group_permission` partial unique
      indexes (`WHERE deleted_at IS NULL`). Verify with a Flyway
      migrate-only run (`./mvnw test` covering an existing
      migration-smoke test, or the app's own startup context test) —
      no separate Red/Green cycle for pure DDL, but confirm it applies
      cleanly against the Testcontainers Postgres instance before
      moving on.

## 1. Entities (REQ-8, REQ-9)

- [ ] 2. Test: an `AccessGroupRepositoryTest`/`@DataJpaTest` case
      persists an `AccessGroup` with a non-null `deletedAt` and reads
      it back unchanged; a second `AccessGroup` with the same
      `(tenant, name)` as a soft-deleted one saves successfully (Red —
      fails today: no `deletedAt` field, and the old table-level
      unique constraint would reject the second insert once the column
      exists but before the constraint swap... use this test only after
      task 1's migration is in place, asserting the *new* partial-index
      behavior).
- [ ] 3. Implement `AccessGroup.java`: add `deletedAt` field (mirrors
      `UserAccessGroup`'s existing retrofit), drop the `@Table`-level
      `uniqueConstraints` attribute (Green).
- [ ] 4. Test: same shape as task 2, for `AccessGroupPermission` —
      persist with `deletedAt`, and a second row with the same
      `(accessGroup, permission)` as a soft-deleted one saves
      successfully (Red).
- [ ] 5. Implement `AccessGroupPermission.java`: add `deletedAt`, drop
      `uniqueConstraints` (Green).

## 2. Repository methods (PLAN "Package/file structure", REQ-3/REQ-13/REQ-17)

- [ ] 6. Test: `AccessGroupRepositoryTest#findByTenantAndIdInAndDeletedAtIsNull`
      — returns only live rows for the calling tenant, excludes
      soft-deleted rows and other-tenant rows, given a submitted id set
      containing a mix of valid/invalid/foreign-tenant ids (Red).
- [ ] 7. Implement `AccessGroupRepository#findByTenantAndIdInAndDeletedAtIsNull`
      (Green).
- [ ] 8. Test: `AccessGroupRepositoryTest#findByIdAndDeletedAtIsNull` —
      returns the row when live, empty when soft-deleted (Red).
- [ ] 9. Implement `AccessGroupRepository#findByIdAndDeletedAtIsNull`
      (Green).
- [ ] 10. Test: `AccessGroupRepositoryTest#findByTenantAndDeletedAtIsNull`
      — excludes soft-deleted rows for the tenant, replacing the
      existing `findByTenant` call site's semantics (Red).
- [ ] 11. Implement `AccessGroupRepository#findByTenantAndDeletedAtIsNull`
      (Green — do not remove `findByTenant` yet; the `listAccessGroups`
      call-site switch happens in section 6).
- [ ] 12. Test: `AccessGroupPermissionRepositoryTest#findByAccessGroupInAndDeletedAtIsNull`
      — given a list of `AccessGroup`s, excludes soft-deleted permission
      rows for those groups (Red).
- [ ] 13. Implement `AccessGroupPermissionRepository#findByAccessGroupInAndDeletedAtIsNull`
      (Green).
- [ ] 14. Test: `AccessGroupPermissionRepositoryTest` — a new
      `@Modifying @Query` bulk soft-delete method
      (`softDeleteByAccessGroupId`) sets `deleted_at = now()` on every
      live row for the given `accessGroupId` and leaves rows for a
      different `accessGroupId` and already-deleted rows untouched
      (Red).
- [ ] 15. Implement the bulk `@Modifying @Query` soft-delete method on
      `AccessGroupPermissionRepository` (Green).
- [ ] 16. Test: `UserAccessGroupRepositoryTest` — same shape as task 14,
      for a new bulk soft-delete method scoped by `accessGroupId` on
      `UserAccessGroupRepository` (Red).
- [ ] 17. Implement the bulk `@Modifying @Query` soft-delete method on
      `UserAccessGroupRepository` (Green).

## 3. New exceptions (REQ-3, REQ-15)

- [ ] 18. Test: `TenancyExceptionHandlerTest` (or equivalent) — throwing
      `AccessGroupNotFoundException` from a handler-covered context
      yields a 404 `TenancyErrorResponseDto` with code
      `ACCESS_GROUP_NOT_FOUND` (Red).
- [ ] 19. Implement `exception/AccessGroupNotFoundException.java` +
      its `@ExceptionHandler` entry (404) in
      `exception/TenancyExceptionHandler.java` (Green).
- [ ] 20. Test: same shape as task 18, for `InvalidAccessGroupBatchException`
      → 400, code `INVALID_ACCESS_GROUP_BATCH` (Red).
- [ ] 21. Implement `exception/InvalidAccessGroupBatchException.java` +
      its `@ExceptionHandler` entry (400) in `TenancyExceptionHandler.java`
      (Green).

## 4. New DTO (REQ-1, REQ-4, AppSec finding)

- [ ] 22. Test: `BatchAccessGroupAssignmentRequestDtoTest` (bean
      validation) — rejects null/empty `accessGroupIds` (`@NotEmpty`),
      rejects a list of 51 ids (`@Size(max = 50)`), accepts a
      non-empty list of ≤50 ids (Red).
- [ ] 23. Implement `dto/BatchAccessGroupAssignmentRequestDto.java`
      (record, `@NotEmpty` + `@Size(max = 50)` on `accessGroupIds`)
      (Green).

## 5. `TenantService` — bulk assignment (REQ-2, REQ-3, REQ-4, REQ-5, REQ-6, REQ-7)

- [ ] 24. Test: `TenantServiceTest#batchAssignAccessGroups` — empty or
      duplicate-containing `accessGroupIds` is rejected
      (`InvalidAccessGroupBatchException`) before any repository call
      (REQ-4) (Red).
- [ ] 25. Test: `TenantServiceTest#batchAssignAccessGroups` — an id that
      doesn't resolve to a live `AccessGroup` for the tenant rejects the
      whole request (`InvalidAccessGroupBatchException`) and writes zero
      `UserAccessGroup` rows (REQ-3) (Red).
- [ ] 26. Test: `TenantServiceTest#batchAssignAccessGroups` — happy path
      with N valid ids creates/reactivates all N `UserAccessGroup` rows
      in one call, reusing `assignAccessGroup`'s reactivate-on-reassign
      logic per id, and leaves already-assigned-but-not-submitted groups
      untouched (REQ-2) (Red).
- [ ] 27. Implement `TenantService#batchAssignAccessGroups` (validates
      via `findByTenantAndIdInAndDeletedAtIsNull`, then loops the
      per-id reactivate-or-create logic) (Green).
- [ ] 28. Test: `TenantControllerIntegrationTest` — a caller without
      `TENANT_PERMISSION_GRANT_CREATE` gets 403 from the batch-assign
      method/endpoint and no `UserAccessGroup` row is written (REQ-5)
      (Red).
- [ ] 29. Wire `@RequiresPermission(TENANT_PERMISSION_GRANT_CREATE)` (or
      the equivalent existing guard used by `assignAccessGroup`) onto
      `batchAssignAccessGroups`, and add `@AuditLog(action =
      "tenant.access_group.batch_assign", resourceType =
      "UserAccessGroup")` with a `resourceIdExpression` capturing
      `membershipId` + the submitted id list, per the PLAN (Green;
      REQ-5, REQ-7).

## 6. `TenantController` — batch-assign endpoint (REQ-1)

- [ ] 30. Test: `TenantControllerIntegrationTest` — `POST
      /api/tenants/{tenantId}/members/{membershipId}/access-groups:batch`
      returns 204 on a valid payload and 400 on an
      empty/duplicate/over-50/invalid-id payload (Red).
- [ ] 31. Implement the `POST .../access-groups:batch` handler on
      `TenantController.java` (new `BatchAccessGroupAssignmentRequestDto`
      import, `@Valid` body, delegates to
      `TenantService#batchAssignAccessGroups`) (Green).

## 7. `TenantService` — delete-confirmation-token generation (REQ-10, REQ-11)

- [ ] 32. Test: `TenantServiceTest#generateAccessGroupDeletionConfirmationToken`
      — a caller without `TENANT_ACCESS_GROUP_DELETE` gets rejected and
      no token is generated (REQ-11); a caller with the permission
      generates a token scoped to `ACCESS_GROUP_DELETE_RESOURCE_TYPE`
      (`"tenant-access-group-delete"`) + `accessGroupId.toString()`
      (Red).
- [ ] 33. Implement `TenantService#generateAccessGroupDeletionConfirmationToken`
      + the new `ACCESS_GROUP_DELETE_RESOURCE_TYPE` constant, guarded by
      `TENANT_ACCESS_GROUP_DELETE` (Green).

## 8. `TenantController` — token-generation endpoint (REQ-10, REQ-11)

- [ ] 34. Test: `TenantControllerIntegrationTest` — `GET
      /api/tenants/{tenantId}/access-groups/{accessGroupId}/deletion-confirmation-token`
      returns 200 + `DeletionConfirmationTokenDto` for a permitted
      caller, 403 for one without `TENANT_ACCESS_GROUP_DELETE`, 404 for
      an unknown/wrong-tenant/already-deleted `accessGroupId` (Red).
- [ ] 35. Implement the `GET .../deletion-confirmation-token` handler on
      `TenantController.java` (Green — depends on task 33's service
      method resolving the group via `findByIdAndDeletedAtIsNull` and
      throwing `AccessGroupNotFoundException` on miss).

## 9. `TenantService` — cascading delete (REQ-13, REQ-14, REQ-15, REQ-16, REQ-18, REQ-19)

- [ ] 36. Test: `TenantServiceTest#deleteAccessGroup` — happy path with
      a valid token sets `deletedAt` on the group and cascades to every
      live `UserAccessGroup`/`AccessGroupPermission` row referencing it,
      asserted via repository reads after the call (REQ-13) (Red).
- [ ] 37. Test: `TenantServiceTest#deleteAccessGroup` — missing/wrong/
      already-consumed token throws `DeletionConfirmationInvalidException`
      and changes nothing (REQ-14) (Red).
- [ ] 38. Test: `TenantServiceTest#deleteAccessGroup` — unknown,
      wrong-tenant, or already-soft-deleted `accessGroupId` throws
      `AccessGroupNotFoundException` (REQ-15) (Red).
- [ ] 39. Test: `TenantServiceTest#deleteAccessGroup` — a caller without
      `TENANT_ACCESS_GROUP_DELETE` is rejected before token validation
      or existence check, independent of token validity (REQ-16) (Red).
- [ ] 40. Implement `TenantService#deleteAccessGroup` (permission check
      → `findByIdAndDeletedAtIsNull` existence check →
      `DeletionConfirmationTokenService#validateAndConsume` against
      `ACCESS_GROUP_DELETE_RESOURCE_TYPE` → set `deletedAt` on the
      loaded `AccessGroup` and `save()` → invoke the two new bulk
      `@Modifying` cascade queries from section 2, all inside one
      `@Transactional` method) plus `@AuditLog(action =
      "tenant.access_group.delete", resourceType = "AccessGroup")`
      (Green; REQ-13, REQ-14, REQ-15, REQ-16, REQ-18).
- [ ] 41. Test: `TenantServiceTest#deleteAccessGroup` — a forced
      mid-cascade failure (Testcontainers-level constraint violation
      injected via a second, unrelated row) rolls back all three writes
      together (group, `UserAccessGroup`, `AccessGroupPermission`), none
      partially applied (Red — Security NFR, transactional atomicity).
- [ ] 42. Confirm `@Transactional` boundary on `deleteAccessGroup`
      already satisfies task 41 (Green — likely no code change if
      section 9's implementation already wraps all three writes in one
      transaction; adjust if the rollback test reveals a gap).

## 10. `TenantController` — delete endpoint (REQ-12)

- [ ] 43. Test: `TenantControllerIntegrationTest` — `DELETE
      /api/tenants/{tenantId}/access-groups/{accessGroupId}` (with
      `DeleteConfirmationRequestDto` body) returns 204 on a valid token,
      400 on missing/wrong token, 403 for a caller lacking
      `TENANT_ACCESS_GROUP_DELETE`, 404 for an unknown/wrong-tenant
      `accessGroupId` (Red).
- [ ] 44. Implement the `DELETE .../access-groups/{accessGroupId}`
      handler on `TenantController.java` (Green).

## 11. Staff-caller coverage for the three new endpoints (existing staff-bypass convention)

- [ ] 45. Test: `TenantControllerIntegrationTest` — a staff caller
      holding the equivalent `GlobalPermission` can call the batch-assign,
      token-generation, and delete endpoints without a `TenantMembership`
      row, mirroring `assignAccessGroup`/`unassignAccessGroup`'s existing
      staff-bypass test coverage; a staff caller lacking it is rejected
      the same way a tenant caller without the tenant permission is
      (Red).
- [ ] 46. Confirm/adjust the guards from tasks 29/33/40 already satisfy
      task 45 via the existing `requireAdminOfTenantOrStaff`/
      `@RequiresPermission` staff-bypass mechanism (Green — likely no
      code change; adjust if a gap is found).

## 12. REQ-17 read-path sweep (every call site named in PLAN.md)

- [ ] 47. Test: `TenantServiceTest#listAccessGroups` — excludes a
      soft-deleted `AccessGroup` from the result (Red).
- [ ] 48. Update `TenantService#listAccessGroups`'s call site from
      `AccessGroupRepository#findByTenant` to
      `findByTenantAndDeletedAtIsNull` (Green).
- [ ] 49. Test: `TenantServiceTest#grantAccessGroupPermission` — a
      soft-deleted `accessGroupId` is rejected (existing
      `TenantAccessDeniedException` convention, unchanged status code)
      rather than silently granting a permission to a dead group (Red).
- [ ] 50. Update `TenantService#grantAccessGroupPermission`'s
      `AccessGroupRepository#findById` call site to
      `findByIdAndDeletedAtIsNull` (Green).
- [ ] 51. Test: `TenantServiceTest#assignAccessGroup` — same shape as
      task 49, for the existing single-assign method (Red).
- [ ] 52. Update `TenantService#assignAccessGroup`'s
      `AccessGroupRepository#findById` call site to
      `findByIdAndDeletedAtIsNull` (Green).
- [ ] 53. Test: `PermissionServiceTest` (effective-permission
      resolution) — a soft-deleted `AccessGroup`'s permissions are no
      longer included in a member's effective permission set even if a
      stale `AccessGroupPermission` row somehow remained live (defensive
      regression covering the join's other side) (Red).
- [ ] 54. Update `PermissionService`'s
      `AccessGroupPermissionRepository#findByAccessGroupIn` call site to
      `findByAccessGroupInAndDeletedAtIsNull` (Green).
- [ ] 55. Test: `TenantServiceTest#getMemberDetail` — after a group is
      deleted (cascade applied), the member's group/effective permission
      lists no longer include anything from it (Red — end-to-end
      regression exercising tasks 40/48/50/52/54 together).
- [ ] 56. Confirm task 55 passes with no further code change (Green —
      `getMemberDetail`'s existing `findByTenantMembershipAndDeletedAtIsNull`
      read on `UserAccessGroup` was already precedent-correct per the
      PLAN; this task only verifies the chain end-to-end. If a gap is
      found, fix the specific call site here.)

## 13. Name/permission reuse after deletion (REQ-8, REQ-9, end-to-end)

- [ ] 57. Test: `TenantServiceTest` — after `deleteAccessGroup` soft-deletes
      a group, creating a new `AccessGroup` with the same `(tenant, name)`
      succeeds; granting the same `Permission` to a *new* group succeeds
      (exercises the two new partial indexes through the service layer)
      (Red).
- [ ] 58. Confirm task 57 passes with no further code change (Green —
      this is a regression check on the migration/entity work from
      sections 0-1; fix here if a gap is found).

## 14. Final verification

- [ ] 59. Run the full `./mvnw spotless:apply && ./mvnw verify` and
      confirm the entire suite is green.
- [ ] 60. Update `PLAN.md` if any decision changed during implementation.
- [ ] 61. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
      what's now verified by tests.
- [ ] 62. Update `../../../../PROJECT_STATUS.md` to reflect the new
      bulk-assign and cascading-delete endpoints, per this subproject's
      `CLAUDE.md` standing "commit each completed task" instruction.
