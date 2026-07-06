# PLAN — Authentication (passwordless-first login)

## Architectural decisions

- New package `br.com.conectabyte.knowly.auth` holds everything for this
  feature.
- `User` is a JPA entity, persisted (not ephemeral) since it's core
  business state: `@Audited` (Envers) + JPA Auditing
  (`@CreatedDate`/`@LastModifiedDate`; `@CreatedBy`/`@LastModifiedBy` via a
  custom `AuditorAware<String>` that returns the authenticated user's id,
  or `"system"` for unauthenticated actions like this feature's own
  writes).
- Login codes are **not** persisted in Postgres: they're short-lived (10
  min), high-volume, and don't need history/audit as data — they live in
  Redis with a native TTL, keyed by email. This avoids DB bloat and needs
  no cleanup job.
- The one-time password **is** persisted on `User` (hash + issued-at),
  since it's part of the user's durable security state and must survive
  Redis restarts and be part of the audited history.
- Failed-attempt counters and lockouts live in Redis (`auth-failures:{email}`,
  `auth-lockout:{email}`), same reasoning as login codes — ephemeral,
  self-expiring via TTL, no new infrastructure (Redis is already
  provisioned).
- CAPTCHA request-velocity tracking also lives in Redis
  (`login-velocity:{ip}`, sliding counter with TTL).
- Login-request throttling (`LoginRequestThrottleService`, new): cooldown
  key `auth:login-code-cooldown:{email}` (TTL = cooldown) and an abuse
  counter `auth:login-request-count:{email}` (TTL = same window as the
  abuse lockout duration, so it can't accumulate forever). Both keyed by
  the *submitted* email regardless of whether it's a real account — same
  reasoning as `FailedAttemptService`, so this can run synchronously in
  `AuthController` (before publishing to RabbitMQ) without reopening the
  timing side-channel REQ-3a closed: cooldown/abuse state doesn't depend
  on account existence, only on the submitted string.
- `FailedAttemptService` gains `lockForAbuse(email)`, setting the *same*
  lockout key `verifyCode`/`verifyPassword` already check, just with a
  longer TTL — reuses `isLocked()` as-is, no changes needed on the
  verification side beyond resetting the new abuse counter on any attempt.
- CAPTCHA verification calls Cloudflare Turnstile's `siteverify` REST
  endpoint server-side using Spring's `RestClient` (no new HTTP client
  dependency needed).
- Sessions use `spring-boot-starter-session-data-redis` (already a
  dependency) — no new session mechanism to build. Concretely: on success,
  `AuthController` builds a `UsernamePasswordAuthenticationToken(email, null,
  List.of())`, sets it on a fresh `SecurityContext`, and persists it via
  `HttpSessionSecurityContextRepository#saveContext`, which creates the
  `HttpSession` (backed by Redis) and sets the `SESSION` cookie. No custom
  `UserDetails`/`AuthenticationProvider` was needed since there's no
  password-based `AuthenticationManager` flow to hook into here.
- `SecurityConfig` permits `/api/auth/**` (`permitAll`), but the CSRF
  exemption is scoped to the three concrete paths (`/api/auth/login-request`,
  `/api/auth/login-code/verify`, `/api/auth/login-password/verify`), not
  the wildcard — a future endpoint under `/api/auth/**` (e.g. logout)
  should not silently inherit CSRF exemption.
- Session fixation: `establishSession` calls `HttpServletRequest#changeSessionId()`
  when a session already exists before building the new authenticated
  `SecurityContext` — Spring Session's Redis-backed session supports this
  natively (creates a new id, carries attributes over, expires the old
  id). No-op when there's no pre-existing session (the normal case), since
  `HttpSessionSecurityContextRepository` creates a fresh one regardless.
- Cookie hardening: `server.servlet.session.cookie.same-site=lax` and
  `http-only` explicit (already the default, made explicit for clarity),
  plus `server.forward-headers-strategy=framework` so the `Secure`
  attribute (auto-detected from the request scheme) is correct behind a
  reverse proxy in production. `Secure` is intentionally left on
  auto-detection rather than hardcoded `true`, so local HTTP dev keeps
  working.
- Timing-safety on verify endpoints: `LoginCodeService.verify` and
  `OneTimePasswordService.verifyAndRotate` now always call
  `PasswordEncoder#matches` — against a real hash when one exists, against
  a constant dummy hash (computed once, at construction) otherwise — so
  the expensive BCrypt comparison always runs and response time no longer
  reveals whether a real code/password existed to compare against.
  `AuthController.verifyPassword` no longer short-circuits via
  `Optional#flatMap` when the user doesn't exist; it now always calls
  `verifyAndRotate` (which accepts a nullable `User`), for the same reason.
- CAPTCHA/velocity on verify endpoints: `verifyCode` and `verifyPassword`
  reuse the *same* `CaptchaService.recordRequestAndIsVelocityExceeded`
  mechanism as `login-request` — not a separate/second CAPTCHA concept —
  but each endpoint gets its own Redis counter, keyed by an `action`
  string (`login-request`, `login-code-verify`, `login-password-verify`),
  and the threshold is a parameter rather than a single shared property.
  This was a correction made during implementation: an initial version
  shared one per-IP counter and threshold across all three endpoints,
  which caused normal verify-retry traffic (and, in tests, ordinary test
  traffic) to trip the same budget tuned for the much-less-frequent
  `login-request` call. Verify endpoints get a higher threshold
  (`verify-velocity-threshold: 20` vs `velocity-threshold: 5`) since
  they're legitimately called more often per session. Turnstile's
  "managed" mode resolves silently for most real users; the visible
  challenge only surfaces under actual abuse-level volume.
- Passwords/codes are hashed with `PasswordEncoder` (BCrypt, already
  available via `spring-boot-starter-security`).
- Emails (login code, new one-time password) are sent via
  `spring-boot-starter-mail` (already a dependency), rendered with `jte`
  templates (already a dependency, currently used for web views — reused
  here for email bodies to avoid adding a second templating engine).
- Login-request processing (email lookup, code generation, email dispatch)
  runs asynchronously via RabbitMQ (already a dependency and already
  provisioned in `compose.yaml`, unused until now): `AuthController`
  publishes a `LoginRequestedEvent(email)` to a durable queue and returns
  `200` immediately; a `@RabbitListener` consumer does the actual
  email-existence-dependent work. This is what makes REQ-3a hold: the HTTP
  response time is now identical for existing and non-existing emails,
  because it no longer includes any of the work that differs between them.
  The CAPTCHA velocity check (REQ-4) still runs synchronously before
  publishing, since it doesn't depend on whether the email exists.
- Local SMTP: MailHog, added to `compose.yaml` as a proper managed service
  (it existed before only as an unmanaged orphan container) —
  `spring.mail.host`/`port` default to `localhost:1025` and are
  env-var-overridable for real SMTP in other environments.
- `management.health.mail.enabled=false`: Spring Boot's mail health
  contributor only recognizes concrete `JavaMailSenderImpl` beans; a
  Mockito-mocked `JavaMailSender` in tests breaks it (`'beans' must not be
  empty`). We don't want per-dependency health exposed under our
  restricted Actuator policy anyway, so it's off globally, not just in
  tests.

## New dependency

- `org.hibernate.orm:hibernate-envers` (matching the project's Hibernate
  version) — required for `@Audited`. No other new dependency.

## Data schema

Flyway migration `V1__create_users_table.sql`:

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  one_time_password_hash VARCHAR(255),
  one_time_password_issued_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
```

Flyway migration `V2__create_envers_audit_tables.sql`: Envers' `revinfo`
table plus `users_aud` (mirroring `users` + `rev`/`revtype` columns).
`spring.jpa.hibernate.ddl-auto` is set to `validate` — schema is always
Flyway-owned, Hibernate never auto-generates DDL, including for Envers.

Verified against a real Postgres (Testcontainers) — two details not
obvious up front, now load-bearing:
- `revinfo.rev` must come from an explicit sequence named `revinfo_seq`
  (Envers' default generator looks it up by that exact name — a
  `BIGSERIAL`'s implicit sequence has a different name and fails schema
  validation).
- That sequence must be `INCREMENT BY 50`, matching Hibernate's default
  allocation size for Envers' revision id generator (a mismatch here also
  fails validation, with a very literal error message pointing at it).

## API contracts

All under `/api/auth`. All error responses share the shape
`{ "code": "<STABLE_CODE>" }` (English, stable identifiers — see
constitution's frontend-integration section).

- `POST /api/auth/login-request`
  - Body: `{ "email": string, "captchaToken"?: string }`
  - `200`: `{}` always, regardless of whether the email exists (REQ-1–REQ-3).
  - `400` `CAPTCHA_REQUIRED`: velocity threshold exceeded, no/invalid token
    (REQ-4).
- `POST /api/auth/login-code/verify`
  - Body: `{ "email": string, "code": string, "captchaToken"?: string }`
  - `200`: session cookie set (REQ-5).
  - `400` `CAPTCHA_REQUIRED`: velocity threshold exceeded, no/invalid token
    (REQ-6b).
  - `401` `INVALID_CREDENTIALS` (REQ-6).
  - `429` `ACCOUNT_LOCKED` (REQ-10).
- `POST /api/auth/login-password/verify`
  - Body: `{ "email": string, "password": string, "captchaToken"?: string }`
  - `200`: session cookie set (REQ-7).
  - `400` `CAPTCHA_REQUIRED`: velocity threshold exceeded, no/invalid token
    (REQ-8b).
  - `401` `INVALID_CREDENTIALS` (REQ-8).
  - `429` `ACCOUNT_LOCKED` (REQ-10).

## Configuration (application.yaml)

New, explicit (no magic numbers in code):

```yaml
knowly:
  auth:
    login-code:
      length: 6
      ttl: 10m
      resend-cooldown: 30s
    one-time-password:
      length: 12
      ttl: 15d
    lockout:
      max-attempts: 3
      duration: 15m
      abuse-request-threshold: 5
      abuse-duration: 1h
    captcha:
      velocity-threshold: 5
      verify-velocity-threshold: 20
      velocity-window: 5m
      turnstile-secret: ${TURNSTILE_SECRET_KEY:?TURNSTILE_SECRET_KEY is required}
```

Standard Spring properties (not under `knowly.*`), for cookie hardening:

```yaml
server:
  forward-headers-strategy: framework
  servlet:
    session:
      cookie:
        http-only: true
        same-site: lax
```

## Package/file structure

```
src/main/java/br/com/conectabyte/knowly/auth/
  User.java
  UserRepository.java
  AuthController.java
  LoginCodeService.java          # Redis: generate/verify/invalidate codes
  OneTimePasswordService.java    # generate/verify/rotate on User
  FailedAttemptService.java      # Redis: counters + lockout
  LoginRequestThrottleService.java # Redis: resend cooldown + abuse counter
  CaptchaService.java            # Turnstile siteverify call
  MailService.java               # sends code/new-password emails via jte
  LoginRequestedEvent.java       # RabbitMQ message payload (email)
  LoginRequestPublisher.java     # publishes LoginRequestedEvent
  LoginRequestListener.java      # @RabbitListener: lookup + generate + send
  dto/LoginRequestDto.java
  dto/VerifyCodeRequestDto.java
  dto/VerifyPasswordRequestDto.java
  dto/AuthErrorResponseDto.java
  exception/InvalidCredentialsException.java
  exception/AccountLockedException.java
  exception/CaptchaRequiredException.java
  exception/AuthExceptionHandler.java   # @RestControllerAdvice → HTTP status mapping
config/
  JpaAuditingConfig.java          # @EnableJpaAuditing + AuditorAware<String> bean
  AuthRabbitConfig.java           # declares the login-requested queue + JSON message converter
src/main/resources/db/migration/
  V1__create_users_table.sql
  V2__create_envers_audit_tables.sql
src/main/jte/mail/
  login-code.jte
  new-one-time-password.jte
```

## Testing strategy

- `LoginCodeServiceTest`, `OneTimePasswordServiceTest`,
  `FailedAttemptServiceTest`: unit tests against a real Redis via
  Testcontainers (already provisioned) — no mocking of Redis semantics
  (TTL behavior matters).
- `AuthControllerIntegrationTest`: full context via Testcontainers, covers
  every REQ end to end, including the indistinguishability requirements
  (REQ-3, lockout behavior identical for existing/non-existing emails) and
  the audit log assertions (structured log/event emitted per attempt).
  Login-request assertions on email dispatch now poll (Awaitility) since
  the work happens on a `@RabbitListener` thread, not inline with the HTTP
  request — the response itself is asserted synchronously and immediately
  (that's the point of REQ-3a).
- `LoginRequestListenerTest`: unit/slice test asserting the listener looks
  up the user, generates a code, and calls `MailService` — independent of
  the HTTP layer.
- `LoginRequestThrottleServiceTest`: unit test against real Redis
  (Testcontainers) covering cooldown suppression, abuse-counter
  accumulation and reset, and triggering `FailedAttemptService.lockForAbuse`
  at the threshold.
- `CaptchaServiceTest`: unit test with a mocked Turnstile HTTP response
  (WireMock or a fake `RestClient`), since we don't want tests hitting
  Cloudflare's real API.
- Timing-safety tests (`LoginCodeServiceTest`, `OneTimePasswordServiceTest`):
  assert that a miss (no code/password) and a real-but-wrong comparison
  both invoke `PasswordEncoder#matches` — verified via a `PasswordEncoder`
  spy/mock rather than by measuring wall-clock time (timing assertions in
  a test suite are inherently flaky; asserting the *call happened* is what
  actually matters).
- `AuthControllerIntegrationTest` additions: session-id rotation (compare
  the session cookie value before/after login when a pre-login session
  exists), CSRF exemption scoped to exactly the three endpoints (a fourth,
  made-up protected POST endpoint would still require a CSRF token — or,
  simpler, assert the `SecurityFilterChain`'s configured exemption list
  directly), and CAPTCHA-required responses from both verify endpoints
  under simulated high velocity.
