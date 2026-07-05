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
- CAPTCHA verification calls Cloudflare Turnstile's `siteverify` REST
  endpoint server-side using Spring's `RestClient` (no new HTTP client
  dependency needed).
- Sessions use `spring-boot-starter-session-data-redis` (already a
  dependency) — no new session mechanism to build.
- Passwords/codes are hashed with `PasswordEncoder` (BCrypt, already
  available via `spring-boot-starter-security`).
- Emails (login code, new one-time password) are sent via
  `spring-boot-starter-mail` (already a dependency), rendered with `jte`
  templates (already a dependency, currently used for web views — reused
  here for email bodies to avoid adding a second templating engine).

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
  - Body: `{ "email": string, "code": string }`
  - `200`: session cookie set (REQ-5).
  - `401` `INVALID_CREDENTIALS` (REQ-6).
  - `429` `ACCOUNT_LOCKED` (REQ-10).
- `POST /api/auth/login-password/verify`
  - Body: `{ "email": string, "password": string }`
  - `200`: session cookie set (REQ-7).
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
    one-time-password:
      length: 12
      ttl: 15d
    lockout:
      max-attempts: 3
      duration: 15m
    captcha:
      velocity-threshold: 5
      velocity-window: 5m
      turnstile-secret: ${TURNSTILE_SECRET_KEY:?TURNSTILE_SECRET_KEY is required}
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
  CaptchaService.java            # Turnstile siteverify call
  MailService.java               # sends code/new-password emails via jte
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
- `CaptchaServiceTest`: unit test with a mocked Turnstile HTTP response
  (WireMock or a fake `RestClient`), since we don't want tests hitting
  Cloudflare's real API.
