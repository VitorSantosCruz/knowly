# SPEC — Authentication event audit logging

## Context and motivation

`AuthController` (`/api/auth/login-request`, `/api/auth/login-code/verify`,
`/api/auth/login-password/verify`, `/api/auth/logout`) has zero `@AuditLog`
coverage today, even though every other mutating action in the system
(`TenantService`, `StaffService`, article/conversation/onboarding
controllers) is audited via the existing `@AuditLog`/`AuditLogAspect`
mechanism. This SPEC closes that gap: every authentication event must
leave a queryable audit trail, without weakening the existing
anti-enumeration/timing-safety design.

## User stories

- As ConectaByte staff investigating a support/security question, I want
  every authentication event (login attempt, success, failure, lockout,
  logout) to leave a queryable audit trail, so "who did what, and when" is
  always answerable, including for actions taken before a session existed.
- As the product owner, I want the audit trail to reveal nothing about
  whether a given email has an account, beyond what the authentication
  flow itself already reveals.
- As a developer extending this system later, I want authentication audit
  events to use the same `AuditEvent`/`AuditOutcome`/`@AuditLog` mechanism
  already used everywhere else.

## Requirements (EARS/GEARS)

### Coverage — one audit event per auth event, regardless of outcome

- **REQ-1 [Event-Driven]** When `POST /api/auth/login-request` is called,
  the system shall record an audit event, regardless of whether the
  submitted email corresponds to an existing account.
- **REQ-2 [Event-Driven]** When `POST /api/auth/login-code/verify`
  completes, the system shall record an audit event whose outcome
  distinguishes success from failure, regardless of whether the submitted
  email corresponds to an existing account.
- **REQ-3 [Event-Driven]** When `POST /api/auth/login-password/verify`
  completes, the system shall record an audit event whose outcome
  distinguishes success from failure, regardless of whether the submitted
  email corresponds to an existing account.
- **REQ-4 [Event-Driven]** When an email's failed-attempt counter crosses
  the lockout threshold (`FailedAttemptService`), the system shall record
  a distinct lockout audit event, separate from the failed-verification
  event that triggered it.
- **REQ-5 [Event-Driven]** When a code or password verification is
  rejected outright because the email is already locked out
  (`AccountLockedException`), the system shall record an audit event with
  an outcome distinguishable from a plain wrong-code/wrong-password
  failure (REQ-2/REQ-3).
- **REQ-6 [Event-Driven]** When `POST /api/auth/logout` is called by an
  authenticated session, the system shall record an audit event
  identifying the authenticated actor.
- **REQ-7 [Unwanted Behavior]** If `POST /api/auth/logout` is called
  without an authenticated session (already rejected with 401), then the
  system need not record an audit event.

### Anti-enumeration and PII — the audit write itself must stay symmetric

- **REQ-8 [Ubiquitous]** The system shall record an audit event for
  REQ-1–REQ-5 through the exact same code path regardless of whether the
  submitted email corresponds to a real account.
- **REQ-9 [Ubiquitous]** The system shall never write a raw email address
  into any audit event field or its metadata; every email reference shall
  use `PiiMasker.maskEmail`, matching `AuthController`'s existing logs.
- **REQ-10 [Unwanted Behavior]** If an authentication audit event is for a
  pre-authentication action (login-request, or a failed/locked-out
  verification), then the system shall not resolve or record a real
  `actorUserId` for it, even when the submitted email matches a real
  account.
- **REQ-11 [Event-Driven]** When a code or password verification succeeds
  or a logout occurs, the system shall record the resolved, real actor's
  user id on that audit event.
- **REQ-12 [Ubiquitous]** The system shall not introduce any additional
  timing difference between the "account exists" and "account does not
  exist" cases of REQ-1–REQ-5 as a side effect of adding the audit-log
  write.

### Reuse of the existing mechanism

- **REQ-13 [Ubiquitous]** The system shall record authentication audit
  events using the existing `AuditEvent`/`AuditLogAspect`/`@AuditLog`
  mechanism, rather than a new or parallel audit-logging mechanism.
- **REQ-14 [Complex]** Where the existing `@AuditLog` actor-resolution
  behavior already returns no actor for a request with no authenticated
  session, the system shall rely on that existing behavior for REQ-10.

## Non-functional requirements

- Security: no new timing side-channel (REQ-12); no plaintext/raw PII in
  any audit record (REQ-9); this feature does not reopen or attempt to fix
  `authentication`'s already-reviewed, already-declined latency-based
  side-channel (see "Out of scope").
- Observability: authentication audit events participate in the same
  structured-log/trace correlation as every other `@AuditLog`-covered
  action.
- Consistency: authentication audit events are queryable via the existing
  `AuditEventRepository`.

## Acceptance criteria

- [ ] Every `POST /api/auth/login-request` call produces exactly one audit
      event, whether or not the submitted email exists.
- [ ] Every `POST /api/auth/login-code/verify` call produces exactly one
      audit event whose outcome differs between success and failure,
      whether or not the submitted email exists.
- [ ] Every `POST /api/auth/login-password/verify` call produces exactly
      one audit event whose outcome differs between success and failure,
      whether or not the submitted email exists.
- [ ] Crossing the failed-attempt lockout threshold produces a lockout
      audit event distinct from the failed-verification event that
      triggered it.
- [ ] A verification attempt rejected outright due to an active lockout
      produces an audit event distinguishable from a plain
      credential-mismatch failure.
- [ ] A successful `POST /api/auth/logout` produces an audit event
      identifying the real actor.
- [ ] No audit event produced by this feature contains a raw (unmasked)
      email address anywhere, including in metadata.
- [ ] No audit event for login-request, or for a failed/locked-out
      verification, carries a non-null `actorUserId`, even when the
      submitted email matches a real account.
- [ ] A successful verification's or a logout's audit event carries the
      real, resolved `actorUserId`.
- [ ] The audit-write code path adds no new conditional branch, extra
      query, or extra work keyed on account existence.
- [ ] Authentication audit events are written via `@AuditLog`/
      `AuditLogAspect` (or, where genuinely infeasible, a manual write to
      `AuditEventRepository` following the same masking/symmetry rules,
      documented in PLAN.md).
- [ ] Authentication audit events are retrievable through the existing
      `AuditEventRepository` query surface.

## Out of scope

- **Fixing `authentication`'s existing, already-reviewed latency
  side-channel.** The product owner already reviewed remediation options
  and explicitly chose not to implement either. This SPEC must not
  reintroduce, reopen, or expand into that decision.
- **CAPTCHA-required and rate-limit/velocity-exceeded responses.**
- **Capturing source IP on the audit event** — left as a PLAN.md decision
  (new column vs. `metadata` JSON), since it affects the shared
  `AuditEvent` entity used by every other `@AuditLog` consumer.
- **A UI/screen for viewing the audit trail** — backend only.
- **Retroactive backfill.**
- **Changing `AuditOutcome`'s existing values or `AuditLogAspect`'s
  existing exception-to-outcome mapping for non-authentication features**
  — any change here is additive only.

## Decisions and judgment calls

- **Source IP**: flagged per the pre-existing `authentication` SPEC
  requirement, but the schema mechanism is left to PLAN.md.
- **New `AuditOutcome` value(s)**: needed to distinguish "wrong
  credential" (routine, expected) from a genuine system `ERROR`, plus a
  way to flag lockout-rejection distinctly — additive only.
- **Anonymous-actor identification**: uses the masked email in
  `resourceId`/`resourceType` rather than a new column, following the
  existing `resourceIdExpression` pattern — PLAN.md to confirm.
- **`@AuditLog` applicability to a pre-authentication controller method**:
  confirmed feasible — `AuditLogAspect.record` already tolerates a null
  actor and null active tenant.
- **Lockout is its own event**, not folded into the triggering failure's
  metadata, so "how many times has this email been locked out" is a
  simple count query.
