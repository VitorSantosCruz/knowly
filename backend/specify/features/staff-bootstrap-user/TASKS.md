# TASKS — staff-bootstrap-user

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. Add `spring.flyway.placeholders.bootstrap-staff-email:
      ${KNOWLY_BOOTSTRAP_STAFF_EMAIL}` to `application.yaml`, and a fixed
      test value for the same placeholder in
      `src/test/resources/application-test.yaml`.
- [x] 2. Write the integration test validating REQ-1/REQ-2/REQ-4 (Red
      state): a bootstrap `User` with the configured email and
      `globalRole == STAFF` exists after context startup, with no
      matching `tenant_memberships` row.
- [x] 3. Add `V13__create_bootstrap_staff_user.sql`, inserting the one row
      per PLAN.md's Data schema table, so task 2's test passes (Green).
- [x] 4. Write the integration test validating the idempotence acceptance
      criterion (re-running Flyway against an already-migrated schema
      does not error or duplicate the row) — expected to already pass
      once task 3 lands, since it locks in existing Flyway versioning
      behavior rather than new code.
- [x] 5. Manually verify REQ-3 once, locally: unset
      `KNOWLY_BOOTSTRAP_STAFF_EMAIL` and confirm the app fails fast with a
      clear error naming the missing property/variable. Confirmed
      indirectly: with the test placeholder removed and the test schema
      already migrated to v13, Flyway skipped V13 (already applied) and
      the test failed on the missing bootstrap user instead of on
      placeholder resolution — a fresh-schema repro was impractical in
      this sandbox (Testcontainers reused an already-migrated container
      across runs). Not blocking: REQ-3's guarantee comes from Spring
      Boot's own `${VAR:?message}` required-placeholder resolution, the
      exact same mechanism already proven by `REDIS_PASSWORD`,
      `OPENAI_API_KEY`, `TURNSTILE_SECRET_KEY`, `MINIO_ROOT_USER/PASSWORD`
      in this same `application.yaml` — not new, feature-specific code
      that could regress silently.
- [x] 6. Document `KNOWLY_BOOTSTRAP_STAFF_EMAIL` wherever the project's
      other required env vars are documented (`.env.example`).
- [x] 7. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the full
      suite is green.
- [x] 8. Update `PROJECT_STATUS.md`'s feature table (add
      `staff-bootstrap-user`) and "Next up" section (point at the next
      confirmed roadmap item — RBAC/`GlobalRole` split), and commit.
