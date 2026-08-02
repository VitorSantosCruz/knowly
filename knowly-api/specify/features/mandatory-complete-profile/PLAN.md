# PLAN — mandatory-complete-profile

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Pending-profile-completion state is *derived*, not persisted** — no
  new column/status enum on `users`/`user_profiles`. A new
  `ProfileCompletenessService.isComplete(User)` computes SPEC's
  completeness definition on the fly (`user_profiles` fields non-null,
  an `addresses` row with every `NOT NULL` column set, at least one
  `contacts` row) by reading `UserProfileRepository`/`AddressRepository`/
  `ContactRepository` directly. *Why*: REQ-7/REQ-8/REQ-9/REQ-10
  guarantee that, once this feature ships, the **only** `User` row that
  can ever be incomplete is the bootstrap `STAFF_ADMIN` — every other
  creation path is now gated at creation time. A derived check therefore
  can't drift from a stored flag (there's nothing to keep in sync), needs
  no migration, and matches the SPEC's own non-functional requirement
  ("one pending/complete state... over `identity-profile-model-v2`'s
  existing... columns", not a new field). This is the SPEC's own Decision
  4, resolved here as "derived" (Tier 2, this file is the record).
- **No new Flyway migration for account state.** Follows directly from
  the decision above — there is no status column to add. (A migration is
  still needed for the new mandatory-field DTOs' server-side validation
  only if a *schema* constraint were being added, which it isn't:
  `identity-profile-model-v2` already made every field nullable at the
  DB layer; completeness is enforced in application code — the DB
  intentionally still allows a null field, since the bootstrap account's
  first-completion submission fills them in one at a time is not
  required — REQ-6 requires all-at-once, but a *partial* write is never
  attempted, so the DB-level nullability doesn't need tightening either.)
- **Enforcement is a new `ProfileCompletionFilter`, sibling to
  `TenantContextFilter`, not folded into it.** *Why*: `TenantContextFilter`
  already has one gating responsibility (tenant selection) with its own
  allowlist and distinct-code response; adding a second, unrelated gate
  (profile completion, which applies to staff *and* is keyed off a
  different notion of "is this the one exceptional account", not tenant
  membership) to the same class would conflate two independent
  concerns. Registered in `SecurityConfig` via `addFilterAfter(...,
  UsernamePasswordAuthenticationFilter.class)`, ordered **before**
  `TenantContextFilter` (a pending bootstrap account has no tenant
  concept at all — no membership, no active-tenant session state — so
  this gate must short-circuit first, not after tenant selection logic
  runs against an account that will never have a tenant).
  **AppSec review note (verify, don't assume):** `SecurityConfig`
  already calls `addFilterAfter(..., UsernamePasswordAuthenticationFilter
  .class)` twice today (`CsrfCookieFilter`, then `TenantContextFilter`).
  Spring Security's `FilterOrderRegistration` resolves relative order
  between multiple filters anchored at the *same* class by call order,
  not by declaration order in this PLAN's prose — the same class of
  mistake this repo's own `DECISIONS.md` incident log warns about
  (`${VAR:?message}` "looked like" required-property syntax but wasn't;
  an ordering claim here is the same shape of risk). Do not merely add a
  third `addFilterAfter(new ProfileCompletionFilter(...),
  UsernamePasswordAuthenticationFilter.class)` call and assume it lands
  before `TenantContextFilter` because the PLAN says so — task 17/19's
  integration tests must include at least one case where a *tenant-scoped*
  endpoint is hit by the pending bootstrap account (not just a
  staff-only, non-tenant-scoped one) and assert the response is `409
  PROFILE_COMPLETION_REQUIRED`, not `409 TENANT_SELECTION_REQUIRED` —
  that is the only way to empirically prove the intended ordering rather
  than infer it from the order filters are registered in source.
- **`ProfileCompletionFilter` resolves the current user the same way
  `AuthController`/`UserProfileController` already do** —
  `SecurityContextHolder.getContext().getAuthentication().getName()` →
  `UserRepository.findByEmailIgnoreCase`. No new session attribute is
  introduced for this (unlike tenant selection, which is genuinely
  session-scoped multi-step state); a per-request lookup plus
  `ProfileCompletenessService`'s existing-table reads is 2–3 cheap
  queries, and this filter runs on every request only until the single
  bootstrap account (ever) completes once — not a general per-request
  cost added to the system's steady state.
- **Allowlist for the pending state (REQ-3), mirroring
  `TenantContextFilter`'s `TENANT_SCOPED_EXEMPT_PATH_PREFIXES` pattern**:
  `/api/auth/**` (already fully open pre-authentication), `GET
  /api/users/me/profile` (retrieve own data), and the new `PUT
  /api/users/me/profile/complete` endpoint (see below). Everything else
  under `/api/**` is rejected with `409 PROFILE_COMPLETION_REQUIRED`
  while pending (REQ-4) — same status/shape convention as
  `TENANT_SELECTION_REQUIRED`.
- **A dedicated, one-time completion endpoint — not the existing
  direct-edit (`PUT /api/users/{id}/profile`) or self-request
  (`POST /api/users/me/profile/edit-requests`) paths.** Per the SPEC's
  own Decision 3: this is the one case where a user applies a change to
  their *own* non-`avatar_url` fields directly, no approval step, and it
  must not be reachable more than once a field is already set (that
  would silently reopen `identity-profile-model-v2`'s approval
  requirement for *changes*, which this feature explicitly does not
  relax). New `POST /api/users/me/profile/complete`:
  - Rejects (`409 PROFILE_ALREADY_COMPLETE`) if the caller's profile is
    already complete — REQ-6 only covers the *first* completion; this
    also makes the endpoint safe to have in the allowlist unconditionally
    (a non-pending caller hitting it just gets a clean, distinct
    rejection instead of ever risking a silent overwrite).
  - Applies **only** to the authenticated caller's own record — no
    `{id}` path variable, removing any possibility of using this
    exception to complete someone else's profile.
  - Validates the full mandatory set (see DTO below) and persists
    `UserProfile` fields + one `Address` + all submitted `Contact` rows
    atomically (`@Transactional`) — matching REQ-7/REQ-8's "no partial
    state" guarantee even though this is a completion, not a creation.
- **One shared "mandatory profile fields" DTO shape**, reused by three
  call sites (staff creation, `addMember`, and the new completion
  endpoint) rather than three ad hoc validated DTOs — same principle
  `staff-rbac-split`'s PLAN already applied ("no new DTO pattern"):
  - `MandatoryProfileFieldsDto(String fullName, LocalDate birthDate,
    String cpf, String rg, String rgOrgaoEmissor, MandatoryAddressDto
    address, List<ContactDto> contacts)` — every scalar field
    `@NotBlank`/`@NotNull`, `address` `@NotNull @Valid`, `contacts`
    `@NotEmpty @Valid`.
  - `MandatoryAddressDto(String cep, String logradouro, String numero,
    String complemento, String bairro, String cidade, String estado,
    String pais)` — `numero`/`complemento` have no constraint (SPEC:
    stay optional); every other field `@NotBlank`. This is a distinct
    type from the existing `identity.dto.AddressDto` (which is correctly
    all-optional for the direct-edit/self-request flows this feature
    doesn't touch) — reusing `AddressDto` and bolting validation onto it
    would make it context-dependently-required, which is worse than one
    extra small record.
  - Both live in `identity.dto` (existing package for this field set,
    per `identity-profile-model-v2`), imported by `tenancy`'s
    `CreateStaffUserRequestDto`/`AddMemberRequestDto` and by the new
    completion endpoint — no duplicated field list.
- **`CreateStaffUserRequestDto`/`AddMemberRequestDto` gain a
  `@NotNull @Valid MandatoryProfileFieldsDto profile` field**, additive
  to their existing `email`/`role` fields (the `role` field itself is
  `user-role-selection-at-creation`'s own, already-approved change to
  these same DTOs — not re-derived here; if it hasn't landed yet, this
  feature's DTO edit and that feature's `role` edit are independent
  additive fields on the same record and can be implemented in either
  order without conflict).
- **All-or-nothing enforcement (REQ-7/REQ-8) is Bean Validation, not a
  service-level manual check.** A `@Valid @RequestBody` failing
  validation never reaches `StaffService.createStaffUser`/
  `TenantService.addMember` — Spring's existing
  `MethodArgumentNotValidException` → `400` mapping (already global,
  used everywhere else in this codebase) guarantees zero rows are
  persisted, satisfying REQ-7/REQ-8's "no `User` row, no partial state"
  requirement for free, with no new exception/handler needed.
- **`createUserWithProfile`/`createStaffUser`'s persistence step is
  extended, not replaced**: after creating `User` + eager empty
  `UserProfile` (existing behavior), the now-guaranteed-present
  `MandatoryProfileFieldsDto` is written into that same `UserProfile`
  row plus a new `Address` row plus each `Contact` row, in the same
  transaction as the `User`/membership creation — reusing
  `UserProfileService`'s existing field-setting/blind-index logic
  (`cpf`/`rg` encryption, `cpfBlindIndex`/`rgBlindIndex` population)
  rather than duplicating it in `TenantService`/`StaffService`.
- **Audit for rejected creations (SPEC's Observability NFR)**: Bean
  Validation failures happen before the `@AuditLog`-annotated service
  method is ever entered, so the *existing* per-method `@AuditLog`
  cannot record them. A new `@RestControllerAdvice` handler for
  `MethodArgumentNotValidException`, scoped to `StaffController`'s
  `createStaffUser` and `TenantController`'s `addMember` endpoints only
  (via a marker or by checking the target handler method), writes one
  `AuditEvent` (`staff.user.creation.denied` /
  `tenant.member.creation.denied`) recording the actor and the list of
  missing/invalid field names from the `BindingResult`. *Why not a
  blanket handler for all validation errors*: every other `@Valid`
  endpoint in the app has never needed an audit trail for its 400s;
  scoping this to exactly the two REQ-7/REQ-8 call sites avoids a
  surprising new cross-cutting audit rule nobody asked for elsewhere.
- **Bootstrap login response (REQ-5)**: `TenantSessionOutcome.Staff`
  (returned by `resolveSessionOutcome`) gains a `boolean
  pendingProfileCompletion` field, computed via
  `ProfileCompletenessService.isComplete(user)` at the same point
  `isAnyStaff(user)` is already checked — no new session round trip,
  reuses the exact call already made during login. `AuthController`'s
  login-code-verify response DTO surfaces this as
  `pendingProfileCompletion`, so the frontend knows before rendering
  anything (including the onboarding tour) that completion is required
  — matches `staff-rbac-split`'s existing `isStaffAccount` field
  precedent (compute once at the point of truth, pass straight through).

- **No new CSRF exemption.** `POST /api/users/me/profile/complete` falls
  under `SecurityConfig`'s already-existing `"/api/users/**"` CSRF-ignore
  entry (added for `identity-profile-model-v2`'s own endpoints, predates
  this feature). No change to `SecurityConfig`'s `ignoringRequestMatchers`
  list is made by this PLAN — flagged explicitly per this repo's rule
  that new CSRF exemptions are Tier 3 and this endpoint needs none.

## Data schema

No new tables, no new columns. Reuses `user_profiles`/`addresses`/
`contacts` exactly as `identity-profile-model-v2` defined them.

## API contracts

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/users/me/profile/complete` | `MandatoryProfileFieldsDto` | `UserProfileDto` | 200 (complete), 400 (validation), 409 `PROFILE_ALREADY_COMPLETE` |
| POST | `/api/staff/users` (existing, `StaffController.createStaffUser`) | `CreateStaffUserRequestDto` **+ `profile: MandatoryProfileFieldsDto`** | `StaffUserDetailDto` (unchanged shape) | 201, 400 (missing/invalid profile field — no row persisted), 409 (existing `StaffUserAlreadyExistsException`) |
| POST | `/api/tenants/{tenantId}/members` (existing, `TenantController.addMember`) | `AddMemberRequestDto` **+ `profile: MandatoryProfileFieldsDto`** | `ResponseEntity<Void>` (unchanged — `addMember` returns no body today, corrected here from an earlier draft that assumed a `MemberDto` response) | 200, 400 (missing/invalid profile field — no row persisted) |
| Any non-allowlisted `/api/**` request while caller is pending | — | — | `{"code":"PROFILE_COMPLETION_REQUIRED"}` | 409 |

Every non-allowlisted, authenticated endpoint implicitly gains the 409
`PROFILE_COMPLETION_REQUIRED` response while the bootstrap account is
pending — this is a filter-level cross-cutting behavior (REQ-3/REQ-4),
not a per-controller contract change, so it isn't restated per-endpoint
elsewhere in the codebase's other PLAN.md files.

## Dependencies

None new — Bean Validation (`jakarta.validation`) is already a
dependency; no new library.

## Package/file structure

- `src/main/java/br/com/conectabyte/knowly/identity/dto/MandatoryProfileFieldsDto.java` (new)
- `src/main/java/br/com/conectabyte/knowly/identity/dto/MandatoryAddressDto.java` (new)
- `src/main/java/br/com/conectabyte/knowly/identity/ProfileCompletenessService.java` (new — `isComplete(User)`, reused by the filter, the login-outcome computation, and the completion endpoint's guard)
- `src/main/java/br/com/conectabyte/knowly/identity/ProfileAlreadyCompleteException.java` (new)
- `src/main/java/br/com/conectabyte/knowly/identity/UserProfileController.java` (modify: new `completeOwnProfile` handler)
- `src/main/java/br/com/conectabyte/knowly/identity/UserProfileService.java` (modify: `completeOwnProfile(User, MandatoryProfileFieldsDto)`, reusing existing field-setting/encryption helpers)
- `src/main/java/br/com/conectabyte/knowly/identity/exception/IdentityExceptionHandler.java` (modify: map `ProfileAlreadyCompleteException` → 409)
- `src/main/java/br/com/conectabyte/knowly/tenancy/ProfileCompletionFilter.java` (new)
- `src/main/java/br/com/conectabyte/knowly/config/SecurityConfig.java` (modify: register `ProfileCompletionFilter` before `TenantContextFilter`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/CreateStaffUserRequestDto.java` (modify: add `profile` field)
- `src/main/java/br/com/conectabyte/knowly/tenancy/dto/AddMemberRequestDto.java` (modify: add `profile` field)
- `src/main/java/br/com/conectabyte/knowly/tenancy/StaffService.java` (modify: `createStaffUser` persists `profile`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `addMember`/`createUserWithProfile` persist `profile`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantController.java` (modify: `addMember` handler passes `request.profile()` through to `TenantService.addMember`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantSessionOutcome.java` (modify: `Staff` variant gains `pendingProfileCompletion`)
- `src/main/java/br/com/conectabyte/knowly/tenancy/TenantService.java` (modify: `resolveSessionOutcome` computes it)
- `src/main/java/br/com/conectabyte/knowly/auth/AuthController.java` (modify: surface `pendingProfileCompletion` in the login-code-verify response)
- `src/main/java/br/com/conectabyte/knowly/audit/CreationValidationAuditAdvice.java` (new — scoped `MethodArgumentNotValidException` handler for `createStaffUser`/`addMember` only, per REQ-7/REQ-8's audit NFR)

## Testing strategy

- Unit: `ProfileCompletenessService` — complete profile, each
  individually-missing field (name/birthDate/cpf/rg/rgOrgaoEmissor,
  missing address, address missing one required column, zero contacts)
  all report incomplete.
- Integration (`@SpringBootTest`, Testcontainers, mirrors
  `TenantSessionIntegrationTest`):
  - Bootstrap account (seeded via the existing `V13` migration, now
    updated to `STAFF_ADMIN` by `V14`) starts pending; a request to an
    arbitrary staff-only endpoint is rejected `409
    PROFILE_COMPLETION_REQUIRED`; `GET /api/users/me/profile` and
    `/api/auth/**` remain reachable while pending.
  - `POST /api/users/me/profile/complete` with every required field
    present transitions the account — the next arbitrary request
    succeeds; the same call repeated afterwards is rejected `409
    PROFILE_ALREADY_COMPLETE`.
  - `POST /api/users/me/profile/complete` missing exactly one required
    field is rejected `400`; the account remains pending afterwards.
  - `StaffController.createStaffUser` missing any one mandatory profile
    field is rejected `400`; no `User`/`UserProfile` row exists
    afterwards (assert via repository count unchanged) and an audit
    event with the missing-field list is recorded.
  - `StaffController.createStaffUser` with every mandatory field present
    succeeds; the resulting user's `ProfileCompletenessService
    .isComplete` is immediately `true` and it is never pending.
  - `TenantService.addMember` mirrors both of the above for tenant
    members.
  - Login-code-verify response for the (still-pending) bootstrap account
    includes `pendingProfileCompletion: true`; after completion, a fresh
    login shows `false`.
