# SPEC — role-permission-revoke

> The what and the why. No technical implementation details.

## Context and motivation

Today a tenant role (`AccessGroup`) or a staff/global role
(`GlobalAccessGroup`) can only ever **gain** permissions —
`POST /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions`
and `POST /api/staff/access-groups/{accessGroupId}/permissions` exist,
but nothing lets an admin take a permission back off a role once
granted. This makes roles effectively meaningless as an access-control
tool in practice: an admin who over-grants (or a role whose
responsibilities later narrow) has no way to correct it — the only
existing workaround, deleting the whole role, is destructive and loses
every other permission and every member assignment the role carries.

This SPEC closes that gap by adding a revoke endpoint on both scopes
(tenant-scoped `AccessGroup`, staff/global-scoped `GlobalAccessGroup`),
symmetric with the existing grant endpoint, so a role's permission set
can be edited in both directions. It also closes a second, related gap
discovered while scoping this SPEC: neither scope's existing list
endpoint (`GET /{tenantId}/access-groups`, `GET /staff/access-groups`)
returns which permissions a role currently has — `AccessGroupDto` today
is just `{id, name}` — so there is currently no way to read a role's
granted permissions at all, grant/revoke-only. The frontend SPEC's
role-editing views (requirements 6-7 there) cannot be built without
this. It is the backend half of a two-SPEC feature — the frontend half (`knowly-app/specify/features/
role-permission-management-ui/SPEC.md`) adds the UI that calls these new
endpoints, plus the permission name/description copy this SPEC's
acceptance criteria also drafts, since the new UI displays that copy for
every existing `Permission`/`GlobalPermission` value (grant and revoke
alike, not just the ones added here).

## User stories

- As a tenant admin (or staff, acting as/for a tenant), I want to revoke
  a specific permission from one of my tenant's roles, so that I can
  correct an over-grant without deleting and recreating the whole role.
- As staff with `STAFF_PERMISSION_MANAGE`, I want to revoke a specific
  permission from a staff/global role, so that a global role's access
  can be narrowed the same way a tenant role's can.
- As either admin, I want a permission I revoked from a role to be
  restorable by granting it again, so that a revoke is never a
  one-way, unrecoverable action.

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** The system shall expose
   `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}`,
   revoking that `Permission` from that tenant `AccessGroup`.
2. **[Ubiquitous]** The system shall expose
   `DELETE /api/staff/access-groups/{accessGroupId}/permissions/{permission}`,
   revoking that `GlobalPermission` from that staff/global
   `GlobalAccessGroup`.
3. **[Event-Driven]** When a permission is revoked from an `AccessGroup`
   or `GlobalAccessGroup`, the system shall mark the corresponding
   `AccessGroupPermission`/`GlobalAccessGroupPermission` row as
   soft-deleted (`deletedAt` set), never physically remove the row —
   consistent with this project's standing "no destructive operation
   may physically remove a row" rule (see `DECISIONS.md`).
4. **[Event-Driven]** When a permission that is currently soft-deleted
   for a given role is granted again (existing grant endpoints, both
   scopes), the system shall reactivate the existing row (clear
   `deletedAt`) rather than insert a second row for the same
   `(role, permission)` pair — mirroring the existing reactivate-on-
   regrant pattern already used elsewhere in this codebase (e.g. direct
   permission grants, access-group member assignment).
5. **[Ubiquitous]** The system shall gate
   `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}`
   with the same authorization rule the existing grant endpoint on the
   same resource already uses (tenant admin or staff, via
   `GlobalPermission.TENANT_ACCESS_GROUP_EDIT`).
6. **[Ubiquitous]** The system shall gate
   `DELETE /api/staff/access-groups/{accessGroupId}/permissions/{permission}`
   with the same authorization rule the existing grant endpoint on the
   same resource already uses (`GlobalPermission.STAFF_PERMISSION_MANAGE`).
7. **[Unwanted Behavior]** If the referenced `AccessGroup`/
   `GlobalAccessGroup` does not exist or is itself soft-deleted, then
   the system shall reject the revoke request the same way the existing
   grant endpoint rejects an unknown/deleted role (404-equivalent), not
   silently succeed.
8. **[Unwanted Behavior]** If the referenced role currently has no
   active grant of that permission (already revoked, or never granted),
   then the system shall reject the revoke request rather than silently
   succeed, so a caller can distinguish "nothing to do" from "revoked."
9. **[Event-Driven]** When a permission is revoked from an `AccessGroup`
   or `GlobalAccessGroup`, the system shall record an audit log event
   for the action (actor, role, permission, outcome), mirroring the
   existing `@AuditLog` pattern already applied to the grant endpoints
   and to direct-permission revoke.
10. **[Ubiquitous]** The system shall add a `deleted_at` column
    (nullable timestamp) to `global_access_group_permissions` via a new
    migration, matching the column `access_group_permissions` already
    has (added by `tenant-access-group-bulk-and-delete`) — today only
    the tenant-scoped table has this column; the staff/global-scoped
    table does not, and revoke cannot be implemented as a soft-delete on
    that table without it.
11. **[Ubiquitous]** The system shall extend `AccessGroupDto` (tenant
    scope) and its staff/global equivalent to include the role's
    currently-granted permissions (e.g. `permissions: List<Permission>`/
    `List<GlobalPermission>`), returned by the existing list endpoints
    (`GET /{tenantId}/access-groups`, `GET /staff/access-groups`) —
    fixing the gap described above where no endpoint on either scope
    currently exposes a role's granted permissions at all.

## Non-functional requirements

- Security: revoke is gated identically to the corresponding grant
  endpoint on the same resource (see requirements 5-6) — no new,
  weaker, or stronger authorization rule is introduced. No deletion-
  confirmation-token step (the pattern used for account/tenant/access-
  group deletion elsewhere in this codebase) is required for this
  action: unlike those destructive operations, a revoked permission is
  immediately and losslessly reversible by granting it again through
  the same screen, and it never removes another party's access to the
  system the way deleting a whole role or account does. This is a
  deliberate, explained (Tier 2) design choice, not an oversight — flag
  it if a future reviewer disagrees and wants the heavier confirmation
  flow applied here too.
- Observability: every grant/revoke action must remain reconstructable
  per-actor via the existing audit trail (`@AuditLog`), per the root
  constitution's audit requirements.
- Performance/SLA: no new requirement beyond existing endpoints in this
  family (single-row lookup/update, no batch operation in scope here).

## Acceptance criteria

- [ ] `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}`
      exists, soft-deletes the matching `AccessGroupPermission` row, is
      gated by `TENANT_ACCESS_GROUP_EDIT` (tenant admin or staff), and
      is audit-logged.
- [ ] `DELETE /api/staff/access-groups/{accessGroupId}/permissions/{permission}`
      exists, soft-deletes the matching `GlobalAccessGroupPermission`
      row, is gated by `STAFF_PERMISSION_MANAGE`, and is audit-logged.
- [ ] A new migration adds `deleted_at` to `global_access_group_permissions`.
- [ ] Granting a previously-revoked permission back onto the same role
      reactivates the existing row (no duplicate row, no unique-
      constraint violation) on both scopes.
- [ ] Revoking an unknown role, a soft-deleted role, or a
      not-currently-granted permission is rejected, not silently
      accepted, on both scopes.
- [ ] Integration tests cover: grant → revoke → re-grant round trip,
      unauthorized caller rejection, and the two "nothing to revoke"
      rejection cases, on both scopes.
- [ ] `AccessGroupDto` and its staff/global equivalent include the
      role's currently-granted permissions; both list endpoints return
      this without an N+1 extra call per role.
- [ ] `./mvnw verify` green; `./mvnw spotless:check` clean.

## Out of scope

- Batch/bulk revoke of multiple permissions in one call — each
  revoke is a single `(role, permission)` pair per request, matching
  the existing single-permission grant endpoints' shape.
- Any deletion-confirmation-token/typed-word confirmation step for
  revoke (see the Non-functional section above for why this was
  deliberately left out — reopen only via an explicit follow-up
  decision, not silently during implementation).
- Any change to `AccessGroup`/`GlobalAccessGroup` deletion, member
  assignment/unassignment, or role creation — untouched by this SPEC.
- Writing the permission name/description copy shown in the frontend's
  new permission-list UI — that copy is drafted as part of the
  **frontend** SPEC's acceptance criteria (it's UI content, not backend
  behavior), even though the underlying `Permission`/`GlobalPermission`
  enums it describes live in this subproject.
