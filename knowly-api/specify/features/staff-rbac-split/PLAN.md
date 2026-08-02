# PLAN — staff-rbac-split

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- `GlobalRole` becomes `STAFF_ADMIN, STAFF` (was `STAFF` only). Every
  existing `isStaff()`/`requireStaff`-style check that meant "unrestricted
  access" now means `globalRole == GlobalRole.STAFF_ADMIN` specifically;
  plain `STAFF` goes through the new global permission check instead.
- New package-local additions to `br.com.conectabyte.knowly.tenancy`
  (same package as the tenant-side equivalents, since this is the same
  concern at a different scope):
  - `GlobalPermission` enum: `TENANT_CREATE`, `TENANT_ACT_AS_ANY`,
    `TENANT_MEMBER_MANAGE_ANY`, `TENANT_ACCESS_GROUP_MANAGE_ANY`,
    `TENANT_PERMISSION_GRANT_MANAGE_ANY`, `STAFF_PERMISSION_MANAGE`.
    `STAFF_PERMISSION_MANAGE` is what SPEC REQ-7 gates (granting/revoking
    global permissions/groups) — deliberately its own permission, not
    implied by any other, so holding e.g. `TENANT_CREATE` never lets a
    `STAFF` user touch anyone's global grants.
  - `DirectGlobalPermissionGrant` entity: `(User, GlobalPermission)`,
    unique pair — mirrors `DirectPermissionGrant` with `User` replacing
    `TenantMembership`.
  - `GlobalAccessGroup` entity: `(name)`, unique — mirrors `AccessGroup`
    minus the `tenant` FK (global groups aren't tenant-scoped, so no
    `@Filter` either).
  - `GlobalAccessGroupPermission` entity: `(GlobalAccessGroup,
    GlobalPermission)`, unique pair — mirrors `AccessGroupPermission`.
  - `UserGlobalAccessGroup` entity: `(User, GlobalAccessGroup)`, unique
    pair — mirrors `UserAccessGroup` with `User` replacing
    `TenantMembership`.
  - `GlobalPermissionService`: `effectivePermissions(User)` /
    `hasPermission(User, GlobalPermission)` — same shape as
    `PermissionService`, operating on `User` directly instead of
    `TenantMembership`.
- `PermissionAspect.checkPermission`'s `tenantContext.isStaff()` bypass
  becomes `tenantContext.isStaffAdmin()` (renamed on `TenantContext` for
  clarity — it currently just tracks "is this user staff at all", now it
  must track "is this user unrestricted"). A `STAFF` (non-admin) user
  falls through to the *existing* tenant-membership permission check
  exactly as before for tenant-scoped `@RequiresPermission` methods —
  this SPEC only adds a *separate* new annotation/check for the
  staff-gated actions themselves (see below), it does not change how
  tenant-scoped permissions work for a `STAFF` member who also happens to
  hold a tenant membership.
- New `@RequiresGlobalPermission(GlobalPermission)` annotation + a
  `GlobalPermissionAspect` (mirrors `PermissionAspect`'s shape): resolves
  the authenticated `User`, bypasses for `STAFF_ADMIN`, otherwise checks
  `GlobalPermissionService.hasPermission`. Applied to every method in
  `TenantService` that today calls `requireStaff`/
  `requireAdminOfTenantOrStaff`'s staff-bypass branch:
  - `createTenant` → `TENANT_CREATE`
  - `listAllTenants`, `requireTenant` (staff act-as-tenant path) →
    `TENANT_ACT_AS_ANY`
  - `addMember`, `removeMember`, `listMembers` (staff branch of
    `requireAdminOfTenantOrStaff`) → `TENANT_MEMBER_MANAGE_ANY`
  - `createAccessGroup`, `listAccessGroups` (staff branch) →
    `TENANT_ACCESS_GROUP_MANAGE_ANY`
  - `grantPermission`, `revokePermission`, `assignAccessGroup`,
    `unassignAccessGroup`, `getMemberDetail` (staff branch) →
    `TENANT_PERMISSION_GRANT_MANAGE_ANY`

  Note: these methods are called by *both* a tenant admin (own tenant)
  and staff (any tenant) today via `requireAdminOfTenantOrStaff`'s
  either/or check. The aspect can't replace that method's internal logic
  (it doesn't know "own tenant admin" is a valid alternate path) — so
  `requireAdminOfTenantOrStaff` itself is refactored in place: its
  `actor.getGlobalRole() == GlobalRole.STAFF` unconditional-return branch
  becomes `actor.getGlobalRole() == GlobalRole.STAFF_ADMIN ||
  (actor.getGlobalRole() == GlobalRole.STAFF &&
  globalPermissionService.hasPermission(actor, <the relevant
  GlobalPermission>))`. Same pattern for `requireStaff`. This keeps the
  tenant-admin-or-staff either/or logic in one place (as today) rather
  than splitting it awkwardly between a method-level annotation and
  in-method tenant-admin logic.
- New endpoints under `/api/staff/permissions` (new `StaffController`,
  mirrors `TenantController`'s permission/access-group endpoints 1:1,
  `User` instead of `TenantMembership` as the target):
  - `GET /api/staff/users/{userId}/permissions` — effective global
    permissions for a staff user (own-permissions variant at `GET
    /api/staff/permissions` for "what can I do", mirroring `GET
    /api/tenants/permissions`).
  - `POST /api/staff/users/{userId}/permissions` /
    `DELETE /api/staff/users/{userId}/permissions/{permission}` — direct
    grant/revoke.
  - `GET/POST /api/staff/access-groups`,
    `POST /api/staff/access-groups/{id}/permissions`,
    `POST/DELETE /api/staff/users/{userId}/access-groups/{id}` — group
    management, mirroring the tenant equivalents.

  All of these are annotated `@RequiresGlobalPermission(STAFF_PERMISSION_MANAGE)`
  (SPEC REQ-7) — `STAFF_ADMIN` bypasses via the aspect as usual, no
  `STAFF` grant of any other permission lets them through.
- Audit (SPEC REQ-6): every grant/revoke/group-management method gets
  `@AuditLog`, same convention as `TenantService`'s existing
  `tenant.permission.grant`/`tenant.permission.revoke`/etc. — new action
  names `staff.permission.grant`, `staff.permission.revoke`,
  `staff.access_group.create`, `staff.access_group.grant_permission`,
  `staff.member.access_group.assign`, `staff.member.access_group.unassign`.
- No `@Filter`/tenant-scoping on any of the new entities — global
  permissions are, by definition, not tenant-owned data (SPEC REQ-8: this
  feature governs *whether* a staff user can act, never *which tenant's
  data* they see once acting — that's still the Hibernate filter,
  untouched).

## Data schema

New migration `V14__create_global_permission_tables.sql` (mirrors
`V3`/`V4`'s tables and their Envers `_aud` counterparts in one file, same
as those did):

```sql
UPDATE users SET global_role = 'STAFF_ADMIN' WHERE global_role = 'STAFF';

CREATE TABLE global_access_groups (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE global_access_group_permissions (
  id BIGSERIAL PRIMARY KEY,
  global_access_group_id BIGINT NOT NULL REFERENCES global_access_groups (id),
  permission VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (global_access_group_id, permission)
);

CREATE TABLE direct_global_permission_grants (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users (id),
  permission VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (user_id, permission)
);

CREATE TABLE user_global_access_groups (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users (id),
  global_access_group_id BIGINT NOT NULL REFERENCES global_access_groups (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  UNIQUE (user_id, global_access_group_id)
);

-- + global_access_groups_aud / global_access_group_permissions_aud /
--   direct_global_permission_grants_aud / user_global_access_groups_aud,
--   same shape as V4's *_aud tables (id, rev, revtype, columns nullable, PK (id, rev)).
```

`global_role` column stays `VARCHAR(20)` (already wide enough for
`STAFF_ADMIN`) — no column change needed, only the `UPDATE` above and the
Java enum split.

## API contracts

New `StaffController`, `/api/staff/**`:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/staff/permissions` | Caller's own effective global permissions |
| GET | `/api/staff/users/{userId}/permissions` | A staff user's effective/direct/group permissions |
| POST | `/api/staff/users/{userId}/permissions` | Direct grant |
| DELETE | `/api/staff/users/{userId}/permissions/{permission}` | Direct revoke |
| GET | `/api/staff/access-groups` | List global access groups |
| POST | `/api/staff/access-groups` | Create a global access group |
| POST | `/api/staff/access-groups/{id}/permissions` | Grant a permission to a group |
| POST | `/api/staff/users/{userId}/access-groups/{id}` | Assign a staff user to a group |
| DELETE | `/api/staff/users/{userId}/access-groups/{id}` | Unassign |

All request/response DTOs follow the existing `tenancy.dto` package's
shape (`MemberDto`-style records), just renamed for the global scope
(`StaffUserDto`, `GlobalAccessGroupDto`, etc.) — no new DTO patterns.

## Dependencies

None new.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalRole.java` (modify: add `STAFF_ADMIN`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalPermission.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/DirectGlobalPermissionGrant.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalAccessGroup.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalAccessGroupPermission.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/UserGlobalAccessGroup.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/*Repository.java` (new, one per new entity)
- `src/main/java/br/com/conectabyte/knowly/tenancy/GlobalPermissionService.java` (new)
- `src/main/java/br/com/conectabyte/knowly/audit/RequiresGlobalPermission.java` (new annotation)
- `src/main/java/br/com/conectabyte/knowly/audit/GlobalPermissionAspect.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantContext.java` (modify: `isStaff()` → `isStaffAdmin()`, callers updated)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `requireStaff`/`requireAdminOfTenantOrStaff`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java` (new)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/*` (new DTOs)
- `src/main/resources/db/migration/V14__create_global_permission_tables.sql` (new)
- Every other current call site of `GlobalRole.STAFF`/`tenantContext.isStaff()` (article-management, conversations, dashboard-metrics per PROJECT_STATUS's cross-references) — audited and updated to `STAFF_ADMIN`/`isStaffAdmin()` task by task; SPEC REQ-2 requires no regression here.

## REQ-9 addendum (2026-08-01)

- `OwnGlobalPermissionsDto` gains one new field: `boolean isStaffAccount`
  — record becomes `OwnGlobalPermissionsDto(List<GlobalPermission>
  permissions, boolean isStaffAccount)`. Named to match the JSON key the
  frontend PLAN (`navigation-menu`) already expects
  (`isStaffAccount`), not a fresh name invented here.
- Staff-ness is **not re-derived** from `User.getGlobalRole()` in
  `StaffController`. `TenantContext.isStaff()` already answers exactly
  this question today: `TenantContextFilter` populates it per-request
  from the session's `TenantSessionKeys.STAFF` attribute, which is set
  true for *both* `GlobalRole.STAFF` and `GlobalRole.STAFF_ADMIN` (see
  `isStaffAdmin()`'s Javadoc: "`isStaff()` tracks is this user staff at
  all", unchanged since the Task-4 rename). Reusing it means REQ-9's
  field is guaranteed to be consistent with the same staff/non-staff
  split already enforced everywhere else (`PermissionAspect`,
  `GlobalPermissionAspect`), rather than a second, potentially-drifting
  source of truth.
- `StaffController.ownPermissions()` changes only its return
  construction: both branches (`STAFF_ADMIN` bypass and the
  `effectivePermissions` branch) now pass `tenantContext.isStaff()` as
  the second constructor argument. No new branch/condition is added —
  reaching `ownPermissions()` at all already implies
  `tenantContext.isStaff()` is `true` in both existing code paths, but
  the field is still read from `tenantContext.isStaff()` directly
  (rather than hardcoding `true` in the controller) so the single source
  of truth stays `TenantContext`, not a controller-local assumption that
  could silently go stale if `ownPermissions()`'s access rules ever
  change.
- No new `@RequiresGlobalPermission`/security change: `GET
  /api/staff/permissions` remains open to any authenticated caller
  exactly as today — REQ-9 only asks for one boolean of *metadata about
  the caller themselves*, not a new authorization gate (SPEC's Security
  NFR: read-only, no permission implication).
- No migration/entity change: this is a response-shape addition over
  data (`GlobalRole`, per-request staff/staff-admin flags) that already
  exists.

## Testing strategy

- Unit tests: `GlobalPermissionService` (direct grant, group grant,
  combined, revocation) — mirrors `PermissionServiceTest`.
- Integration tests (`@SpringBootTest`, mirrors
  `TenantManagementIntegrationTest`/`TenantSessionIntegrationTest`):
  - `STAFF_ADMIN` retains every capability today's `STAFF` has (REQ-2,
    acceptance criterion 1) — re-run of existing staff-bypass assertions
    against the renamed role.
  - A zero-grant `STAFF` user is rejected from every staff-gated action
    (REQ-5, acceptance criterion 2).
  - A `STAFF` user with a specific direct grant can do only that action
    (acceptance criterion 3).
  - A `STAFF` user granted via a global access group has equivalent
    access; removing the group/grant removes it (acceptance criterion 4).
  - A `STAFF` user (even one holding `STAFF_PERMISSION_MANAGE`... no,
    holding some *other* permission) cannot call any
    `/api/staff/permissions`-family endpoint (REQ-7, acceptance criterion
    5) — only `STAFF_ADMIN` can.
  - Every grant/revoke emits the expected `AuditEvent` (REQ-6).
