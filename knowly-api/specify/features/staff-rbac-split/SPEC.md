# SPEC — staff-rbac-split

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-02**: **REQ-3 amended — this is a confirmed reversal of a
  prior decision, not a reinterpretation.** REQ-3 previously mirrored
  `tenancy` SPEC's original REQ-18 verbatim ("no permission implies any
  other"). The product owner confirmed the same reversal applies at the
  global/staff scope as at the tenant scope: view/list remains
  independent, but edit and delete now each additionally require the
  caller to also hold view on that same resource; create remains fully
  independent. See `tenancy` SPEC's own 2026-08-02 changelog entry and
  the new `permission-granularity-model` SPEC (the canonical source of
  this rule) for full detail. REQ-1, REQ-2, REQ-4 through REQ-9, all
  prior acceptance criteria, and the existing "Out of scope"/"Decisions"
  sections are unchanged — nothing else pre-existing was reinterpreted
  or removed.
- **2026-08-01**: Added REQ-9 and its acceptance criterion. Fixes a
  consumer-reported gap surfaced while implementing `knowly-app`'s
  `navigation-menu` feature (REQ-10/REQ-11 there): `GET
  /api/staff/permissions` returns an empty `permissions` list identically
  for a `STAFF` account holding zero global grants and for a plain
  `MEMBER` — there is no field letting a caller tell "this is a staff
  account with no grants" apart from "this is not a staff account at
  all." REQ-1 through REQ-8, all prior acceptance criteria, and the
  existing "Out of scope"/"Decisions" sections are unchanged — nothing
  pre-existing was reinterpreted or removed.

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

**2026-08-01 addition:** `GET /api/staff/permissions` is the endpoint
`knowly-app`'s `navigation-menu` feature uses to decide what a staff
session can see. That SPEC needs to tell apart a `STAFF` account with an
atypical, real tenant membership (who still needs a "leave to staff
area" affordance) from a plain `MEMBER` with one membership (who doesn't)
— see `knowly-app/specify/features/navigation-menu/PLAN.md`'s flagged
gap. Today's response (just a permission list) cannot make that
distinction when the list is empty in both cases, since holding zero
global grants is a normal, valid `STAFF` state (`STAFF_ADMIN` bypasses
this entirely, per REQ-2, so it's unaffected). REQ-9 closes this gap.

## User stories

- As a platform owner, I want a `STAFF_ADMIN` tier with unrestricted
  access (today's behavior, unchanged) so that trusted operators keep
  working exactly as they do today.
- As a platform owner, I want a `STAFF` tier whose access is limited to
  explicitly granted actions so that I can bring on support staff without
  giving them the same reach as a platform owner.
- As a `STAFF` user, I want my access to be exactly what was granted to
  me — no more, no less, except that being able to edit or delete a
  resource always implies I can also see it — so that "I can view X"
  never silently implies "I can also edit or delete X," but "I can edit
  X" does require that I can already view X.
- As a frontend consuming `GET /api/staff/permissions`, I want to know
  whether the caller is a staff account at all, independent of whether
  they currently hold any granted global permission, so that I can show
  staff-only UI (e.g. a "leave to staff area" action) to a staff account
  with zero grants without misidentifying a plain tenant member the same
  way.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** `GlobalRole` shall have exactly two values:
  `STAFF_ADMIN` (unrestricted) and `STAFF` (permission-gated).
- **REQ-2 [Ubiquitous]** `STAFF_ADMIN` shall retain every capability
  `GlobalRole.STAFF` has today — bypassing all permission checks, both
  tenant-scoped (already true today) and the new global ones this
  feature introduces.
- **REQ-3 [Ubiquitous]** *(Amended 2026-08-02 — see Changelog above;
  supersedes this requirement's original "no permission implies any
  other" wording.)* A global permission shall be independent per action,
  following the same principle established for tenant permissions
  (`tenancy` SPEC REQ-18, itself amended 2026-08-02) — **except** that
  edit and delete global permissions each additionally require the
  caller to also hold the corresponding view permission for that same
  resource; view/list and create remain fully independent. The
  canonical, authoritative statement of this rule (and the per-resource
  gap analysis for the global/staff scope) is `permission-granularity-model`
  SPEC's REQ-1 through REQ-3; this requirement must not drift from that
  one.
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
- **REQ-9 [Ubiquitous]** `GET /api/staff/permissions`'s response shall
  include, alongside the existing granted-permissions list, a boolean
  field indicating whether the calling account is a staff account
  (`GlobalRole.STAFF` or `STAFF_ADMIN`) at all — `true` for any staff
  account regardless of how many (if any) global permissions it
  currently holds, `false` for a caller with no `GlobalRole` (a plain
  tenant `MEMBER`/`MEMBER_ADMIN`).

## Non-functional requirements

- Security: this is a privilege-narrowing change, not a privilege-adding
  one — no existing behavior for what is now `STAFF_ADMIN` may regress.
- Security: default-deny — a `STAFF` user with no grants at all can do
  nothing beyond authenticate.
- Security: REQ-9's new field is read-only, purely informational
  metadata about the caller's own account — it grants no capability by
  itself and must not be treated as a permission check anywhere
  server-side; every actual staff-gated action continues to be enforced
  by its own `GlobalPermission` check exactly as REQ-5 already requires.
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
- [x] `GET /api/staff/permissions` for a `STAFF` account with zero
      granted global permissions returns the new field as `true` and an
      empty `permissions` list.
- [x] `GET /api/staff/permissions` for a `STAFF_ADMIN` account returns the
      new field as `true` (alongside the existing full-permission-list
      behavior).
- [x] `GET /api/staff/permissions` for a plain tenant member (`MEMBER` or
      `MEMBER_ADMIN`, no `GlobalRole`) returns the new field as `false`
      and an empty `permissions` list.
- [ ] **New, per REQ-3's 2026-08-02 amendment**: a `STAFF` user granted
      only `STAFF_USER_EDIT`/`STAFF_USER_DELETE` (once those exist, per
      `permission-granularity-model`) without `STAFF_USER_VIEW` is
      denied — tracked and detailed in `permission-granularity-model`
      SPEC, not duplicated here.

## Out of scope

- Any UI for managing global permissions/groups (staff user management
  screens) — a separate, later roadmap item.
- Any change to tenant-level permissions, `AccessGroup`, or
  `DirectPermissionGrant` — untouched by this feature; this introduces
  parallel, global-scope equivalents, not a change to the tenant ones.
- Any change to what `GET /api/staff/permissions` returns for the
  granted-permissions list itself, or to who may call the endpoint —
  REQ-9 only adds one additional boolean field to the existing response
  shape.
- The frontend's actual use of REQ-9's new field (which UI element it
  gates, how it's labeled) — covered entirely by the companion frontend
  SPEC (`knowly-app/specify/features/navigation-menu/SPEC.md`).

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
