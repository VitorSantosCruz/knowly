# SPEC — staff-user-provisioning

> The what and the why. No technical implementation details.

## Context and motivation

Both `staff-bootstrap-user` and `staff-rbac-split` explicitly deferred
this: there is still no way for any staff account — `STAFF_ADMIN` or a
granted `STAFF` — to create *another* staff user. The bootstrap user
solves the very first login; `staff-rbac-split` lets you manage an
*existing* staff user's global permissions; neither lets you bring a new
person onto the staff roster at all. Today the only way a second staff
account can exist is another database migration, which doesn't scale
past the one bootstrap case it was built for.

Separately, tenant member provisioning (`TenantService.addMember`)
already creates a `User` row today, and that's already sufficient for a
first login: the existing passwordless login-code flow
(`specify/features/authentication/SPEC.md`) works for any `User` row
purely from its email, no password of any kind required. So unlike
staff, tenant member provisioning has no functional gap — this feature
is scoped to staff provisioning specifically.

## User stories

- As a `STAFF_ADMIN` (or a `STAFF` user granted the right permission), I
  want to create a new staff user by email so that I can bring a new
  team member onto the platform without needing a database migration.
- As a newly-provisioned staff user, I want a way to log in immediately
  after being created — not just wait for someone to separately trigger
  a login-code — so that onboarding doesn't depend on a second manual
  step.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When an authorized staff user creates a new
  staff user by email, the system shall create exactly one `User` row
  with `GlobalRole.STAFF` (the new, permission-gated tier — never
  `STAFF_ADMIN`; see REQ-6) and no tenant membership, the same shape as
  `staff-bootstrap-user`'s row.
- **REQ-2 [Unwanted Behavior]** If the given email already belongs to an
  existing `User` (staff or not), then the system shall reject the
  request rather than silently reusing or upgrading that account.
- **REQ-3 [Event-Driven]** When a new staff user is created, the system
  shall generate and email them a one-time password (the existing
  `OneTimePasswordService`/`MailService` mechanism — no new credential
  type), giving them an immediate way to log in in addition to the
  always-available login-code flow.
- **REQ-4 [Ubiquitous]** Creating a staff user shall be its own,
  independent global permission (`GlobalPermission.STAFF_USER_CREATE`)
  — distinct from `STAFF_PERMISSION_MANAGE` (managing an *existing*
  staff user's permissions) — following `staff-rbac-split`'s established
  principle that no permission implies any other.
- **REQ-5 [Event-Driven]** When a staff user is created, the system
  shall record an audit event (actor, action, outcome, the new user's
  id), per the constitution's audit requirements.
- **REQ-6 [Ubiquitous]** A newly-created staff user's `GlobalRole` shall
  always be `STAFF`, never `STAFF_ADMIN` — this feature provisions
  permission-gated staff only; promoting someone to `STAFF_ADMIN` is not
  addressed here (see Out of scope).

## Non-functional requirements

- Security: default-deny — a freshly created staff user has zero global
  permissions until explicitly granted via `staff-rbac-split`'s existing
  grant endpoints; this feature does not itself grant anything beyond
  existing.
- Security: the one-time password follows the exact same hashing/expiry
  rules already established for `User.oneTimePasswordHash`.
- Observability: per REQ-5.

## Acceptance criteria

- [x] A `STAFF_ADMIN` can create a new staff user by email; the new user
      appears with `GlobalRole.STAFF` and no permissions.
- [x] A `STAFF` user granted `STAFF_USER_CREATE` can do the same; a
      `STAFF` user without it is rejected.
- [x] A `STAFF` user granted only `STAFF_PERMISSION_MANAGE` (not
      `STAFF_USER_CREATE`) cannot create a new staff user.
- [x] Attempting to create a staff user with an email that already
      exists (staff or tenant member) is rejected, not silently merged.
- [x] The new staff user receives a one-time password by email and can
      log in with it; they can also log in via the ordinary login-code
      flow without ever having received that email.
- [x] Creating a staff user emits an audit event.

## Out of scope

- Promoting an existing `STAFF` user to `STAFF_ADMIN`, or demoting a
  `STAFF_ADMIN` — no mechanism exists for either today (only the
  `staff-bootstrap-user` migration and this feature's `STAFF`-only
  creation path put a role on a `User`); if you need this, it's a
  separate, later decision (who's authorized to mint another
  `STAFF_ADMIN` is a bigger question than this SPEC should silently
  answer).
- Any change to tenant member provisioning (`addMember`) — already
  functionally complete per this SPEC's Context section.
- Deactivating/removing a staff user — this feature only adds staff,
  it doesn't yet let you take one away (tenant members already have this
  via `removeMember`'s soft-removal; staff doesn't yet, and isn't
  addressed here).
- Any UI for this — a separate, later roadmap item (staff user
  management screens).
