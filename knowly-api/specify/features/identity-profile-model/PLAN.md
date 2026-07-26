# PLAN — identity-profile-model

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md (including its "Resolved: CPF/RG uniqueness +
> encryption mechanism" section, which is the locked-in mechanism this
> PLAN implements — not re-derived here).

## Sequencing dependency (read first)

This feature reuses `tenant-membership-acceptance`'s `Notification`
entity (`br.com.conectabyte.knowly.tenancy.Notification`,
`NotificationType`, `NotificationRepository`, `NotificationService`,
`NotificationController`). **Verified as already implemented in
`src/main/java` at time of writing** (not just planned) — so there is no
actual sequencing wait needed; this section is kept only so a future
reader doesn't have to re-verify that. If a future implementer finds
`Notification` has since been removed/renamed, treat this as blocking
and re-read `tenant-membership-acceptance/PLAN.md` before proceeding.

One real constraint the existing `Notification` shape imposes on this
PLAN: `Notification.tenantMembership` is `@ManyToOne(optional = false)`
— every existing `Notification` is anchored to a `TenantMembership` row.
A profile-edit-request notification has no such natural anchor (the
approver might be a `STAFF`/`STAFF_ADMIN` global-permission holder with
no membership in the requester's tenant at all, or the requester
themself may be staff with no membership row whatsoever — see
`CLAUDE.md`'s documented staff/no-real-membership edge case, backend
analog). See "New `ProfileEditRequest` entity" below for how this is
resolved without weakening `Notification`'s existing NOT NULL contract
for its original consumer.

## Architectural decisions

- **New `User` fields**: `fullName`, `address`, `rg`, `cpf`, `phone` —
  all nullable `VARCHAR` columns (REQ-1, REQ-2a/retrofit convention
  already established by `onboarding_completed_at`). `rg`/`cpf` are
  additionally routed through `CpfRgEncryptionConverter` (see below).
  *Why nullable*: matches SPEC Decision 3 and the existing
  `onboarding_completed_at` precedent — no backfill, no forced
  `NOT NULL`.
- **`cpf`/`rg` encrypted at rest via a new `CpfRgEncryptionConverter`**
  (`AttributeConverter<String, String>`, `@Converter`), AES-256-GCM with
  a random 96-bit IV generated per write, IV prepended to the ciphertext
  before Base64 encoding (`Base64(iv || ciphertext || tag)`, GCM's tag
  already appended by the cipher) so a single opaque `String` column
  round-trips through one converter method pair with no extra column
  needed for the IV. *Why GCM*: authenticated encryption (detects
  tampering, not just confidentiality) and the JDK's `Cipher` supports
  it natively with no new dependency. *Why per-write random IV*: this is
  exactly the property that makes the column unusable for equality —
  which is precisely why the blind index exists (SPEC's "Resolved"
  section) rather than trying to make the encrypted column itself
  support uniqueness.
- **Blind index: two new columns `cpfBlindIndex`, `rgBlindIndex`**
  (`VARCHAR(64)`, hex-encoded HMAC-SHA256 output), computed by a new
  `BlindIndexService` at the same point every write to `cpf`/`rg`
  happens (`UserProfileService`, never a separate/independent write
  path — SPEC's "Resolved" section requirement: "populated alongside
  every write... never independently editable"). Concretely:
  `UserProfileService` never calls `user.setCpf(...)` without also
  calling `user.setCpfBlindIndex(blindIndexService.hmac(cpf))` in the
  same method, and there is no setter exposed at the controller/DTO
  layer for the blind-index columns themselves — they are
  service-internal, derived, never part of any request DTO.
  - **Normalization rule** (applied identically before both encryption
    and hashing, so `"123.456.789-00"` and `"12345678900"` always
    collide/deduplicate correctly): strip every non-digit character
    (`value.replaceAll("[^0-9]", "")`). Empty string after stripping is
    treated as "not provided" (null), consistent with REQ-2a.
  - **Two independent keys, both externally sourced** — reusing this
    codebase's existing `${VAR}`-from-environment convention (see
    `application.yaml`'s `OPENAI_API_KEY`/`TURNSTILE_SECRET_KEY`
    pattern, i.e. bare `${SOME_ENV_VAR}`, never `${VAR:?msg}` inside
    `application.yaml` per `DECISIONS.md`'s existing entry on that
    exact syntax trap):
    ```yaml
    knowly:
      identity:
        cpf-rg-encryption-key: ${CPF_RG_ENCRYPTION_KEY}
        cpf-rg-hmac-key: ${CPF_RG_HMAC_KEY}
    ```
    Bound via a new `@ConfigurationProperties(prefix = "knowly.identity")`
    class (`IdentityCryptoProperties`), mirroring however this codebase
    already binds `knowly.*` properties elsewhere (checked: `storage`,
    `article`, `auth` sections in `application.yaml` follow this same
    externally-sourced-secret shape). Both are base64-encoded 256-bit
    keys, decoded once at startup. **Tier 2 judgment call**: no existing
    precedent for a second, independent secret pair in this codebase, so
    this is a new but unsurprising application of the existing
    "secrets never hardcoded, always `${VAR}`" rule — documented in
    `DECISIONS.md` (see below) since it establishes the pattern for any
    future blind-index need, not because the choice itself is
    controversial.
  - `CpfRgEncryptionConverter` and `BlindIndexService` both read from
    `IdentityCryptoProperties`, never from each other's key.
- **DB-level uniqueness (REQ-2) lives entirely on the blind-index
  columns**, not on the encrypted `cpf`/`rg` columns themselves (partial
  unique indexes `WHERE cpf_blind_index IS NOT NULL` /
  `WHERE rg_blind_index IS NOT NULL`) — per SPEC's "Resolved" section,
  the encrypted columns are never used for equality. `email` uniqueness
  is unchanged (`ux_users_email_lower` already exists). New partial
  unique indexes for `address` and `phone` (plain columns, no encryption
  requirement in SPEC/REQ-3 — only `cpf`/`rg` are named as needing
  encryption; `address`/`phone` are uniqueness-enforced in plaintext).
- **New `Tenant` fields**: `cnpj` (nullable, partial-unique), `razaoSocial`
  (nullable — SPEC Decision 1 calls it "required" as a business rule but
  REQ-7b's retrofit exception means existing rows must tolerate it unset
  at the DB level; app-level validation on new/edited tenants enforces
  "required" going forward, matching how `onboarding_completed_at`-style
  fields are handled elsewhere: DB nullable, service-layer required for
  new writes), `nomeFantasia` (same nullable-at-DB/required-at-service
  shape), `inscricaoEstadual` (nullable, partial-unique per REQ-7a). No
  encryption — not personal data (SPEC explicit).
- **New `Permission.PROFILE_VIEW`/`PROFILE_EDIT`** (tenant-scoped enum
  value) and **`GlobalPermission.PROFILE_VIEW`/`PROFILE_EDIT`**
  (global-scoped enum value) — plain additions to the existing enums,
  no new grant-storage mechanism (`DirectPermissionGrant`/`AccessGroup`/
  `DirectGlobalPermissionGrant`/`GlobalAccessGroup` already generically
  support any enum value, per `staff-rbac-split`'s reference shape —
  confirmed by reading `PermissionService`/`GlobalPermissionService`,
  which iterate grants generically and never hardcode a permission
  list).
- **Authorization design for REQ-9 through REQ-14a**, layered as
  explicit checks in `UserProfileService` rather than only
  `@RequiresPermission`/`@RequiresGlobalPermission`, because the actual
  rule set here is richer than "does the caller hold permission X" —
  it depends on *whose* record is targeted (self vs. other) and on
  admin-role shortcuts that bypass the permission system entirely:
  - `STAFF_ADMIN` (`tenantContext.isStaffAdmin()`): full bypass, any
    user, including self (REQ-12) — already the existing aspect
    behavior for any `@Requires*`-annotated method, reused as-is.
  - `MEMBER_ADMIN` of the target user's tenant: full bypass for that
    tenant's members only, including self (REQ-11) — **not** expressible
    by `@RequiresPermission` alone (that annotation checks a `Permission`
    grant, not a `MembershipRole`), so `UserProfileService` checks
    `TenantMembership.getRole() == MEMBER_ADMIN` directly via
    `TenantMembershipRepository`, mirroring how `TenantService`'s own
    admin-checks already work (`requireAdminOfTenantOrStaff`-style
    lookup, reused pattern, not invented).
  - Tenant-scoped `PROFILE_EDIT`/`PROFILE_VIEW` holder (REQ-10/13/13a):
    checked via `PermissionService.hasPermission(membership,
    Permission.PROFILE_EDIT)` exactly like any other
    `@RequiresPermission` consumer, **plus** an explicit
    `!targetUserId.equals(callerUserId)` guard before allowing a direct
    edit (REQ-13a) — this self-exclusion is the part no existing
    aspect/annotation expresses, so it's an explicit `if` in
    `UserProfileService.directEdit`, not a new annotation parameter
    (Tier 1: same shape as any other business-rule guard already inside
    a service method, e.g. `NotificationService`'s recipient check).
  - Global-scoped `PROFILE_EDIT`/`PROFILE_VIEW` holder, `STAFF` (not
    `STAFF_ADMIN`) (REQ-10a/14/14a): identical shape via
    `GlobalPermissionService.hasPermission(user,
    GlobalPermission.PROFILE_EDIT)` + the same self-exclusion guard.
  - **Concretely**: `UserProfileController`'s edit endpoint carries no
    `@RequiresPermission`/`@RequiresGlobalPermission` annotation at all
    (since no single annotation can express "admin bypass OR
    tenant-permission-with-self-exclusion OR
    global-permission-with-self-exclusion") — `UserProfileService`
    performs the full REQ-9…14a decision tree itself, throwing
    `PermissionDeniedException` (existing exception, existing
    `@ControllerAdvice` mapping to 403) for every rejected path. This
    mirrors `NotificationService`'s existing precedent of doing its own
    authorization inline rather than forcing SPEC-specific logic into a
    generic aspect. **Tier 2 judgment call**, documented in
    `DECISIONS.md` (see below) since this is the first feature whose
    authorization genuinely doesn't fit the `@Requires*` shape.
  - View (REQ-8/9/10/10a/10b/10c) follows the identical decision tree
    minus the self-exclusion guard (viewing your own record is always
    allowed per REQ-8, viewing others requires one of the
    admin-bypass/permission paths — no "can't view self via this grant"
    restriction exists in the SPEC, unlike edit).
- **New `ProfileEditRequest` entity** (own table, not a bare new
  `Notification.type` value with no backing payload) — **the SPEC says
  "reuses the `Notification` entity/mechanism, adding new `type`
  values," and this PLAN keeps `Notification` as the actual
  notification/inbox row (REQ-16) with no changes to its existing
  columns**, but a `Notification` alone has nowhere to hold the proposed
  field values (REQ-15) or a resolvable pending/approved/rejected state
  (REQ-17/18/19/20) — `Notification.resolved` is a plain boolean with no
  "what was proposed" payload, and its `tenantMembership` FK is
  `NOT NULL`/tenant-anchored, which a staff-to-staff or
  staff-approving-a-tenant-member's request scenario doesn't always
  have. So: `ProfileEditRequest` is the actual pending-request business
  record (requester, proposed field values, status, resolver, resolved
  timestamp); `Notification` gets a **new nullable**
  `profile_edit_request_id` column (FK to `ProfileEditRequest`) and its
  existing `tenant_membership_id` column is **relaxed to nullable** —
  exactly one of the two FKs is populated per row, enforced by a new
  `CHECK` constraint (`tenant_membership_id IS NOT NULL OR
  profile_edit_request_id IS NOT NULL`, and the two are never both set).
  New `NotificationType.PROFILE_EDIT_REQUEST_PENDING` value. **This is a
  schema change to a table introduced by another already-implemented
  feature** (relaxing `tenant_membership_id` to nullable) — flagged
  explicitly: it does not remove any guarantee `tenant-membership-
  acceptance` relies on (every existing row still has it populated;
  every existing query path — `NotificationService.listMine`, the
  recipient-scoped accept/decline lookups — filters by `recipient`/`id`
  first and only reads `tenantMembership` for rows whose `type` is a
  membership-invitation type, never assumes it's non-null generically).
  Verified by reading `NotificationService`/`NotificationController` in
  full: neither dereferences `.getTenantMembership()` without already
  being on a membership-invitation-only code path. **Tier 2 judgment
  call** (reusing a shared entity's shape by relaxing a constraint,
  rather than duplicating a second in-app-notification mechanism) —
  documented in `DECISIONS.md` below.
- **New `UserProfileService`** (`br.com.conectabyte.knowly.identity`
  package — new package, since `User`/personal-data concerns don't
  naturally belong in `auth` (session/credentials) or `tenancy`
  (membership/permissions) — a `User`'s profile is neither):
  - `getOwnProfile(User caller)` — REQ-8.
  - `getProfile(User caller, Long targetUserId)` — REQ-9/10/10a/10b/10c
    decision tree (view variant, no self-exclusion).
  - `directEdit(User caller, Long targetUserId, ProfileFieldsDto fields)`
    — REQ-11/12/13/13a/14/14a decision tree; throws
    `PermissionDeniedException` for a rejected self-edit-via-permission
    attempt (REQ-13a/14a) or no-applicable-right at all (REQ-9-style
    rejection extended to edit).
  - `submitEditRequest(User caller, ProfileFieldsDto proposed)` — REQ-15;
    throws a new `PendingProfileEditRequestExistsException` (409) if an
    unresolved `ProfileEditRequest` already exists for that requester
    (REQ-20); creates the `ProfileEditRequest` + one `Notification` per
    holder of the applicable edit right over the requester's record
    (REQ-16) — "applicable edit right" resolved via the same
    admin/permission enumeration `directEdit` uses (every
    `MEMBER_ADMIN` of the requester's tenant(s), every tenant-scoped
    `PROFILE_EDIT` holder in those tenants, every `STAFF_ADMIN`, every
    global-scoped `PROFILE_EDIT` holder) — deduplicated by recipient
    `User` id, mirroring `tenant-membership-acceptance`'s existing
    accept-notification dedup precedent.
  - `approveEditRequest(User caller, Long requestId)` — REQ-17; re-runs
    the same permission check as `directEdit` against the *original*
    requester as target (a caller who no longer holds an applicable
    right by the time of approval is rejected — REQ-19); applies fields
    directly to the `User` row (going through the same
    `UserProfileService` field-write helper `directEdit` uses, so the
    blind-index columns are always kept in sync — see decision above);
    on a uniqueness violation from the blind-index partial unique index
    (caught as `DataIntegrityViolationException`, standard Spring Data
    translation), rolls back and throws a new
    `ProfileFieldConflictException` (REQ-21, mapped to 409) instead of
    ever partially applying fields.
  - `rejectEditRequest(User caller, Long requestId)` — REQ-18; same
    right-check as approve, discards proposed values, marks resolved.
  - Every method call site for direct edit/approve reuses one private
    `applyFields(User target, ProfileFieldsDto fields)` helper that sets
    plain fields directly and routes `cpf`/`rg` through
    `BlindIndexService` in the same call — this is the single choke
    point that satisfies "populated alongside every write... never
    independently editable."
- **`ProfileEditRequestRepository`** — plain `JpaRepository`, plus
  `findByRequesterAndStatus(User requester, ProfileEditRequestStatus
  status)` for the REQ-20 pending-check.
- **Audit logging**: `@AuditLog` on `directEdit` (action
  `identity.profile.edit`, resourceType `User`), `submitEditRequest`
  (`identity.profile.edit_request.submit`, resourceType
  `ProfileEditRequest`), `approveEditRequest`/`rejectEditRequest`
  (`identity.profile.edit_request.approve`/`.reject`), and the
  view-permission-denied path (reuses the existing
  `PermissionDeniedException`→`@ControllerAdvice` mapping, which per
  the existing `AuditLog` aspect design already logs failures — verified
  by reading `AuditLog`'s javadoc: "for every call... success or
  failure"). `metadata` carries only field *names* changed (e.g.
  `["fullName","address"]`), never values — `cpf`/`rg` values are never
  placed in `metadata` under any circumstance, per SPEC NFR.
- **New controller `UserProfileController`**, `/api/users/{id}/profile`
  and `/api/users/me/profile`, following `TenantController`'s existing
  shape (record DTOs, `ResponseEntity<...>`, `currentUser()` from
  `SecurityContextHolder`).
- **New controller `ProfileEditRequestController`**,
  `/api/profile-edit-requests`, following `NotificationController`'s
  existing shape for the approve/reject actions (REQ-17/18/19 map to
  POST `.../approve` / `.../reject`, same 403/404/409 exception-mapping
  convention as `Notification`'s existing
  `NotificationAlreadyResolvedException`).

## Data schema

`User` (new columns, all nullable, migration V17):
```sql
ALTER TABLE users ADD COLUMN full_name VARCHAR(255);
ALTER TABLE users ADD COLUMN address VARCHAR(500);
ALTER TABLE users ADD COLUMN rg VARCHAR(255);           -- encrypted (Base64 ciphertext)
ALTER TABLE users ADD COLUMN cpf VARCHAR(255);          -- encrypted (Base64 ciphertext)
ALTER TABLE users ADD COLUMN phone VARCHAR(50);
ALTER TABLE users ADD COLUMN rg_blind_index VARCHAR(64);
ALTER TABLE users ADD COLUMN cpf_blind_index VARCHAR(64);

CREATE UNIQUE INDEX ux_users_address ON users (address) WHERE address IS NOT NULL;
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX ux_users_rg_blind_index ON users (rg_blind_index) WHERE rg_blind_index IS NOT NULL;
CREATE UNIQUE INDEX ux_users_cpf_blind_index ON users (cpf_blind_index) WHERE cpf_blind_index IS NOT NULL;
```

`users_aud` (Envers, `User` is already `@Audited`): mirror new columns
as nullable, no backfill needed (Envers audit rows are historical, never
retrofitted).
```sql
ALTER TABLE users_aud ADD COLUMN full_name VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN address VARCHAR(500);
ALTER TABLE users_aud ADD COLUMN rg VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN cpf VARCHAR(255);
ALTER TABLE users_aud ADD COLUMN phone VARCHAR(50);
ALTER TABLE users_aud ADD COLUMN rg_blind_index VARCHAR(64);
ALTER TABLE users_aud ADD COLUMN cpf_blind_index VARCHAR(64);
```
**Security note carried into TASKS.md/appsec review**: `users_aud`
stores the *same encrypted* `cpf`/`rg` ciphertext as `users` per Envers'
standard column-mirroring behavior — this is consistent with REQ-3
("stored encrypted at rest") applying to every historical revision too,
not a new exposure, but it does mean a key rotation must also handle
historical `_aud` rows, out of scope for this feature but worth flagging
in `PROJECT_STATUS.md`.

`Tenant` (new columns, migration V17):
```sql
ALTER TABLE tenants ADD COLUMN cnpj VARCHAR(20);
ALTER TABLE tenants ADD COLUMN razao_social VARCHAR(255);
ALTER TABLE tenants ADD COLUMN nome_fantasia VARCHAR(255);
ALTER TABLE tenants ADD COLUMN inscricao_estadual VARCHAR(30);

CREATE UNIQUE INDEX ux_tenants_cnpj ON tenants (cnpj) WHERE cnpj IS NOT NULL;
CREATE UNIQUE INDEX ux_tenants_inscricao_estadual ON tenants (inscricao_estadual) WHERE inscricao_estadual IS NOT NULL;
```
`tenants_aud`: mirror as nullable, same pattern as `users_aud` above.

`Notification` (existing table, relaxed + extended — migration V17):
```sql
ALTER TABLE notifications ALTER COLUMN tenant_membership_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN profile_edit_request_id BIGINT
  REFERENCES profile_edit_requests (id);
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_exactly_one_ref
  CHECK (
    (tenant_membership_id IS NOT NULL AND profile_edit_request_id IS NULL)
    OR (tenant_membership_id IS NULL AND profile_edit_request_id IS NOT NULL)
  );
```
(`notifications_aud` does not exist — SPEC's sibling feature explicitly
omitted it; nothing to change there.)

New table `profile_edit_requests` (migration V17):
```sql
CREATE TABLE profile_edit_requests (
  id BIGSERIAL PRIMARY KEY,
  requester_user_id BIGINT NOT NULL REFERENCES users (id),
  proposed_full_name VARCHAR(255),
  proposed_address VARCHAR(500),
  proposed_rg VARCHAR(255),          -- encrypted, same converter as User.rg
  proposed_cpf VARCHAR(255),         -- encrypted, same converter as User.cpf
  proposed_phone VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  resolved_by_user_id BIGINT REFERENCES users (id),
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_profile_edit_requests_requester_pending
  ON profile_edit_requests (requester_user_id) WHERE status = 'PENDING';
```
`ProfileEditRequestStatus` enum: `PENDING`, `APPROVED`, `REJECTED`. Only
one `PENDING` row per requester is enforced at the service layer
(REQ-20; not a DB constraint, since "one row per requester regardless of
status" isn't the rule — a resolved row must not block a new one).
`ProfileEditRequest` is **not** `@Audited`/Envers, same rationale as
`Notification` (ephemeral request state; the resulting `User` field
change is itself Envers-audited via `users_aud`, which is what matters
for history).

## API contracts

New `UserProfileController`:

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | — | `UserProfileDto` | 200 |
| GET | `/api/users/{id}/profile` | — | `UserProfileDto` | 200, 403 (REQ-9), 404 |
| PUT | `/api/users/{id}/profile` | `ProfileFieldsDto` | `UserProfileDto` | 200, 403 (REQ-9/13a/14a), 404, 409 (uniqueness) |
| POST | `/api/users/me/profile/edit-requests` | `ProfileFieldsDto` | `ProfileEditRequestDto` | 201, 409 (REQ-20 already-pending) |

New `ProfileEditRequestController`:

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/profile-edit-requests` | — | `List<ProfileEditRequestDto>` (pending, applicable to caller's right) | 200 |
| POST | `/api/profile-edit-requests/{id}/approve` | — | — | 200, 403 (REQ-19), 404, 409 (already resolved, or REQ-21 uniqueness conflict) |
| POST | `/api/profile-edit-requests/{id}/reject` | — | — | 200, 403 (REQ-19), 404, 409 (already resolved) |

`ProfileFieldsDto`: `record ProfileFieldsDto(String fullName, String
address, String rg, String cpf, String phone)` — plaintext in
request/response; `rg`/`cpf` are decrypted into this shape only when
`UserProfileService` has already confirmed the caller's applicable
right (REQ-4). No blind-index field is ever part of this DTO (derived,
not client-settable).

`UserProfileDto`: same shape as `ProfileFieldsDto` plus `Long userId`,
`String email`.

`ProfileEditRequestDto`: `record ProfileEditRequestDto(Long id, Long
requesterUserId, ProfileFieldsDto proposedFields,
ProfileEditRequestStatus status, Instant createdAt)`.

Tenant's new fields (`cnpj`/`razaoSocial`/`nomeFantasia`/
`inscricaoEstadual`) are exposed through whatever existing
tenant-detail/update contract already exists in `TenantController`
(out of this feature's controller scope per SPEC's "Tenant/role
management" out-of-scope line — this PLAN only adds the columns/entity
fields; wiring them into `TenantController`'s existing DTOs, if not
already generic enough to pick them up, is a one-line addition tracked
in TASKS.md, not a new endpoint).

## Dependencies

None new. `Cipher`/`Mac`/`SecureRandom` (AES-GCM, HMAC-SHA256) are
JDK-standard (`javax.crypto`), no new `pom.xml` entry.

## Package/file structure

New package `br.com.conectabyte.knowly.identity`:
- `UserProfileService.java` (new)
- `UserProfileController.java` (new)
- `ProfileEditRequestService.java` (new — or folded into
  `UserProfileService`; kept separate here since approve/reject act on a
  distinct resource with its own repository, mirroring
  `NotificationService` being separate from `TenantService`)
- `ProfileEditRequestController.java` (new)
- `ProfileEditRequest.java` (new entity)
- `ProfileEditRequestStatus.java` (new enum)
- `ProfileEditRequestRepository.java` (new)
- `CpfRgEncryptionConverter.java` (new, `@Converter`)
- `BlindIndexService.java` (new)
- `IdentityCryptoProperties.java` (new `@ConfigurationProperties`)
- `dto/ProfileFieldsDto.java`, `dto/UserProfileDto.java`,
  `dto/ProfileEditRequestDto.java` (new)
- `exception/PendingProfileEditRequestExistsException.java`,
  `exception/ProfileFieldConflictException.java` (new, 409-mapped)

Modified:
- `br.com.conectabyte.knowly.auth.User.java` — new fields +
  `@Convert(converter = CpfRgEncryptionConverter.class)` on `cpf`/`rg`.
- `br.com.conectabyte.knowly.tenancy.Tenant.java` — new fields.
- `br.com.conectabyte.knowly.tenancy.Permission.java` — add
  `PROFILE_VIEW`, `PROFILE_EDIT`.
- `br.com.conectabyte.knowly.tenancy.GlobalPermission.java` — add
  `PROFILE_VIEW`, `PROFILE_EDIT`.
- `br.com.conectabyte.knowly.tenancy.Notification.java` — relax
  `tenantMembership` to nullable, add nullable `profileEditRequest`
  `@ManyToOne`.
- `br.com.conectabyte.knowly.tenancy.NotificationType.java` — add
  `PROFILE_EDIT_REQUEST_PENDING`.
- `src/main/resources/application.yaml` — add `knowly.identity.*` keys.
- `src/main/resources/db/migration/V17__add_identity_profile_fields.sql`
  (new — next after V16).

## Testing strategy

- Unit: `CpfRgEncryptionConverterTest` — round-trip encrypt/decrypt,
  distinct ciphertext for identical plaintext across two calls
  (confirms randomized IV), tamper-detection (flipped byte fails to
  decrypt).
- Unit: `BlindIndexServiceTest` — normalization (formatted vs.
  unformatted CPF/RG hash identically), same plaintext always same hash
  with the same key, empty-after-stripping treated as absent.
- Unit: `UserProfileServiceTest` — full REQ-8…14a decision matrix
  (`STAFF_ADMIN` self/other, `MEMBER_ADMIN` self/other,
  tenant-`PROFILE_EDIT` self (rejected)/other (allowed), global-
  `PROFILE_EDIT` self (rejected)/other (allowed), no-right (rejected));
  `submitEditRequest` double-submission rejection (REQ-20);
  `approveEditRequest`/`rejectEditRequest` apply/discard + resolve;
  uniqueness-violation-on-approve → `ProfileFieldConflictException`
  (REQ-21), transactional rollback verified (no partial field write).
- Integration (`@SpringBootTest`, Testcontainers, mirrors
  `TenantManagementIntegrationTest`): DB-level uniqueness rejects
  duplicate `cpf`/`rg` (via blind index)/`phone`/`address` even via
  direct repository save bypassing service validation; `cpf`/`rg`
  verified encrypted at the raw JDBC/column level (raw query shows
  Base64 ciphertext, not plaintext); full request→approve flow end to
  end; full request→reject flow; `Notification` row created for every
  applicable-right holder, deduplicated; existing
  `tenant-membership-acceptance` `Notification` flows
  (accept/decline invitation) regression-tested unchanged after the
  nullable-FK migration.
