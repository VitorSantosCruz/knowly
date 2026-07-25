# PLAN — Onboarding status

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- Reuse the existing `User` entity: add a nullable
  `onboardingCompletedAt: Instant` field rather than a separate table.
  `null` means not completed; a value present means completed at that
  timestamp. No boolean flag alongside it — the timestamp itself is the
  boolean (`!= null`) plus a free audit fact ("when"), at zero extra
  storage cost.
- New package `br.com.conectabyte.knowly.onboarding` — small, self
  contained (one controller, one DTO, no dedicated service class: the
  logic is a one-line read/write on `User`, delegated straight from the
  controller through `UserRepository`, matching how thin the SPEC's scope
  actually is; introducing a service class for two one-liners would be
  the kind of speculative layering the constitution's "no speculative
  abstractions" rule warns against).
- Both endpoints resolve the current user the same way
  `TenantController.currentUser()` already does
  (`SecurityContextHolder` → email → `UserRepository`) — same pattern,
  no new abstraction for it.
- `@AuditLog` on both endpoints per REQ-5, consistent with every other
  user-attributable action added since the tenancy feature.

## Data schema

`V6__add_onboarding_completed_at_to_users.sql`:

```sql
ALTER TABLE users ADD COLUMN onboarding_completed_at TIMESTAMPTZ;
ALTER TABLE users_aud ADD COLUMN onboarding_completed_at TIMESTAMPTZ;
```

(Both in one migration: unlike the tenancy tables' `_aud` mirrors, which
Flyway split from the base tables across `V3`/`V4`, this is a single
column addition on two already-existing tables — no reason to split it
across two files.)

## API contracts

Both under `/api/users/me`, requiring authentication (enforced by the
existing `SecurityConfig`'s `anyRequest().authenticated()` — no new rule
needed).

- `GET /api/users/me/onboarding-status` → `200 { "completed": boolean }`
- `POST /api/users/me/onboarding-complete` → `200 {}` (idempotent per
  REQ-3 — calling it when already completed just re-sets the same
  non-null timestamp field, no special-case branching needed)

## Dependencies

None new.

## Testing strategy

- `OnboardingControllerIntegrationTest` (Testcontainers,
  `MockMvcTester`, same style as `AuthControllerIntegrationTest`):
  - a freshly created user's status reads `completed: false`.
  - calling mark-complete then reading status back returns
    `completed: true`.
  - calling mark-complete twice does not error (idempotency, REQ-3).
  - both endpoints return `401` when not authenticated.
  - a user with two tenant memberships still has exactly one onboarding
    status (REQ-4) — since the field lives on `User`, not
    `TenantMembership`, this is true by construction, but worth a test
    making it explicit.
  - an `AuditEvent` is recorded for both endpoints (REQ-5).
