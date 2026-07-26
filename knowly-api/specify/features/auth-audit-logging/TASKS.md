# TASKS — auth-audit-logging

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. `AuditOutcome`: add `FAILURE`, `LOCKED_OUT` (additive, no
      changes to existing values). Test (Red first in
      `AuditLogAspectTest`): a joinpoint throwing
      `InvalidCredentialsException` records `FAILURE`; a joinpoint
      throwing `AccountLockedException` records `LOCKED_OUT`. Then wire
      the two new `catch` clauses into `AuditLogAspect.logAudit` (Green).
      Confirm existing `DENIED`/`ERROR`/`SUCCESS` `AuditLogAspectTest`
      cases still pass unchanged.
- [x] 2. ~~`AuditLogAspect.record`: capture `sourceIp` into `metadata` as
      `{"sourceIp": "<remote address>"}` via
      `RequestContextHolder.currentRequestAttributes()`~~ **Superseded by
      task 2a/2b below** (AppSec flagged this as raw PII captured
      system-wide — see `DECISIONS.md`'s 2026-07-26 entry). Original
      implementation left in place only as the starting point for 2a/2b,
      not as the final behavior.
- [x] 2a. **(New, post-AppSec review)** `PiiMasker.maskIp(String)`: add
      alongside the existing `maskEmail`. Test (Red, `PiiMaskerTest`):
      IPv4 `"203.0.113.45"` → `"203.0.113.0"` (last octet zeroed); IPv6
      (e.g. `"2001:db8:85a3::8a2e:370:7334"`) → last 80 bits zeroed
      (`/48` retained); `null`/blank input handled the same defensive
      way `maskEmail` already is (return `""`, don't throw). Then
      implement (Green).
- [x] 2b. **(New, post-AppSec review)** Scope + mask `sourceIp` capture:
      add `boolean captureSourceIp() default false` to the `@AuditLog`
      annotation. In `AuditLogAspect.record`, only resolve/write
      `metadata.sourceIp` when `auditLog.captureSourceIp()` is `true`,
      and mask the resolved address via `PiiMasker.maskIp` before
      writing it (never the raw `getRemoteAddr()` value from here on).
      Test (Red, `AuditLogAspectTest`): a joinpoint with
      `captureSourceIp = true` invoked within a mocked request context
      produces an `AuditEvent` whose `metadata.sourceIp` is the
      *masked* form (not the raw mock address); a joinpoint with
      `captureSourceIp = false` (the default) never populates `metadata`
      even when a request context is present; a joinpoint with
      `captureSourceIp = true` but no request context available does not
      throw (`metadata` stays `null`). Then implement (Green). Confirm
      no existing non-auth `@AuditLog` usage anywhere in the codebase
      sets `captureSourceIp = true` (grep check) — they must all default
      to `false` and therefore keep producing `metadata == null`, exactly
      as before this feature existed.
- [x] 3. `FailedAttemptService.recordFailure` returns `boolean` (`true`
      iff this call is the one that reaches `maxAttempts` and engages the
      lockout). Test (Red) in `FailedAttemptServiceTest`: calls below
      threshold return `false`; the exact call that reaches
      `maxAttempts` returns `true`; the call after that (already locked)
      is unaffected (existing `isLocked` behavior unchanged). Then
      implement (Green). Update the one existing caller
      (`AuthController`, currently ignoring the return value) to compile.
- [x] 4. `POST /api/auth/login-request` coverage (REQ-1, REQ-8, REQ-9,
      REQ-10). Test (Red, integration): a call with an existing email and
      a call with a non-existent email each produce exactly one
      `AuditEvent` with `action = "auth.login_request"`,
      `outcome = SUCCESS`, `actorUserId = null`,
      `resourceType = "auth-email"`, `resourceId` equal to
      `PiiMasker.maskEmail(email)` (never the raw email), and identical
      shape between the two cases. Then add
      `@AuditLog(action = "auth.login_request", resourceType =
      "auth-email", resourceIdExpression =
      "T(br.com.conectabyte.knowly.observability.PiiMasker).maskEmail(#request.email())")`
      to `AuthController.requestLogin` (Green).
      **Follow-up (post-AppSec review, do after task 2b):** add
      `captureSourceIp = true` to this same annotation; extend the test
      to assert `metadata.sourceIp` is present and masked (`/24`/`/48`
      form).
- [x] 5. `POST /api/auth/login-code/verify` success + failure coverage
      (REQ-2, REQ-9, REQ-10, REQ-11). Test (Red, integration): a correct
      code produces one `AuditEvent` with `outcome = SUCCESS` and the
      real, resolved `actorUserId`; a wrong code produces one
      `AuditEvent` with `outcome = FAILURE` and `actorUserId = null`;
      both use the masked-email `resourceId` pattern from task 4; no raw
      email appears anywhere in either row. Then add the same
      `@AuditLog` annotation shape (action
      `auth.login_code_verify`) to `AuthController.verifyCode` (Green).
      **Follow-up (post-AppSec review, do after task 2b):** add
      `captureSourceIp = true` here too, same assertion addition as
      task 4.
- [x] 6. `POST /api/auth/login-password/verify` success + failure
      coverage (REQ-3, REQ-9, REQ-10, REQ-11) — same test shape as task
      5, action `auth.login_password_verify`, applied to
      `AuthController.verifyPassword`.
      **Follow-up (post-AppSec review, do after task 2b):** same
      `captureSourceIp = true` addition as tasks 4/5.
- [x] 7. Lockout-rejection coverage (REQ-5). Test (Red, integration): with
      the account already locked out, `login-code/verify` and
      `login-password/verify` each produce one `AuditEvent` with
      `outcome = LOCKED_OUT` (not `FAILURE`) and `actorUserId = null` —
      relies on task 1's `AccountLockedException → LOCKED_OUT` mapping
      already being wired through the `@AuditLog` added in tasks 5/6, no
      new annotation needed. Green once tasks 1/5/6 are in place —
      if this test is red at that point, the exception-mapping order in
      `AuditLogAspect.logAudit` is wrong (fix that, not this test).
- [x] 8. Lockout-threshold-crossing coverage (REQ-4). Test (Red,
      integration): submitting wrong codes/passwords until the attempt
      count reaches `maxAttempts` produces, on that specific call, *two*
      audit rows — the triggering verify's own `FAILURE` event (from
      task 5/6's `@AuditLog`) and a separate `auth.login.lockout`
      `DENIED` event; earlier failed attempts (below threshold) produce
      only the `FAILURE` event. Then implement: in both
      `AuthController.verifyCode`/`verifyPassword`, after
      `failedAttemptService.recordFailure(request.email())` returns
      `true`, manually save an `AuditEvent(null, null,
      "auth.login.lockout", "auth-email",
      PiiMasker.maskEmail(request.email()), AuditOutcome.DENIED)` via
      `AuditEventRepository` before throwing `InvalidCredentialsException`
      (Green). Inject `AuditEventRepository` into `AuthController`'s
      constructor.
      **Follow-up (post-AppSec review, do after task 2b):** this manual
      write also sets `metadata = {"sourceIp": PiiMasker.maskIp(...)}`
      (resolved from `RequestContextHolder` the same way the aspect
      does, since this is a manual write, not `@AuditLog`-driven) —
      extend the test to assert the masked IP is present on this event
      too.
- [x] 9. `POST /api/auth/logout` coverage (REQ-6, REQ-7, REQ-11). Test
      (Red, integration): an authenticated logout call produces exactly
      one `AuditEvent` with `action = "auth.logout"`,
      `outcome = SUCCESS`, and the real, resolved `actorUserId` of the
      session that logged out; confirm (via the existing 401 test path,
      no new one needed) that an unauthenticated logout call — already
      rejected before reaching the controller — produces zero audit
      events. Then implement: in `AuthController.logout`, resolve the
      authenticated user's id from `SecurityContextHolder` *before*
      calling `SecurityContextLogoutHandler.logout(...)`, and manually
      save the `AuditEvent` with that id right after (Green) — do **not**
      use `@AuditLog` here per PLAN.md's documented infeasibility
      (`SecurityContextLogoutHandler` clears the context before the
      aspect would resolve the actor).
      **Follow-up (post-AppSec review, do after task 2b):** this manual
      write also sets `metadata = {"sourceIp": PiiMasker.maskIp(...)}`,
      same as the lockout write in task 8.
- [x] 10. PII/symmetry regression sweep (REQ-9, REQ-12). Test: a single
      integration test asserting, across every `AuditEvent` produced by
      tasks 4–9's scenarios, that no row's `resourceId` or `metadata`
      contains the literal submitted email string (only the masked
      form) — a straightforward substring-absence assertion, not a new
      mechanism. Manually re-review the diff for tasks 4–9 (not a runtime
      test) to confirm no new conditional branch, extra query, or extra
      work was added keyed on whether the email corresponds to a real
      account — record the confirmation in this task's completion note.
      **Follow-up (post-AppSec review, do after task 2b):** extend this
      same sweep to assert no row's `metadata` contains a raw
      (unmasked) IP — only the `/24`/`/48`-truncated form — across all
      of tasks 4/5/6/8/9's scenarios.
- [x] 11. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
      full suite is green (must include tasks 2a/2b's new/modified
      tests and the follow-ups added to tasks 4/5/6/8/9/10 above).
- [x] 12. Hand off to `qa-test-automation` and `appsec` agents for
      **re-**review before commit (this is a second pass — the first
      `appsec` review is what produced this fix): `qa-test-automation`
      re-checks every SPEC.md acceptance-criteria checkbox against the
      finished tests; `appsec` specifically re-verifies (a) `metadata`
      never contains a raw IP for any `@AuditLog` consumer, auth or
      otherwise, (b) no non-auth `@AuditLog` usage anywhere in the
      codebase has `captureSourceIp = true` (the scoping actually
      holds, not just in the four auth call sites this PLAN touches),
      and (c) REQ-9/REQ-10/REQ-12 (no PII leak, no actor leak pre-auth,
      no new timing side-channel) still hold with the masked/scoped
      version. Address findings, then commit (Conventional Commits, per
      root `constitution.md`).
- [x] 13. Update `PLAN.md`/`PROJECT_STATUS.md` if any decision changed
      during implementation, and confirm the `DECISIONS.md` entries were
      actually appended. **Done as part of this AppSec-driven revision**
      (2026-07-26): `PLAN.md` updated (source-IP section revised, new
      `Decisions requiring a DECISIONS.md entry` item 3, flag-for-sign-off
      section resolved); `DECISIONS.md` amended (original metadata entry
      annotated as superseded-in-part) plus a new entry added recording
      the final mask+scope decision and the PO's explicit delegation of
      the choice. `PROJECT_STATUS.md` still needs a line noting this
      feature's audit-log work is implemented but tasks 2a/2b/11/12
      (mask+scope fix, verification, re-review) are pending — add when
      picking this feature back up for implementation.
