# TASKS — mandatory-complete-profile

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

## Completeness check (shared building block)

- [ ] 1. Write `ProfileCompletenessServiceTest` covering REQ-2/REQ-6's
      completeness definition: complete profile → `true`; each
      individually-missing field (`fullName`, `birthDate`, `cpf`, `rg`,
      `rgOrgaoEmissor`, missing `Address` row, `Address` missing one
      required column, zero `Contact` rows) → `false` (Red).
- [ ] 2. Implement `ProfileCompletenessService.isComplete(User)` reading
      `UserProfileRepository`/`AddressRepository`/`ContactRepository`
      (Green).

## Mandatory-fields DTOs

- [ ] 3. Write a validation test asserting `MandatoryProfileFieldsDto`/
      `MandatoryAddressDto` reject a payload missing any one required
      field (Bean Validation, `Validator` unit test) and accept one with
      `numero`/`complemento` omitted (Red).
- [ ] 4. Implement `MandatoryProfileFieldsDto`/`MandatoryAddressDto` with
      the correct `@NotBlank`/`@NotNull`/`@NotEmpty`/`@Valid`
      annotations (Green).

## Staff creation — REQ-7/REQ-8 (staff-user-provisioning path)

- [ ] 5. Write an integration test: `POST /api/staff/users` missing one
      mandatory profile field is rejected `400`; no `User`/`UserProfile`
      row is persisted (Red).
- [ ] 6. Add `profile: MandatoryProfileFieldsDto` to
      `CreateStaffUserRequestDto`; wire `@Valid` on the controller method
      (Green for task 5).
- [ ] 7. Write an integration test: `POST /api/staff/users` with every
      mandatory field present succeeds; the created user's
      `ProfileCompletenessService.isComplete` is immediately `true`
      (Red).
- [ ] 8. Implement `StaffService.createStaffUser` persisting the
      `MandatoryProfileFieldsDto` into `UserProfile`/`Address`/`Contact`
      rows in the same transaction (Green).
- [ ] 9. Write an integration test: the REQ-8 rejection emits an
      `AuditEvent` (`staff.user.creation.denied`) recording the missing
      field names (Red).
- [ ] 10. Implement `CreationValidationAuditAdvice`'s
      `MethodArgumentNotValidException` handler scoped to
      `createStaffUser` (Green).

## Tenant member creation — REQ-8/REQ-9 (`addMember`)

- [ ] 11. Write an integration test: `addMember` missing one mandatory
      profile field is rejected `400`; no `User`/`TenantMembership`/
      `UserProfile` row is persisted (Red).
- [ ] 12. Add `profile: MandatoryProfileFieldsDto` to
      `AddMemberRequestDto`; wire `@Valid` (Green for task 11).
- [ ] 13. Write an integration test: `addMember` with every mandatory
      field present succeeds; the created user is never pending (Red).
- [ ] 14. Implement `TenantService.createUserWithProfile`'s call sites to
      persist the `MandatoryProfileFieldsDto` (Green).
- [ ] 15. Write an integration test: the REQ-8 rejection on `addMember`
      emits `tenant.member.creation.denied` with the missing field names
      (Red).
- [ ] 16. Extend `CreationValidationAuditAdvice` to also cover `addMember`
      (Green).

## Bootstrap pending-state gate — REQ-2/REQ-3/REQ-4

- [ ] 17. Write an integration test: the seeded bootstrap `STAFF_ADMIN`
      (via `V13`/`V14` migrations) is rejected `409
      PROFILE_COMPLETION_REQUIRED` on an arbitrary staff-only endpoint
      (e.g. `GET /api/staff/users`) (Red).
- [ ] 18. Implement `ProfileCompletionFilter`, register it in
      `SecurityConfig` before `TenantContextFilter` (Green).
- [ ] 19. Write an integration test: `GET /api/users/me/profile` and
      `/api/auth/**` remain reachable for the pending bootstrap account
      (Red — confirms the allowlist).
- [ ] 20. Add the allowlist entries to `ProfileCompletionFilter` (Green).

## Bootstrap completion endpoint — REQ-6

- [ ] 21. Write an integration test: `POST
      /api/users/me/profile/complete` with every required field
      transitions the bootstrap account; the next arbitrary request
      succeeds afterwards (Red).
- [ ] 22. Implement `UserProfileService.completeOwnProfile` +
      `UserProfileController`'s new handler, added to
      `ProfileCompletionFilter`'s allowlist (Green).
- [ ] 23. Write an integration test: calling
      `/api/users/me/profile/complete` again afterwards is rejected `409
      PROFILE_ALREADY_COMPLETE` (Red).
- [ ] 24. Implement `ProfileAlreadyCompleteException` +
      `IdentityExceptionHandler` mapping (Green).
- [ ] 25. Write an integration test: submitting all but one required
      field to `/api/users/me/profile/complete` is rejected `400` and the
      account remains pending afterwards (Red — confirms task 4's Bean
      Validation is applied here too, and no partial write happens).
- [ ] 26. Fix any gap found by task 25 (Green — expected to already pass
      given tasks 4/22, but written explicitly per this feature's own
      acceptance criteria).

## Login response surfacing — REQ-5

- [ ] 27. Write an integration test: login-code-verify response for the
      pending bootstrap account includes `pendingProfileCompletion:
      true`; after completion, a fresh login shows `false` (Red).
- [ ] 28. Add `pendingProfileCompletion` to `TenantSessionOutcome.Staff`
      and surface it in `AuthController`'s verify-code response DTO
      (Green).

## Wrap-up

- [ ] 29. Run the full `./mvnw spotless:apply && ./mvnw verify` and
      confirm the suite is green.
- [ ] 30. Update `PLAN.md`/`PROJECT_STATUS.md` if any decision changed
      during implementation.
