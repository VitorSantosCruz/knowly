# PLAN — staff-bootstrap-user

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- Implemented as a plain versioned Flyway SQL migration
  (`V13__create_bootstrap_staff_user.sql`), no new Java code, no new
  entity/table — it reuses the existing `users` table and `GlobalRole`
  enum as-is (SPEC REQ-4 explicitly rules out any new column/mechanism).
- The email comes in via a **Flyway placeholder**
  (`spring.flyway.placeholders.bootstrap-staff-email`), not a Java-based
  migration or app-startup `CommandLineRunner`. Flyway placeholders are
  the existing, idiomatic way this stack already injects config into SQL
  at migration time, and Spring Boot's relaxed env-var binding already
  supports required (no-default) placeholders that fail application
  startup if unset — satisfying REQ-3 (fail fast, explicit error) without
  any new plumbing.
- `application.yaml` adds:
  ```yaml
  spring:
    flyway:
      placeholders:
        bootstrap-staff-email: ${KNOWLY_BOOTSTRAP_STAFF_EMAIL}
  ```
  No default value — if `KNOWLY_BOOTSTRAP_STAFF_EMAIL` is unset, Spring's
  property resolution throws at context refresh, before Flyway (or
  anything else) runs, which is exactly the "fail to start" behavior
  REQ-3 asks for.
- `created_by`/`updated_by` are set to `'system'` in the inserted row,
  matching the existing `AuditorAware<String>` fallback used for
  unauthenticated/system-originated writes (`JpaAuditingConfig`) — this
  keeps the bootstrap row consistent with how every other
  system-originated write is already attributed, rather than inventing a
  new sentinel value.
- `global_role` is set to `'STAFF'`, the only existing `GlobalRole` value
  — reuses today's full-access staff semantics unchanged (SPEC REQ-4).
- No row is inserted into `tenant_memberships` — the bootstrap user has
  no tenant membership, consistent with how staff already operate
  cross-tenant without one (see `DECISIONS.md`, "Staff can act as any
  tenant without holding a membership").
- `src/test/resources/application-test.yaml` sets
  `spring.flyway.placeholders.bootstrap-staff-email` to a fixed test
  value (e.g. `bootstrap-test@conectabyte.com`), so the test suite never
  depends on a real environment variable — consistent with how other
  test-only config values (dummy OpenAI key, dummy Turnstile secret)
  already work in that file.
- `compose.yaml` and any deployment docs/README gain
  `KNOWLY_BOOTSTRAP_STAFF_EMAIL` alongside the project's existing `.env`
  convention (never hardcoded, never committed).

## Data schema

No schema change. Migration only inserts one row into the existing
`users` table:

| column | value |
|---|---|
| `email` | `${bootstrap-staff-email}` (Flyway placeholder) |
| `one_time_password_hash` | `NULL` |
| `one_time_password_issued_at` | `NULL` |
| `global_role` | `'STAFF'` |
| `onboarding_completed_at` | `NULL` |
| `created_by` / `updated_by` | `'system'` |
| `created_at` / `updated_at` | `now()` (column default) |

`V4__create_tenancy_envers_audit_tables.sql`'s Envers audit-table
insert convention (if any) is followed for this row too, so the
bootstrap user's creation is queryable via Envers like any other `User`
history — no special-casing needed since Envers/Hibernate populates
audit history from ORM writes, not raw SQL inserts; **this migration's
insert will NOT appear in the Envers `users_aud` table**, since it
bypasses Hibernate entirely. This is called out explicitly here because
it's a real (accepted) gap: Flyway-inserted rows are invisible to
Envers by construction, same as any other pure-SQL data migration in
this codebase. Not treated as a defect — just documented so it isn't
mistaken for a bug later.

## API contracts

None — no new endpoint. Login happens through the existing
`POST /api/auth/login-request` → `POST /api/auth/login-code/verify` flow,
unchanged.

## Dependencies

None new — Flyway and Spring Boot's placeholder support are already in
use.

## Package/file structure

- `src/main/resources/db/migration/V13__create_bootstrap_staff_user.sql` (new)
- `src/main/resources/application.yaml` (add `spring.flyway.placeholders.bootstrap-staff-email`)
- `src/test/resources/application-test.yaml` (add the same placeholder, fixed test value)
- `compose.yaml` / `.env.example` (if one exists) — document the new required variable
- `README.md` — add `KNOWLY_BOOTSTRAP_STAFF_EMAIL` to the prerequisites/env-var list, if such a list already exists

## Testing strategy

- Integration test (new, `@SpringBootTest`, `@ActiveProfiles("test")`):
  boots the full context (migration runs against the test Postgres
  container as always) and asserts a `User` row exists with the
  configured test email and `globalRole == GlobalRole.STAFF`
  (REQ-1, REQ-2, REQ-4).
- A second integration test asserts that re-running `flyway migrate`
  against an already-migrated schema does not error and does not create
  a duplicate row (Flyway's own versioning already guarantees this; the
  test documents/locks in that expectation rather than testing Flyway
  itself) — covers the acceptance criterion about idempotence.
- REQ-3 (fail fast without the env var) is not covered by the main test
  suite (which always sets the test placeholder) — instead it's verified
  manually once (`unset KNOWLY_BOOTSTRAP_STAFF_EMAIL && ./mvnw spring-boot:run`
  should fail at startup) and the behavior itself comes for free from
  Spring's existing required-placeholder resolution, not from
  feature-specific code that could regress silently.
