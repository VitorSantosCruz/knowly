# SPEC — Authentication (passwordless-first login)

## Context and motivation

knowly needs a login mechanism that doesn't rely on a memorized persistent
password. A user identifies themselves by email, then completes login
either with a one-time code sent to that email, or with a one-time
password (previously emailed to them) — using either one lets them in, and
using the password also rotates it for next time.

This SPEC covers **authentication only**: establishing "who is this user".
Multi-tenancy, roles, and authorization are explicitly out of scope here
(see "Out of scope") but the data model must not preclude them later.

## User stories

- As a user, I want to log in with just my email and a code sent to it, so
  I don't need to remember a password.
- As a user, I want the option to use a password I already have (from a
  previous login) if I don't have easy access to my email at that exact
  moment.
- As an attacker, I should never be able to tell whether a given email
  belongs to a real account, nor brute-force my way into one.

## Requirements (EARS/GEARS)

### Requesting a login (email submission)

- **REQ-1 [Ubiquitous]** The system shall expose an endpoint that accepts
  an email address and, regardless of whether that email exists in the
  system, shall respond with the same generic success response.
- **REQ-2 [Event-Driven]** When the submitted email exists in the system,
  the system shall generate a one-time login code (6 digits, numeric,
  expires after 10 minutes, single-use, stored hashed) and email it to that
  address.
- **REQ-3 [Unwanted Behavior]** If the submitted email does not exist in
  the system, then the system shall not send any email, so the response is
  indistinguishable from REQ-2's case.
- **REQ-3a [Ubiquitous]** The system shall respond to a login request
  (REQ-1) before performing any of the work that differs based on whether
  the email exists (code generation, hashing, email dispatch) — that work
  happens asynchronously, off the request/response cycle, so response
  timing cannot be used to infer whether an email is registered.
- **REQ-4a [State-Driven]** While an email is within its resend cooldown
  (30 seconds since its last generated code), the system shall respond to
  a new login request with the same generic success response (REQ-1)
  without generating or sending a new code — applied identically whether
  or not the email corresponds to a real account.
- **REQ-4b [Event-Driven]** When an email accumulates 5 login requests
  (REQ-1) that actually resulted in a new code being generated (i.e., not
  suppressed by REQ-4a) without a single login-code or one-time-password
  verification attempt (REQ-5, REQ-6, REQ-7, REQ-8) in between, the system
  shall lock that email (same lockout as REQ-10) for an extended duration
  (1 hour).
- **REQ-4c [Unwanted Behavior]** If any login-code or one-time-password
  verification is attempted for an email (REQ-5, REQ-6, REQ-7, REQ-8),
  then the system shall decrement that email's request-without-verification
  counter (REQ-4b) by one, regardless of whether the verification
  succeeded — a full reset to zero would let an attacker send requests
  just under the REQ-4b threshold, throw away one verification attempt to
  clear the counter, and repeat indefinitely without ever triggering the
  lockout; a one-for-one decrement still lets a genuine user retry without
  being punished, while a request-heavy/verify-light pattern still
  converges on the threshold.
- **REQ-4 [Event-Driven]** When the request volume/velocity from a given
  source exceeds the configured threshold, the system shall require a
  valid CAPTCHA (Cloudflare Turnstile) token on the request, and shall
  reject the request with a specific "captcha required" error code if it's
  missing or invalid.

### Verifying a login code

- **REQ-5 [Event-Driven]** When a client submits an email and a login code
  that matches the current, non-expired, unused code for that email, the
  system shall establish a session (see REQ-12) and invalidate the code.
- **REQ-6 [Unwanted Behavior]** If the submitted code is wrong, expired, or
  the email has no pending code (including when the email doesn't exist),
  then the system shall respond with the same generic "invalid credentials"
  error code and increment that email's failed-attempt counter (REQ-9).
- **REQ-6a [Ubiquitous]** The system shall take the same amount of time to
  reject a wrong code regardless of whether the email has a pending code —
  the password-hash comparison always runs, against a constant dummy hash
  when there is no real one, so response time cannot be used to infer
  whether the email has an account or a pending code.
- **REQ-6b [Event-Driven]** When the request volume/velocity from a given
  source exceeds the configured threshold, the system shall require a
  valid CAPTCHA token on the code-verification request, same as REQ-4.

### Verifying a one-time password

- **REQ-7 [Event-Driven]** When a client submits an email and a one-time
  password that matches the current, unused, unexpired password on file for
  that email, the system shall establish a session (REQ-12), invalidate
  that password, generate a new one-time password (12 characters,
  mixed-case alphanumeric, excluding ambiguous characters `0/O/1/l/I`,
  stored hashed), and email the new one to that address.
- **REQ-8 [Unwanted Behavior]** If the submitted password is wrong or the
  email has no valid password on file (including when the email doesn't
  exist), then the system shall respond with the same generic "invalid
  credentials" error code as REQ-6 and increment that email's
  failed-attempt counter (REQ-9).
- **REQ-8a [Ubiquitous]** The system shall take the same amount of time to
  reject a wrong password regardless of whether the email exists or has a
  valid password on file — same mechanism and reasoning as REQ-6a.
- **REQ-8b [Event-Driven]** When the request volume/velocity from a given
  source exceeds the configured threshold, the system shall require a
  valid CAPTCHA token on the password-verification request, same as REQ-4.

### Abuse prevention

- **REQ-9 [Ubiquitous]** The system shall maintain a single failed-attempt
  counter per email, shared between login-code and one-time-password
  verification (REQ-6, REQ-8), regardless of whether the email corresponds
  to a real account.
- **REQ-10 [Event-Driven]** When an email's failed-attempt counter reaches
  3, the system shall lock that email's authentication for 15 minutes,
  during which any code/password submitted for it — correct or not, and
  regardless of the email's existence — shall be rejected with a "locked,
  try again later" error code.
- **REQ-11 [Unwanted Behavior]** If a login code or one-time password is
  verified successfully, then the system shall reset that email's
  failed-attempt counter to zero.

### Session

- **REQ-12 [Ubiquitous]** On successful authentication (REQ-5 or REQ-7),
  the system shall establish a server-side session (Redis-backed) and set
  an httpOnly, secure session cookie on the response.
- **REQ-12a [Unwanted Behavior]** If a session already exists at the time
  of successful authentication (e.g., an anonymous pre-login session),
  then the system shall rotate its identifier before establishing the
  authenticated session, preventing session fixation.
- **REQ-12b [Ubiquitous]** The system shall exempt only the three
  authentication endpoints (`login-request`, `login-code/verify`,
  `login-password/verify`) from CSRF protection — no other endpoint,
  present or future, shall inherit this exemption via a shared path
  prefix.

### One-time password lifecycle

- **REQ-13 [State-Driven]** While a one-time password has been issued for
  more than 15 days, the system shall treat it as expired and reject it
  with the same generic "invalid credentials" error code as REQ-8 if
  submitted.
- **REQ-14 [Event-Driven]** When a user logs in successfully via a login
  code (REQ-5) and does not currently have a valid (unexpired, unused)
  one-time password, the system shall generate a new one-time password and
  email it to the user.
- **REQ-15 [Unwanted Behavior]** If a user logs in successfully via a login
  code (REQ-5) while still holding a valid, unexpired, unused one-time
  password, then the system shall leave that password untouched — a
  one-time password is only replaced when it is actually used (REQ-7) or
  when it has expired and the user needed the code path to get back in
  (REQ-14).

## Non-functional requirements

- Security: one-time codes/passwords are always stored hashed (never
  plaintext); backend responses use stable English error codes, never
  freeform text (the frontend localizes user-facing messages — see
  `knowly-app` constitution).
- Audit: every authentication attempt (success, wrong code/password,
  lockout triggered, lockout rejection) must emit a structured audit log
  event per the project constitution's "Observability and audit" section,
  including the email attempted, source IP (for audit/forensics only, never
  as a blocking key), and outcome.
- Abuse prevention: CAPTCHA verification and per-email counters must be
  implemented without introducing new persistent infrastructure — reuse the
  existing Redis instance already provisioned in `compose.yaml`.
- Timing side-channel: the login-request endpoint (REQ-1) must not let an
  attacker infer account existence by measuring response time. Implemented
  by returning the response before doing any email-existence-dependent
  work (REQ-3a) — reuses the existing RabbitMQ instance already provisioned
  in `compose.yaml`, no new infrastructure. The same class of leak is
  closed on both verification endpoints via REQ-6a/REQ-8a — otherwise an
  attacker could recover the same information login-request now protects,
  just through a different endpoint.
- Session cookie: `HttpOnly` always; `SameSite=Lax` and `Secure`
  auto-detected correctly behind a reverse proxy (requires
  `server.forward-headers-strategy=framework`) — explicit rather than
  relying on framework defaults nobody re-checks.

## Acceptance criteria

- [x] Submitting an existing email sends a 6-digit code by email; response
      is identical (shape, generic wording) to submitting a non-existing
      email.
- [x] Submitting a non-existing email sends no email, but behaves
      identically from the client's perspective.
- [x] The login-request endpoint responds before the email-existence check
      and code generation/dispatch happen, so response time doesn't
      correlate with account existence.
- [x] Requesting a code twice for the same email within 30 seconds only
      generates/sends a code the first time; the second request still gets
      the generic success response.
- [x] 5 code requests for the same email with no verification attempt in
      between lock that email for 1 hour (not just the usual 15 minutes).
- [x] Attempting to verify a code or password — successfully or not —
      resets that email's request-without-verification counter.
- [x] Correct, unexpired code logs the user in and sets a session cookie.
- [x] Correct, unexpired one-time password logs the user in, invalidates
      that password, and emails a new 12-character one-time password.
- [x] 3 wrong code/password submissions for the same email (in any
      combination) lock that email for 15 minutes; a 4th attempt — even
      with the correct code/password — is rejected during the lockout.
- [x] The lockout and error behavior for a non-existing email is
      indistinguishable from a real, existing email.
- [x] After a configured request volume/velocity threshold, all three
      authentication endpoints (login-request and both verify endpoints)
      require a valid Turnstile token.
- [x] Rejecting a wrong code/password takes the same amount of time
      whether or not the email has a real pending code/password.
- [x] A pre-existing (pre-login) session's identifier changes after a
      successful login.
- [x] Only the three authentication endpoints are exempt from CSRF; a
      request to any other endpoint without a CSRF token is rejected as
      before.
- [x] A one-time password stops working 15 days after being issued.
- [x] Logging in via code while holding no valid one-time password (never
      had one, or it expired) triggers issuing and emailing a new one.
- [x] Logging in via code while still holding a valid, unused, unexpired
      one-time password does not change that password.
- [x] Every authentication decision (success, failure, lockout) appears as
      a structured audit log entry queryable by email, trace id, and
      outcome.

## Out of scope

- User registration/provisioning: this SPEC assumes `User` rows (with an
  initial one-time password) already exist. How they get created (signup,
  invite, admin action) is a separate future feature.
- Multi-tenancy, roles, and authorization: this feature only establishes
  identity. Which tenant(s)/role(s) a user has, and what they're allowed to
  do, is resolved by later features on top of the session this SPEC
  creates.
- Logout, session refresh/expiry policy, and "remember me": not addressed
  here.
- Password reset / account recovery flows beyond what's described above
  (the one-time password mechanism already acts as the recovery path).
