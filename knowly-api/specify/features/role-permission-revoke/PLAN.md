# PLAN — role-permission-revoke

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Revoke rejects unknown/deleted role with `TenantAccessDeniedException`
  (tenant scope, 403) / the same exception via `requireAccessGroup`
  (staff scope)** — REQ-7 says "the same way the existing grant endpoint
  rejects" a bad role id; both existing grant methods
  (`TenantService#grantAccessGroupPermission`,
  `StaffService#grantAccessGroupPermission`) already reject that way
  (not `AccessGroupNotFoundException`, which is deliberately pinned to
  the *delete-access-group* endpoint per its own Javadoc). Revoke reuses
  the sibling grant's exact lookup/exception rather than the delete
  endpoint's, so REQ-7's "same way" is read as "same as the grant
  endpoint on the same resource," matching the SPEC's own framing.
- **New `AccessGroupPermissionNotGrantedException` (400) for the
  "nothing to revoke" case** — no existing exception fits: the
  precedent for revoking a *not-currently-granted* row
  (`TenantService#revokePermission`/`StaffService#revokePermission` on
  `DirectPermissionGrant`) silently no-ops (`.ifPresent(...)`) rather
  than rejecting, which REQ-8 explicitly requires this feature *not* to
  do. This is a deliberate divergence from that no-op precedent, not an
  oversight — flagged here since REQ-8 is unambiguous about it.
- **No deletion-confirmation-token step on either revoke endpoint** —
  the SPEC's own Non-functional section makes this call and explains
  why (losslessly reversible, no access removed from another party);
  carried through unchanged, not re-decided here.
- **Reactivate-on-regrant already works for tenant scope** —
  `AccessGroupPermissionRepository#findByAccessGroupAndPermission` is
  already unfiltered by `deletedAt` (see its Javadoc: "intentionally
  unfiltered... so it can reactivate it"), and
  `grantAccessGroupPermission` already does
  `.orElseGet(() -> save(new ...))`. That `orElseGet` path does **not**
  currently clear `deletedAt` on an existing soft-deleted row it finds —
  it must be changed to `.map(existing -> { existing.setDeletedAt(null); return save(existing); }).orElseGet(...)`
  to actually satisfy REQ-4 once revoke exists (today this path is
  unreachable dead code since nothing sets `deletedAt` on this table
  yet). Same fix applies to the staff-scope equivalent once its
  repository method is likewise required to see soft-deleted rows.
- **`GlobalAccessGroupPermissionRepository#findByGlobalAccessGroupAndPermission`
  must become explicitly unfiltered (already is, since no `deletedAt`
  column exists yet) and stay that way once the column is added** —
  document this with the same Javadoc pattern as the tenant-side
  repository's, so a future reader doesn't "fix" it to filter deleted
  rows out and break reactivate-on-regrant.
- **`findByGlobalAccessGroupIn` (used by the bulk permission-listing
  fetch for REQ-11) gains a `AndDeletedAtIsNull` variant** — mirrors
  `AccessGroupPermissionRepository#findByAccessGroupInAndDeletedAtIsNull`,
  needed because the staff table has no soft-delete filtering today and
  the new list-endpoint fetch must exclude revoked rows.
- **`AccessGroupDto`/`GlobalAccessGroupDto` gain a `permissions` field
  populated via one bulk query per list call, not per-role** —
  `listAccessGroups` (both scopes) already loads all roles for the
  tenant/staff scope in one query; a second bulk query
  (`findByAccessGroupInAndDeletedAtIsNull(allGroups)` /
  `findByGlobalAccessGroupInAndDeletedAtIsNull(allGroups)`) fetches every
  live permission row for those roles in one round trip, then the
  service groups them by role id in memory before mapping to DTOs — same
  shape as the existing `PermissionService#effectivePermissions`
  bulk-fetch-then-group pattern used elsewhere in this file, per REQ-11's
  explicit N+1 constraint.
- **Read-path sweep confirms no regression**: `PermissionService`
  (tenant scope)'s effective-permission resolution already calls
  `findByAccessGroupInAndDeletedAtIsNull`. `GlobalPermissionService`
  (staff/global scope) currently calls the *unfiltered*
  `findByGlobalAccessGroupIn` — this must be switched to the new
  `...AndDeletedAtIsNull` variant in the same change, or a revoked
  staff-role permission would still count as effectively granted. This
  is a required fix, not optional, to avoid REQ-3 being undermined on the
  staff side.
- **Audit actions**: `tenant.access_group.revoke_permission`
  (resourceType `AccessGroupPermission`) and
  `staff.access_group.revoke_permission` (resourceType
  `GlobalAccessGroupPermission`) — mirror the exact naming pattern of
  the sibling grant actions (`tenant.access_group.grant_permission`,
  `staff.access_group.grant_permission`) with `grant_permission` ->
  `revoke_permission`, nothing else changed.
- **No new dependency** — everything here is existing Spring
  Data/Hibernate/Flyway machinery already used by the sibling grant
  endpoints.

## Data schema

- New migration `V30__global_access_group_permission_soft_delete.sql`,
  mirroring `V29__access_group_soft_delete.sql`'s pattern exactly:
  - `ALTER TABLE global_access_group_permissions ADD COLUMN deleted_at TIMESTAMPTZ;`
  - `ALTER TABLE global_access_group_permissions_aud ADD COLUMN deleted_at TIMESTAMPTZ;`
    (Envers audit table — confirm exact name via the existing
    `V4__create_tenancy_envers_audit_tables.sql`/subsequent Envers
    migrations before writing; follows the `_aud` suffix convention
    V29 uses for `access_group_permissions_aud`.)
  - `GlobalAccessGroupPermission` currently has a table-level
    `@UniqueConstraint(columnNames = {"global_access_group_id", "permission"})`
    (unlike `AccessGroupPermission`, which already went through this in
    V29) — drop it and replace with
    `CREATE UNIQUE INDEX ux_global_access_group_permissions_group_permission ON global_access_group_permissions (global_access_group_id, permission) WHERE deleted_at IS NULL;`,
    the exact partial-index substitution V29 already did for the
    tenant-side table's equivalent constraint.
  - `GlobalAccessGroupPermission` entity: add the `deletedAt` field with
    the same Javadoc/annotations as `AccessGroupPermission#deletedAt`,
    and remove the now-redundant `uniqueConstraints` attribute from its
    `@Table` (index is DB-managed, not JPA-managed, matching how
    `AccessGroupPermission`/`AccessGroup` already do this).
- No other schema changes. `AccessGroupDto`/`GlobalAccessGroupDto` gain
  an in-memory-only `permissions` field — not a new column.

## API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| DELETE | `/api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}` | path vars only (`permission` = `Permission` enum) | empty body | 200 success; 403 `TENANT_ACCESS_DENIED` (unknown/deleted role, or caller lacks `TENANT_ACCESS_GROUP_EDIT`); 400 `ACCESS_GROUP_PERMISSION_NOT_GRANTED` (nothing to revoke) |
| DELETE | `/api/staff/access-groups/{accessGroupId}/permissions/{permission}` | path vars only (`permission` = `GlobalPermission` enum) | empty body | 200 success; 403 `TENANT_ACCESS_DENIED` (unknown/deleted role) or `PERMISSION_DENIED` (caller lacks `STAFF_PERMISSION_MANAGE`, via `@RequiresGlobalPermission`); 400 `ACCESS_GROUP_PERMISSION_NOT_GRANTED` |
| GET (existing, extended) | `/api/tenants/{tenantId}/access-groups` | — | `[{id, name, permissions: Permission[]}]` | 200 |
| GET (existing, extended) | `/api/staff/access-groups` | — | `[{id, name, permissions: GlobalPermission[]}]` | 200 |

`TenancyErrorResponseDto` body shape for the new 400 case:
`{"error": "ACCESS_GROUP_PERMISSION_NOT_GRANTED"}`, registered in
`TenancyExceptionHandler` alongside the other `@ExceptionHandler`
methods, same pattern as `InvalidAccessGroupBatchException`'s.

## Dependencies

None. No `pom.xml` change.

## Package/file structure

- `br.com.conectabyte.knowly.tenancy.TenantController` — new
  `deleteAccessGroupPermission` handler (`@DeleteMapping(".../permissions/{permission}")`).
- `br.com.conectabyte.knowly.tenancy.TenantService` —
  `revokeAccessGroupPermission` method; `grantAccessGroupPermission`
  patched for reactivate-on-regrant (see above); `listAccessGroups`
  patched for bulk permission fetch.
- `br.com.conectabyte.knowly.tenancy.StaffController` — new
  `deleteAccessGroupPermission` handler.
- `br.com.conectabyte.knowly.tenancy.StaffService` —
  `revokeAccessGroupPermission` (`@RequiresGlobalPermission(STAFF_PERMISSION_MANAGE)`);
  `grantAccessGroupPermission` and `listAccessGroups` patched likewise.
- `br.com.conectabyte.knowly.tenancy.AccessGroupPermissionRepository` —
  no new method needed (reactivate lookup already exists).
- `br.com.conectabyte.knowly.tenancy.GlobalAccessGroupPermissionRepository` —
  add `findByGlobalAccessGroupInAndDeletedAtIsNull`; keep
  `findByGlobalAccessGroupAndPermission` unfiltered (document why).
- `br.com.conectabyte.knowly.tenancy.exception.AccessGroupPermissionNotGrantedException`
  — new, `RuntimeException`, no fields, mirroring
  `AccessGroupNotFoundException`'s shape.
- `br.com.conectabyte.knowly.tenancy.exception.TenancyExceptionHandler` —
  new `@ExceptionHandler` entry, `HttpStatus.BAD_REQUEST`,
  `"ACCESS_GROUP_PERMISSION_NOT_GRANTED"`.
- `br.com.conectabyte.knowly.tenancy.dto.AccessGroupDto` /
  `GlobalAccessGroupDto` — add `permissions` field + update `from(...)`
  factories to accept the pre-fetched permission list.
- `br.com.conectabyte.knowly.tenancy.GlobalAccessGroupPermission` — add
  `deletedAt`; drop `@Table(uniqueConstraints = ...)`.
- `GlobalPermissionService` (staff/global effective-permission
  resolution path — not `PermissionService`, which is the tenant-scoped
  one) — swap `findByGlobalAccessGroupIn` call for the new
  `...AndDeletedAtIsNull` variant.
- `src/main/resources/db/migration/V30__global_access_group_permission_soft_delete.sql`
  — new.

## Testing strategy

- Unit: `TenantServiceTest`/`StaffServiceTest` — revoke on
  unknown/deleted role throws `TenantAccessDeniedException`; revoke on
  not-currently-granted permission throws
  `AccessGroupPermissionNotGrantedException`; revoke soft-deletes
  (`deletedAt` set, row not removed); regrant after revoke clears
  `deletedAt` on the same row (no duplicate saved). Repository tests
  (`AccessGroupPermissionRepositoryTest`,
  `GlobalAccessGroupPermissionRepositoryTest`) cover the new/adjusted
  query methods directly.
- Integration (Testcontainers): both scopes — grant -> revoke ->
  re-grant round trip via the real endpoints, asserting the same
  underlying row id persists across the cycle; unauthorized caller
  (missing `TENANT_ACCESS_GROUP_EDIT`/`STAFF_PERMISSION_MANAGE`) gets
  403; the two "nothing to revoke" cases (never granted, already
  revoked) both get 400; list endpoints return `permissions` correctly
  after a revoke (regression check that REQ-11's fix and REQ-3's
  soft-delete are wired together correctly); one test asserting the new
  partial unique index doesn't reject a same-`(role, permission)`
  regrant after revoke (would previously have hit the dropped
  table-level constraint pre-migration).
- Run scoped tests per task (e.g.
  `./mvnw test -Dtest=TenantServiceTest,StaffServiceTest,AccessGroupBulkAndDeleteIntegrationTest`)
  during implementation; reserve `./mvnw verify` for the final
  pre-merge pass.
