# TASKS — Authentication (passwordless-first login)

> Each task is small enough to be its own commit (Conventional Commits,
> per constitution). Follow TDAD: test first (Red), then minimal code
> (Green), for every task that touches behavior.

- [x] 1. Add `hibernate-envers` dependency to `pom.xml`.
- [x] 2. Add `knowly.auth.*` configuration properties (login code, one-time
      password, lockout, captcha) to `application.yaml` + a
      `@ConfigurationProperties` class with a test asserting binding.
- [x] 3. Create `JpaAuditingConfig` (`@EnableJpaAuditing` +
      `AuditorAware<String>`) + test.
- [x] 4. Write Flyway migration `V1__create_users_table.sql` and
      `V2__create_envers_audit_tables.sql`; set
      `spring.jpa.hibernate.ddl-auto=validate`.
- [x] 5. Create `User` entity (`@Audited`, JPA auditing fields) +
      `UserRepository` + repository test (Testcontainers) asserting the
      unique case-insensitive email index.
- [x] 6. Write `LoginCodeServiceTest` covering generate/verify/invalidate/
      expiry (Red), then implement `LoginCodeService` (Redis-backed)
      (Green).
- [x] 7. Write `FailedAttemptServiceTest` covering counter increment,
      lockout at 3, reset on success, shared counter across mechanisms
      (Red), then implement `FailedAttemptService` (Green).
- [x] 8. Write `OneTimePasswordServiceTest` covering
      generate/verify/rotate-on-use/15-day-expiry (Red), then implement
      `OneTimePasswordService` (Green).
- [x] 9. Write `CaptchaServiceTest` with a mocked Turnstile HTTP response
      (Red), then implement `CaptchaService` (Green).
- [x] 10. Write `MailServiceTest` (Red), then implement `MailService` +
       `login-code.jte` and `new-one-time-password.jte` templates (Green).
- [x] 14. Write a test for `AuthExceptionHandler` asserting each custom
       exception maps to the correct HTTP status/code (Red), then
       implement it (Green).
- [x] 11. Write `AuthControllerIntegrationTest` cases for REQ-1–REQ-4
       (`POST /login-request`, including indistinguishable response and
       `CAPTCHA_REQUIRED`) (Red), then implement that endpoint (Green).
- [x] 12. Write `AuthControllerIntegrationTest` cases for REQ-5, REQ-6,
       REQ-9, REQ-10, REQ-11, REQ-14, REQ-15 (`POST /login-code/verify`)
       (Red), then implement that endpoint (Green).
- [x] 13. Write `AuthControllerIntegrationTest` cases for REQ-7, REQ-8,
       REQ-9, REQ-10, REQ-11, REQ-13 (`POST /login-password/verify`) (Red),
       then implement that endpoint (Green).
- [x] 15. Add structured audit logging (trace id, email, outcome) to every
       decision point in `AuthController`/services; add a test asserting
       the log/audit event is emitted for at least one success and one
       failure path.
- [x] 16. Run `./mvnw verify` (Spotless + full suite) and confirm it's
       green.
- [x] 17. Update `PLAN.md` if any decision changed during implementation.
- [x] 18. Write `LoginRequestListenerTest` (Red), then implement
       `LoginRequestedEvent`, `AuthRabbitConfig` (queue + JSON converter),
       `LoginRequestPublisher`, and `LoginRequestListener` (Green) — moves
       REQ-2's lookup/generate/send work off the request thread.
- [x] 19. Update `AuthController.requestLogin` to publish via
       `LoginRequestPublisher` and return immediately after the CAPTCHA
       check; update `AuthControllerIntegrationTest`'s login-request cases
       to assert immediate response + poll (Awaitility) for the async
       side effect (Red → Green).
- [x] 20. Run `./mvnw verify` and confirm it's green.
- [x] 21. Add `resend-cooldown` to `login-code` config and
       `abuse-request-threshold`/`abuse-duration` to `lockout` config +
       update `AuthPropertiesTest`.
- [x] 22. Add `FailedAttemptService.lockForAbuse(email)` + test (Red →
       Green): same lockout key as the 3-strikes case, configurable
       duration.
- [x] 23. Write `LoginRequestThrottleServiceTest` covering cooldown
       suppression, abuse counter accumulation, reset on any verify
       attempt, and triggering `lockForAbuse` at the threshold (Red), then
       implement `LoginRequestThrottleService` (Green).
- [x] 24. Wire `LoginRequestThrottleService` into `AuthController`:
       `requestLogin` checks cooldown/lockout before publishing and calls
       `recordRequest` after; `verifyCode`/`verifyPassword` call
       `recordVerifyAttempt` on every attempt. Update
       `AuthControllerIntegrationTest` with cases for REQ-4a–REQ-4c (Red →
       Green).
- [x] 25. Run `./mvnw verify` and confirm it's green.
- [x] 26. Update `PLAN.md` if any decision changed during implementation.
- [ ] 27. Fix timing side-channel on verify (REQ-6a/REQ-8a): add a
       constant dummy hash to `LoginCodeService` and
       `OneTimePasswordService`, always call `PasswordEncoder#matches`
       even on a miss; make `OneTimePasswordService.verifyAndRotate`
       accept a nullable `User`; update `AuthController.verifyPassword` to
       stop short-circuiting via `Optional#flatMap`. Tests first (assert
       `matches` is invoked on the miss path too), then implement.
- [x] 28. Add CAPTCHA/velocity to verify endpoints (REQ-6b/REQ-8b):
       `captchaToken` field on `VerifyCodeRequestDto`/
       `VerifyPasswordRequestDto`; `verifyCode`/`verifyPassword` call the
       existing `CaptchaService.recordRequestAndIsVelocityExceeded(ip)`.
       Tests first, then implement.
- [ ] 29. Fix session fixation (REQ-12a): `establishSession` calls
       `HttpServletRequest#changeSessionId()` when a session already
       exists. Test: pre-seed a session, log in, assert the session id
       changed.
- [ ] 30. Scope the CSRF exemption to the three concrete auth paths
       (REQ-12b) instead of the `/api/auth/**` wildcard in `SecurityConfig`.
- [ ] 31. Add cookie hardening config (`server.forward-headers-strategy`,
       `server.servlet.session.cookie.same-site`/`http-only`) to
       `application.yaml`.
- [ ] 32. Run `./mvnw verify` and confirm it's green.
- [ ] 33. Update `PLAN.md` if any decision changed during implementation.
