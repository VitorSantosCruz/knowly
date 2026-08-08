# SPEC — Tenant access-group bulk assignment and cascading soft-delete

## Context and motivation

`knowly-app`'s `tenant-access-group-management` SPEC
(`knowly-app/specify/features/tenant-access-group-management/SPEC.md`)
gives tenant-scoped `AccessGroup`s their own management screen, but two
of its requirement groups are explicitly blocked on backend work that
does not exist yet in `knowly-api` — that frontend SPEC's own
"Dependencies" section names both and defers designing the actual
contract to this SPEC:

1. **Bulk multi-group assignment.** Today, assigning a tenant member to
   an access group is one call per group (`POST
   /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`,
   `TenantController`/`TenantService#assignAccessGroup`). Assigning one
   member to several groups at once therefore requires N sequential
   requests from the frontend — explicitly rejected as the shipped
   behavior. This SPEC adds a single endpoint that assigns a set of
   access groups to one membership in one request.
2. **Access-group deletion with cascading soft-delete.** No delete
   endpoint exists for tenant `AccessGroup` at all today —
   `AccessGroupRepository`/`AccessGroupController`(`TenantController`)
   expose create/list/grant-permission/assign/unassign only. This SPEC
   adds a delete endpoint, and — per `../../../../DECISIONS.md`'s
   "Logical delete is now a standing, system-wide rule" (2026-08-04) —
   the delete is logical (`deleted_at`), not physical, and cascades to
   the group's own dependent rows, since they are tightly-coupled owned
   resources of the group (the same reasoning that entry already applies
   to `User`→`user_profiles`/`addresses`/`contacts` and
   `Tenant`→`Article`/`Conversation`): the group's `UserAccessGroup`
   (member-assignment) rows and its `AccessGroupPermission`
   (permission-grant) rows are cascade-soft-deleted alongside it, not
   left to be silently unreachable via some other, unaudited read path.

Neither `AccessGroup` nor `AccessGroupPermission` currently has a
`deleted_at` column (confirmed by reading
`knowly-api/src/main/java/br/com/conectabyte/knowly/tenancy/AccessGroup.java`
and `AccessGroupPermission.java`) — this SPEC introduces it on both.
`UserAccessGroup` already has `deleted_at` (added when `unassignAccessGroup`
was retrofitted to logical delete per the same 2026-08-04 decision,
backed by the partial unique index `ux_user_access_groups_membership_group`
from migration `V28`) and this SPEC reuses that column and its existing
reactivate-on-reassign behavior unchanged — it does not redesign
`UserAccessGroup`'s own soft-delete, only adds a second path (cascade
from the group's own delete) that also sets it.

This SPEC is the second of two backend capabilities that were designed
together with a Tier-2 judgment call on scope (deletion is one-way for
the group itself, no restore path — see "Out of scope"), following the
precedent already set for `TenantMembership#hardDeleteMember` and
`DirectPermissionGrant`/`UserAccessGroup` revoke/unassign paths (both
retrofitted to `deleted_at` under the same 2026-08-04 decision, both
following the same "reactivate a found-but-deleted row on re-grant,
never insert a duplicate" and "every permission-resolution/listing read
filters `deleted_at IS NULL`" consequences named there).

## User stories

- As a tenant `MEMBER_ADMIN` (or a staff caller with the equivalent
  granular tenant permissions), I want to assign one member to several
  access groups in a single request, so that onboarding a member into
  multiple groups doesn't cost one confirmation and one round trip per
  group.
- As a tenant `MEMBER_ADMIN`, I want to delete an access group that is no
  longer needed, so that stale groups stop appearing as an assignment
  option and stop granting permissions to anyone.
- As the system, I want a deleted access group's member-assignments and
  permission-grants to stop being effective and stop appearing in any
  read the moment the group itself is deleted, so a group's permissions
  can never silently "leak" through a row that outlived the group it
  belonged to.
- As the system, I want the delete to be logical, not physical, so the
  group's history remains reconstructable (audit trail, Envers
  revisions) the same way every other destructive action in this system
  already behaves, per the standing 2026-08-04 rule.

## Requirements (EARS/GEARS)

### Bulk assignment

- **REQ-1 [Ubiquitous]** The system shall provide `POST
  /api/tenants/{tenantId}/members/{membershipId}/access-groups:batch`,
  accepting a JSON body `{ "accessGroupIds": [<Long>, ...] }`, that
  assigns every listed access group id to the given membership in a
  single request/transaction.
- **REQ-2 [Event-Driven]** When a caller holding
  `TENANT_PERMISSION_GRANT_CREATE` for the tenant calls REQ-1's endpoint
  with a non-empty `accessGroupIds` list, the system shall, for each
  listed id that resolves to a live (`deleted_at IS NULL`) `AccessGroup`
  belonging to that tenant, create or reactivate (per the existing
  `UserAccessGroup` reactivate-on-reassign behavior — set `deleted_at =
  null` on a found-but-deleted row rather than inserting a duplicate) a
  `UserAccessGroup` row linking the membership to that group, mirroring
  `assignAccessGroup`'s existing single-group logic applied once per id
  in the same transaction. This is additive only — groups the membership
  is already assigned to remain assigned; groups already assigned to the
  membership but *not* in the submitted list are left untouched (not
  unassigned) by this endpoint.
- **REQ-3 [Unwanted Behavior]** If any id in `accessGroupIds` does not
  resolve to a live `AccessGroup` belonging to the calling tenant (wrong
  tenant, unknown id, or a soft-deleted group), then the system shall
  reject the entire request (no partial assignment of the other, valid
  ids in the same list) with a 4xx response identifying that the request
  was invalid, and shall not create or reactivate any `UserAccessGroup`
  row from that request.
- **REQ-4 [Unwanted Behavior]** If `accessGroupIds` is empty, missing, or
  contains a duplicate id, then the system shall reject the request as
  invalid (400) before attempting any assignment; a duplicate id shall
  not be treated as if it produces two assignment attempts.
- **REQ-5 [Unwanted Behavior]** If a caller without
  `TENANT_PERMISSION_GRANT_CREATE` for the tenant calls REQ-1's endpoint,
  then the system shall reject the request (403) and perform no
  assignment.
- **REQ-6 [Ubiquitous]** REQ-1's endpoint requires no deletion
  confirmation token — assignment (single or bulk) is additive, not
  destructive, mirroring the existing single-group `assignAccessGroup`
  endpoint, which also requires none.
- **REQ-7 [Event-Driven]** When REQ-1's endpoint succeeds, the system
  shall audit-log the batch assignment (actor, tenant, membership id, the
  full submitted `accessGroupIds` list) as a single event, mirroring this
  codebase's existing `@AuditLog`-per-state-change convention — not one
  event per group id.

### Access-group deletion (cascading soft-delete)

- **REQ-8 [Ubiquitous]** `AccessGroup` gains a nullable `deleted_at`
  column. The existing table-level unique constraint on
  `(tenant_id, name)` is replaced by a partial unique index (`WHERE
  deleted_at IS NULL`), mirroring this codebase's existing pattern for
  every other soft-deletable uniqueness constraint (e.g.
  `ux_user_access_groups_membership_group`), so that deleting a group
  frees its name for reuse by a new group in the same tenant.
- **REQ-9 [Ubiquitous]** `AccessGroupPermission` gains a nullable
  `deleted_at` column. Its existing unique constraint on
  `(access_group_id, permission)` is replaced by a partial unique index
  (`WHERE deleted_at IS NULL`), for the same reason as REQ-8 — re-granting
  a permission to a group after it was revoked (existing behavior,
  currently out of scope for revoke itself per the frontend SPEC's "Out
  of scope," but the column must not collide if a future revoke feature
  needs it) must not collide with the cascade this SPEC introduces.
- **REQ-10 [Ubiquitous]** The system shall provide `GET
  /api/tenants/{tenantId}/access-groups/{accessGroupId}/deletion-confirmation-token`,
  gated by `TENANT_ACCESS_GROUP_DELETE`, generating a deletion
  confirmation token scoped to that access-group instance and the calling
  user, per the existing `deletion-confirmation-token` mechanism
  (`DeletionConfirmationTokenService`) — mirroring
  `generateAccessGroupUnassignmentDeletionConfirmationToken`'s existing
  shape for the sibling unassignment action.
- **REQ-11 [Unwanted Behavior]** If a caller without
  `TENANT_ACCESS_GROUP_DELETE` calls REQ-10's endpoint, then the system
  shall reject the request (403) and generate no token.
- **REQ-12 [Ubiquitous]** The system shall provide `DELETE
  /api/tenants/{tenantId}/access-groups/{accessGroupId}`, accepting the
  confirmation word as a request parameter (mirroring
  `unassignAccessGroup`'s existing parameter shape), gated by
  `TENANT_ACCESS_GROUP_DELETE`.
- **REQ-13 [Event-Driven]** When REQ-12's endpoint is called with a valid,
  unexpired, unused confirmation token scoped to that exact access-group
  instance and calling user (per the existing
  `deletion-confirmation-token` mechanism's REQ-5–REQ-11), for a live
  (`deleted_at IS NULL`) `AccessGroup` belonging to the calling tenant,
  the system shall, in one transaction:
  - set `deleted_at` on the `AccessGroup` row itself;
  - set `deleted_at` on every currently-live (`deleted_at IS NULL`)
    `UserAccessGroup` row referencing that group (cascading the
    unassignment to every member currently assigned to it, without
    requiring a separate confirmation per member);
  - set `deleted_at` on every currently-live (`deleted_at IS NULL`)
    `AccessGroupPermission` row referencing that group (cascading the
    revocation of every permission the group grants).
- **REQ-14 [Unwanted Behavior]** If REQ-12's endpoint is called without a
  valid confirmation token for that exact access-group instance and
  calling user, then the system shall reject the deletion (per the
  existing mechanism's REQ-7 generic-failure behavior) and leave the
  group and its dependent rows untouched.
- **REQ-15 [Unwanted Behavior]** If REQ-12's endpoint is called for an
  access-group id that does not resolve to a live `AccessGroup` belonging
  to the calling tenant (unknown id, wrong tenant, or already
  soft-deleted), then the system shall reject the request (404) and
  perform no change.
- **REQ-16 [Unwanted Behavior]** If a caller without
  `TENANT_ACCESS_GROUP_DELETE` calls REQ-12's endpoint, then the system
  shall reject the request (403) and perform no deletion, independent of
  whether a valid token was supplied.
- **REQ-17 [Ubiquitous]** Every existing read that lists, looks up, or
  resolves effective permissions through `AccessGroup`,
  `UserAccessGroup`, or `AccessGroupPermission` (`listAccessGroups`, the
  member-roster read path, `PermissionService`'s effective-permission
  resolution, and the assignment/unassignment/grant write paths' own
  existence checks) shall filter `deleted_at IS NULL` on every one of
  those three tables it touches, so that a soft-deleted group, its
  cascaded assignments, and its cascaded permission-grants are excluded
  from every such read — not just the ones this SPEC adds. This closes
  the exact security-relevant gap named in `../../../../DECISIONS.md`'s
  standing rule ("a missed filter silently re-grants a permission the
  caller believes was revoked").
- **REQ-18 [Event-Driven]** When REQ-12's endpoint succeeds, the system
  shall audit-log the deletion as a single event (actor, tenant, access
  group id), mirroring `hardDeleteMember`'s existing
  `@AuditLog`-per-action convention — not one event per cascaded
  `UserAccessGroup`/`AccessGroupPermission` row.
- **REQ-19 [Ubiquitous]** Deleting an access group per REQ-13 is one-way:
  the system provides no endpoint or mechanism to restore
  (`deleted_at = null`) a soft-deleted `AccessGroup`, its cascaded
  `UserAccessGroup` rows, or its cascaded `AccessGroupPermission` rows.
  Creating a *new* access group with the same name in the same tenant
  after the old one was deleted is unaffected by this — REQ-8's partial
  unique index means the name is free the moment the old row is
  soft-deleted — but that new group is a distinct row with no historical
  connection to the deleted one (no assignments or permission-grants
  carry over).

## Non-functional requirements

- Security: REQ-13's cascade runs inside a single database transaction —
  a failure partway through must not leave the group deleted while its
  `UserAccessGroup`/`AccessGroupPermission` rows remain live (or vice
  versa).
- Security: REQ-17's filtering is the load-bearing safeguard of this
  entire SPEC — every current and future read touching these three
  tables must be checked against it, the same "easy to miss" warning
  `DECISIONS.md`'s standing rule already gives for this exact class of
  change.
- Observability: REQ-7 and REQ-18 are each a single audit event per
  request, not one per affected row, consistent with `hardDeleteMember`'s
  existing convention (contrast with the per-permission audit events in
  `batchUpdatePermissions`, which intentionally logs one event per
  permission changed — that finer granularity is not required here since
  a group's deletion is a single semantic action, not a per-permission
  toggle).
- Performance/SLA: REQ-2's per-id loop and REQ-13's per-row cascade
  updates must not introduce N+1 queries beyond what a single
  bulk/batch update per affected table already requires — a single
  `UPDATE ... WHERE access_group_id = ? AND deleted_at IS NULL` (and the
  equivalent for `AccessGroupPermission`) is preferred over loading and
  saving each row individually in REQ-13's cascade.

## Acceptance criteria

- [x] A caller with `TENANT_PERMISSION_GRANT_CREATE` can assign a single
      membership to N access groups (N ≥ 1, no duplicates) in one `POST
      .../access-groups:batch` request; the response reflects all N
      assignments having been created or reactivated.
- [x] A caller without `TENANT_PERMISSION_GRANT_CREATE` cannot call the
      batch endpoint; no assignment occurs.
- [x] An invalid id (wrong tenant, unknown, or soft-deleted group) inside
      an otherwise-valid batch request rejects the whole request; none of
      the other, valid ids in that same request get assigned.
- [x] An empty, missing, or duplicate-containing `accessGroupIds` list is
      rejected with 400 before any assignment attempt.
- [x] A caller with `TENANT_ACCESS_GROUP_DELETE` can request a deletion
      confirmation token for a specific access group and, supplying that
      token, delete the group; the group's row, every currently-assigned
      member's `UserAccessGroup` row, and every one of its
      `AccessGroupPermission` rows all end up with a non-null
      `deleted_at`, set within the same transaction.
- [x] A caller without `TENANT_ACCESS_GROUP_DELETE` cannot generate a
      token for, or delete, an access group.
- [x] Deleting an access group without a valid confirmation token is
      rejected and changes nothing.
- [x] After deletion, the group no longer appears in `listAccessGroups`,
      its former members' effective permissions no longer include
      anything it granted, and creating a new group with the same name in
      the same tenant succeeds.
- [x] No endpoint exists to restore a soft-deleted access group or its
      cascaded rows.

## Out of scope

- Reactivating/restoring a soft-deleted `AccessGroup` (or its cascaded
  `UserAccessGroup`/`AccessGroupPermission` rows) — deletion is one-way
  for this resource (REQ-19). A future SPEC could add a restore
  capability, but it is not designed here and was not asked for.
- Revoking a single permission previously granted to an access group
  (independent of group deletion) — no such endpoint exists today
  (`grantAccessGroupPermission` has no corresponding revoke), and adding
  one is explicitly out of scope of the frontend SPEC this backend work
  depends on; REQ-9's partial-index groundwork is laid so a future revoke
  feature does not have to touch the uniqueness constraint again, but
  the revoke endpoint itself is not part of this SPEC.
- A bulk-*unassign* endpoint (removing several groups from one member in
  one request) — only bulk *assignment* was requested; unassignment
  remains one call per group via the existing `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`
  endpoint.
- A bulk-*delete* endpoint (deleting several access groups in one
  request) — only single-group deletion was requested.
- Any change to `assignAccessGroup`'s (single-group) or
  `unassignAccessGroup`'s existing endpoints, permissions, or
  confirmation-token requirements — this SPEC only adds the batch
  sibling (REQ-1) and reuses `unassignAccessGroup`'s deletion-confirmation
  pattern for the new group-delete endpoint (REQ-10/REQ-12) without
  modifying the member-level unassignment flow itself.
- Any change to `GlobalAccessGroup`/`UserGlobalAccessGroup`/
  `GlobalAccessGroupPermission` (the *staff*-scoped, cross-tenant access
  group model) — this SPEC is scoped entirely to the tenant-scoped
  `AccessGroup` model; the global/staff equivalent is a different set of
  entities and endpoints, untouched here.
- The `knowly-app` UI work that consumes these two endpoints — that is
  `knowly-app`'s `tenant-access-group-management` SPEC's concern; this
  SPEC only defines the backend contract that SPEC's REQ-9/10/11 and
  REQ-13/14/15 depend on.
- Flyway migration SQL, entity/repository/service implementation details
  — this SPEC states the requirement for new columns/indexes and the
  filtering/cascade behavior they must support; the actual migration and
  code are PLAN.md/TASKS.md work.
