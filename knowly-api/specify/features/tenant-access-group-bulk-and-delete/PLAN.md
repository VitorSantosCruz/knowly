# PLAN — Tenant access-group bulk assignment and cascading soft-delete

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Bulk assignment is a new `TenantService#batchAssignAccessGroups` method,
  not a loop over the existing single-assign method from the controller.**
  `assignAccessGroup`'s per-id logic (`findByTenantMembershipAndAccessGroup`
  → reactivate-or-create → `setDeletedAt(null)` → save) is reused verbatim
  inside the new method's per-id loop, but validation (REQ-3/REQ-4: reject
  the *whole* request on any invalid/duplicate/empty id) must happen before
  any write, which the single-assign method has no reason to do on its own.
  Duplicating that validation into `assignAccessGroup` itself would change
  its existing behavior for no reason; a new method is the smaller diff.
- **REQ-3's all-ids-must-resolve check is done with one bulk repository
  query, not N `findById` calls.** `AccessGroupRepository` gains
  `findByTenantAndIdInAndDeletedAtIsNull(Tenant, Collection<Long>)`; the
  service compares the returned set's size against the submitted id set's
  size (after de-duplication is already rejected by REQ-4, this is a
  correctness check, not a dedup step) — if they don't match, at least one
  id didn't resolve (wrong tenant, unknown, or soft-deleted), and the whole
  request is rejected before any `UserAccessGroup` write. This keeps REQ-3
  atomic without needing a savepoint/rollback trick: nothing is written
  until every id is confirmed valid.
- **The cascade in REQ-13 is done via two bulk `@Modifying` `UPDATE`
  queries (one per dependent table) inside the same `@Transactional`
  service method that also sets `deletedAt` on the `AccessGroup` row
  itself — not by loading each `UserAccessGroup`/`AccessGroupPermission`
  row into memory and saving it individually.** This is explicitly what
  the SPEC's own non-functional "Performance/SLA" section asks for
  (`UPDATE ... WHERE access_group_id = ? AND deleted_at IS NULL`), and
  mirrors `batchUpdatePermissions`'s existing "one transaction, no N+1"
  shape at the query level (that method still loops per-permission because
  it needs a per-permission audit event and reactivate-vs-revoke branching
  — this cascade needs neither: REQ-18 wants exactly one audit event for
  the whole deletion, and every affected row moves the same direction,
  `deletedAt = now()`, so there's nothing case-by-case to branch on).
- **`AccessGroup`'s own `deletedAt` is set via `save()` after being loaded
  by `findById`, not folded into the same bulk `UPDATE`.** The group row
  itself is a single row already loaded to run the `TenantAccessDeniedException`/
  not-found check and the confirmation-token validation before the cascade
  runs, so there's no N+1 concern in setting it the normal JPA way; only the
  *dependent* tables (potentially many rows) get the bulk-`UPDATE` treatment.
- **Every existing read path against `AccessGroup`, `UserAccessGroup`, and
  `AccessGroupPermission` is audited and updated to filter `deletedAt IS
  NULL`, per REQ-17 — this is the load-bearing part of the SPEC and is
  enumerated explicitly below (not left implicit) so nothing is missed:**
  - `AccessGroupRepository.findByTenant` → add `AndDeletedAtIsNull` (used
    by `listAccessGroups`, REQ-17's named "listAccessGroups" case).
  - `AccessGroupRepository.findById` calls in `grantAccessGroupPermission`,
    `assignAccessGroup`, the new batch-assign method, and the new
    delete/token-generation methods must all resolve through a new
    `findByIdAndDeletedAtIsNull` instead of plain `findById`, so a
    soft-deleted group can no longer be granted a permission, assigned to a
    member, or have its own delete endpoint called twice.
  - `AccessGroupPermissionRepository.findByAccessGroupIn` (consumed by
    `PermissionService`'s effective-permission resolution — confirmed by
    reading `PermissionService` before writing this PLAN) → add
    `AndDeletedAtIsNull`, and the `List<AccessGroup>` passed into it must
    itself already be the live-only list from `UserAccessGroup`'s own
    `deletedAt IS NULL` filter, which it already is
    (`findByTenantMembershipAndDeletedAtIsNull(membership).stream().map(UserAccessGroup::getAccessGroup)`
    in `getMemberDetail`/`PermissionService`) — so this closes the gap on
    both sides of that join, not just one.
  - `AccessGroupPermissionRepository.findByAccessGroupAndPermission` (the
    grant path's reactivate-or-create lookup) is **intentionally left
    unfiltered** — like `assignAccessGroup`'s equivalent
    `findByTenantMembershipAndAccessGroup`, this lookup must see a
    soft-deleted row so it can reactivate it rather than colliding with the
    new partial unique index; this mirrors the existing, deliberate
    "write path sees deleted rows, read/listing path doesn't" split
    documented in `DECISIONS.md`'s 2026-08-04 entry.
  - `UserAccessGroupRepository` already has both shapes precedent-correct
    (`findByTenantMembershipAndAccessGroupAndDeletedAtIsNull` for reads,
    unfiltered `findByTenantMembershipAndAccessGroup` for the write path) —
    no change needed there beyond what this SPEC's own cascade adds.
- **A new `AccessGroupNotFoundException` (404) is introduced for REQ-15,
  rather than reusing `TenantAccessDeniedException` (403)** — a Tier 2 call,
  flagged here rather than silently matched to the existing convention.
  Every other `AccessGroup`-id lookup in this codebase today
  (`grantAccessGroupPermission`, `assignAccessGroup`) throws
  `TenantAccessDeniedException` (403) for "wrong tenant or unknown id,"
  deliberately not distinguishing "doesn't exist" from "exists but isn't
  yours" (existence-hiding). REQ-15, however, explicitly specifies 404 for
  this exact endpoint ("reject the request (404)"), overriding that
  existing default — since the SPEC pins the status code explicitly, this
  isn't a scope decision this PLAN is making unilaterally, only the choice
  of *how* to produce a 404 (a new exception + handler entry, matching
  `TenantNotFoundException`'s/`NotificationNotFoundException`'s existing
  404 shape in `TenancyExceptionHandler`, rather than reusing 403's
  `TenantAccessDeniedException` for a status code it doesn't return).
  REQ-16 (permission-denied) is still checked *before* existence, exactly
  like every other guarded endpoint in `TenantService` — a caller without
  `TENANT_ACCESS_GROUP_DELETE` gets 403 regardless of whether the id even
  resolves, so 404 never leaks past the permission gate.
- **REQ-3's "reject the entire request" also gets its own 400, not 404** —
  a batch of ids that includes even one invalid entry is a client input
  error against the whole request shape, not "this one resource wasn't
  found" (there's no single resource being addressed). A new
  `InvalidAccessGroupBatchException` (400) is added rather than reusing
  `AccessGroupNotFoundException`.
- **The bulk-assign endpoint requires no confirmation token (REQ-6)** — no
  new work needed here beyond simply not calling
  `DeletionConfirmationTokenService` from the new method, mirroring
  `assignAccessGroup`'s existing lack of one.
- **The delete-confirmation-token resource id for REQ-10/REQ-12 is scoped
  by `accessGroupId` alone (`"tenant-access-group-delete"` resource type,
  resource id = `accessGroupId.toString()`)** — distinct from the existing
  `ACCESS_GROUP_RESOURCE_TYPE` constant (`"tenant-access-group"`), which is
  scoped by `(membershipId, accessGroupId)` for the *unassign* action.
  Reusing that constant/shape here would be wrong: this delete action has
  no membership in play at all, and colliding resource-type strings across
  two semantically different actions on the same id risks one token
  validating the other action by accident. New constant:
  `ACCESS_GROUP_DELETE_RESOURCE_TYPE = "tenant-access-group-delete"`.
- **REQ-7's batch-assignment audit event and REQ-18's delete audit event
  both use the existing `@AuditLog` annotation**, one event per request —
  `@AuditLog(action = "tenant.access_group.batch_assign", resourceType =
  "UserAccessGroup")` and `@AuditLog(action = "tenant.access_group.delete",
  resourceType = "AccessGroup")` respectively — mirroring
  `assignAccessGroup`'s/`hardDeleteMember`'s existing per-action (not
  per-row) granularity. REQ-7's full submitted `accessGroupIds` list is
  captured via `resourceIdExpression` (the existing `@AuditLog` mechanism
  already supports a SpEL expression over method arguments, used today for
  `#membershipId` on `demoteMember`/`promoteMember`) rendering the
  membership id plus the id list as the event's resource id string, the
  same "single event, richer resource-id string" shape rather than adding
  a new metadata field.

## Data schema

New migration: **`V29__access_group_soft_delete.sql`** (next number after
`V28__soft_delete_everywhere.sql`).

- `access_groups`:
  - `ADD COLUMN deleted_at TIMESTAMPTZ NULL`.
  - `DROP` the existing table-level unique constraint on `(tenant_id,
    name)`.
  - `CREATE UNIQUE INDEX ux_access_groups_tenant_name ON access_groups
    (tenant_id, name) WHERE deleted_at IS NULL` — same partial-index shape
    as `ux_user_access_groups_membership_group` (`V28`).
- `access_group_permissions`:
  - `ADD COLUMN deleted_at TIMESTAMPTZ NULL`.
  - `DROP` the existing table-level unique constraint on
    `(access_group_id, permission)`.
  - `CREATE UNIQUE INDEX ux_access_group_permissions_group_permission ON
    access_group_permissions (access_group_id, permission) WHERE
    deleted_at IS NULL`.
- No schema change needed for `user_access_groups` — it already has
  `deleted_at` and its partial unique index from `V28`; this feature only
  adds a second write path (the cascade) that sets the same column.

Entity changes (both mirror `UserAccessGroup`'s existing `deletedAt`
retrofit exactly — `@Column(name = "deleted_at") private Instant
deletedAt;`, no new annotations needed beyond that):
- `AccessGroup.java`: add `deletedAt`; drop the `@Table`-level
  `uniqueConstraints` attribute (now enforced by the partial index instead,
  same reasoning `UserAccessGroup` already used).
- `AccessGroupPermission.java`: same two changes.

## API contracts

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| `POST` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups:batch` | `BatchAccessGroupAssignmentRequestDto { accessGroupIds: List<Long> }` (`@NotEmpty`, `@Size(max = 50)`) | `204 No Content` | `204` success; `400` empty/missing/duplicate/over-limit `accessGroupIds` or `InvalidAccessGroupBatchException` (unresolved id); `403` `PermissionDeniedException`/`TenantAccessDeniedException`; `404` unknown `membershipId` (existing `TenantAccessDeniedException`-shaped 403, unchanged — REQ-3/REQ-15 only pin status codes for the access-group-id case, not membership resolution, so membership lookup keeps its existing 403-hides-existence behavior) |
| `GET` | `/api/tenants/{tenantId}/access-groups/{accessGroupId}/deletion-confirmation-token` | none | `DeletionConfirmationTokenDto { word: String }` (existing shape, reused unchanged) | `200` success; `403` `PermissionDeniedException` (REQ-11); `404` `AccessGroupNotFoundException` (unknown/wrong-tenant/already-deleted) |
| `DELETE` | `/api/tenants/{tenantId}/access-groups/{accessGroupId}` | `DeleteConfirmationRequestDto { word: String }` (existing shape, optional body — mirrors `unassignAccessGroup`'s exact request shape) | `204 No Content` | `204` success; `403` `PermissionDeniedException` (REQ-16); `404` `AccessGroupNotFoundException` (REQ-15); `409`/`422`... none needed — `DeletionConfirmationInvalidException` (existing, REQ-14) maps to its existing status code, unchanged |

New DTO: `BatchAccessGroupAssignmentRequestDto` (record), validated with
`@NotEmpty` on `accessGroupIds` (covers REQ-4's "empty or missing") and
`@Size(max = 50)` (AppSec review, pre-TASKS.md: an already-permissioned
caller could otherwise submit an arbitrarily large array, driving a
correspondingly large `IN (...)` clause and an unbounded per-id
reactivate-or-create loop in one transaction — pathological-load-shaped,
not an authorization bypass, but cheap to cap now; 50 is comfortably
above any real tenant's expected group count, not a product-specified
number). The duplicate-id check (also REQ-4) is not expressible as a
bean-validation annotation against a plain `List<Long>` and is done in
the service (`new HashSet<>(ids).size() != ids.size()` →
`InvalidAccessGroupBatchException`) before any repository call,
consistent with REQ-4's "before attempting any assignment."

Existing `TenancyErrorResponseDto`/`TenancyExceptionHandler` shape is
reused for the two new exceptions
(`ACCESS_GROUP_NOT_FOUND`/`INVALID_ACCESS_GROUP_BATCH` error codes), no new
error DTO shape introduced.

## Dependencies

None. Reuses `DeletionConfirmationTokenService` (Redis-backed, already
provisioned), Spring Data JPA `@Modifying` queries (already used
elsewhere, e.g. any existing bulk-update query — no new library), Flyway
(already the migration tool). No `pom.xml` change.

## Package/file structure

All under `br.com.conectabyte.knowly.tenancy` (existing package for this
domain — no new package):

- **New**
  - `src/main/resources/db/migration/V29__access_group_soft_delete.sql`
  - `dto/BatchAccessGroupAssignmentRequestDto.java`
  - `exception/AccessGroupNotFoundException.java`
  - `exception/InvalidAccessGroupBatchException.java`
- **Modified**
  - `AccessGroup.java` — add `deletedAt`, drop `uniqueConstraints`.
  - `AccessGroupPermission.java` — add `deletedAt`, drop
    `uniqueConstraints`.
  - `AccessGroupRepository.java` — add `findByTenantAndDeletedAtIsNull`
    (replaces `findByTenant` call site in `listAccessGroups`),
    `findByTenantAndIdInAndDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`.
  - `AccessGroupPermissionRepository.java` — add
    `findByAccessGroupInAndDeletedAtIsNull` (replaces `findByAccessGroupIn`
    call site), a `@Modifying @Query` bulk soft-delete method scoped by
    `accessGroupId`.
  - `UserAccessGroupRepository.java` — add a `@Modifying @Query` bulk
    soft-delete method scoped by `accessGroupId` (mirrors the
    `AccessGroupPermissionRepository` addition above).
  - `TenantService.java` — new `batchAssignAccessGroups`,
    `generateAccessGroupDeletionConfirmationToken`, `deleteAccessGroup`;
    update `listAccessGroups`, `grantAccessGroupPermission`,
    `assignAccessGroup`, `getMemberDetail`'s `AccessGroupPermission`
    read (via `PermissionService`, see below) call sites to the
    `...AndDeletedAtIsNull` repository methods named above; new
    `ACCESS_GROUP_DELETE_RESOURCE_TYPE` constant.
  - `TenantController.java` — new `POST
    .../access-groups:batch`, `GET
    .../access-groups/{accessGroupId}/deletion-confirmation-token`,
    `DELETE .../access-groups/{accessGroupId}` handlers; new
    `BatchAccessGroupAssignmentRequestDto` import.
  - `exception/TenancyExceptionHandler.java` — add
    `@ExceptionHandler(AccessGroupNotFoundException.class)` (404) and
    `@ExceptionHandler(InvalidAccessGroupBatchException.class)` (400)
    entries, same shape as the existing ones.
  - `PermissionService.java` — confirm/update its
    `AccessGroupPermissionRepository`/`findByAccessGroupIn` call site to
    the new `...AndDeletedAtIsNull` method (REQ-17's effective-permission
    case).

## Testing strategy

- **Repository tests** (`@DataJpaTest` or equivalent existing pattern in
  this codebase): `findByTenantAndIdInAndDeletedAtIsNull` returns only
  live rows for the calling tenant and excludes soft-deleted/other-tenant
  ids; the two new `@Modifying` bulk-update queries actually set
  `deleted_at` on every matching row and leave non-matching rows
  (different `access_group_id`, already-deleted rows) untouched; the two
  new partial unique indexes allow a same-named/same-permission row to be
  recreated after the original is soft-deleted (mirrors the existing
  `UserAccessGroup` partial-index test, if one exists — reuse its shape).
- **Service tests** (`TenantServiceTest` or equivalent):
  - `batchAssignAccessGroups`: happy path creates/reactivates all N rows
    in one call; an invalid id in an otherwise-valid list leaves zero rows
    written (REQ-3); empty/duplicate `accessGroupIds` is rejected before
    any repository call (REQ-4); permission-denied caller triggers no
    write (REQ-5); reactivate-on-reassign (a previously-unassigned group
    included in a new batch call comes back live, not duplicated).
  - `deleteAccessGroup`: happy path sets `deletedAt` on the group and
    cascades to every live `UserAccessGroup`/`AccessGroupPermission` row
    referencing it, inside one transaction (assert via repository reads
    after the call, not by inspecting the transaction itself); a
    forced mid-cascade failure (e.g. a Testcontainers-level constraint
    violation injected via a second, unrelated row) rolls back all three
    writes together, not partially; unknown/wrong-tenant/already-deleted
    id → `AccessGroupNotFoundException`; missing/wrong/already-consumed
    token → `DeletionConfirmationInvalidException`, no changes made;
    permission-denied → no token generated, no deletion, independent of
    token validity (REQ-16 checked before token validation, matching
    every other guarded delete in this service).
  - Regression coverage for REQ-17: after a group is deleted,
    `listAccessGroups` excludes it, `getMemberDetail`'s effective/group
    permission lists no longer include anything from it, and
    `grantAccessGroupPermission`/`assignAccessGroup` called against the
    now-deleted id 404s (or 403s, per the existing convention for those
    two untouched endpoints) rather than silently succeeding.
  - Name/permission reuse after deletion: creating a new `AccessGroup`
    with the same `(tenant, name)`, or re-granting the same `Permission`
    to a *new* group, succeeds once the old row is soft-deleted (exercises
    the two new partial indexes end-to-end through the service, not just
    the repository layer).
- **Controller integration tests** (`TenantControllerIntegrationTest` or
  equivalent, Testcontainers-backed):
  - `POST .../access-groups:batch`: 204 happy path with response
    reflecting all assignments; 400 for empty/duplicate/invalid-id
    payloads; 403 for a caller lacking `TENANT_PERMISSION_GRANT_CREATE`.
  - `GET .../deletion-confirmation-token` + `DELETE
    .../access-groups/{accessGroupId}`: full token round trip (generate →
    delete succeeds); delete without a token, or with a wrong/expired one,
    leaves the group and its dependents untouched; 403 for a caller
    lacking `TENANT_ACCESS_GROUP_DELETE` on both endpoints; 404 for an
    unknown/wrong-tenant access-group id on both endpoints.
  - Staff-caller variants for both new endpoints (staff bypassing the
    tenant-membership check but still gated by the named
    `GlobalPermission`s), mirroring this codebase's existing staff-bypass
    test coverage for `assignAccessGroup`/`unassignAccessGroup`.
