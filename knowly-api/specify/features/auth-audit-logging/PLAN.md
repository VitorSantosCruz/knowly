# PLAN — auth-audit-logging

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **`@AuditLog` on `login-request`, `login-code/verify`,
  `login-password/verify`; a manual `AuditEventRepository` write for
  `logout`.** Verified against `AuditLogAspect.record`: it resolves
  `actorUserId` from `SecurityContextHolder` *after* `joinPoint.proceed()`
  returns. For the three verify/request endpoints this is exactly right —
  `AuthController.establishSession(...)` sets the `SecurityContext`
  *before* the method returns, so a successful verify's audit event
  correctly gets the real actor (REQ-11), while every pre-auth/failure/
  lockout path throws before `establishSession` runs, so the context is
  still unauthenticated and `actorUserId` resolves to `null` (REQ-10) —
  no extra code needed for that symmetry, it falls out of the existing
  aspect behavior (REQ-14). For `logout`, this breaks: `AuthController`
  calls `SecurityContextLogoutHandler.logout(...)`, which **clears** the
  `SecurityContext` before the method returns, so if `@AuditLog` wrapped
  `logout`, `resolveActorUserId()` would run post-clear and record `null`
  — silently violating REQ-11. This is a genuine infeasibility of the
  existing mechanism for this one endpoint (not a design choice), so
  `logout` gets a manual `AuditEventRepository.save(...)` call, executed
  *before* invoking the logout handler, capturing the authenticated
  user's id first. This is Tier 2 (a technical choice not exactly
  precedented, but following the SPEC's own documented allowance) —
  written up in `DECISIONS.md` below since it's a reusable rule about
  `@AuditLog`'s post-`proceed()` timing generally, not just this
  endpoint.
- **New `AuditOutcome` values, additive only: `FAILURE` and
  `LOCKED_OUT`.** `AuditLogAspect.logAudit`'s catch chain currently maps
  only `PermissionDeniedException`/`TenantAccessDeniedException` →
  `DENIED`, everything else → `ERROR`. Two new `catch` clauses are added,
  ordered before the generic `catch (Throwable ...)`:
  `InvalidCredentialsException` → `FAILURE` (routine wrong code/password,
  not a system error), `AccountLockedException` → `LOCKED_OUT` (rejected
  outright due to an active lockout, distinct from a plain mismatch per
  REQ-5). Both exception types live only in
  `br.com.conectabyte.knowly.auth.exception` and are thrown only by
  `AuthController` today (verified by grep) — adding them to the shared
  aspect cannot change outcome mapping for any other `@AuditLog`
  consumer, satisfying the SPEC's "additive only" constraint.
  `CaptchaRequiredException` stays unmapped (falls through to `ERROR`) —
  captcha-required responses are explicitly out of scope for this SPEC.
- **Source IP: generic `metadata` JSON capture in `AuditLogAspect`,
  masked, scoped to auth events only — no new column.** *(Revised
  2026-07-26 after an AppSec review; see `DECISIONS.md`'s
  "source IP capture is masked ... and scoped to auth events only"
  entry for the full reasoning — this replaces the original
  raw/system-wide approach below.)* `AuditEvent.metadata` already
  exists as a JSON column used by no current `@AuditLog` writer.
  `AuditLog` gains a new `boolean captureSourceIp() default false`
  attribute; only the four auth call sites (`login-request`,
  `login-code/verify`, `login-password/verify`'s `@AuditLog`
  annotations, and `logout`/`auth.login.lockout`'s manual
  `AuditEventRepository` writes) set it `true`. `AuditLogAspect.record`
  only resolves/writes `metadata` when `auditLog.captureSourceIp()` is
  `true` — every other current and future `@AuditLog` consumer
  (tenant/staff/article/conversation) gets `metadata = null` by
  default, unchanged from before this feature, unless that consumer's
  own SPEC deliberately opts in later. When it does fire, the aspect
  pulls the request's remote address via
  `RequestContextHolder.currentRequestAttributes()` and masks it via a
  new `PiiMasker.maskIp(String)` — IPv4 truncated to `/24` (last octet
  zeroed), IPv6 truncated to `/48` (last 80 bits zeroed) — before
  writing `metadata = {"sourceIp": "<masked>"}`. The manual writes
  (`logout`, `auth.login.lockout`) call `PiiMasker.maskIp` directly and
  set `metadata` on the `AuditEvent` themselves, the same as
  `AuditLogAspect` does for the annotated endpoints.

  <details><summary>Original approach (superseded, kept for history)</summary>

  `AuditLogAspect.record` was extended to pull the current request's
  remote address via `RequestContextHolder.currentRequestAttributes()`
  (when a request context exists) and set
  `metadata = {"sourceIp": "<raw addr>"}` for **every** `@AuditLog`
  event system-wide, not just auth's. AppSec flagged this as a new,
  unmasked PII type entering a permanent, queryable column with a
  blast radius the SPEC never asked for — see `DECISIONS.md`.

  </details>
- **Anonymous/pre-auth actor identification: masked email in
  `resourceType`/`resourceId`, via `resourceIdExpression`, exactly as
  the SPEC's judgment call proposed.** Confirmed the SpEL mechanism
  supports this: `AuditLogAspect.resolveResourceId` builds a
  `StandardEvaluationContext` (not the restricted `SimpleEvaluationContext`),
  which permits `T(...)` static type references — the existing
  `TenantController` precedent (`resourceIdExpression =
  "#request.tenantId()"`) already proves record-accessor SpEL against a
  request DTO parameter resolves correctly with this codebase's compiled
  parameter-name info. `resourceType = "auth-email"`,
  `resourceIdExpression =
  "T(br.com.conectabyte.knowly.observability.PiiMasker).maskEmail(#request.email())"`
  on all three `@AuditLog`-annotated auth endpoints. This never touches
  `actorUserId` (stays `null` pre-auth per REQ-10) and never puts a raw
  email anywhere (REQ-9) — `PiiMasker.maskEmail` is applied inline by the
  aspect itself, so the controller code doesn't need to change to
  support it.
- **Lockout is a separate `@AuditLog`-produced event, not folded into
  the triggering failure.** `FailedAttemptService.recordFailure` has no
  Spring-managed proxy boundary the aspect can wrap cleanly (it's a
  private Redis read/increment, not the AOP join point), and it doesn't
  throw — it just flips a Redis key. Rather than instrumenting
  `FailedAttemptService` itself (which would require a second
  `@AuditLog` on a non-controller method, a pattern not used elsewhere;
  every other `@AuditLog` usage in this codebase is on a controller or
  service method the caller directly awaits), the crossing-the-threshold
  event is detected in `AuthController` at the same point
  `recordFailure` is called: `FailedAttemptService.recordFailure` is
  changed to return `boolean` (true iff this call just caused the
  lockout to engage — i.e., the attempt count reached
  `maxAttempts`), and `AuthController` performs one manual
  `AuditEventRepository.save(...)` for `action = "auth.login.lockout"`
  when that boolean is `true`, immediately after the existing
  `failedAttemptService.recordFailure(...)` call, before throwing
  `InvalidCredentialsException`. This keeps the triggering failure's own
  `@AuditLog`-produced event (`FAILURE`) and the lockout event as two
  separate rows (SPEC's own stated preference), without adding an
  `@AuditLog` shape (controller-method-only) to a Redis-only service
  that doesn't fit it.
- **Action naming**, following the existing dotted convention
  (`tenant.permission.grant`, `staff.permission.grant`):
  `auth.login_request`, `auth.login_code_verify`,
  `auth.login_password_verify`, `auth.logout`, `auth.login.lockout`.
- **No changes to `AuthController`'s exception/timing logic** — this
  PLAN adds observability only; it does not touch
  `LoginCodeService.verify`, `OneTimePasswordService.verifyAndRotate`, or
  any of the constant-time comparison / dummy-hash logic that
  implements the already-declined timing-safety tradeoff. `@AuditLog`'s
  and the manual writes' extra work (one `INSERT`) happens identically
  on every branch (real vs. non-existent email), so it introduces no new
  conditional work keyed on account existence (REQ-8/REQ-12) — flagged
  explicitly per this task's instructions, and confirmed **not** to
  reopen that decision.

## Data schema

- `audit_events.outcome` (`VARCHAR(20)`, already wide enough): two new
  enum literals added to `AuditOutcome` (`FAILURE`, `LOCKED_OUT`) — no
  column-width migration needed, no migration at all (enum values are
  stored as their `.name()` string, not a DB-level `CHECK`/native enum
  per the existing `@Enumerated(EnumType.STRING)` mapping — confirmed by
  reading `AuditEvent.java`).
- `audit_events.metadata` (existing `JSON` column via
  `@JdbcTypeCode(SqlTypes.JSON)`): populated by `AuditLogAspect`/the
  manual auth writes with `{"sourceIp": "<masked address>"}`, only for
  the four auth call sites (`AuditLog.captureSourceIp() == true`) —
  every other `@AuditLog` consumer's `metadata` stays `null`, unchanged
  from before this feature. No migration.
- No new tables, no new columns.

## API contracts

No new/changed endpoints or DTOs — this feature only adds observability
around the four existing `AuthController` endpoints. For reference, the
endpoints instrumented:

| Method | Path | Audit action | Outcome values now possible |
|---|---|---|---|
| POST | `/api/auth/login-request` | `auth.login_request` | `SUCCESS`, `ERROR` |
| POST | `/api/auth/login-code/verify` | `auth.login_code_verify` | `SUCCESS`, `FAILURE`, `LOCKED_OUT`, `ERROR` |
| POST | `/api/auth/login-password/verify` | `auth.login_password_verify` | `SUCCESS`, `FAILURE`, `LOCKED_OUT`, `ERROR` |
| POST | `/api/auth/logout` | `auth.logout` (manual write) | `SUCCESS` only (unauthenticated calls never reach the handler — REQ-7) |
| (side effect of login-code/verify or login-password/verify) | n/a | `auth.login.lockout` (manual write) | `DENIED` |

## Dependencies

None new. `RequestContextHolder` and `ServletRequestAttributes` are
already transitively available via `spring-boot-starter-web` (already a
dependency); no `pom.xml` change.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/audit/AuditOutcome.java`
  (modify: add `FAILURE`, `LOCKED_OUT`)
- `src/main/java/br/com/conectabyte/knowly/audit/AuditLog.java`
  (modify: new `boolean captureSourceIp() default false` attribute)
- `src/main/java/br/com/conectabyte/knowly/audit/AuditLogAspect.java`
  (modify: new `catch` clauses for `InvalidCredentialsException`/
  `AccountLockedException`; `metadata` sourceIp capture in `record(...)`
  gated behind `auditLog.captureSourceIp()`, masked via
  `PiiMasker.maskIp`)
- `src/main/java/br/com/conectabyte/knowly/observability/PiiMasker.java`
  (modify: new `maskIp(String)` — IPv4 → `/24` truncation, IPv6 → `/48`
  truncation)
- `src/main/java/br/com/conectabyte/knowly/auth/AuthController.java`
  (modify: `@AuditLog(..., captureSourceIp = true)` on
  `requestLogin`/`verifyCode`/`verifyPassword`; manual
  `AuditEventRepository` write in `logout` and the lockout event both
  set `metadata` via `PiiMasker.maskIp` directly; constructor gains
  `AuditEventRepository`)
- `src/main/java/br/com/conectabyte/knowly/auth/FailedAttemptService.java`
  (modify: `recordFailure` returns `boolean` — `true` iff this call just
  crossed the lockout threshold)
- `src/test/java/br/com/conectabyte/knowly/audit/AuditLogAspectTest.java`
  (modify: new outcome-mapping cases, metadata sourceIp assertion)
- `src/test/java/br/com/conectabyte/knowly/auth/*` (new/modified
  integration tests per TASKS.md, one per endpoint)

## Testing strategy

- Unit: `AuditLogAspectTest` — `InvalidCredentialsException` →
  `FAILURE`, `AccountLockedException` → `LOCKED_OUT`, existing
  `DENIED`/`ERROR`/`SUCCESS` cases unchanged; `metadata` contains a
  masked `sourceIp` (never the raw remote address) when
  `captureSourceIp() == true` and a request context is present; a
  joinpoint with `captureSourceIp() == false` (the default — every
  non-auth `@AuditLog` usage today) never populates `metadata` at all,
  confirming the scoping doesn't leak to other consumers.
- Unit: `PiiMaskerTest` — `maskIp` zeroes the last octet for IPv4
  (`203.0.113.45` → `203.0.113.0`) and the last 80 bits for IPv6,
  handles `null`/blank the same defensive way `maskEmail` does.
- Unit: `FailedAttemptServiceTest` — `recordFailure` returns `false`
  below threshold, `true` exactly on the call that reaches
  `maxAttempts`.
- Integration (`@SpringBootTest`, mirrors existing
  `AuthenticationIntegrationTest`-style tests, Testcontainers Postgres +
  Redis already provisioned for this suite): one test per endpoint per
  SPEC acceptance criterion —
  - `login-request`: exactly one `AuditEvent` for both an existing and a
    non-existent email, same action/outcome shape, `actorUserId == null`,
    `resourceId` = masked email.
  - `login-code/verify` and `login-password/verify`: success →
    `SUCCESS` + real `actorUserId`; wrong code/password → `FAILURE` +
    `null` actor; locked-out rejection → `LOCKED_OUT` + `null` actor;
    crossing the threshold → an additional `auth.login.lockout` /
    `DENIED` event distinct from the triggering `FAILURE` event.
  - `logout`: authenticated call → one `auth.logout` `SUCCESS` event
    with the real actor; the existing pre-authenticated-401 case is
    confirmed to still produce zero audit events (no controller method
    invocation happens).
  - Assert no raw email appears in any `AuditEvent` row's `resourceId`
    or `metadata` for any of the above.
  - Assert no raw (unmasked) IP appears in any `AuditEvent` row's
    `metadata` — only the `/24`/`/48`-truncated form.
  - Assert a non-auth `@AuditLog` consumer (any existing
    tenant/staff/article/conversation action already covered by another
    feature's tests) still produces `metadata == null`, confirming
    `captureSourceIp`'s default-`false` scoping holds.
  - Assert timing: no new `Thread.sleep`/conditional branch was added
    keyed on account existence (a static check against the diff, not a
    runtime assertion — call out explicitly in the task).

## Decisions requiring a `DECISIONS.md` entry (Tier 2, written as part of this PLAN)

1. `@AuditLog`'s actor resolution happens strictly after `proceed()`
   returns — any future endpoint that clears authentication state as
   part of its own handler (like `logout`) cannot rely on `@AuditLog`
   for capturing the actor and needs a manual write instead.
2. `AuditEvent.metadata` is the generic home for per-event contextual
   data the aspect can derive from the ambient request (starting with
   `sourceIp`) — new fields should be added to this same JSON blob
   rather than new dedicated columns, unless a field needs to be
   indexed/queried directly.
3. **(Added 2026-07-26, post-AppSec review)** Source IP capture is
   masked (`/24`/`/48` truncation via `PiiMasker.maskIp`) and scoped to
   auth events only via a new `AuditLog.captureSourceIp()` opt-in flag
   (default `false`) — the original raw/system-wide capture described
   in item 2 above was reverted. See `DECISIONS.md`'s "source IP
   capture is masked ... and scoped to auth events only" entry for the
   full reasoning; this was a Tier 3 call explicitly delegated to the
   agents by the product owner after AppSec flagged the original
   approach, not decided unilaterally.

## Flag for human sign-off before implementation

Already resolved (see item 3 above): AppSec flagged the original
system-wide raw-IP capture as a Tier 3 security/privacy tradeoff; the
product owner was asked and explicitly delegated the choice among
AppSec's four options rather than picking one themselves. The agents'
final call (mask + scope to auth-only) and reasoning are recorded in
`DECISIONS.md`. No further sign-off pending on this point.

None found (beyond the above) that changes the authentication
timing-safety decision or
introduces a new, previously-uncovered security/privacy tradeoff: every
resolution above stays inside REQ-8/REQ-9/REQ-10/REQ-12's existing
guardrails, and the two `DECISIONS.md`-worthy items are ordinary Tier 2
"pick one, document it" calls with no exact precedent, not scope
changes or new exemptions. No stop-and-ask raised.
