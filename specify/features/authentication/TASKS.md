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
- [ ] 6. Write `LoginCodeServiceTest` covering generate/verify/invalidate/
      expiry (Red), then implement `LoginCodeService` (Redis-backed)
      (Green).
- [ ] 7. Write `FailedAttemptServiceTest` covering counter increment,
      lockout at 3, reset on success, shared counter across mechanisms
      (Red), then implement `FailedAttemptService` (Green).
- [ ] 8. Write `OneTimePasswordServiceTest` covering
      generate/verify/rotate-on-use/15-day-expiry (Red), then implement
      `OneTimePasswordService` (Green).
- [ ] 9. Write `CaptchaServiceTest` with a mocked Turnstile HTTP response
      (Red), then implement `CaptchaService` (Green).
- [ ] 10. Write `MailServiceTest` (Red), then implement `MailService` +
       `login-code.jte` and `new-one-time-password.jte` templates (Green).
- [ ] 11. Write `AuthControllerIntegrationTest` cases for REQ-1–REQ-4
       (`POST /login-request`, including indistinguishable response and
       `CAPTCHA_REQUIRED`) (Red), then implement that endpoint (Green).
- [ ] 12. Write `AuthControllerIntegrationTest` cases for REQ-5, REQ-6,
       REQ-9, REQ-10, REQ-11, REQ-14, REQ-15 (`POST /login-code/verify`)
       (Red), then implement that endpoint (Green).
- [ ] 13. Write `AuthControllerIntegrationTest` cases for REQ-7, REQ-8,
       REQ-9, REQ-10, REQ-11, REQ-13 (`POST /login-password/verify`) (Red),
       then implement that endpoint (Green).
- [ ] 14. Write a test for `AuthExceptionHandler` asserting each custom
       exception maps to the correct HTTP status/code (Red), then
       implement it (Green).
- [ ] 15. Add structured audit logging (trace id, email, outcome) to every
       decision point in `AuthController`/services; add a test asserting
       the log/audit event is emitted for at least one success and one
       failure path.
- [ ] 16. Run `./mvnw verify` (Spotless + full suite) and confirm it's
       green.
- [ ] 17. Update `PLAN.md` if any decision changed during implementation.
