# SPEC — staff-bootstrap-user

> The what and the why. No technical implementation details.

## Context and motivation

Today there is no way to create the very first staff user in a fresh
knowly deployment — `specify/features/authentication/SPEC.md` explicitly
puts user provisioning out of scope, and nothing in `tenancy` creates a
`User` row either. Without a first staff account, nobody can ever log in
to do anything, including provisioning further staff (a separate, later
feature).

This feature is deliberately minimal: it does not invent a new
authentication mechanism. The existing passwordless login-code flow
(`specify/features/authentication/SPEC.md`) already lets any existing
`User` log in via an emailed code, with no password of any kind — a
database migration that inserts one staff `User` row is therefore
sufficient to unblock the very first login, as long as the email address
is one the deploying operator actually controls before that first
attempt.

## User stories

- As the person deploying knowly for the first time, I want a
  ready-made staff account (identified by an email address I control) so
  that I can log in via the existing login-code flow without any manual
  database surgery.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The database migration suite shall create
  exactly one staff `User` row, with no tenant membership.
- **REQ-2 [Ubiquitous]** That user's email address shall be supplied via
  an environment variable at migration time — never hardcoded, never
  committed to version control.
- **REQ-3 [Unwanted Behavior]** If the required environment variable is
  absent (or empty) when the bootstrap migration runs, then the
  application shall fail to start with an explicit error naming the
  missing variable, rather than creating an account with a
  placeholder/guessable email.
- **REQ-4 [Ubiquitous]** The bootstrap user shall carry no permissions or
  capabilities beyond those already granted to any staff user today — no
  new privilege tier, no new authentication mechanism, and no password of
  any kind is introduced by this feature.

## Non-functional requirements

- Security: this feature introduces no new credential, no new
  authentication path, and no new column/field on `User` — it only adds
  a row, using login flows that already exist and are already specified
  elsewhere.

## Acceptance criteria

- [ ] A fresh deployment with the required environment variable set boots
      successfully and has exactly one staff `User` in the database with
      that email address.
- [ ] Booting without the environment variable set fails fast with a
      clear error identifying the missing variable.
- [ ] The bootstrap user can log in through the existing login-code flow
      like any other user, with full existing staff access.
- [ ] Re-running the application (without a new migration) does not
      create a second bootstrap user or alter the existing one
      (idempotent migration, per normal Flyway semantics — no special
      handling needed beyond that).

## Out of scope

- Any new password, one-time-password, or freeze/expiry mechanism for
  this account — it behaves exactly like any other staff `User` from the
  moment it's created.
- A general "staff creates staff" capability/endpoint — there is
  currently no way for any staff user (bootstrap or otherwise) to
  provision additional staff users. That capability is a separate, later
  roadmap item (staff/user management), not addressed here.
- The staff-admin/staff permission split (`GlobalRole` granularity beyond
  the one role that already exists) — a separate, later feature.
- Anything about tenant-scoped users/roles — untouched by this feature.
- Rotating or changing the bootstrap email after the migration has
  already run once (standard Flyway behavior: versioned migrations don't
  re-run; changing the env var after first deploy has no effect without a
  new migration, which is out of scope to design here).
