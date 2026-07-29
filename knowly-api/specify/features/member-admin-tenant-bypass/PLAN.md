# PLAN — member-admin-tenant-bypass

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **REQ-1(a) bypass lives in `PermissionAspect.checkPermission`, after the
  existing `membership = requireActiveMembership()` fetch, not before
  it** — `requireActiveMembership()` already resolves the caller's
  `TenantMembership` for `TenantContext`'s active tenant only, filtered
  to `isActive()`. Checking `membership.getRole() == MembershipRole.MEMBER_ADMIN`
  on that already-fetched object gives REQ-1/REQ-2/REQ-3/REQ-6 for free
  (active-tenant-only, `isActive()`-filtered) without a second DB round
  trip or a new `TenantContext` field. Concretely:
  ```java
  if (tenantContext.isStaffAdmin()) {
      return joinPoint.proceed();
  }
  RequiresPermission requiresPermission = ...;
  TenantMembership membership = requireActiveMembership();
  if (membership.getRole() == MembershipRole.MEMBER_ADMIN) {
      return joinPoint.proceed();
  }
  if (!permissionService.hasPermission(membership, requiresPermission.value())) {
      throw new PermissionDeniedException();
  }
  return joinPoint.proceed();
  ```
  *Why not a `TenantContext.isMemberAdmin()` mirroring `isStaffAdmin()`
  exactly:* `TenantContext` is a per-request `ThreadLocal` populated by a
  filter from session/JWT claims and today only carries
  `activeTenantId`/`staff`/`staffAdmin` — none of which are tenant-role
  data. Populating it with the caller's per-tenant role would require the
  auth filter to do the same `TenantMembershipRepository` lookup
  `requireActiveMembership()` already performs on every `@RequiresPermission`
  call, i.e. two lookups instead of one for no benefit. Reusing the
  membership already in hand is the smaller, equally-explicit change.

- **REQ-1(b) is already implemented for the tenant-scope check** —
  `TenantService.requireAdminOfTenantOrStaff` (lines 452-476) already
  grants a bypass to a caller with an active `MembershipRole.MEMBER_ADMIN`
  membership for the exact `tenantId` argument. This PLAN does not touch
  that branch. What's missing there is only REQ-4 (self-escalation guard)
  — see below. This corrects the SPEC's context section, which describes
  this method as absent of any `MEMBER_ADMIN` bypass; code inspection at
  PLAN time shows the bypass branch already exists (likely landed with
  `role-model-refinement`). No code change needed for REQ-1(b) itself.
  *(Flagging this as a PLAN-time correction of the SPEC's factual premise,
  not a scope change — the acceptance criteria this SPEC actually needs
  to satisfy, REQ-4/REQ-5 self-escalation, are still unmet and are this
  PLAN's real work.)*

- **`tenantId` in `TenantService.*` methods is a path-variable
  (client-supplied) parameter, not `TenantContext`'s active tenant — and
  that's fine for REQ-2/REQ-3 because `requireAdminOfTenantOrStaff`
  never *trusts* it.** It only uses `tenantId` to look up whether the
  actor holds an active `MEMBER_ADMIN` `TenantMembership` for that
  *specific* tenant id; a caller who is `MEMBER_ADMIN` of tenant A gets
  nothing by passing tenant B's id, because no such membership row
  exists for tenant B. This satisfies REQ-2/REQ-3's intent (scoped
  strictly to a tenant the caller actually administers) without needing
  `TenantContext`, which `TenantService`'s call path does not have
  available in the same way controller path variables do. No change
  needed; documenting so a future reader doesn't mistake "not sourced
  from `TenantContext`" for a REQ-2/REQ-3 violation.

- **REQ-4 self-escalation guard is a single new private helper on
  `TenantService`, called from all five target-bearing methods, not
  duplicated per method.**
  ```java
  private void requireNotSelfTarget(User actor, Long targetUserId) {
      if (targetUserId != null && targetUserId.equals(actor.getId())) {
          throw new PermissionDeniedException();
      }
  }
  ```
  Call sites, each right after resolving the target and before any
  mutation, immediately after the existing `requireAdminOfTenantOrStaff`
  call's surrounding fetch:
  - `addMember`: after `Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email)`,
    call `requireNotSelfTarget(actor, existingUser.map(User::getId).orElse(null))`
    before the membership is created/mutated. A brand-new email can never
    match `actor.getId()`, so this is a no-op for the "invite a new user"
    path and only fires when the target email resolves to the actor's
    own account.
  - `grantPermission`, `revokePermission`, `assignAccessGroup`,
    `unassignAccessGroup`: after the existing
    `tenantMembershipRepository.findById(membershipId).orElseThrow(...)`
    fetch, call `requireNotSelfTarget(actor, membership.getUser().getId())`
    before the grant/revoke/assign/unassign mutation.
  *Why a shared helper instead of a shared wrapper around
  `requireAdminOfTenantOrStaff` itself:* `requireAdminOfTenantOrStaff` is
  called before the target (membership/user) is resolved in every one of
  these methods — the target doesn't exist as a value yet at that call
  site. Folding the self-check into `requireAdminOfTenantOrStaff` would
  require passing an extra, sometimes-null target-id parameter through
  every call site including the ones that have no target user at all
  (`createAccessGroup`, `grantAccessGroupPermission`, `listMembers`,
  etc.), widening a helper used by 9 methods for the sake of 5. A second,
  narrowly-named helper called only where a target user actually exists
  is the smaller change and keeps `requireAdminOfTenantOrStaff`'s
  contract (tenant-scope admin/staff check) uncoupled from REQ-4's
  contract (self-target check).
  *This guard applies to every caller, not just `MEMBER_ADMIN`* — per
  REQ-4 ("no user — regardless of role — may alter their own role or
  grants"), staff acting via the same methods are equally blocked from
  self-targeting through this path. This is in scope: REQ-4 is written
  role-agnostically, and the Tier 3 flag only carves out *not adding an
  equivalent guard to `STAFF_ADMIN`'s own global-scope actions*
  (`GlobalPermissionAspect`/global permission grants), which this PLAN
  does not touch.

- **REQ-5's audit event for denial requires zero new code** —
  `AuditLogAspect.logAudit` already wraps every `@AuditLog`-annotated
  method (all five target-bearing `TenantService` methods already carry
  `@AuditLog`) and already catches `PermissionDeniedException`, recording
  `AuditOutcome.DENIED` with actor/action/resourceType from the existing
  annotation. Throwing `PermissionDeniedException` from
  `requireNotSelfTarget` inside an `@AuditLog`-annotated method is
  therefore automatically recorded as a denial with no new audit
  infrastructure, consistent with how every other permission denial in
  these methods is already logged (`requireAdminOfTenantOrStaff`'s own
  `PermissionDeniedException` goes through the identical path today).

- **No `PermissionAspect` self-escalation enforcement needed for REQ-4** —
  none of the five self-escalation-relevant actions
  (`addMember`/`grantPermission`/`revokePermission`/`assignAccessGroup`/
  `unassignAccessGroup`) are `@RequiresPermission`-gated; they're gated
  exclusively by `requireAdminOfTenantOrStaff`. `PermissionAspect` today
  has no visibility into "is the target of this call the same as the
  actor" because `@RequiresPermission`-gated methods carry no
  target-user convention. REQ-4/NFR's "regardless of which code path"
  language is satisfied by there being exactly one code path where a
  target-bearing mutation can occur (`TenantService`), not by adding a
  no-op check to `PermissionAspect`. If a future `@RequiresPermission`-
  gated action ever takes a target-user parameter, that action needs its
  own explicit self-target check at that time — not preemptively added
  here against a hypothetical.

## Data schema

No schema changes. No new entities, columns, or migrations — this
feature only adds authorization logic against `MembershipRole`/
`TenantMembership` data that already exists.

## API contracts

No new endpoints, no request/response DTO shape changes.
`TenantController`'s existing endpoints for `addMember`/`grantPermission`/
`revokePermission`/`assignAccessGroup`/`unassignAccessGroup` are
unchanged at the HTTP layer; only their underlying `TenantService`
authorization behavior changes (new rejection case), so their existing
status codes are what apply:

| Method | Path (existing, unchanged) | Behavior change |
|---|---|---|
| POST | `/api/tenants/{tenantId}/members` | `addMember`: 403 (via `PermissionDeniedException`) if `email` resolves to the caller's own account |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/permissions` | `grantPermission`: 403 if `membershipId` resolves to the caller's own membership |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/permissions/{permission}` | `revokePermission`: 403 if `membershipId` resolves to the caller's own membership |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` | `assignAccessGroup`: 403 if `membershipId` resolves to the caller's own membership |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` | `unassignAccessGroup`: 403 if `membershipId` resolves to the caller's own membership |

All other `@RequiresPermission`-gated endpoints: no contract change,
only that a `MEMBER_ADMIN` of the active tenant now passes checks it
previously failed (200/201 where it was previously 403).

## Dependencies

None. No `pom.xml` changes — everything needed (`PermissionDeniedException`,
`AuditLog`/`AuditLogAspect`, `MembershipRole`, `TenantMembershipRepository`)
already exists in the codebase.

## Package/file structure

- `br.com.conectabyte.knowly.audit.PermissionAspect` — modify
  `checkPermission` to add the `MEMBER_ADMIN` bypass branch (REQ-1a).
- `br.com.conectabyte.knowly.tenancy.TenantService` — add
  `requireNotSelfTarget(User, Long)` private helper (REQ-4); call it from
  `addMember`, `grantPermission`, `revokePermission`, `assignAccessGroup`,
  `unassignAccessGroup`.
- No changes to `UserProfileService`, `ProfileEditRequestService`,
  `TenantController`, `GlobalPermissionAspect`, `AuditLogAspect`, or any
  DTO — confirmed out of scope by SPEC and by the "zero new code needed"
  findings above.

## Testing strategy

Unit tests (existing style — `PermissionAspectTest`/`TenantServiceTest`,
Mockito-based, no Testcontainers needed since this is pure authorization
logic against mocked repositories):

- `PermissionAspectTest`:
  - `MEMBER_ADMIN` in active tenant, zero explicit grants → proceeds
    (REQ-1a, acceptance criterion 1).
  - Same user, active tenant switched to a tenant where they hold
    `MEMBER`/no membership → rejected exactly as a non-admin (REQ-3,
    acceptance criterion 2).
  - `MEMBER_ADMIN` membership present but `isActive() == false` → no
    bypass, falls through to ordinary permission check (REQ-6,
    acceptance criterion 6).
  - `STAFF_ADMIN` path unchanged — existing test(s) still pass unmodified
    (acceptance criterion 7).
- `TenantServiceTest`:
  - `addMember(actor, tenantId, actor's own email, newRole)` →
    `PermissionDeniedException`, and (via `AuditLogAspectTest` or an
    integration-level assertion on `AuditEventWriter`) a `DENIED` event
    is recorded (REQ-4/REQ-5, acceptance criterion 3).
  - `grantPermission`/`revokePermission`/`assignAccessGroup`/
    `unassignAccessGroup(actor, tenantId, actor's own membershipId, ...)`
    → `PermissionDeniedException` each (REQ-4, acceptance criterion 4).
  - Same four methods with a *different* user's `membershipId`, actor is
    `MEMBER_ADMIN` of that tenant → succeeds (acceptance criterion 5).
- Regression pass: run full `./mvnw test` for `UserProfileServiceTest`/
  `ProfileEditRequestServiceTest` unmodified — confirms REQ-1a's new
  bypass branch, which lives entirely in `PermissionAspect`, does not
  affect those services' independent hardcoded checks (acceptance
  criterion 8).
- No Testcontainers/integration test needed: no schema, no new endpoint,
  no cross-service wiring beyond what unit tests with mocked repositories
  already exercise for these two classes today.
</content>
