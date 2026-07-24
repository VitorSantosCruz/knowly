# SPEC — Onboarding status

## Context and motivation

The frontend's onboarding-and-dashboard feature
(`knowly-app/specify/features/onboarding-dashboard/SPEC.md`) needs to know,
per user, whether they've already been through the first-run guided tour —
persisted server-side (per REQ-3 there: "persisted per-user, not
per-browser"), so it doesn't restart on a new device or after clearing
local storage. This feature is exactly that: a minimal per-user flag and
the two endpoints to read/set it.

Metrics endpoints (article/conversation/message counts) that the same
frontend feature also depends on are deliberately **not** covered here —
see "Out of scope".

## User stories

- As a user logging in for the first time, I want the app to know I
  haven't seen the tour yet, even if I log in from a different browser or
  device than my very first session.
- As a returning user, I don't want the tour to start again automatically
  just because I cleared my browser data.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall record, per user, whether they
  have completed onboarding, defaulting to not-completed for every
  existing and newly created user.
- **REQ-2 [Ubiquitous]** The system shall expose an authenticated endpoint
  that returns the calling user's own onboarding-completion status.
- **REQ-3 [Event-Driven]** When a user calls the mark-complete endpoint,
  the system shall record that user as having completed onboarding,
  regardless of their previous state (idempotent — calling it again when
  already completed is not an error).
- **REQ-4 [Ubiquitous]** Onboarding completion is a global, per-user flag,
  not per-tenant — a user who is a member of multiple tenants completes
  onboarding once, not once per tenant.
- **REQ-5 [Ubiquitous]** Both endpoints shall be logged per this project's
  audit conventions (`@AuditLog`), consistent with every other
  user-attributable action in the system.

## Non-functional requirements

- Consistency: reuses the existing `User` entity and its Envers/JPA
  Auditing conventions rather than introducing a parallel per-user record
  type for a single boolean+timestamp.

## Acceptance criteria

- [ ] A brand-new user's onboarding status reads as not-completed.
- [ ] Calling mark-complete then reading status back returns completed.
- [ ] Calling mark-complete twice in a row does not error.
- [ ] A user who belongs to two tenants has one onboarding status, not one
      per tenant.
- [ ] Both endpoints require authentication (401 if not logged in).

## Out of scope

- The four `/api/tenants/metrics/*` endpoints the same frontend feature
  also depends on — they need `Article`/`Conversation`/`Message` domain
  entities that don't exist in this backend yet. This is its own future
  feature, built once those entities do.
- Any UI (owned entirely by `knowly-app`'s `onboarding-dashboard` feature).
