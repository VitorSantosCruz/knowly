# SPEC — staff-rbac-split

> The what and the why. No technical implementation details.

## Context and motivation

Today `GlobalRole` has exactly one value, `STAFF`, and it means total,
unconditional access: `PermissionAspect` bypasses every permission check
for it, and `TenantService` treats it as an automatic pass for every
staff-gated action (creating tenants, managing any tenant's members,
access groups, and permission grants). There is no way to give a staff
member narrower access than "everything, everywhere."

As ConectaByte's own team grows beyond a small trusted circle, that's no
longer the right default: someone doing first-line support shouldn't
necessarily be able to do everything a platform owner can (e.g. see every
tenant's audit log, or provision new staff accounts). This feature
introduces exactly the same shape of granular, per-action permission
model tenants already have for their own members
(`Permission`/`AccessGroup`/`DirectPermissionGrant`, see `tenancy` SPEC
REQ-18) — but at the global (staff) level instead of the tenant level.

`GlobalRole.STAFF` today's "full, unconditional access" behavior is
preserved exactly as-is, just renamed/split so it's an explicit choice
rather than the only option: `STAFF_ADMIN` keeps that unconditional
access; a new, narrower `STAFF` tier gets only what's explicitly granted.

## User stories

- As a platform owner, I want a `STAFF_ADMIN` tier with unrestricted
  access (today's behavior, unchanged) so that trusted operators keep
  working exactly as they do today.
- As a platform owner, I want a `STAFF` tier whose access is limited to
  explicitly granted actions so that I can bring on support staff without
  giving them the same reach as a platform owner.
- As a `STAFF` user, I want my access to be exactly what was granted to
  me — no more, no less — so that "I can view X" never silently implies
  "I can also edit or delete X."

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** `GlobalRole` shall have exactly two values:
  `STAFF_ADMIN` (unrestricted) and `STAFF` (permission-gated).
- **REQ-2 [Ubiquitous]** `STAFF_ADMIN` shall retain every capability
  `GlobalRole.STAFF` has today — bypassing all permission checks, both
  tenant-scoped (already true today) and the new global ones this
  feature introduces.
- **REQ-3 [Ubiquitous]** A global permission shall be independent per
  action, following the exact same principle already established for
  tenant permissions (`tenancy` SPEC REQ-18): no permission implies any
  other, access is always exactly what was explicitly granted.
- **REQ-4 [Ubiquitous]** A `STAFF` user's global permissions shall be
  grantable both directly (to that specific user) and via a reusable,
  named group of permissions assignable to multiple `STAFF` users —
  mirroring the direct-grant/access-group duality tenants already have.
- **REQ-5 [Event-Driven]** When a `STAFF` user (without the relevant
  permission) attempts a staff-gated action, the system shall reject it
  as a permission failure, the same way a tenant member without the
  relevant permission is rejected today.
- **REQ-6 [Event-Driven]** When any global permission is granted or
  revoked (directly or via a group), the system shall record an audit
  event (actor, action, outcome), per the constitution's audit
  requirements.
- **REQ-7 [Ubiquitous]** Only a `STAFF_ADMIN` shall be able to grant or
  revoke global permissions (directly or via groups) or manage global
  permission groups — a `STAFF` user cannot escalate their own or
  another staff user's global access, regardless of what they've been
  granted.
- **REQ-8 [State-Driven]** While tenant isolation is enforced (Hibernate
  filter), this feature shall not change that guarantee — a `STAFF`
  user's global permissions govern *whether* they can act, never bypass
  *which tenant's data* they see once acting (same split already
  established for `STAFF_ADMIN`/today's `STAFF` in `DECISIONS.md`).

## Non-functional requirements

- Security: this is a privilege-narrowing change, not a privilege-adding
  one — no existing behavior for what is now `STAFF_ADMIN` may regress.
- Security: default-deny — a `STAFF` user with no grants at all can do
  nothing beyond authenticate.
- Observability: every permission grant/revoke and every permission
  denial must emit an audit event, per the constitution.

## Acceptance criteria

- [x] `GlobalRole.STAFF_ADMIN` can do everything today's `GlobalRole.STAFF`
      can (every existing staff-gated action across `tenancy`,
      `article-management`, `conversations`, `dashboard-metrics`).
- [x] A `STAFF` user with zero grants is rejected from every staff-gated
      action.
- [x] A `STAFF` user granted a specific global permission (directly) can
      perform only the action(s) that permission covers, and is rejected
      from every other staff-gated action.
- [x] A `STAFF` user granted a global permission via a group has the same
      access as if it were granted directly; removing them from the
      group (or the group's permission) removes that access.
- [x] A `STAFF` user cannot grant/revoke any global permission, to
      themselves or anyone else, even one they hold themselves.
- [x] Existing `tenancy` tests that assume unconditional staff bypass
      continue to pass unmodified against `STAFF_ADMIN`.

## Out of scope

- Any UI for managing global permissions/groups (staff user management
  screens) — a separate, later roadmap item.
- Any change to tenant-level permissions, `AccessGroup`, or
  `DirectPermissionGrant` — untouched by this feature; this introduces
  parallel, global-scope equivalents, not a change to the tenant ones.

## Decisions (confirmed 2026-07-25)

1. **Migrating existing `STAFF` rows**: the migration maps every existing
   `GlobalRole.STAFF` row (including the `staff-bootstrap-user` account)
   to `STAFF_ADMIN` — no existing account silently loses capability.
2. **Global permission list for day one**: the full set of everything
   currently bypassed via `isStaff()`/`requireStaff`/
   `requireAdminOfTenantOrStaff` gets its own `GlobalPermission` value —
   tenant creation, listing/acting-as any tenant without a membership,
   and managing any tenant's members/access-groups/permission-grants.
   Nothing is left as an unconditional bypass for plain `STAFF` once this
   ships.
3. **Grant structure**: mirrors the tenant-side model 1:1, with `User`
   in place of `TenantMembership` as the anchor — `GlobalPermission` enum,
   `DirectGlobalPermissionGrant(User, GlobalPermission)`,
   `GlobalAccessGroup`, `GlobalAccessGroupPermission`,
   `UserGlobalAccessGroup`.
