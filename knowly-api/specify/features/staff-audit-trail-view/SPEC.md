# SPEC — staff-audit-trail-view (backend)

## Context and motivation

`PROJECT_STATUS.md` item 6's remaining backlog text describes a
staff-side member-listing screen that lets a staff user open a person's
profile, edit it (already covered by `identity-profile-model`'s
profile-edit rules), and **view that person's audit trail**. No part of
that capability exists in the backend today: `AuditEvent`
(`br.com.conectabyte.knowly.audit.AuditEvent`) is written by
`AuditLogAspect`/`AuditEventWriter` on every audited action, but nothing
in the codebase reads it back through an API — there is no
`AuditEventController` or equivalent.

This is a **sibling, not an extension**, of `global-staff-dashboard-metrics`:
that feature returns cross-tenant aggregate *counts* for staff's own
operational visibility; this feature returns a specific *person's*
audit history, row by row, so a staff user reviewing an account (e.g.
during a support/compliance investigation) can see what that person
actually did. Different shape, different data-exposure profile — kept
as its own SPEC per the product owner's confirmation (2026-07-28),
rather than folded into `global-staff-dashboard-metrics`, which stays
untouched by this feature.

**Confirmed by the product owner (2026-07-28), a deliberate product
decision:** a staff caller viewing a target user's audit trail sees that
person's **full history, including audit rows from every tenant they've
ever acted in** — not just global/staff-level events. **Architectural
correction (2026-07-28, `software-architect` review)**: unlike
`global-staff-dashboard-metrics`, there is no `TenantFilter` to bypass
here — `AuditEvent` is not a `TenantAwareEntity` and carries no `@Filter`
annotation at the entity level, so a plain
`findByActorUserIdOrderByOccurredAtDesc` query already returns
cross-tenant rows with zero special-case plumbing. The product decision
being confirmed is therefore not "suppress a filter" but "expose
row-level, cross-tenant `AuditEvent` content — including tenant-internal
identifiers like `resourceId`/`resourceType` — to a staff caller who may
have no active membership in the tenant that content came from." That
row-level exposure is a materially different risk than
`global-staff-dashboard-metrics` REQ-11's aggregate-only cross-tenant
counts (`appsec` review, 2026-07-28): counts reveal nothing about any
specific resource, while this feature's rows can. **This is intentional,
confirmed scope, not an oversight** — a future AppSec/reviewer pass
should treat this as a documented, approved exposure (see
`DECISIONS.md`'s "Multi-tenancy is enforced at the ORM layer" entry for
the general pattern this deliberately departs from for this one
feature), not a gap to silently "fix" by adding tenant scoping.

## User stories

- As a `STAFF`/`STAFF_ADMIN` holding the new audit-trail-view permission,
  I want to open any user's profile from the staff member-listing screen
  and see their full audit history (including actions taken inside any
  tenant), so I can investigate or support an account without needing to
  separately switch into every tenant that person belongs to.
- As a `STAFF`/`STAFF_ADMIN` without the permission, I want to be
  rejected from viewing anyone's audit trail, consistent with this
  project's default-deny model for `STAFF`.
- As a product owner, I want this cross-tenant visibility to be
  explicitly permission-gated and explicitly documented as an accepted
  isolation exception, so it's never mistaken for an oversight later.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall expose
  `GET /api/staff/users/{userId}/audit-trail`, returning every
  `AuditEvent` row where `actorUserId` equals the target `userId`, each
  including `occurredAt`, `action`, `resourceType`, `resourceId`,
  `tenantId` (nullable), `outcome`, and `metadata`, ordered
  reverse-chronologically (most recent first).
- **REQ-2 [Ubiquitous]** The system shall gate this endpoint with a new,
  dedicated `GlobalPermission.AUDIT_TRAIL_VIEW`, via the existing
  `@RequiresGlobalPermission` mechanism (`STAFF_ADMIN` always passes
  unconditionally).
- **REQ-3 [Ubiquitous]** The target user identified by `{userId}` may be
  any `User` regardless of `GlobalRole` — `STAFF`, `STAFF_ADMIN`,
  `MEMBER`, or `MEMBER_ADMIN` — this endpoint is not restricted to staff
  targets only, since the member-listing screen that consumes it covers
  tenant members too.
- **REQ-4 [Ubiquitous]** The system shall query `AuditEvent` by
  `actorUserId` alone, with no tenant scoping applied (`AuditEvent` is
  not a `TenantAwareEntity` and has no `@Filter`, so this requires no
  special-case plumbing) — the result includes the target user's audit
  rows across every tenant they have ever acted in, plus any
  global/staff-level rows, including tenant-internal identifiers
  (`resourceId`/`resourceType`) from tenants the caller has no active
  membership in. This row-level cross-tenant exposure is a deliberate,
  confirmed product decision (see "Context and motivation" above), not
  an oversight.
- **REQ-5 [Event-Driven]** When called by a caller holding
  `AUDIT_TRAIL_VIEW` (or `STAFF_ADMIN`) for a `{userId}` that exists,
  the system shall return `200 OK` with the result described in REQ-1,
  or an empty list if the target user has no audit events.
- **REQ-6 [Unwanted Behavior]** If a caller lacks `AUDIT_TRAIL_VIEW` (and
  isn't `STAFF_ADMIN`), then the system shall respond `403 Forbidden`.
- **REQ-7 [Unwanted Behavior]** If the caller is a tenant `MEMBER`/
  `MEMBER_ADMIN` with no `GlobalRole`, then the system shall respond
  `403 Forbidden` regardless of any tenant-side permissions they hold.
- **REQ-8 [Unwanted Behavior]** If `{userId}` does not correspond to any
  existing `User`, then the system shall respond `404 Not Found`.
- **REQ-9 [State-Driven]** While the `role-model-refinement` STAFF
  ceiling governs *management* actions against `STAFF`/`STAFF_ADMIN`
  targets, this feature's read-only audit-trail endpoint is unaffected
  by that ceiling — a `STAFF` user holding `AUDIT_TRAIL_VIEW` can view a
  `STAFF`/`STAFF_ADMIN` target's audit trail (viewing history grants no
  ability to act on the account, mirroring the same reasoning
  `staff-user-listing` already applied to the listing endpoint).

## Non-functional requirements

- Security: default-deny — gated exclusively by
  `GlobalPermission.AUDIT_TRAIL_VIEW`; no tenant `Permission` involved
  anywhere, and no implicit grant from holding `PROFILE_VIEW`,
  `STAFF_USER_VIEW`, or any other existing permission.
- Security/data exposure: this endpoint deliberately bypasses
  `TenantFilter` — REQ-4 records this explicitly as confirmed, in-scope
  behavior; it must not be "corrected" back to tenant-scoped filtering
  without a fresh Tier 3 decision, the same way `global-staff-dashboard-
  metrics` REQ-11 is protected.
- Data exposure: `metadata` is returned as already stored — `sourceIp`
  is already `PiiMasker`-masked at write time by `AuditLogAspect` for
  any `@AuditLog(captureSourceIp=true)`-annotated action (not
  auth-specific — audit-annotation-driven), confirmed by `appsec` review
  2026-07-28. This endpoint applies no additional redaction beyond
  what's already true of the stored `AuditEvent` row, and introduces no
  new unmasked field.
- Performance: `AuditEventRepository.findByActorUserIdOrderByOccurredAtDesc`
  already exists — no new query method is needed. Whether a DB index on
  `actor_user_id` is warranted (none exists today) is a PLAN-level
  decision based on expected `AuditEvent` volume, not specified here.
  Given the table is append-only and grows unboundedly per active user,
  this endpoint shall cap results at the 500 most recent rows for the
  target user (defensive limit against unbounded response size / a
  large single-shot data-exfiltration surface if a staff credential is
  compromised — `software-architect`/`appsec` review, 2026-07-28); full
  pagination remains out of scope and can supersede this cap later.
- Observability: this endpoint is itself `@AuditLog`-annotated, action
  `staff.audit_trail.view`, `resourceType = "User"`,
  `resourceId = {userId}` — viewing someone's audit trail is itself an
  auditable action, consistent with "everything is audited" in
  `VISION.md`.

## Acceptance criteria

- [ ] `GlobalPermission.AUDIT_TRAIL_VIEW` exists.
- [ ] `GET /api/staff/users/{userId}/audit-trail` returns the target
      user's audit events (occurredAt, action, resourceType, resourceId,
      tenantId, outcome, metadata), reverse-chronological.
- [ ] Result includes tenant-scoped audit rows from every tenant the
      target user has acted in, not just global/staff-level rows,
      verified with an integration test asserting a row from tenant A
      and a row from tenant B both appear for the same target user in a
      single call, with no active tenant selected by the caller.
- [ ] A caller without `AUDIT_TRAIL_VIEW` (and not `STAFF_ADMIN`) gets
      403.
- [ ] A tenant member with no `GlobalRole` gets 403.
- [ ] `STAFF_ADMIN` always succeeds without an explicit grant.
- [ ] A target user with more than 500 audit events returns only the
      500 most recent (defensive cap, not full pagination).
- [ ] A `STAFF` user holding `AUDIT_TRAIL_VIEW` can view a
      `STAFF`/`STAFF_ADMIN` target's audit trail (ceiling does not block
      viewing), but remains blocked by the existing ceiling from any
      *management* action against that same target.
- [ ] A nonexistent `{userId}` returns 404.
- [ ] The call itself is captured as an `AuditEvent`
      (`staff.audit_trail.view`).
- [ ] `./mvnw spotless:apply && ./mvnw verify` passes.

## Out of scope

- **Support-ticket-related audit events** — support tickets don't exist
  yet (blocked on `PROJECT_STATUS.md` item 14); nothing here invents a
  support-ticket audit shape.
- **Staff welcome screen / member-listing screen itself** —
  frontend concerns, addressed in `knowly-app/`.
- **Self-service audit-trail viewing** (a user viewing their own
  history) — this SPEC covers staff viewing *another* person's trail
  only; no endpoint here lets a caller view their own `AuditEvent` rows.
  A future SPEC would be needed if self-view is ever requested.
- **Editing, filtering by date range/action type, or pagination** of the
  audit trail — this SPEC returns the target user's most recent events
  up to the defensive cap (see non-functional requirements); true
  pagination/filtering is a possible follow-up, not addressed here.
- **Any change to `AuditEvent`'s schema, retention, or write path** —
  this feature is read-only against the existing table.
- **Any change to `global-staff-dashboard-metrics`'s endpoints or
  scope** — that feature is untouched by this one.
- **Any change to the `role-model-refinement` STAFF ceiling itself** —
  REQ-9 only clarifies that viewing is outside the ceiling's scope, it
  does not alter what the ceiling blocks.

## Decisions (confirmed by the product owner, 2026-07-28)

1. **Separate SPEC**, not folded into `global-staff-dashboard-metrics` —
   different data shape (per-user rows vs. cross-tenant aggregate
   counts) and different exposure profile.
2. **Cross-tenant scope confirmed**: the target's tenant-scoped audit
   rows are included, across every tenant, not just global/staff-level
   events — an explicit, confirmed exception to `TenantFilter`, recorded
   in REQ-4 and the "Context and motivation" section specifically so a
   future reviewer doesn't mistake it for an oversight.
3. **New dedicated `GlobalPermission.AUDIT_TRAIL_VIEW`** — not reusing
   `PROFILE_VIEW`, `STAFF_USER_VIEW`, or `DASHBOARD_VIEW_GLOBAL`, per the
   established precedent that every staff capability gets its own
   grantable permission.
4. **Staff-viewing-others only** — no self-view endpoint is included in
   this SPEC.

## Tier 3 flag

The cross-tenant, `TenantFilter`-bypassing query (REQ-4) is the one
genuinely new isolation-adjacent tradeoff in this feature. It was
explicitly raised and confirmed by the product owner before drafting
(2026-07-28) — not decided unilaterally — and is documented here,
in REQ-4's requirement text, and in "Context and motivation" so it
survives independently of this conversation, the same way
`global-staff-dashboard-metrics` REQ-11 and its "Out of scope" section
already protect that feature's own aggregate-only exception.
