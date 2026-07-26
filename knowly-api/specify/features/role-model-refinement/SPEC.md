# SPEC — role-model-refinement

## Context and motivation

Today the tenant-scoped role model (`MembershipRole`) has two values,
`ADMIN` and `MEMBER`. `ADMIN` is an informal name for what the product
actually means: the tenant-local administrator role, as distinct from
`STAFF_ADMIN` (ConectaByte's own unrestricted global tier, see
`staff-rbac-split`). The name collision between `MembershipRole.ADMIN`
and the global roles is confusing in code, logs, and audit trails once
both concepts sit side by side. The confirmed final model is exactly
four tiers: `STAFF_ADMIN` / `STAFF` (global scope, `GlobalRole`, already
shipped) and `MEMBER_ADMIN` / `MEMBER` (tenant scope, renaming today's
`MembershipRole.ADMIN`/`MEMBER`). There is no separate "USER" role —
that was always just informal language for a `MEMBER` holding no
particular tenant permissions, not a distinct enum value.

Separately, `staff-rbac-split` introduced a permission-gated `STAFF`
tier whose access is, by design, "exactly what's explicitly granted, no
more." Taken completely literally, a `STAFF_ADMIN` who granted a `STAFF`
user *every* existing `GlobalPermission` would today be able to create
new staff users, inspect any staff user's permissions, and grant/revoke
any staff user's global permissions — including their own, or a
`STAFF_ADMIN`'s. That's a real self-escalation path. This feature closes
that path with a **hardcoded ceiling** that no permission grant can
lift: a `STAFF` user can never manage another `STAFF` or `STAFF_ADMIN`
user's account or global permissions, full stop — that stays exclusively
`STAFF_ADMIN`-only, the one deliberate exception to "STAFF fully
permissioned == STAFF_ADMIN."

`GET /api/tenants/memberships` is explicitly confirmed as **not** part of
this feature's scope (see Out of scope) — hiding the tenant list for a
single-membership user is a frontend UX decision, not a backend
restriction, and this SPEC makes no change to that endpoint.

## User stories

- As a platform owner, I want the tenant-local admin role named
  `MEMBER_ADMIN` (not `ADMIN`) so that it's never confused with
  `STAFF_ADMIN` in code, logs, or audit trails.
- As a platform owner, I want a `STAFF` user — no matter how many global
  permissions they've been granted — to never be able to create, edit,
  or manage the global permissions of any `STAFF` or `STAFF_ADMIN` user,
  so that a fully-permissioned `STAFF` account can never self-escalate or
  elevate an accomplice to staff power.
- As a `STAFF_ADMIN`, I want to remain the only tier that can manage
  staff/staff-admin accounts, so this ceiling never limits my own work.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** `MembershipRole.ADMIN` shall be renamed to
  `MembershipRole.MEMBER_ADMIN` everywhere it appears — the enum
  constant itself, every Java call site, every DTO/API contract field
  that carries a `MembershipRole` value, and every persisted row whose
  stored value is the string `"ADMIN"`.
- **REQ-2 [Ubiquitous]** `MembershipRole` shall continue to have exactly
  two values after the rename: `MEMBER_ADMIN` and `MEMBER` — no third
  ("USER" or otherwise) value shall be introduced.
- **REQ-3 [Event-Driven]** When the rename migration runs, the system
  shall rewrite every existing `tenant_memberships` row (and any other
  table persisting `MembershipRole` as a string, including its Envers
  audit-history table) so that stored `"ADMIN"` values become
  `"MEMBER_ADMIN"` — no existing membership silently loses or gains
  meaning because of the rename.
- **REQ-4 [Ubiquitous]** The system shall enforce a hardcoded ceiling,
  independent of any `GlobalPermission` grant: a `STAFF` user shall never
  be authorized to create a new `STAFF` or `STAFF_ADMIN` user, view
  another `STAFF`/`STAFF_ADMIN` user's permission detail, grant or revoke
  another `STAFF`/`STAFF_ADMIN` user's global permissions, or assign/
  unassign another `STAFF`/`STAFF_ADMIN` user's global access-group
  membership — regardless of how many `GlobalPermission`s (including
  literally all of them) that `STAFF` user has been granted.
- **REQ-5 [Unwanted Behavior]** If a `STAFF` user (however permissioned)
  attempts any action listed in REQ-4 against a target user whose
  `GlobalRole` is `STAFF` or `STAFF_ADMIN`, then the system shall reject
  it as a permission failure — the same rejection shape as an ungranted
  permission today — rather than allowing it because the relevant
  `GlobalPermission` was granted.
- **REQ-6 [Ubiquitous]** `STAFF_ADMIN` shall remain exempt from the REQ-4
  ceiling and able to perform every action listed there against any
  target user, exactly as today.
- **REQ-7 [State-Driven]** While a target user's `GlobalRole` is neither
  `STAFF` nor `STAFF_ADMIN`, the REQ-4 ceiling shall not apply — a
  `STAFF` user's existing, permission-gated ability to act on non-staff
  users is unaffected by this feature.
- **REQ-8 [Event-Driven]** When the REQ-4 ceiling rejects a `STAFF` user's
  attempt to manage a `STAFF`/`STAFF_ADMIN` target, the system shall
  record an audit event (actor, action, outcome = denied).

## Non-functional requirements

- Security: the REQ-4 ceiling is enforced in code as an unconditional
  check, never expressed as (or satisfiable by) a `GlobalPermission`
  value — it must not be possible to grant your way past it, by design.
- Security: this is a privilege-narrowing change for `STAFF` and a pure
  rename for the tenant role — no existing `STAFF_ADMIN` or
  `MEMBER_ADMIN` capability may regress.
- Data integrity: the `MembershipRole` rename's data migration must be
  applied atomically with the code change (same deploy).
- Observability: per REQ-8; existing audit events referencing
  `MembershipRole.ADMIN` in stored `metadata`/action payloads are
  historical and are not rewritten (see Decisions).

## Acceptance criteria

- [ ] `MembershipRole.ADMIN` no longer exists anywhere in
      `knowly-api/src/main` — the enum has exactly `MEMBER_ADMIN` and
      `MEMBER`, and every call site compiles against the new name.
- [ ] A Flyway migration rewrites every existing `tenant_memberships`
      row (and its Envers audit-history table) with stored value
      `"ADMIN"` to `"MEMBER_ADMIN"`; no row is left with the old value.
- [ ] All existing tenant-admin-gated behavior continues to work
      identically for a membership now stored as `MEMBER_ADMIN`.
- [ ] A `STAFF` user granted every existing `GlobalPermission` is still
      rejected when attempting to: create a new staff user, view a
      `STAFF`/`STAFF_ADMIN` user's permission detail, grant/revoke a
      `STAFF`/`STAFF_ADMIN` user's global permissions, or assign/unassign
      a `STAFF`/`STAFF_ADMIN` user's global access group.
- [ ] The same fully-permissioned `STAFF` user *can* still perform every
      one of those same actions against a target user who holds no
      `GlobalRole` (plain tenant member).
- [ ] `STAFF_ADMIN` is unaffected — can still create/manage any staff
      user regardless of the target's role.
- [ ] Every REQ-5 rejection emits an audit event with outcome = denied.
- [ ] `GET /api/tenants/memberships` is untouched by this feature.

## Out of scope

- **`GET /api/tenants/memberships`** — confirmed 2026-07-26 as a pure
  frontend UX concern; this SPEC makes no backend change to this
  endpoint.
- **Promoting a `STAFF` user to `STAFF_ADMIN`, or demoting a
  `STAFF_ADMIN`** — no mechanism exists today; this feature adds a
  ceiling on what `STAFF` can do *to* `STAFF`/`STAFF_ADMIN` accounts, it
  does not add any promotion/demotion capability for anyone.
- **Staff-joins-tenant acceptance flow, tenant-membership invitation
  acceptance, and profile/identity model** — separate backlog items (9,
  10, 13 in `PROJECT_STATUS.md`), unrelated to this rename/ceiling.
- **Any UI change** — this is a backend-only SPEC.
- **A "listing all staff users" endpoint** — not introduced here; still
  the known gap flagged under backlog item 5.
- **Rewriting historical audit-event payloads/metadata that reference
  the old `"ADMIN"` string** — left as-is; only live, queryable
  `MembershipRole` data is migrated, per REQ-3's own scope.

## Decisions (judgment calls made without blocking, 2026-07-26)

1. **No new `GlobalPermission` value is introduced for the REQ-4
   ceiling.** The whole point is that it can't be granted away, so
   modeling it as a `GlobalPermission` nobody could ever hold would be a
   misleading pattern. Instead, it's an unconditional code-level check
   applied as an additional guard alongside (not instead of) each
   existing `@RequiresGlobalPermission`-gated method in `StaffService`
   that accepts a target `userId` (`getStaffUserDetail`,
   `grantPermission`, `revokePermission`, `assignAccessGroup`,
   `unassignAccessGroup`) plus `createStaffUser`.
2. **`createStaffUser` already only ever creates `GlobalRole.STAFF`**
   (never `STAFF_ADMIN`), so REQ-4's "never create a new
   STAFF/STAFF_ADMIN" clause reduces to "only `STAFF_ADMIN` may call
   `createStaffUser`" — a narrowing of today's `STAFF_USER_CREATE`-gated
   behavior for `STAFF` callers. Confirmed intentional by the
   requirement text, flagged here since it changes existing, shipped
   `staff-user-provisioning` behavior.
3. **Migration numbering**: the next available migration is `V15`
   (`V14` was `staff-rbac-split`'s). A single new migration,
   `V15__rename_membership_role_admin_to_member_admin.sql`, does an
   `UPDATE tenant_memberships SET role = 'MEMBER_ADMIN' WHERE role =
   'ADMIN'` (and the equivalent `UPDATE` on the Envers
   `tenant_memberships_aud` table) — `role` is stored as `VARCHAR(20)`
   via `@Enumerated(EnumType.STRING)`, not as a Postgres enum type or
   ordinal, so no schema/type change is needed, following the same
   pattern `V14` used.
4. **Where the REQ-4 check lives**: immediately after each
   `StaffService` method's existing `requireUser(userId)` lookup,
   checking `actor.getGlobalRole() == GlobalRole.STAFF && (target.getGlobalRole()
   == GlobalRole.STAFF || target.getGlobalRole() == GlobalRole.STAFF_ADMIN)`.
   PLAN.md to finalize exact code structure.

## Tier 3 flag

None identified. The rename is a pure naming change with a
straightforward data migration; the `STAFF` ceiling is a
privilege-*narrowing* security change already fully specified by the
human product owner, not a new tradeoff being decided here.
