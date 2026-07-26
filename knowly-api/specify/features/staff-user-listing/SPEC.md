# SPEC — staff-user-listing

## Context and motivation

`staff-rbac-split` introduced `/api/staff/**` endpoints for managing an
individual staff user's global permissions/access groups
(`GET /api/staff/users/{userId}/permissions`, grant/revoke, access-group
assign/unassign) and `staff-user-provisioning` added
`POST /api/staff/users` to create one. None of this ever added a way to
**list** staff users globally — there is no `GET` endpoint that returns
"who are all the staff users." This is a known, already-flagged gap (see
`PROJECT_STATUS.md` item 5 and `role-model-refinement`'s "Out of scope").
This feature closes exactly that gap: a listing/search endpoint for
`GlobalRole.STAFF`/`STAFF_ADMIN` users, with nothing else in scope.

`role-model-refinement` established a hardcoded **STAFF ceiling**: a
`STAFF` user, however permissioned, can never *manage* (create, view
permission detail of, grant/revoke permissions for, assign/unassign
access groups of) another `STAFF`/`STAFF_ADMIN` user — that ceiling
exists specifically to close a self-escalation path. This SPEC makes a
deliberate, documented judgment call that **listing is not the same
capability the ceiling protects against**: merely seeing that a staff
account exists (id, email, role) does not let a `STAFF` user act on it —
every individual management action still goes through `StaffService`'s
`enforceStaffCeiling` regardless of whether the caller can see the list.
Listing is therefore gated by its own, separate, grantable
`GlobalPermission` rather than folded into the ceiling or restricted to
`STAFF_ADMIN` only.

## User stories

- As a `STAFF_ADMIN`, I want to list/search all staff users so I can find
  a specific account to manage without already knowing their numeric id.
- As a `STAFF_ADMIN`, I want to grant a specific `STAFF` user the ability
  to see the staff directory without granting them any ability to manage
  other staff accounts.
- As a `STAFF` user with no listing grant, I want to be rejected from
  seeing the staff directory, consistent with this project's
  default-deny model for `STAFF`.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall expose `GET /api/staff/users`,
  returning every `User` whose `GlobalRole` is `STAFF` or `STAFF_ADMIN`
  (id, email, global role for each) — a plain list, not paginated,
  consistent with today's `GET /api/tenants` and
  `GET /api/tenants/{tenantId}/members`.
- **REQ-2 [Optional Feature]** Where an optional `email` query parameter
  is supplied, the system shall filter the REQ-1 result to only users
  whose email contains that value, case-insensitively.
- **REQ-3 [Ubiquitous]** `STAFF_ADMIN` shall always be authorized to call
  `GET /api/staff/users`, unconditionally.
- **REQ-4 [Event-Driven]** When a `STAFF` user holding the new
  `GlobalPermission.STAFF_USER_VIEW` calls `GET /api/staff/users`, the
  system shall return the same result REQ-1/REQ-2 describe, including
  rows for other `STAFF` and `STAFF_ADMIN` users.
- **REQ-5 [Unwanted Behavior]** If a `STAFF` user without
  `GlobalPermission.STAFF_USER_VIEW` calls `GET /api/staff/users`, then
  the system shall reject it as a permission failure.
- **REQ-6 [State-Driven]** While the `role-model-refinement` STAFF
  ceiling governs *management* actions, this feature's listing endpoint
  is unaffected by that ceiling — a `STAFF_USER_VIEW`-holding `STAFF`
  user sees `STAFF`/`STAFF_ADMIN` rows in the list, but the ceiling still
  independently blocks any attempt by that same user to act on those
  rows through the existing management endpoints.

## Non-functional requirements

- Security: default-deny — a `STAFF` user with no `STAFF_USER_VIEW` grant
  sees nothing from this endpoint.
- Security: `GlobalPermission.STAFF_USER_VIEW` is a normal, grantable
  permission, deliberately *not* subject to the ceiling (see Decisions).
- Data exposure: the list response includes only `id`, `email`,
  `globalRole` per user — no permission grants or access-group
  membership (that remains behind the existing per-user detail
  endpoint, still ceiling-protected).
- Observability: read-only listing endpoint, consistent with other
  read-only list endpoints — no `@AuditLog` needed for the call itself.

## Acceptance criteria

- [ ] `GET /api/staff/users` exists and returns every `STAFF`/`STAFF_ADMIN`
      user (id, email, globalRole).
- [ ] `GET /api/staff/users?email=<substring>` filters case-insensitively
      by email substring.
- [ ] `STAFF_ADMIN` can call this endpoint unconditionally.
- [ ] A `STAFF` user with zero grants is rejected.
- [ ] A `STAFF` user granted `GlobalPermission.STAFF_USER_VIEW` can call
      this endpoint and sees `STAFF`/`STAFF_ADMIN` rows in the result.
- [ ] That same `STAFF_USER_VIEW`-holding `STAFF` user remains rejected
      when attempting any management action against a
      `STAFF`/`STAFF_ADMIN` target — proving listing visibility and
      management authorization are enforced independently.
- [ ] No pagination is introduced.

## Out of scope

- **Pagination** — not introduced here; matches the existing unbounded
  convention. `PROJECT_STATUS.md` item 11 already tracks pagination
  project-wide.
- **Any detail beyond id/email/globalRole in the list response.**
- **Any change to the `role-model-refinement` STAFF ceiling itself.**
- **Any UI** — backend-only SPEC.
- **Filtering by anything other than email.**

## Decisions (judgment call made without blocking, 2026-07-26)

1. **Listing is gated by its own new `GlobalPermission.STAFF_USER_VIEW`,
   not restricted to `STAFF_ADMIN`-only, and not subject to the
   `role-model-refinement` ceiling.** The ceiling's stated purpose is
   preventing self-escalation via *managing* other staff accounts.
   Seeing that an account exists grants no ability to act on it — every
   mutating path independently re-checks `enforceStaffCeiling`. Folding
   listing into the ceiling would be stricter than the stated purpose
   requires and would block a legitimate narrower use case (a support
   lead who should see the team without touching permissions).
2. **No pagination added** — follows the existing unbounded-list
   convention (`GET /api/tenants`, `listMembers`).
3. **Response shape kept minimal (id/email/globalRole)** — keeps listing
   and detail responsibilities distinct, matching `MemberDto` vs.
   `MemberDetailDto` on the tenant side.
4. **Endpoint path**: `GET /api/staff/users`, reusing the existing
   `/api/staff/users` collection path (`POST` already occupies it for
   creation).

## Tier 3 flag

None identified. The one genuinely new tradeoff — gating listing
visibility separately from the management ceiling — was explicitly
delegated to this SPEC and documented above as a Decision.
