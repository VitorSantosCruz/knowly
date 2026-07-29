# SPEC — member-admin-tenant-bypass

## Context and motivation

`MembershipRole.MEMBER_ADMIN` is documented and intended to be the
tenant-scoped analog of `GlobalRole.STAFF_ADMIN`: an unrestricted owner
of everything within their own scope (tenant for `MEMBER_ADMIN`, whole
system for `STAFF_ADMIN`). In practice this symmetry does not exist in
code today.

`PermissionAspect.checkPermission`
(`knowly-api/src/main/java/br/com/conectabyte/knowly/audit/PermissionAspect.java:38-56`)
has an automatic bypass keyed on `tenantContext.isStaffAdmin()` — a
`STAFF_ADMIN` skips every `@RequiresPermission` check unconditionally.
There is no equivalent `isMemberAdmin()` bypass. A `MEMBER_ADMIN` with
zero explicit `Permission`/`AccessGroup` grants therefore fails
`@RequiresPermission` checks exactly like a bare `MEMBER` would — the
tenant-scoped "unrestricted admin" tier is unrestricted in name only.

The only places `MEMBER_ADMIN` currently receives elevated treatment are
two hardcoded, feature-specific bypasses:
`UserProfileService.isMemberAdminOfSharedTenant`
(`knowly-api/src/main/java/br/com/conectabyte/knowly/identity/UserProfileService.java:167-182`,
used for profile view/edit authorization) and
`ProfileEditRequestService.applicableEditRightHolders`
(`knowly-api/src/main/java/br/com/conectabyte/knowly/identity/ProfileEditRequestService.java:168-186`,
used to route profile-edit-request approvals to, among others, every
`MEMBER_ADMIN` of the requester's tenant). Every other tenant-scoped
`@RequiresPermission`-gated action is not covered by any bypass —
`MEMBER_ADMIN` must hold the exact permission like anyone else.

The product owner has confirmed the intended role model is symmetric:
`STAFF_ADMIN` = unrestricted owner of the whole system (global scope),
`MEMBER_ADMIN` = unrestricted owner of their own tenant (tenant scope).
`STAFF`/`MEMBER` are specialist roles that only get what's explicitly
granted via permissions. This feature closes the gap: `MEMBER_ADMIN`
gets a blanket bypass of tenant-scoped permission checks, scoped
strictly to their own tenant, with a new anti-self-escalation guard
introduced to prevent role/permission self-mutation.

This SPEC is scoped narrowly to that bypass and guard. `role-model-refinement`
(`knowly-api/specify/features/role-model-refinement/SPEC.md`) already
renamed `ADMIN` → `MEMBER_ADMIN` and added the `STAFF` management
ceiling on the global side; it explicitly did not implement this
tenant-scope bypass. This SPEC does not reopen or duplicate that work.

## User stories

- As a `MEMBER_ADMIN`, I want to be treated as an unrestricted admin of
  my own tenant — the same way `STAFF_ADMIN` is unrestricted globally —
  so that I don't need explicit `Permission`/`AccessGroup` grants to
  perform tenant-scoped actions that are conceptually "mine to do" as
  the tenant's admin.
- As a platform owner, I want a `MEMBER_ADMIN`'s bypass strictly
  confined to their own tenant, so that a `MEMBER_ADMIN` of tenant A
  gets zero elevated access to tenant B's data or actions.
- As a platform owner, I want `MEMBER_ADMIN` to be unable to alter their
  own role or permission grants — not even through the new bypass — so this
  enhanced capability cannot be used as a self-escalation or role-laundering
  path.

## Requirements (EARS/GEARS)

- **REQ-1 [State-Driven]** While the acting user holds `MembershipRole.MEMBER_ADMIN`
  in the active tenant, the system shall grant that user an automatic
  bypass of tenant-scoped authorization checks for that tenant, applicable
  to both:
  - `@RequiresPermission`-gated actions checked via `PermissionAspect.checkPermission`, equivalent in effect to `isStaffAdmin()`'s existing bypass, AND
  - Tenant-scoped role/permission mutations authorized via `TenantService.requireAdminOfTenantOrStaff` (methods like `grantPermission`, `revokePermission`, `assignAccessGroup`, `unassignAccessGroup`, `addMember` with role param).
- **REQ-2 [Ubiquitous]** The `MEMBER_ADMIN` bypass shall be scoped
  exclusively to the tenant in which the acting user holds an active
  `MEMBER_ADMIN` membership — it shall confer no bypass, elevated
  access, or visibility into any other tenant, including one where the
  same user holds a plain `MEMBER` membership or no membership at all.
- **REQ-3 [Unwanted Behavior]** If a user holds `MEMBER_ADMIN` in tenant
  A and attempts a tenant-scoped action while tenant B is the active
  tenant, then the system shall evaluate that action under tenant B's
  own permission rules (i.e. as if the user held no `MEMBER_ADMIN`
  bypass at all), never under tenant A's admin status.
- **REQ-4 [Ubiquitous]** The system shall enforce a new guard,
  applicable to `MEMBER_ADMIN` actions under REQ-1: no user — regardless
  of role — may alter their own role or their own permission/access-group
  grants, even through the bypass introduced by REQ-1. The `MEMBER_ADMIN`
  bypass shall not extend to actions where the acting user and the target
  of a role/permission-grant change are the same user.
- **REQ-5 [Event-Driven]** When a `MEMBER_ADMIN`'s attempt to alter their
  own role or own permission/access-group grants is rejected under
  REQ-4, the system shall record an audit event (actor, action, outcome
  = denied), consistent with how other permission denials are logged.
- **REQ-6 [State-Driven]** While an inactive (soft-deactivated or
  otherwise non-active) `TenantMembership` is the only `MEMBER_ADMIN`
  record a user holds for a tenant, the system shall not grant the REQ-1
  bypass for that tenant — the bypass requires an active `MEMBER_ADMIN`
  membership, mirroring `PermissionAspect.requireActiveMembership`'s
  existing `isActive()` filter for the ordinary permission-check path.

## Non-functional requirements

- Security: the REQ-1 bypass must be implemented as a positive,
  explicit check (analogous to `isStaffAdmin()`) in the authorization
  code paths it governs — both inside `PermissionAspect` (for
  `@RequiresPermission`-gated actions) and inside `TenantService`'s
  `requireAdminOfTenantOrStaff` method (for role/permission-grant
  mutations) — applied in a single, auditable location per code path,
  never duplicated ad hoc across services, to avoid the same kind of
  asymmetry this SPEC is fixing from recurring piecemeal.
- Security: the REQ-2/REQ-3 tenant-scoping must be derived from
  `TenantContext`'s active-tenant state (the same source of truth
  `PermissionAspect.requireActiveMembership` already uses), never from a
  client-supplied tenant id or any other unverified input.
- Security: REQ-4's self-escalation carve-out must be enforced
  regardless of which code path grants elevated access — a
  `MEMBER_ADMIN` must never be able to reach their own role/permission
  record through any bypass this feature introduces, whether in
  `PermissionAspect` or `TenantService`.
- Observability: per REQ-5; this reuses the existing audit-event
  mechanism for permission denials, no new audit infrastructure.

## Acceptance criteria

- [ ] A `MEMBER_ADMIN` with zero explicit `Permission`/`AccessGroup`
      grants successfully performs a tenant-scoped, `@RequiresPermission`-
      gated action in their own active tenant that would otherwise
      require an explicit grant.
- [ ] The same `MEMBER_ADMIN`, with their active tenant switched to a
      different tenant where they hold no `MEMBER_ADMIN` membership
      (either a plain `MEMBER` membership or no membership at all), is
      rejected on the identical `@RequiresPermission`-gated action for
      that other tenant, exactly as a non-admin caller with no grant
      would be.
- [ ] A `MEMBER_ADMIN` attempting to change their own `MembershipRole`
      via `TenantService.addMember(self, newRole)` or similar is rejected,
      and an audit event with outcome = denied is recorded.
- [ ] A `MEMBER_ADMIN` attempting to grant or revoke their own
      `Permission` or `AccessGroup` via `TenantService.grantPermission`,
      `revokePermission`, `assignAccessGroup`, or `unassignAccessGroup`
      (with themselves as target) is rejected, and an audit event with
      outcome = denied is recorded.
- [ ] A `MEMBER_ADMIN` acting on another user's role/permission grants
      within their own tenant (via the same `TenantService` methods, with a
      different user as target) succeeds (the bypass applies to on-others
      actions, only the self-target is excluded).
- [ ] A user whose only `MEMBER_ADMIN` `TenantMembership` record for a
      tenant is inactive does not receive the bypass for that tenant.
- [ ] `STAFF_ADMIN`'s existing global bypass behavior is unchanged.
- [ ] All existing tests for `PermissionAspect`, `TenantService`,
      `UserProfileService`, and `ProfileEditRequestService` continue to
      pass or are deliberately, visibly updated to reflect the new bypass
      (not silently broken).

## Out of scope

- **`GlobalRole`/`STAFF` logic** — `isStaffAdmin()`'s existing behavior,
  the `STAFF` management ceiling from `role-model-refinement`, and any
  global-scope permission logic are untouched by this SPEC.
- **The `MembershipRole` rename or any further role-model restructuring**
  — already completed by `role-model-refinement`; not reopened here.
- **Any UI change** — this is a backend-only SPEC.
- **Removing or redesigning the hardcoded bypasses in
  `UserProfileService.isMemberAdminOfSharedTenant` or
  `ProfileEditRequestService.applicableEditRightHolders`** — see "Open
  question" below; this SPEC does not assert they must be
  simplified/removed as part of this change, only that the new general
  bypass must not conflict with or weaken their existing behavior.
- **Cross-tenant `MEMBER_ADMIN` capabilities of any kind** — explicitly
  rejected by REQ-2/REQ-3; a `MEMBER_ADMIN` of tenant A gets nothing in
  tenant B through this feature.
- **Any change to how `TenantContext`'s active tenant is selected/
  switched** — this feature only reads that existing state, it does not
  change `TenantService`'s switching logic.
- **Adding a self-escalation guard to `STAFF_ADMIN`** — this SPEC
  introduces the guard for `MEMBER_ADMIN` only; see "Tier 3 flag" below
  regarding the current absence of such a guard for `STAFF_ADMIN`.

## Open question (needs product owner decision before PLAN)

`UserProfileService`/`ProfileEditRequestService`'s hardcoded
`MEMBER_ADMIN` checks predate this general bypass and target a slightly
different question ("does the caller hold `MEMBER_ADMIN` in *any* tenant
the target shares with them," independent of the caller's *currently
active* tenant) than REQ-1's aspect-level bypass (scoped to the
*active* tenant only, per `PermissionAspect.requireActiveMembership`).
It is not yet decided whether:

(a) these two services should be left exactly as they are (their logic
    is bespoke service-layer authorization per `DECISIONS.md`'s
    `identity-profile-model` entry, not `@RequiresPermission`-gated, so
    REQ-1's aspect bypass does not automatically touch them at all), or

(b) they should be revisited in a follow-up to align their notion of
    "is this caller a `MEMBER_ADMIN` for this purpose" with the
    active-tenant-scoped model this SPEC introduces, for consistency.

This SPEC assumes (a) — no change to those two services — since they
are outside the `@RequiresPermission`/`PermissionAspect` code path this
SPEC governs, and flags it explicitly rather than asserting they should
be superseded. Confirm before PLAN.md if (b) is actually wanted.

## Tier 3 flag

**Absence of self-escalation guard on `STAFF_ADMIN` today:** The
architect's code inspection confirmed that `STAFF_ADMIN` currently has
**no** self-escalation guard — a `STAFF_ADMIN` can today grant/revoke
their own global permissions or alter their own `GlobalRole`, which is a
genuine security gap. This SPEC introduces the guard for `MEMBER_ADMIN`,
resolving the same gap at the tenant scope. **The decision being made
here:** this feature introduces the guard for `MEMBER_ADMIN` only,
leaving `STAFF_ADMIN`'s lack of such a guard as a separate, future
security fix (likely `staff-rbac-split` follow-up or a new backlog item).
This is the narrowest scope that delivers the stated goal (make
`MEMBER_ADMIN` symmetric with `STAFF_ADMIN`'s *current* unrestricted
bypass, minus the new self-escalation carve-out), but it does leave
`STAFF_ADMIN` with a known security gap. If the product owner wants to
close the `STAFF_ADMIN` gap as part of this feature, instead of as a
separate follow-up, this SPEC should be expanded to introduce REQ-4's
guard for `STAFF_ADMIN` as well — flagging this as a judgment call
rather than silently assuming out-of-scope.
