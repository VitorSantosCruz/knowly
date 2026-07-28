# TASKS — identity-profile-model

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> References SPEC.md/PLAN.md.

## Task 0 — read this before starting (ONE-TIME exception, dated 2026-07-26, NOT standing policy)

**This exception was given directly by the human product owner in
conversation on 2026-07-26, for this specific batch of features only —
it is NOT a change to this project's standing process.**
`constitution.md`'s TDAD Red/Green cycle and the "commit each completed
task as you go" rule remain this project's real, ongoing policy. Do not
let this note justify skipping test execution or batching commits in any
other feature or any future session — re-confirm with the human first.

**Test-first authorship is still mandatory** — every task below still
starts with writing the test for that behavior (Red), then the minimal
code to make it pass (Green), exactly as normal TDAD.

**BUT: do not run any test command during implementation this time** —
not `./mvnw test`, not a targeted `-Dtest=...` run, not `./mvnw verify`,
for any task in this list. Write each test, write the code you believe
makes it pass by reading it carefully, and move to the next task without
executing the suite. The user has explicitly accepted the risk of
deferring all verification. This is a deliberate, one-time exception to
the usual "test first, then run it green before moving on" discipline —
do not fall back to running tests per task out of habit. All test
execution (this feature's tests *and* the full existing suite) happens
in exactly one place: the final task at the end of this list, after
every other backlog item planned alongside this one is also implemented.

Do not run `./mvnw spotless:apply`/`spotless:check` per task either, for
the same reason — batch formatting to the same final pass. (If your
environment's pre-commit hook runs Spotless automatically on commit,
that's fine and expected; just don't invoke it manually mid-task.)

Still commit each completed task as you go per the standing repo
convention — committing is not test execution, it's just recording the
work; do not batch commits either.

## Sequencing note

`Notification` already exists in `src/main/java` (verified in PLAN.md)
— no need to wait on `tenant-membership-acceptance` before starting.
Task 8 below (relaxing `Notification`'s FK) must land before any task
that creates a `PROFILE_EDIT_REQUEST_PENDING` notification.

## Crypto foundation

- [x] 1. Write `CpfRgEncryptionConverterTest`: encrypt/decrypt round-trip
      returns original plaintext; two calls with identical plaintext
      produce different ciphertext (randomized IV); a tampered
      ciphertext byte fails to decrypt (Red).
- [x] 2. Implement `IdentityCryptoProperties`
      (`@ConfigurationProperties(prefix = "knowly.identity")`,
      `cpfRgEncryptionKey`/`cpfRgHmacKey`, both base64-decoded 256-bit
      keys) and `CpfRgEncryptionConverter` (AES-256-GCM,
      `AttributeConverter<String, String>`) to satisfy task 1 (Green).
      Add `knowly.identity.cpf-rg-encryption-key`/`cpf-rg-hmac-key`
      (`${CPF_RG_ENCRYPTION_KEY}`/`${CPF_RG_HMAC_KEY}`) to
      `application.yaml` and `application-test.yaml` (dummy
      base64 key, same convention as the existing dummy OpenAI key).
- [x] 3. Write `BlindIndexServiceTest`: normalization strips non-digits
      before hashing (formatted/unformatted CPF hash identically); same
      plaintext + same key always produces the same hash; empty string
      after stripping is treated as absent/null (Red).
- [x] 4. Implement `BlindIndexService` (HMAC-SHA256, hex-encoded) to
      satisfy task 3 (Green).

## Migration + entity changes

- [x] 5. Write a migration-level test/assertion (or a focused
      `@DataJpaTest`) confirming the new `users`/`tenants` columns exist
      and existing rows remain valid with them unset (Red — expect
      failure before the migration exists).
- [x] 6. Write `V17__add_identity_profile_fields.sql` per PLAN.md's
      "Data schema" section: `users`/`users_aud` new columns + partial
      unique indexes (`address`, `phone`, `cpf_blind_index`,
      `rg_blind_index`); `tenants`/`tenants_aud` new columns + partial
      unique indexes (`cnpj`, `inscricao_estadual`); `notifications`
      FK relaxation + new `profile_edit_request_id` column + `CHECK`
      constraint; new `profile_edit_requests` table (Green for task 5).
- [x] 7. Update `User.java`: add `fullName`, `address`, `rg`, `cpf`,
      `phone`, `cpfBlindIndex`, `rgBlindIndex` fields; `@Convert` on
      `cpf`/`rg` using `CpfRgEncryptionConverter`. No setters for
      blind-index fields exposed outside the entity's own package
      contract beyond what Lombok generates — enforcement that they're
      only ever set alongside the encrypted field lives in
      `UserProfileService`, not the entity (documented in a class-level
      Javadoc comment on `User` noting this).
- [x] 8. Update `Tenant.java`: add `cnpj`, `razaoSocial`,
      `nomeFantasia`, `inscricaoEstadual` fields. Update
      `Notification.java`: relax `tenantMembership` to
      `@ManyToOne(optional = true)` + nullable `@JoinColumn`, add new
      nullable `profileEditRequest` `@ManyToOne`. Add
      `NotificationType.PROFILE_EDIT_REQUEST_PENDING`.
- [x] 9. Write/confirm a regression test on existing
      `NotificationServiceTest`/`NotificationController` integration
      tests: membership-invitation accept/decline flows behave
      identically after the nullable-FK change (Red then immediately
      Green/confirm — this is verifying PLAN.md's "no existing consumer
      assumes non-null" claim actually holds).

## Permissions

- [x] 10. Add `Permission.PROFILE_VIEW`/`PROFILE_EDIT` and
       `GlobalPermission.PROFILE_VIEW`/`PROFILE_EDIT`. No test needed on
       the bare enum addition itself (Tier 1); covered by the
       authorization tests below.

## `UserProfileService` — view (REQ-8/9/10/10a/10b/10c)

- [x] 11. Write `UserProfileServiceTest` cases for `getOwnProfile`
       (always succeeds) and `getProfile` (other user): no-right
       rejected (REQ-9); tenant `PROFILE_VIEW` holder allowed within
       tenant (REQ-10); global `PROFILE_VIEW` holder (`STAFF`) allowed
       any user (REQ-10a); `MEMBER_ADMIN` allowed within tenant, no
       separate grant (REQ-10b); `STAFF_ADMIN` allowed any user, no
       separate grant (REQ-10c) (Red).
- [x] 12. Implement `UserProfileService.getOwnProfile`/`getProfile`
       (decrypting `cpf`/`rg` only after the right is confirmed, per
       REQ-4) to satisfy task 11 (Green).

## `UserProfileService` — direct edit (REQ-11/12/13/13a/14/14a)

- [x] 13. Write `UserProfileServiceTest` cases for `directEdit`:
       `MEMBER_ADMIN` edits self and others within tenant (REQ-11);
       `STAFF_ADMIN` edits self and others (REQ-12); tenant
       `PROFILE_EDIT` holder edits an *other* member (REQ-13) but is
       rejected editing self (REQ-13a); global `PROFILE_EDIT` holder
       (`STAFF`) edits an *other* user (REQ-14) but is rejected editing
       self (REQ-14a); no-right caller rejected entirely (Red).
- [x] 14. Implement `UserProfileService.directEdit` (the full decision
       tree from PLAN.md, routing every `cpf`/`rg` write through the
       shared `applyFields` helper that also updates the blind-index
       columns) to satisfy task 13 (Green).

## `ProfileEditRequest` — submit/approve/reject (REQ-15…21)

- [x] 15. Write `ProfileEditRequestServiceTest` cases: `submitEditRequest`
       creates a `PENDING` request + one `Notification` per applicable
       right-holder, deduplicated by recipient (REQ-15/16); a second
       submission while one is pending is rejected (REQ-20);
       `approveEditRequest` applies fields + resolves (REQ-17);
       `rejectEditRequest` discards + resolves (REQ-18); a caller
       without the applicable right approving/rejecting is rejected
       (REQ-19); approving a request whose proposed `cpf`/`rg`/
       `phone`/`address` would collide with another user's existing
       value fails cleanly with no partial write
       (`ProfileFieldConflictException`, REQ-21) (Red).
- [x] 16. Implement `ProfileEditRequest`/`ProfileEditRequestStatus`
       entity + `ProfileEditRequestRepository` + `ProfileEditRequestService`
       to satisfy task 15 (Green).

## Controllers

- [x] 17. Write controller tests for `UserProfileController`:
       `GET /api/users/me/profile` (200), `GET/PUT
       /api/users/{id}/profile` (200/403/404/409 per PLAN.md's API
       contract table), `POST /api/users/me/profile/edit-requests`
       (201/409) (Red).
- [x] 18. Implement `UserProfileController` + `ProfileFieldsDto`/
       `UserProfileDto` + exception-to-status mapping
       (`PendingProfileEditRequestExistsException`→409,
       `ProfileFieldConflictException`→409, reusing the existing
       `PermissionDeniedException`→403 mapping) to satisfy task 17
       (Green).
- [x] 19. Write controller tests for `ProfileEditRequestController`:
       `GET /api/profile-edit-requests` (200, only requests the caller
       holds the applicable right over), `POST .../{id}/approve` and
       `.../reject` (200/403/404/409) (Red).
- [x] 20. Implement `ProfileEditRequestController` +
       `ProfileEditRequestDto` to satisfy task 19 (Green).

## Tenant fields wiring

- [x] 21. Check `TenantController`'s existing detail/update DTOs: if
       they already serialize `Tenant` generically, confirm
       `cnpj`/`razaoSocial`/`nomeFantasia`/`inscricaoEstadual` appear
       with no code change and write a confirming test; if not, add the
       minimal DTO field additions + a test asserting they round-trip
       (Red then Green) — this is a small DTO-completeness fix, not a
       new endpoint, per PLAN.md.

## Audit logging

- [x] 22. Write tests confirming `@AuditLog` fires with the expected
       `action`/`resourceType` for `directEdit`,
       `submitEditRequest`, `approveEditRequest`, `rejectEditRequest`,
       and that `metadata` never contains a raw `cpf`/`rg` value (only
       field names) (Red).
- [x] 23. Add `@AuditLog` annotations per PLAN.md's "Audit logging"
       section to satisfy task 22 (Green).

## DB-level uniqueness (integration, Testcontainers)

- [x] 24. Write an integration test that inserts two `User` rows with
       colliding `cpfBlindIndex`/`rgBlindIndex`/`phone`/`address`
       directly via the repository (bypassing service validation) and
       confirms the database itself rejects the second insert (REQ-2,
       acceptance criterion); confirm existing rows with these fields
       unset are unaffected (REQ-2a) (Red then Green/confirm — this
       should already pass once task 6's migration exists, so this task
       is verification, not new production code).
- [x] 25. Write an integration test confirming `cpf`/`rg` are stored as
       non-plaintext at the raw JDBC/column level (query the raw column
       value, assert it does not equal or contain the plaintext) (Red
       then Green/confirm against tasks 2/7).
- [x] 26. Write an integration test for `Tenant.cnpj`/
       `inscricaoEstadual` uniqueness (REQ-7/7a) and REQ-7b's
       unset-coexistence case, same shape as task 24.

## Final verification pass (only after every other planned backlog item alongside this one is also implemented)

- [x] 27. Run `./mvnw spotless:apply` once, across all accumulated
       changes.
- [x] 28. Run the full `./mvnw verify` (formatting + entire test suite,
       not just this feature's tests) and fix any real failures
       surfaced — this is the first and only test execution for this
       feature's work, per Task 0.
- [x] 29. Update `PROJECT_STATUS.md`/`PLAN.md`/`DECISIONS.md` if any
       decision changed during implementation (in particular: flag the
       `users_aud`/key-rotation note from PLAN.md's Data schema section
       if not already tracked in `PROJECT_STATUS.md`'s operational
       gotchas).
- [x] 30. Hand off to `qa-test-automation` and `appsec` for review now
       that the final full verification pass has actually run — not
       before, and not per-task. Flag for `appsec` specifically: the
       blind-index equality-revealing tradeoff (already confirmed, but
       worth a fresh look at the actual implementation), the
       `users_aud`/key-rotation gap, and the `Notification` FK
       relaxation's regression coverage (task 9).
