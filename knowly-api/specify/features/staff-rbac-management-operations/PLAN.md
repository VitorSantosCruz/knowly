# PLAN — staff-rbac-management-operations

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Coordination note (read first)

**Updated**: `user-role-selection-at-creation/PLAN.md` has since landed
(written concurrently with this PLAN) and already specifies the exact
shared helper this feature needs — `StaffService
.requireCallerIsStaffAdmin()` (no-arg, private, resolves the caller via
the service's own `currentActor()`) and `TenantService
.requireCallerIsAdminOfTenant(User actor, Long tenantId)`. This PLAN
**reuses those two methods verbatim (same name, same signature)** rather
than defining its own "shared authorization helper" — the section below
is corrected accordingly (an earlier draft of this PLAN, written before
that sibling PLAN existed, proposed a slightly different signature for
the staff-scope helper; that has been fixed here to match).

As of writing, neither method's body exists yet in
`StaffService.java`/`TenantService.java` (confirmed by inspection — the
sibling feature's TASKS have not landed). Whichever of the two features'
TASKS.md lands first implements the method bodies; the other's TASKS.md
task for it becomes a no-op "confirm already present, call it" step
rather than a duplicate implementation.

## Architectural decisions

- **No new controllers/services.** Every endpoint in this PLAN is added
  to the existing `StaffController`/`StaffService` (global scope) and
  `TenantController`/`TenantService` (tenant scope) — same pattern as
  every other action added to these classes to date (grant/revoke,
  access groups). A parallel `StaffMembershipController` was considered
  and rejected: nothing about demote/delete/promote/batch-update differs
  enough from the existing permission/access-group endpoints on these
  same classes to justify a new file.
- **Demotion and promotion are separate endpoints per scope, not one
  role-change endpoint with a direction inferred from current vs.
  requested role.** (Tier 2, no exact precedent.) *Why:* this codebase's
  established convention is one endpoint per distinct mutation
  (`grantPermission`/`revokePermission`, `assignAccessGroup`/
  `unassignAccessGroup`) rather than a single endpoint branching on
  direction; demotion and promotion also have entirely different
  authorization/floor-check bodies (REQ-1–6 vs. REQ-24–30), so collapsing
  them into one endpoint would immediately re-split internally anyway.
- **The new hard-delete endpoints are distinct paths from the existing
  soft-deactivation `removeMember`** (SPEC's "Out of scope" explicitly
  defers this path choice to PLAN time). (Tier 2.) `DELETE
  /api/staff/users/{userId}` for staff (no existing endpoint at that
  exact path, so no collision) and `DELETE
  /api/tenants/{tenantId}/members/{membershipId}/hard-delete` for tenant
  members (an explicit, self-documenting suffix, since the bare
  `DELETE .../members/{membershipId}` path is already taken by
  `removeMember`'s soft deactivation and must keep meaning that,
  unchanged, per the SPEC's explicit "Out of scope" line).
- **Both new delete endpoints reuse `DeletionConfirmationTokenService`
  exactly as already wired for member removal / permission revocation /
  access-group unassignment** — new resource types `"staff-user"` and
  `"tenant-member-hard-delete"`, resource id = the target's id, same
  generate-then-consume shape, same sibling
  `.../deletion-confirmation-token` generation endpoint convention. No
  new confirmation mechanism.
- **The batch permission update endpoint also reuses
  `DeletionConfirmationTokenService`**, scoped by a new resource type
  (`"staff-permission-batch"` / `"tenant-permission-batch"`) and resource
  id = the target's id (userId or membershipId) — **not** a hash of the
  submitted diff. *Why (Tier 2):* REQ-6/REQ-13 require the token be
  scoped to "that batch operation" against a resource instance and
  caller, the same way member-removal's token isn't bound to any
  specific field either; binding it to a diff hash would additionally
  require re-generating a token every time the admin tweaks their draft
  batch mid-edit, which the SPEC never asks for and would be a worse UX
  than the existing single-permission tokens have today. REQ-14's no-op
  exemption is checked in the service before ever calling
  `validateAndConsume` — a no-op batch never needs a token requested for
  it in the first place, so no special-case token validation branch is
  needed.
- **Authorization helper reuse (not a new shared class)**: this feature
  calls, rather than redefines, the two helpers `user-role-selection-at-
  creation/PLAN.md` already specifies:
  - `StaffService.requireCallerIsStaffAdmin()` (no-arg, private) —
    throws `PermissionDeniedException` unless the current caller's
    `GlobalRole == STAFF_ADMIN`. Reused by demote-to-STAFF,
    delete-STAFF_ADMIN-target, promote-to-STAFF_ADMIN (REQ-21/REQ-27).
  - `TenantService.requireCallerIsAdminOfTenant(User actor, Long
    tenantId)` (private) — throws unless `actor` is `STAFF_ADMIN`
    globally **or** holds an active `MEMBER_ADMIN` membership in that
    exact tenant. Reused by demote-to-MEMBER, delete-MEMBER_ADMIN-target,
    promote-to-MEMBER_ADMIN (REQ-22/REQ-28). This is deliberately **not**
    the existing `requireAdminOfTenantOrStaff` (which also accepts a
    `STAFF` user holding a specific `GlobalPermission`) — SPEC
    REQ-21/22/27/28 explicitly reject that path for admin-tier targets,
    so this is a narrower check, already established as its own method
    by the sibling PLAN.
  Both stay on the service classes (not a new shared class) since each
  only reads that service's own repositories, per the sibling PLAN's own
  "why one method per scope" reasoning, which this PLAN does not
  re-derive.
- **Last-admin floor check is enforced with a pessimistic row lock, not
  a plain `COUNT` read-then-write**, to close the TOCTOU window the SPEC
  explicitly calls out (Non-functional requirements). New repository
  methods:
  - `UserRepository.findByGlobalRoleForUpdate(GlobalRole role)` — `@Lock
    (LockModeType.PESSIMISTIC_WRITE)` over `SELECT u FROM User u WHERE
    u.globalRole = :role`. Demote/delete-`STAFF_ADMIN` first calls this
    (locking every current `STAFF_ADMIN` row, including the target's),
    then counts rows with an id different from the target's — if zero,
    reject; only after that check does it perform the mutation, all
    inside the same `@Transactional` method, so a second concurrent
    demote/delete request against a different "last remaining" admin
    blocks on the same row lock until the first transaction commits or
    rolls back, and re-reads a now-accurate count.
  - `TenantMembershipRepository.findByTenantIdAndRoleAndActiveTrueForUpdate(Long
    tenantId, MembershipRole role)` — same shape, scoped to one tenant,
    used by demote/delete-`MEMBER_ADMIN`.
  Promotion never calls either lock/count path (REQ-26) — confirmed no
  behavior change needed there.
- **REQ-17/18/19 (reject grant/revoke/assign against an admin-tier
  target)** is implemented as an early guard at the top of the four
  existing methods (`StaffService.grantPermission`,
  `TenantService.grantPermission`/`revokePermission`/`assignAccessGroup`,
  plus `StaffService`'s access-group assign) — `if (target role is
  STAFF_ADMIN/MEMBER_ADMIN) throw PermissionDeniedException()` before any
  repository mutation. This reuses `PermissionDeniedException` (already
  thrown by every other authorization failure in these classes) rather
  than inventing a new exception type — a 403 either way, and the SPEC
  doesn't ask for a distinguishable error message here.
- **Batch update DTO is a full-replacement set, not an add/remove diff**
  (Tier 2 — SPEC REQ-12 allows either shape). *Why:* "accepts the full
  desired set" is REQ-12's primary phrasing, it's simpler for the
  redesigned UI (which already has to render the full current set to let
  someone edit it) to submit back exactly what it's showing rather than
  compute a diff client-side, and it makes REQ-14's no-op check a single
  set-equality comparison server-side instead of requiring the client to
  self-report "I changed nothing." The service computes the actual
  added/removed sets itself (current directly-granted set vs. submitted
  set) for REQ-15's per-permission audit events and for REQ-13's
  any-change check.
- **AppSec addition (2026-08-02): explicit base permission gate for the
  non-admin-target case on every new endpoint** — the architectural
  decisions above only specify the *admin-tier-target* guards
  (`requireCallerIsStaffAdmin`/`requireCallerIsAdminOfTenant`,
  REQ-21/22/27/28) and the floor-check guards; they do not state what
  gates a demote/promote/hard-delete/batch call whose *target* is a
  plain `STAFF`/`MEMBER` (REQ-23: "continues to follow whatever
  authorization already governs that action today"). Left unstated,
  TASKS.md could implement these endpoints reachable by any
  authenticated caller for a non-admin target with no permission check
  beyond the self-target guard. Resolved here, reusing only
  already-established gates (no new permission concept):
  - `StaffService.demoteStaffUser`/`promoteStaffUser`: target is always
    `STAFF_ADMIN` either way (demote's source, promote's destination),
    so `requireCallerIsStaffAdmin()` alone is the complete gate — no
    additional annotation needed (matches the architectural decision
    above).
  - `StaffService.deleteStaffUser`: gains
    `@RequiresGlobalPermission(GlobalPermission.STAFF_USER_DELETE)`
    (the placeholder `permission-granularity-model` reserves for this
    exact use, per that PLAN's REQ-8 note) **and** the existing
    `enforceStaffCeiling(user.getGlobalRole())` call, same as every
    other `StaffService` mutation. Because `enforceStaffCeiling` already
    rejects any `STAFF` actor against *any* `STAFF`/`STAFF_ADMIN`
    target, this combination means deletion of a non-admin `STAFF`
    target is — same as today's other staff mutations — reachable only
    by a `STAFF_ADMIN` caller in practice; `STAFF_USER_DELETE` granted to
    a `STAFF` user has no live effect until/unless `enforceStaffCeiling`
    itself is ever revisited (out of scope here, flagging so it isn't
    read as a bug).
  - `StaffService.batchUpdatePermissions`: gains
    `@RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)`
    plus `enforceStaffCeiling(user.getGlobalRole())`, mirroring
    `grantPermission`/`revokePermission` exactly (REQ-16 already rejects
    admin-tier targets outright, so this is the non-admin-target gate).
  - `TenantService.hardDeleteMember`: gains
    `requireAdminOfTenantOrStaff(actor, tenantId,
    GlobalPermission.TENANT_MEMBER_DELETE)` for the non-admin-target
    (`MEMBER`) case, mirroring `removeMember`'s existing gate
    (post-`permission-granularity-model` migration constant) — checked
    before the `MEMBER_ADMIN`-target branch, which instead requires
    `requireCallerIsAdminOfTenant` per REQ-22.
  - `TenantService.demoteMember`/`promoteMember`: target is always
    `MEMBER_ADMIN` either way, so `requireCallerIsAdminOfTenant` alone is
    the complete gate, same reasoning as the staff-scope case above.
  - `TenantService.batchUpdatePermissions` (tenant-scoped): gains
    `requireAdminOfTenantOrStaff(actor, tenantId,
    GlobalPermission.TENANT_PERMISSION_GRANT_CREATE)`, mirroring
    `grantPermission`'s existing gate (REQ-16 already rejects
    `MEMBER_ADMIN` targets outright).
- Every new mutation gets `@AuditLog` following this codebase's existing
  action-name convention: `staff.user.demote`, `staff.user.delete`,
  `staff.user.promote`, `staff.permission.batch_update`,
  `tenant.member.demote`, `tenant.member.hard_delete`,
  `tenant.member.promote`, `tenant.permission.batch_update`.

## Amendment (2026-08-02): `isLastAdminOfType` on the detail DTOs

`staff-members-management-redesign` (frontend) needs to pre-emptively
disable the demote/delete buttons for the last admin of a type, with an
explanation, **before** any click (its REQ-5/6/9/10) — trying the call
and surfacing the 409 is not sufficient. That requires a boolean the
detail-fetch response doesn't currently carry. Resolved here rather than
left as a frontend-side gap, since this feature already owns the
canonical last-admin check the field must mirror:

- `StaffUserDetailDto` (`knowly-api/src/main/java/br/com/conectabyte/knowly/tenancy/dto/StaffUserDetailDto.java`,
  owned by `staff-rbac-split`, amended here) gains one field:
  `isLastAdminOfType: boolean`, becoming `StaffUserDetailDto(Long userId,
  String email, GlobalRole globalRole, List<GlobalPermission>
  directPermissions, List<GlobalAccessGroupDto> accessGroups,
  List<GlobalPermission> effectivePermissions, boolean
  isLastAdminOfType)`. **Note:** the current record has no `globalRole`
  field at all (confirmed by inspection) — the frontend cannot render
  REQ-3/4's role-conditional view without it either, so this amendment
  adds `globalRole` alongside `isLastAdminOfType` as the same
  minimal-viable fix rather than leaving that a second undiscovered gap.
- `MemberDetailDto` already carries `role: MembershipRole`, so it only
  gains `isLastAdminOfType: boolean` (last field), becoming
  `MemberDetailDto(Long membershipId, Long userId, String email,
  MembershipRole role, List<Permission> directPermissions,
  List<AccessGroupDto> accessGroups, List<Permission>
  effectivePermissions, boolean isLastAdminOfType)`.
- **Computation reuses this feature's existing count query, not the
  locking variant** (Tier 2 — no exact precedent for a read-only reuse of
  a lock-backed check). *Why:* `getStaffUserDetail`/`getMemberDetail` are
  plain reads with no mutation to protect from a TOCTOU race — taking a
  `PESSIMISTIC_WRITE` lock here would serialize every detail-page view
  against every demote/delete transaction for no correctness benefit,
  only contention. `isLastAdminOfType` is computed as:
  - `StaffService.getStaffUserDetail`: `user.getGlobalRole() ==
    GlobalRole.STAFF_ADMIN && userRepository.countByGlobalRoleIn(List.of(
    GlobalRole.STAFF_ADMIN)) == 1` — reusing the existing
    `countByGlobalRoleIn` method (no new repository method needed); for
    a `STAFF` target the field is always `false` (REQ-7d/REQ-11 of the
    frontend SPEC — the floor rule never applies to non-admin roles).
  - `TenantService.getMemberDetail`: `membership.getRole() ==
    MembershipRole.MEMBER_ADMIN &&
    tenantMembershipRepository.countByTenantIdAndActive(tenantId, true)`
    is insufficient (that counts all active members, not
    `MEMBER_ADMIN`s specifically) — this amendment adds a new read-only
    repository method `TenantMembershipRepository
    .countByTenantIdAndRoleAndActiveTrue(Long tenantId, MembershipRole
    role)` (the non-locking sibling of the existing
    `findByTenantIdAndRoleAndActiveTrueForUpdate`, same predicate, `COUNT`
    instead of row fetch) and computes `membership.getRole() ==
    MembershipRole.MEMBER_ADMIN &&
    tenantMembershipRepository.countByTenantIdAndRoleAndActiveTrue(
    tenantId, MembershipRole.MEMBER_ADMIN) == 1`; `false` for a `MEMBER`
    target.
  - Both computations are best-effort/advisory for UI purposes only — a
    concurrent demote/delete between this read and the user's next click
    can still make the number stale by the time they act, which is fine
    because the mutation endpoints' own pessimistic-locked check (above)
    remains the actual enforcement boundary; this field never substitutes
    for that check server-side, matching the SPEC's existing "UI-only
    guidance, backend independently enforces" security posture.
- No `@AuditLog`/authorization change — same existing gates on
  `getStaffUserDetail`/`getMemberDetail` apply unchanged; this is a
  response-shape addition only.

## Data schema

No migration. `GlobalRole`/`MembershipRole` enums already contain every
value this SPEC needs (`STAFF`, `STAFF_ADMIN`, `MEMBER`, `MEMBER_ADMIN`);
hard delete removes existing `User`/`TenantMembership` rows (and their
dependent `DirectGlobalPermissionGrant`/`DirectPermissionGrant`/
`UserGlobalAccessGroup`/`UserAccessGroup` rows via
`ON DELETE CASCADE`, already present per the `staff-rbac-split` and
original tenancy migrations — confirmed by inspection of the existing FK
definitions referenced from those tables). No schema change needed for
the batch endpoint either — it operates on the existing
`DirectGlobalPermissionGrant`/`DirectPermissionGrant` tables.

## API contracts

New endpoints on `StaffController` (`/api/staff`):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/staff/users/{userId}/demote` | — | — | 200; 403 (not `STAFF_ADMIN` caller); 409 (last `STAFF_ADMIN`, REQ-8-style error) |
| POST | `/api/staff/users/{userId}/promote` | — | — | 200; 403 (caller not `STAFF_ADMIN`, or self-target) |
| POST | `/api/staff/users/{userId}/deletion-confirmation-token` | — | `DeletionConfirmationTokenDto` | 200; 403 |
| DELETE | `/api/staff/users/{userId}` | `DeleteConfirmationRequestDto` (optional body, `word`) | — | 200; 403; 409 (last `STAFF_ADMIN`); generic deletion-confirmation rejection (per `deletion-confirmation-token` SPEC REQ-7) |
| PUT | `/api/staff/users/{userId}/permissions/batch` | `BatchPermissionUpdateRequestDto(Set<GlobalPermission> permissions, String word)` | — | 200; 403 (target is `STAFF_ADMIN`); generic deletion-confirmation rejection if changed and no/invalid word |
| POST | `/api/staff/users/{userId}/permissions/batch/deletion-confirmation-token` | — | `DeletionConfirmationTokenDto` | 200; 403 |

New endpoints on `TenantController` (`/api/tenants`):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/tenants/{tenantId}/members/{membershipId}/demote` | — | — | 200; 403; 409 (last `MEMBER_ADMIN` in tenant) |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/promote` | — | — | 200; 403; self-target rejected |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/hard-delete/deletion-confirmation-token` | — | `DeletionConfirmationTokenDto` | 200; 403 |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/hard-delete` | `DeleteConfirmationRequestDto` | — | 200; 403; 409 (last `MEMBER_ADMIN`); generic deletion-confirmation rejection |
| PUT | `/api/tenants/{tenantId}/members/{membershipId}/permissions/batch` | `BatchPermissionUpdateRequestDto(Set<Permission> permissions, String word)` | — | 200; 403 (target is `MEMBER_ADMIN`); generic deletion-confirmation rejection if changed and no/invalid word |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/permissions/batch/deletion-confirmation-token` | — | `DeletionConfirmationTokenDto` | 200; 403 |

All existing endpoints unchanged in path/shape; `grantPermission`,
`revokePermission`, `assignAccessGroup` (both scopes) gain the new
`STAFF_ADMIN`/`MEMBER_ADMIN`-target rejection (REQ-17/18/19) as an
in-method guard, no signature change.

## Dependencies

None new.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: `demoteStaffUser`, `promoteStaffUser`, `generateStaffUserDeletionConfirmationToken`, `deleteStaffUser`, `generateBatchPermissionUpdateDeletionConfirmationToken`, `batchUpdatePermissions`, `requireCallerIsStaffAdmin` helper, `STAFF_ADMIN`/`MEMBER_ADMIN`-target guards added to existing `grantPermission`/`assignAccessGroup`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java` (modify: new endpoints per API contract table)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `demoteMember`, `promoteMember`, `generateMemberHardDeletionConfirmationToken`, `hardDeleteMember`, `generateBatchPermissionUpdateDeletionConfirmationToken`, `batchUpdatePermissions`, `requireCallerIsAdminOfTenant` helper, guards added to existing `grantPermission`/`revokePermission`/`assignAccessGroup`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantController.java` (modify: new endpoints per API contract table)
- `src/main/java/br/com/conectabyte/knowly/auth/UserRepository.java` (modify: add `findByGlobalRoleForUpdate`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantMembershipRepository.java` (modify: add `findByTenantIdAndRoleAndActiveTrueForUpdate`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/BatchPermissionUpdateRequestDto.java` (new, global-scope variant, `Set<GlobalPermission>` + `word`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/BatchTenantPermissionUpdateRequestDto.java` (new, tenant-scope variant, `Set<Permission>` + `word`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/exception/LastAdminRemainingException.java` (new, maps to 409, message per REQ-2/4/8/10's "clear, specific error")
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/StaffUserDetailDto.java` (modify: add `globalRole`, `isLastAdminOfType`, per amendment above)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/MemberDetailDto.java` (modify: add `isLastAdminOfType`, per amendment above)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantMembershipRepository.java` (modify: add `countByTenantIdAndRoleAndActiveTrue`, non-locking sibling of `findByTenantIdAndRoleAndActiveTrueForUpdate`)

## Testing strategy

TDAD, red-then-green, integration tests (`@SpringBootTest`, Testcontainers) mirroring `TenantManagementIntegrationTest`/existing `StaffService`-family tests:

- **Demotion**: `STAFF_ADMIN`→`STAFF` succeeds when ≥2 `STAFF_ADMIN`s exist; rejected (409) when target is the last one; self-demotion rejected regardless of count; `STAFF` caller (even with a granted permission) rejected from demoting a `STAFF_ADMIN`. Same 4 cases for `MEMBER_ADMIN`→`MEMBER`, plus cross-tenant isolation (a `MEMBER_ADMIN` of tenant A cannot demote a member of tenant B).
- **Concurrency (last-admin floor)**: two concurrent demote/delete requests against two different `STAFF_ADMIN`s when exactly two exist — using two threads/`CompletableFuture`s racing against the pessimistic lock — assert exactly one succeeds and one gets 409, never both succeeding (which would leave zero admins). Same for `MEMBER_ADMIN` at tenant scope. This is the one test class allowed to intentionally introduce a race (via a `CountDownLatch` to align both transactions' entry into the locked section) — documented inline since most tests in this codebase are single-threaded by convention.
- **Deletion**: staff/tenant-member hard delete succeeds with a valid token; rejected without one/with a wrong one (reusing `deletion-confirmation-token`'s existing generic-rejection assertions); same last-admin floor and self-deletion cases as demotion; deleting a non-admin target is never blocked by the floor even as the tenant's only `MEMBER`; confirm the row and its dependent grants/group-memberships are actually gone after a successful delete (not just deactivated).
- **Batch update**: full-set update with additions only / removals only / both requires and consumes a valid token, rejected without one; a no-op batch (identical set) succeeds with no token required and doesn't call `validateAndConsume` (verified via a spy/no-Redis-interaction assertion or equivalent); one `AuditEvent` per added and per removed permission; targeting a `STAFF_ADMIN`/`MEMBER_ADMIN` is rejected outright regardless of token.
- **Admin-target grant/revoke/assign rejection**: existing single-permission grant/revoke and access-group assign endpoints (both scopes), called against a `STAFF_ADMIN`/`MEMBER_ADMIN` target, now return 403 where they previously silently no-op'd — regression-style tests confirming no `DirectPermissionGrant`/`DirectGlobalPermissionGrant`/`UserAccessGroup`/`UserGlobalAccessGroup` row is created.
- **Promotion**: `STAFF`→`STAFF_ADMIN` and `MEMBER`→`MEMBER_ADMIN` succeed regardless of existing admin count (explicit test with e.g. 5 existing admins, to prove no ceiling exists); `STAFF`/`MEMBER` caller (with or without any granted permission) rejected; self-promotion rejected; no `DeletionConfirmationTokenService` interaction at all (asserted the same spy/no-interaction way as the batch no-op case); audit event recorded.
- Unit tests: `requireCallerIsStaffAdmin`/`requireCallerIsAdminOfTenant` in isolation (mock repositories) covering the STAFF_ADMIN-bypass / matching-tenant-MEMBER_ADMIN / wrong-tenant-MEMBER_ADMIN / plain-STAFF-or-MEMBER-with-permission-still-rejected cases, since these are the two most safety-critical net-new checks in this feature.
- **`isLastAdminOfType` (amendment)**: `getStaffUserDetail` returns
  `isLastAdminOfType == true` for the sole `STAFF_ADMIN` and `false` once
  a second `STAFF_ADMIN` exists, `false` for any `STAFF` target
  regardless of count; `getMemberDetail` mirrors this per-tenant for
  `MEMBER_ADMIN`/`MEMBER`, including a cross-tenant case (a lone
  `MEMBER_ADMIN` in tenant A does not make tenant B's `MEMBER_ADMIN`
  read as last). Also asserts the new `countByTenantIdAndRoleAndActiveTrue`
  query only counts active memberships of the given role/tenant, not all
  active members.
</content>
