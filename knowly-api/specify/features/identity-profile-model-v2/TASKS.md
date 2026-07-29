# TASKS — identity-profile-model-v2 (backend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> TDAD: test first (Red), minimal code (Green), `./mvnw test`, repeat.
> Migration/entities/services land first — the frontend retrofit
> (`user-profile-v2`, `knowly-app`) depends on this contract existing.

## Migration and entities

- [ ] 1. Write a Flyway migration test asserting `V18` creates
      `user_profiles`/`addresses`/`contacts`/`profile_edit_request_contacts`
      with the confirmed columns/constraints (Red — run against a fresh
      Testcontainers DB, assert via raw JDBC metadata query).
- [ ] 2. Write `V18__retrofit_identity_profile_tables.sql` implementing
      the schema in PLAN.md (Green) — new tables + `profile_edit_requests`
      column changes + self-approval `CHECK`, no backfill/cancel logic
      yet (kept separate, tasks 3-5, so each is independently testable).
- [ ] 3. Write an integration test seeding pre-migration `users` rows
      (`full_name`/`cpf`/`cpf_blind_index`/`rg`/`rg_blind_index`/`phone`
      populated on some rows, all null on others) and asserting
      `user_profiles`/`contacts` are backfilled correctly after
      migration, including the "eager empty row for every user" case
      (Red); add the `INSERT ... SELECT` backfill statements to `V18`
      (Green).
- [ ] 4. Extend the migration test: seed a `PENDING`
      `profile_edit_requests` row before migration, assert it is
      `CANCELLED` with `resolved_at` set after migration, and that no
      other status value's rows are touched (Red); add the `UPDATE`
      statement to `V18`, sequenced before the schema-changing `ALTER`s
      (Green).
- [ ] 5. Extend the migration test: seed `users.address` with a
      free-text value, assert no row in `addresses` references that
      user after migration (Red — confirms REQ-26 by omission; no
      migration code needed, just an assertion that nothing was added,
      but keep the explicit test so a future accidental backfill add
      breaks it).
- [ ] 6. Create `UserProfile`/`Address`/`Contact` JPA entities
      (`@Audited`, tenant-filter N/A — these are user-owned, not
      tenant-owned, confirm no `@Filter` needed by checking `User`'s own
      entity for precedent), `ContactType`/`ContactChangeAction` enums,
      and `UserProfileRepository`/`AddressRepository`/
      `ContactRepository`. Move `@Convert(converter =
      CpfRgEncryptionConverter.class)` from `User.cpf`/`User.rg` onto
      `UserProfile.cpf`/`UserProfile.rg`.
- [ ] 7. Write a repository test asserting the blind-index partial
      unique indexes on `user_profiles` reject a duplicate `cpf`/`rg`
      even via direct repository save (Red — mirrors
      `identity-profile-model`'s existing equivalent test, now pointed
      at `UserProfileRepository`); confirm green against the entities
      from task 6.
- [ ] 8. Wire eager `UserProfile` creation into the existing
      user-registration path — write a test asserting a new `User`
      registration also creates an empty `UserProfile` row in the same
      transaction (Red); implement (Green).

## ContactService

- [ ] 9. Write `ContactServiceTest`: adding a 6th contact for a user
      with 5 existing rows is rejected (Red); implement the cap check in
      `ContactService.addContact` (Green).
- [ ] 10. Extend the test: setting `isPrimary=true` on a new contact of
      a type that already has a primary either replaces the old primary
      or is rejected (pick one behavior, assert it) (Red); implement
      (Green).
- [ ] 11. Extend the test: `EMAIL`-type format validation
      accepts/rejects accordingly; `PHONE`/`WHATSAPP` accepts/rejects
      via the normalized-digits regex; `OTHER` accepts any value (Red);
      implement `ContactService.validateFormat` +
      `InvalidContactFormatException` (400-mapped) (Green).

## AvatarStorageService and avatar endpoint

- [ ] 12. Add `avatarBucket` to `StorageProperties`
      (`knowly.storage.avatar-bucket`) and the corresponding
      `application.yaml` key.
- [ ] 13. Write `AvatarStorageServiceTest` (mirrors
      `ArticleStorageService`'s existing test pattern): `upload`/
      `presignedUrl` (or public-URL variant, per PLAN's deferred choice)
      against the new bucket (Red); implement `AvatarStorageService`
      (Green).
- [ ] 14. Write a `UserProfileController` test: `POST
      /api/users/me/profile/avatar` with a multipart file updates
      `UserProfile.avatarUrl` and returns it in the response (Red);
      implement `UserProfileService.updateOwnAvatar` + the controller
      endpoint (Green) — no permission check beyond "own row," per
      REQ-10.
- [ ] 15. Extend the test: an unsupported content type or oversized file
      is rejected with 400 (Red); implement (Green).

## UserProfileService — retrofit the permission decision tree

- [ ] 16. Rewrite `UserProfileServiceTest`'s existing REQ-11…14a matrix
      (from `identity-profile-model`) to assert the new REQ-11 rule:
      `STAFF_ADMIN` and `MEMBER_ADMIN` self-edit attempts on any field
      other than avatar are now rejected (Red — this test currently
      asserts the opposite); update `UserProfileService.directEdit` to
      remove the self-allowance from both admin branches (Green).
- [ ] 17. Confirm (existing coverage, no new test needed if already
      covered) the four "edit *other* user" paths (`STAFF_ADMIN`,
      `MEMBER_ADMIN` of target's tenant, tenant `PROFILE_EDIT`, global
      `PROFILE_EDIT`) remain unchanged and green.
- [ ] 18. Update `ProfileFieldsDto`/`AddressDto`/`ContactDto` per
      PLAN.md's shape; update every existing test/mapping call site
      compiling against the old `String address`/`String phone` fields.

## Edit-request flow — contacts + atomicity

- [ ] 19. Write a test: `submitEditRequest` with a
      `ProfileEditRequestFieldsDto` carrying both flattened field
      changes and a `contactChanges` list (add + update + remove)
      persists a `ProfileEditRequest` with matching
      `profile_edit_request_contacts` rows (Red); implement the DTO
      plumbing + `ProfileEditRequestContact` entity/repository (Green).
- [ ] 20. Write a test: `approveEditRequest` on that request applies
      both the flattened fields and every contact change atomically
      (Red); implement `applyFields`'s new `contactChanges` parameter,
      delegating each change to `ContactService` inside the same
      `@Transactional` boundary (Green).
- [ ] 21. Extend the test: a mid-transaction failure (e.g. a
      `ContactService` format-validation failure on one of several
      contact changes) rolls back the flattened field changes too — no
      partial application (Red); confirm the existing `@Transactional`
      boundary already guarantees this, or fix if it doesn't (Green).
- [ ] 22. Write a test: approving a request whose proposed `cpf`/`rg`
      collides with another user's blind index rolls back entirely and
      throws `ProfileFieldConflictException` (409) — re-run of
      `identity-profile-model`'s existing REQ-21 test, now against
      `UserProfile` instead of `User` (Red/Green as needed).
- [ ] 23. Write a test: a resolver attempting to approve/reject their
      own submitted request is rejected at the service layer (existing
      coverage) **and** a direct repository `UPDATE` bypassing the
      service layer is rejected by the new DB `CHECK` (Red — integration
      test, raw JDBC update expected to throw); confirm the `V18`
      constraint (Green, no code change needed beyond the migration).
- [ ] 24. Add `ProfileEditRequestStatus.CANCELLED`; write a test
      confirming a `CANCELLED` request is excluded from
      `GET /api/profile-edit-requests` and cannot be approved/rejected
      (404/409, matching how a resolved request already behaves) (Red);
      implement (Green).

## Controller/DTO wiring

- [ ] 25. Update `UserProfileController`'s `PUT
      /api/users/{id}/profile` request/response to the new
      `ProfileFieldsDto` shape (no `avatarUrl`); update
      `UserProfileDto` to add `avatarUrl` (read-only).
- [ ] 26. Update `ProfileEditRequestController`'s response DTO to
      include `proposedContactChanges`; write/extend a controller test
      confirming the full shape round-trips.

## Cleanup migration (later milestone — only after the above is verified running)

- [ ] 27. Write `V19__drop_legacy_user_identity_columns.sql` dropping
      `users`/`users_aud`'s `full_name`/`address`/`rg`/`cpf`/`phone`/
      `rg_blind_index`/`cpf_blind_index`; remove the corresponding
      fields/`@Convert` annotations from `User.java`; run the full suite
      to confirm nothing still references them (Red on any leftover
      reference → Green once removed).

## Final verification

- [ ] 28. Run `./mvnw spotless:apply && ./mvnw verify` and confirm the
      full suite is green.
- [ ] 29. Update `PLAN.md`'s "Deviations from this PLAN" section if any
      decision changed during implementation.
- [ ] 30. Update `PROJECT_STATUS.md`: mark `identity-profile-model-v2`
      shipped, note the two-migration split (`V18` retrofit / `V19`
      column drop) and that `identity-profile-model`'s original row in
      the status table should be annotated as superseded, not deleted
      (matching how `primeng-migration`/`primeng-removal` handled the
      same situation).
- [ ] 31. Coordinate with `knowly-app`'s `user-profile-v2` (frontend
      retrofit) — its contract table depends on this feature's DTO
      shapes (tasks 18/25/26) being final before frontend implementation
      starts.
