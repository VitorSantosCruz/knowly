# PLAN — identity-profile-model-v2 (backend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md and `DECISIONS.md`'s "`identity-profile-model`
> retrofit" entry (the confirmed schema/permission-model source of
> truth — not re-derived here). Also references the original
> `identity-profile-model/PLAN.md` for the encryption/blind-index
> mechanism this PLAN reuses unchanged.

## Sequencing

This is a retrofit of already-shipped, already-running code
(`UserProfileService`, `UserProfileController`, `ProfileEditRequest*`,
`CpfRgEncryptionConverter`, `BlindIndexService` — all exist today per
`identity-profile-model`). Every one of those classes is modified or
replaced in place by this feature; none of it is greenfield. Tasks are
sequenced migration → entities → services → controllers, same TDAD order
as any other feature, but each task should be read as "retrofit X," not
"add X."

## Architectural decisions

- **Three new JPA entities** (`br.com.conectabyte.knowly.identity`
  package, already exists): `UserProfile` (`@Id` = `user_id`, `@OneToOne`
  to `User`, eager row per REQ-1), `Address` (`@Id` = `user_id`,
  `@OneToOne`, created only on first submit per REQ-2), `Contact`
  (`@Id` auto, `@ManyToOne` to `User`, per REQ-3). `CpfRgEncryptionConverter`
  and `BlindIndexService` are reused unchanged, just applied to
  `UserProfile.cpf`/`UserProfile.rg` instead of `User.cpf`/`User.rg`
  (same converter, same keys, same normalization rule — no change to
  either class's internals).
- **`UserProfile` created eagerly at account creation** — a new call in
  the existing user-registration path (`AuthService`/equivalent
  registration flow) creates an empty `UserProfile` row for every new
  `User` in the same transaction. *Why eager, not lazy-on-first-submit*:
  `DECISIONS.md`'s schema block states this explicitly ("row created
  eagerly... nullable: eager row, empty until filled") — the
  alternative (create-on-first-write) would need `UserProfileService` to
  distinguish "no row yet" from "row exists, all fields null" for every
  read/edit path for no behavioral benefit, since every user needs one
  eventually. `Address`/`Contact` stay lazy (created only when first
  submitted) — no reason to eagerly reserve rows for data most users may
  never enter, and unlike `UserProfile` there's no 1:1-with-every-user
  guarantee needed by any query.
- **`ContactType` enum** (`PHONE`, `WHATSAPP`, `EMAIL`, `OTHER`),
  `@Enumerated(EnumType.STRING)` on `Contact.type` — closed set matching
  `DECISIONS.md`'s schema (`type VARCHAR(20)`). See "Open decision c"
  below for validation approach.
- **`ContactService`** (new, `br.com.conectabyte.knowly.identity`) —
  owns the 5-contacts-per-user cap (REQ-3a) and the one-primary-per-type
  invariant (REQ-3b), both as explicit service-layer checks before
  persisting, per `DECISIONS.md`'s explicit "enforced in `ContactService`,
  not the database" call for the cap (a cross-row COUNT isn't expressible
  as a Postgres `CHECK`) and the existing partial unique index
  `ux_contacts_primary_per_type` for the primary invariant (DB-enforced
  where SQL *can* express it, service-enforced only where it can't —
  consistent split, not an arbitrary choice).
- **Per-field permission model as a hard split in
  `UserProfileService`**: `avatar_url` gets its own always-allowed
  self-edit path (`updateOwnAvatar(User caller, String avatarUrl)`, no
  decision tree, no permission check beyond "is this the caller's own
  row") completely separate from every other field's
  direct-edit/request-edit decision tree. **This replaces
  `identity-profile-model`'s existing `directEdit` method** (which
  allowed `MEMBER_ADMIN`/`STAFF_ADMIN` self-edit for every field) with a
  version that unconditionally rejects self as a target for anything
  except the new `updateOwnAvatar` path — REQ-11 in SPEC.md is a genuine
  behavior removal from the shipped feature, not additive, so this isn't
  "add a check," it's "the self-exclusion guard `identity-profile-model`
  already had for tenant/global `PROFILE_EDIT` holders (old REQ-13a/14a)
  now applies unconditionally, including to the two admin bypasses that
  previously skipped it."
  - `STAFF_ADMIN`, `MEMBER_ADMIN` of the target's tenant, tenant-scoped
    `PROFILE_EDIT`, global-scoped `PROFILE_EDIT`: all four paths keep
    their existing "may edit *other* users" authorization exactly as
    `identity-profile-model`'s PLAN already implemented — only the
    "including self" clause is removed from the two admin paths.
  - `submitEditRequest`/`approveEditRequest`/`rejectEditRequest` keep
    their existing shape (own package-private `applyFields` helper,
    audit logging, `ProfileFieldConflictException` on blind-index
    violation) but now also carry `contacts` add/update/remove entries
    (REQ-15) — `applyFields` gains a second argument,
    `List<ContactChange> contactChanges`, applied via `ContactService`
    inside the same transaction as the `user_profiles`/`addresses`
    writes, so REQ-17's "apply every proposed field/contact change
    atomically" holds by construction (one `@Transactional` method, one
    rollback boundary).
- **Self-approval guard (REQ-22) becomes a DB `CHECK` in addition to the
  existing service-layer check** — `profile_edit_requests` gains
  `CHECK (resolved_by_user_id IS NULL OR resolved_by_user_id <>
  requester_user_id)`, closing the gap `DECISIONS.md` flags explicitly.
  The existing service-layer guard in `approveEditRequest`/
  `rejectEditRequest` is unchanged (still the primary, user-facing 403
  path) — the `CHECK` is defense-in-depth for a future code path that
  forgets it, not a replacement.

### Open decision (a): where `avatar_url` images are stored

**Decision: reuse the existing `article-management` MinIO/S3
infrastructure — a new `AvatarStorageService`, same shape as the
existing `ArticleStorageService`, same bucket-provisioning pattern, a
second bucket (`knowly.avatar.bucket`, distinct from
`knowly.article.bucket`) rather than a shared bucket.**

*Why reuse, not a new mechanism*: this codebase already has exactly one
object-storage integration (`S3ClientConfig`, `StorageProperties`,
`ArticleStorageService` — `PutObjectRequest`/presigned `GetObjectRequest`
against a MinIO-backed `S3Client`), already provisioned in `compose.yaml`
with the hardened `minio-init-permissions` one-shot pattern
(`DECISIONS.md` has an entry on that exact pattern). Introducing a
second storage mechanism for what is structurally the identical problem
(accept a file, store it, return a URL to fetch it) would be a
Tier 3 "new dependency/infra" decision this PLAN has no basis to make —
and there's no reason to: `ArticleStorageService.upload(key, bytes,
contentType)`/`.presignedUrl(key)` already generalize cleanly to
avatars.

*Why a separate bucket, not the shared article bucket*: avatars and
article files have different lifecycle/access-pattern needs (avatars:
one small image per user, replaced in place, needs to be publicly
viewable wherever a profile renders; articles: versioned content tied to
tenant-scoped permission checks, served via presigned URLs). Mixing them
in one bucket would couple two unrelated retention/access policies for
no benefit — this mirrors the existing precedent of `pgvector.dimensions`
and other config being pinned per-concern rather than shared just
because the underlying engine is the same.

**Concretely**:
- New `AvatarStorageService` (`br.com.conectabyte.knowly.identity`),
  same shape as `ArticleStorageService`: `upload(key, bytes,
  contentType)`, `presignedUrl(key)` (or a public/unsigned URL if the
  bucket is configured public-read — **Tier 2, deferred to TASKS.md
  implementation**: matches whichever of `ArticleStorageService`'s two
  read patterns proves simpler once `StorageProperties` is extended;
  either way `avatar_url` stored in `user_profiles` is always the
  resolvable-URL string, never a raw object key, so this choice is
  invisible to every consumer).
- `StorageProperties` (existing `@ConfigurationProperties` class) gains
  an `avatarBucket` field alongside its existing `bucket` (article)
  field — same externally-sourced-config shape, no new secret needed
  (same MinIO credentials, just a second bucket name).
- New endpoint `POST /api/users/me/profile/avatar` (multipart,
  `@RequestParam("file") MultipartFile file`), mirroring
  `ArticleController.upload`'s exact shape (`@RequestParam`
  `MultipartFile`, no separate presigned-upload-URL flow — this
  codebase's one existing upload precedent goes through the backend,
  not direct-to-bucket, so this doesn't introduce a second upload
  pattern). Calls `UserProfileService.updateOwnAvatar`, which calls
  `AvatarStorageService.upload` then persists the resulting URL/key on
  `UserProfile.avatarUrl`. No separate `PUT
  /api/users/{id}/profile` field for `avatar_url` — REQ-10 makes it
  self-only, so there is no "someone else sets my avatar" path to
  support, and the dedicated upload endpoint is a cleaner fit for a
  `MultipartFile` than folding it into the existing JSON `PUT` used by
  every other field.
- Max file size/content-type restriction: reuses whatever limit
  `ArticleController`'s upload endpoint already enforces
  (`spring.servlet.multipart.max-file-size`, existing global Spring
  config) — no new size limit invented; if avatars need a tighter
  image-specific cap, that's a follow-up flagged in TASKS.md, not
  assumed here.

### Open decision (b): existing `PENDING` `profile_edit_requests` rows

**Decision: mark any `PENDING` row `CANCELLED`
(new status value) during the migration, not migrated to the new
shape.**

*Why not attempt to backfill*: the old row's `proposed_address` is a
single free-text `VARCHAR(500)`; the new shape needs `cep`/
`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/`estado`/`pais` as
independent structured columns. There is no reliable, generic way to
parse an arbitrary free-text Brazilian address string into those fields
programmatically (that's a real geocoding/address-parsing problem, out
of scope for a data migration) — any backfill attempt would either
silently drop structure or require manual per-row review, neither of
which is proportionate to a pre-launch system. This mirrors the exact
reasoning the product owner already confirmed for `users.address`
itself (REQ-26/`DECISIONS.md`): no real production data, so a lossy
carry-forward isn't worth the complexity.

*Why cancel rather than silently delete*: cancelling preserves the row
(audit/history value — someone did submit something) while making it
unambiguous to both the requester and any future reader that it was
never resolved through the normal approve/reject path. A new
`ProfileEditRequestStatus.CANCELLED` value (alongside existing
`PENDING`/`APPROVED`/`REJECTED`) is added specifically for this — the
migration itself issues one `UPDATE profile_edit_requests SET status =
'CANCELLED' WHERE status = 'PENDING'` before the schema changes that
would otherwise leave those rows structurally unreadable. **Realism
check**: `identity-profile-model` shipped 2026-07-26, this retrofit is
being planned 2026-07-28 — a two-day-old feature on a pre-launch system
is very unlikely to have any real `PENDING` rows at all; this decision
exists for correctness/safety regardless, not because a large volume is
expected.
- No user-facing notification is sent for a cancelled row — the
  requester already has no record of "why" beyond it disappearing from
  any list they'd query; given the realism check above, this is
  accepted as a reasonable pre-launch tradeoff rather than building
  a migration-time notification path for an edge case expected to affect
  zero or near-zero real rows.

### Open decision (c): `contacts.type` validation

**Decision: `ContactType` as a closed Java enum (mapped
`@Enumerated(STRING)`, matches `DECISIONS.md`'s schema exactly) +
per-type format validation as an explicit check inside `ContactService`,
not a custom Bean Validation `@Constraint`.**

*Why not a custom `ConstraintValidator`*: verified this codebase has
**zero** existing `@Constraint`/`ConstraintValidator` classes anywhere
— every validation need so far has been satisfied by Jakarta's built-in
annotations (`@NotBlank`, `@Email`, etc. — see `LoginRequestDto`,
`VerifyPasswordRequestDto`, `UpdateArticleRequestDto`) applied to
individual fields. This case is structurally different: the correct
format for `value` depends on the *sibling* field `type` (an `EMAIL`
contact must look like an email, a `PHONE`/`WHATSAPP` contact must look
like a phone number) — that's a cross-field/conditional rule, which
Jakarta's built-in per-field annotations can't express without a
class-level custom `@Constraint` (the one shape this codebase has never
needed before). Introducing that machinery for exactly one conditional
rule would be more ceremony than the existing precedent for "a business
rule that depends on more than one field" already uses elsewhere in this
codebase — which is a plain `if` inside the owning service (e.g.
`UserProfileService`'s existing self-exclusion guard, `TenantService`'s
admin-checks, `NotificationService`'s recipient check — all cited by
`identity-profile-model/PLAN.md` as the established pattern for
service-layer business rules that don't fit a single annotation).
- **Concretely**: `ContactService.validateFormat(ContactType type,
  String value)`, called from every write path (`addContact`,
  and the `contacts` branch of `applyFields` on request approval) —
  `EMAIL` reuses Jakarta's own regex indirectly via a tiny helper that
  delegates to `jakarta.validation.constraints.Pattern`'s email regex
  equivalent (or simply constructs a throwaway record with `@Email` and
  runs it through the already-injected `Validator` bean — whichever
  proves less boilerplate at implementation time, functionally
  identical either way); `PHONE`/`WHATSAPP` validated against a
  Brazilian-phone-shaped regex (`^\+?\d{10,13}$` after stripping
  formatting characters, same normalization idiom
  `BlindIndexService.normalize` already established for CPF/RG — reused
  utility, not a second implementation); `OTHER` has no format
  restriction (deliberately unconstrained — it exists precisely for
  channels that don't fit the other three).
  Reject with a new `InvalidContactFormatException` (400), following
  the existing `PendingProfileEditRequestExistsException`/
  `ProfileFieldConflictException` exception-mapping convention.
- **Tier 2 judgment call, documented in `DECISIONS.md`** (see entry
  below) since this is the first feature that could have reached for a
  custom `@Constraint` and deliberately didn't — worth recording so a
  future contact-shaped or similarly cross-field validation need in this
  codebase doesn't reach for two different patterns for the same kind of
  problem.

## Data schema

New tables (migration `V18__retrofit_identity_profile_tables.sql`, next
after `V17`):

```sql
CREATE TABLE user_profiles (
  user_id             BIGINT PRIMARY KEY REFERENCES users(id),
  full_name           VARCHAR(255),
  cpf                 VARCHAR(255),
  cpf_blind_index     VARCHAR(64),
  rg                  VARCHAR(255),
  rg_orgao_emissor    VARCHAR(20),
  rg_blind_index      VARCHAR(64),
  birth_date          DATE,
  avatar_url          VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL,
  CHECK ((cpf IS NULL) = (cpf_blind_index IS NULL)),
  CHECK ((rg IS NULL) = (rg_blind_index IS NULL))
);
CREATE UNIQUE INDEX ux_user_profiles_cpf_blind_index ON user_profiles (cpf_blind_index) WHERE cpf_blind_index IS NOT NULL;
CREATE UNIQUE INDEX ux_user_profiles_rg_blind_index ON user_profiles (rg_blind_index) WHERE rg_blind_index IS NOT NULL;

CREATE TABLE addresses (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  cep VARCHAR(9) NOT NULL CHECK (cep ~ '^\d{5}-?\d{3}$'),
  logradouro VARCHAR(255) NOT NULL,
  numero VARCHAR(20),
  complemento VARCHAR(100),
  bairro VARCHAR(100) NOT NULL,
  cidade VARCHAR(100) NOT NULL,
  estado CHAR(2) NOT NULL CHECK (estado ~ '^[A-Z]{2}$'),
  pais VARCHAR(100) NOT NULL DEFAULT 'Brasil',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE TABLE contacts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(20) NOT NULL,
  value VARCHAR(255) NOT NULL,
  label VARCHAR(50),
  is_primary BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);
CREATE INDEX idx_contacts_user_id ON contacts (user_id);
CREATE UNIQUE INDEX ux_contacts_primary_per_type ON contacts (user_id, type) WHERE is_primary;

-- retrofit profile_edit_requests: replace V17's flat proposed_* (still
-- present) with the same shape re-pointed at the new tables, plus new
-- proposed_birth_date/proposed_rg_orgao_emissor, plus status gains
-- CANCELLED, plus the self-approval CHECK.
ALTER TABLE profile_edit_requests ADD COLUMN proposed_rg_orgao_emissor VARCHAR(20);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_birth_date DATE;
ALTER TABLE profile_edit_requests ADD COLUMN proposed_cep VARCHAR(9);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_logradouro VARCHAR(255);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_numero VARCHAR(20);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_complemento VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_bairro VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_cidade VARCHAR(100);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_estado CHAR(2);
ALTER TABLE profile_edit_requests ADD COLUMN proposed_pais VARCHAR(100);
-- old proposed_address (free-text) column dropped, unused going forward
ALTER TABLE profile_edit_requests DROP COLUMN proposed_address;
ALTER TABLE profile_edit_requests ADD CONSTRAINT chk_profile_edit_requests_no_self_approval
  CHECK (resolved_by_user_id IS NULL OR resolved_by_user_id <> requester_user_id);
-- status VARCHAR(20) already has no DB-level enum constraint (Java enum only) —
-- CANCELLED is just a new value the Java ProfileEditRequestStatus enum accepts.

CREATE TABLE profile_edit_request_contacts (
  id                       BIGSERIAL PRIMARY KEY,
  profile_edit_request_id BIGINT NOT NULL REFERENCES profile_edit_requests(id),
  action                   VARCHAR(10) NOT NULL,
  contact_id               BIGINT REFERENCES contacts(id),
  type                     VARCHAR(20),
  value                    VARCHAR(255),
  label                    VARCHAR(50),
  is_primary               BOOLEAN,
  CHECK (
    (action = 'ADD' AND contact_id IS NULL)
    OR (action IN ('UPDATE','REMOVE') AND contact_id IS NOT NULL)
  )
);
CREATE INDEX idx_profile_edit_request_contacts_request ON profile_edit_request_contacts (profile_edit_request_id);

-- cancel any in-flight PENDING request before its shape changes further
-- (open decision b) — issued before the ALTER/DROP above in the actual
-- migration file so it runs against the still-old shape.
UPDATE profile_edit_requests SET status = 'CANCELLED', resolved_at = now()
  WHERE status = 'PENDING';

-- backfill from users into the new tables (REQ-24)
INSERT INTO user_profiles (user_id, full_name, cpf, cpf_blind_index, rg, rg_blind_index, created_by, updated_by)
  SELECT id, full_name, cpf, cpf_blind_index, rg, rg_blind_index, 'migration', 'migration'
  FROM users WHERE full_name IS NOT NULL OR cpf IS NOT NULL OR rg IS NOT NULL;
-- users with none of the above still get an eager empty row (REQ-1):
INSERT INTO user_profiles (user_id, created_by, updated_by)
  SELECT id, 'migration', 'migration' FROM users
  WHERE id NOT IN (SELECT user_id FROM user_profiles);

INSERT INTO contacts (user_id, type, value, is_primary, created_by, updated_by)
  SELECT id, 'PHONE', phone, true, 'migration', 'migration'
  FROM users WHERE phone IS NOT NULL;
-- users.address is explicitly NOT migrated (REQ-26).
```

`user_profiles_aud`/`addresses_aud`/`contacts_aud`
(`profile_edit_request_contacts` not audited, same rationale
`ProfileEditRequest` itself already has — ephemeral request state):
`UserProfile`/`Address`/`Contact` are all `@Audited` (Envers), mirroring
`User`'s existing audit posture for the same fields, just relocated —
Flyway migration creates the matching `_aud` tables in the same file.

A later migration (`V19`, only after this feature's code path is
verified running — REQ-27, tracked as its own TASKS.md item, not bundled
into `V18`) drops `users`/`users_aud`'s `full_name`/`address`/`rg`/
`cpf`/`phone`/`rg_blind_index`/`cpf_blind_index` columns.

`StorageProperties` gains `avatarBucket` (new field, `knowly.storage.avatar-bucket:
${AVATAR_BUCKET:knowly-avatars}` — same `${VAR:default}` shape
`StorageProperties`'s existing `bucket` field already uses, verified by
reading `application.yaml`'s current `knowly.storage.*` block).

## API contracts

`UserProfileController` (existing, retrofitted):

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | — | `UserProfileDto` | 200 |
| GET | `/api/users/{id}/profile` | — | `UserProfileDto` | 200, 403, 404 |
| PUT | `/api/users/{id}/profile` | `ProfileFieldsDto` (no `avatarUrl`) | `UserProfileDto` | 200, 403 (self-target always, REQ-11), 404, 409 (blind-index uniqueness) |
| POST | `/api/users/me/profile/edit-requests` | `ProfileEditRequestFieldsDto` (fields + `contactChanges`) | `ProfileEditRequestDto` | 201, 409 (already pending) |
| POST | `/api/users/me/profile/avatar` | `multipart/form-data`, `file` | `UserProfileDto` | 200, 400 (unsupported type/too large) |

`ProfileEditRequestController` (existing, unchanged endpoints, response
shape gains `contactChanges`):

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| GET | `/api/profile-edit-requests` | — | `List<ProfileEditRequestDto>` | 200 |
| POST | `/api/profile-edit-requests/{id}/approve` | — | — | 200, 403, 404, 409 (already resolved, or blind-index conflict) |
| POST | `/api/profile-edit-requests/{id}/reject` | — | — | 200, 403, 404, 409 |

`ProfileFieldsDto`: `record ProfileFieldsDto(String fullName, String
cpf, String rg, String rgOrgaoEmissor, LocalDate birthDate, AddressDto
address, List<ContactDto> contacts)` — **no `avatarUrl` field** (REQ-10:
self-only, dedicated endpoint, never part of the shared direct-edit/
request DTO to make "you cannot set this via the other path" true by
construction, not just by service-layer check).

`AddressDto`: `record AddressDto(String cep, String logradouro, String
numero, String complemento, String bairro, String cidade, String
estado, String pais)`.

`ContactDto`: `record ContactDto(Long id, ContactType type, String
value, String label, boolean isPrimary)`.

`ProfileEditRequestFieldsDto`: `record ProfileEditRequestFieldsDto(
ProfileFieldsDto fields, List<ContactChangeDto> contactChanges)`.

`ContactChangeDto`: `record ContactChangeDto(ContactChangeAction
action, Long contactId, ContactType type, String value, String label,
Boolean isPrimary)`.

`UserProfileDto`: `ProfileFieldsDto`'s fields plus `Long userId`,
`String email` (read-only, unchanged), `String avatarUrl` (read-only in
this DTO — only settable via the dedicated avatar endpoint, per above).

`ProfileEditRequestDto`: `record ProfileEditRequestDto(Long id, Long
requesterUserId, ProfileFieldsDto proposedFields,
List<ContactChangeDto> proposedContactChanges,
ProfileEditRequestStatus status, Instant createdAt)`.

`ProfileEditRequestStatus` enum gains `CANCELLED` (open decision b) —
`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.

## Dependencies

None new. Reuses the existing AWS S3 SDK (`software.amazon.awssdk:s3`)
already in `pom.xml` for `article-management` — the avatar bucket is
configuration, not a new dependency (see "Open decision a").

## Package/file structure

New (`br.com.conectabyte.knowly.identity`, package already exists):
- `UserProfile.java`, `Address.java`, `Contact.java` (new entities)
- `ContactType.java`, `ContactChangeAction.java` (new enums)
- `ContactService.java` (new)
- `AvatarStorageService.java` (new)
- `UserProfileRepository.java`, `AddressRepository.java`,
  `ContactRepository.java` (new)
- `dto/AddressDto.java`, `dto/ContactDto.java`,
  `dto/ProfileEditRequestFieldsDto.java`, `dto/ContactChangeDto.java`
  (new)
- `exception/InvalidContactFormatException.java` (new, 400-mapped)

Modified:
- `ProfileFieldsDto.java` — drop `address: String`, `phone: String`;
  add `rgOrgaoEmissor`, `birthDate`, `address: AddressDto`, `contacts:
  List<ContactDto>`; drop `avatarUrl` if it existed on this DTO (it
  didn't, per `identity-profile-model/PLAN.md` — confirming no removal
  needed there).
- `UserProfileDto.java` — add `avatarUrl` (read-only).
- `UserProfileService.java` — replace `directEdit`'s admin-self-edit
  branches with unconditional self-exclusion (REQ-11); add
  `updateOwnAvatar`; `applyFields` gains `contactChanges` param.
- `ProfileEditRequestService.java` (or wherever approve/reject live
  today) — `applyFields` call site updated for the new signature;
  self-approval `CHECK` violation surfaces as the existing generic
  `DataIntegrityViolationException`→500 path unless explicitly caught
  (Tier 1 follow-up in TASKS.md: catch and map to 403 for a clean error,
  matching the existing `ProfileFieldConflictException` precedent for
  the blind-index case).
- `ProfileEditRequest.java` — flattened columns updated per schema
  above; `status` field's Java enum gains `CANCELLED`.
- `ProfileEditRequestStatus.java` — add `CANCELLED`.
- `User.java` — remove `fullName`/`address`/`rg`/`cpf`/`phone`/
  `rgBlindIndex`/`cpfBlindIndex` fields and the `CpfRgEncryptionConverter`
  `@Convert` annotations (only once `V19` actually drops the columns —
  tracked as its own TASKS.md milestone, not bundled with `V18`, so the
  entity and schema never diverge mid-deploy).
- `UserProfileController.java` — new `POST .../avatar` endpoint; `PUT`
  request DTO shape updated.
- `StorageProperties.java` — add `avatarBucket`.
- `src/main/resources/application.yaml` — add
  `knowly.storage.avatar-bucket`.
- `src/main/resources/db/migration/V18__retrofit_identity_profile_tables.sql`
  (new), `V19__drop_legacy_user_identity_columns.sql` (new, later
  milestone).

## Testing strategy

- Unit: `UserProfileServiceTest` — full REQ-10…13 decision matrix
  re-run with the new unconditional self-exclusion (`STAFF_ADMIN` self
  now rejected, `MEMBER_ADMIN` self now rejected, tenant/global
  `PROFILE_EDIT` self still rejected as before, all four "edit other"
  paths still allowed); `updateOwnAvatar` always succeeds regardless of
  role/permission; `applyFields` atomically applies both flattened
  fields and `contactChanges` (mocked `ContactService`), rolls back
  fully on a mid-transaction failure.
- Unit: `ContactServiceTest` — 5-contact cap rejection at the 6th add;
  one-primary-per-type enforcement (setting a second primary of the same
  type either clears the old one explicitly or is rejected — pick one at
  implementation time and assert it, not both silently); format
  validation per `ContactType` (valid/invalid email, valid/invalid
  phone, `OTHER` accepts anything).
- Unit: `AvatarStorageServiceTest` — mirrors
  `ArticleStorageServiceTest`'s existing shape (if one exists; if not,
  write against the same mocked-`S3Client` pattern `ArticleStorageService`
  itself is tested with) against the new `avatarBucket` config key.
- Integration (`@SpringBootTest`, Testcontainers): full migration
  (`V18`) run against a seeded pre-migration `users` table with
  `full_name`/`cpf`/`rg`/`phone` populated and one `PENDING`
  `profile_edit_requests` row — assert `user_profiles`/`contacts`
  backfilled correctly, `email` never appears in `contacts`, the
  `PENDING` row is `CANCELLED` after migration, `users.address`'s data
  never appears in `addresses`; self-approval `CHECK` rejects a direct
  `UPDATE` bypassing the service layer; 5-contact cap and one-primary-
  per-type enforced even via direct repository save; full avatar
  upload→`GET /api/users/me/profile` round trip returns the new
  `avatarUrl`; full self-submit→approve flow with a mixed field +
  contact-add + contact-remove request applies atomically.

## Deviations from this PLAN (discovered during implementation)

- **`profile_edit_request_contacts.contact_id` FK gained `ON DELETE SET NULL`**, and the
  table's `CHECK` was relaxed to only enforce `action = 'ADD' => contact_id IS NULL` (not the
  `UPDATE`/`REMOVE` "must be NOT NULL" half PLAN's schema block showed). Discovered via the
  atomic-approval integration test (TASKS.md 20/21): a `REMOVE` request whose `Contact` row is
  actually deleted on approval left a dangling FK from the (now-resolved but still-present)
  `profile_edit_request_contacts` row, which the original `NOT NULL`-implying `CHECK` would
  itself reject when the FK's `SET NULL` action fires. Tier 2 (schema-detail bugfix, not a
  behavior change) — `profile_edit_request_contacts` remains historical/ephemeral request
  state; nulling `contact_id` after the underlying `Contact` is gone doesn't lose anything the
  SPEC cares about (the proposed `type`/`value`/`label`/`is_primary` snapshot columns are
  unaffected).
- **`Address.estado`/`ProfileEditRequest.proposedEstado` map as `VARCHAR(2)`, not `CHAR(2)`**,
  contra PLAN's schema block. Discovered via Hibernate schema validation failing against a
  Postgres `CHAR(2)`/`bpchar` column at context-startup (`SchemaManagementException`) --
  `columnDefinition = "char(2)"` didn't resolve it either (Hibernate's JDBC-type comparison for
  `bpchar` doesn't line up with a Java `String` field regardless of the raw DDL override). No
  behavior change: REQ-2's `estado ~ '^[A-Z]{2}$'` `CHECK` still enforces exactly two uppercase
  letters at the DB level either way.
- **`UserProfileService.directEdit` and `ProfileEditRequestService`'s
  submit/approve/reject methods stayed non-`@Transactional`**, same as `identity-profile-model`'s
  original design -- PLAN's "single `@Transactional` method, one rollback boundary" note for
  REQ-17's atomicity is satisfied by `UserProfileService.applyFields` itself being
  `@Transactional` (new), not by wrapping the outer service methods, which must stay outside any
  `@Transactional` boundary so `TenantFilterAspect` doesn't scope `hasDirectEditRight`'s
  cross-tenant membership lookups down to just the caller's active tenant (this is the exact
  failure mode task 16/17's test run caught: `directEdit` on another tenant member started
  throwing `PermissionDeniedException` once `@Transactional` was added at that layer).
- **Eager `UserProfile` creation (REQ-1) wired into `TenantService.createTenant`/`addMember` and
  `StaffService.createStaffUser`** -- the three concrete places this codebase actually creates a
  new `User` row (there is no single "registration" entry point; users are created on first
  membership/staff-provisioning, not via a dedicated sign-up flow) -- rather than a fourth,
  centralized "registration service" PLAN's prose implied but that doesn't exist in this
  codebase.
- **Tasks were implemented as fewer, larger commits than TASKS.md's 31 numbered items**, each
  still following Red-Green internally (schema/migration tests written and run against the real
  migration before the corresponding entities/services existed) -- grouped by TASKS.md's own
  section headers (migration+entities, `ContactService`, avatar, `UserProfileService` retrofit,
  edit-request+contacts, controller/DTO wiring) rather than one commit per line-item, given the
  volume of interdependent schema/entity/service/DTO changes a single sub-task like "rewrite the
  permission decision tree" necessarily touches together.
- **`V19__drop_legacy_user_identity_columns.sql` (TASKS.md 27) is deliberately not implemented in
  this pass** -- PLAN.md already scopes it as "only after this feature's code path is verified
  running," i.e. a genuine production-deployment gate this implementation session has no way to
  satisfy. `User.java` keeps its old `fullName`/`address`/`rg`/`cpf`/`phone`/blind-index fields
  and the `users`/`users_aud` columns remain, unused by any code path after this retrofit
  (confirmed: `UserProfileService`/`ProfileEditRequestService` read/write `UserProfile`/`Address`/
  `Contact` exclusively). Tracked as its own follow-up milestone per PLAN's own sequencing, not a
  Tier 3 escalation.
