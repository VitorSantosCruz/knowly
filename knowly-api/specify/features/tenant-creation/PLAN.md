# PLAN — Tenant creation: full company identification (backend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md, `Tenant.java`, `TenantService#createTenant`,
> `TenantController`, and `tenant-crud/SPEC.md` (already written
> **assuming** the field names finalized below — this PLAN is the
> canonical source for those names; `tenant-crud/PLAN.md` must match it
> verbatim, not re-derive it).

## Changelog / Amends (2026-08-02, CNPJ normalization + checksum)

Implements SPEC's REQ-6a–REQ-6d (recovered/recreated after a
working-tree reset — same design, already appsec-reviewed twice: once
initially, once after appsec caught the ordering bug described below,
fixed in this recreation from the start).

**New components (`br.com.conectabyte.knowly.tenancy.validation`,
alongside the existing `TaxIdValidator`/`ValidTaxId`):**

- **`TaxIdNormalizer`** (new, **public**, tenancy-module-scoped) —
  a single static method, `normalize(String taxId)`, that strips `.`,
  `-`, and `/` characters (REQ-6a). Public rather than package-private
  because `TenantService` lives in the parent `tenancy` package, not
  `tenancy.validation`, and needs to call it directly for the REQ-6d
  ordering fix. Deliberately **not** shared with `identity` module's
  own CPF-normalization logic (which does the same character-stripping
  for `cpf`) — this is a small, intentional duplication across a module
  boundary the codebase already treats as a hard seam (`identity` vs.
  `tenancy`), rather than introducing a new shared "common validation"
  module for two four-line methods. Called before any other `taxId`
  processing.
- **`CnpjChecksumValidator`** (new, **public**, same cross-package
  reason as `TaxIdNormalizer`) — `boolean isValid(String
  normalizedTaxId)`, applied only when `country` denotes Brazil and
  only after REQ-6's shape check (`TaxIdValidator.isValid`) already
  passed on the *normalized* value (REQ-6b: shape check is now "14
  characters," digits-or-letters in the first 12, digits in the last
  2). Algorithm (REQ-6c):
  1. Each character's numeric value: `Character.toUpperCase(c) - 48`
     (ASCII '0' is 48; for digits this is the digit's value, for
     uppercase letters `A`–`Z` this yields 17–42, per Receita
     Federal's alphanumeric-CNPJ convention — same "alphanumeric-
     adjusted" adjustment named in SPEC REQ-6c).
  2. First check digit: weighted sum of the first 12 characters'
     values against weights `5,4,3,2,9,8,7,6,5,4,3,2` (index-aligned,
     leftmost character × 5); `remainder = sum % 11`; expected digit =
     `remainder < 2 ? 0 : 11 - remainder`. Compare against character
     13. **(Corrected during implementation — see "Deviations from
     this PLAN": the weight sequence first recorded here,
     `6,5,4,3,2,9,8,7,6,5,4,3`, did not reproduce the real/published
     CNPJ fixtures below; these are the standard Receita Federal
     weights, verified against all three numeric fixtures.)**
  3. Second check digit: same rule over the first 13 characters
     (original 12 + the now-known-correct check digit 1) against
     weights `6,5,4,3,2,9,8,7,6,5,4,3,2`. Compare against character 14.
     **(Corrected alongside the first — was
     `7,6,5,4,3,2,9,8,7,6,5,4,3`.)**
  4. Valid only if both computed digits match the submitted ones.
- **`InvalidTaxIdException`** (new,
  `br.com.conectabyte.knowly.tenancy.exception`, mirrors
  `identity.exception.InvalidCpfException`'s shape exactly: a bare
  `RuntimeException`, no fields, so the fixed `INVALID_TAX_ID` code is
  the only thing ever returned — never the submitted value) → new
  `TenancyExceptionHandler#handleInvalidTaxId` → 400 +
  `TenancyErrorResponseDto("INVALID_TAX_ID")`, same pattern as every
  other handler in that class.

**`TenantService#createTenant` ordering fix (REQ-6d — the bug appsec
caught the first time, built correctly from the start this time):**

Today (pre-amendment) `createTenant` calls
`tenantRepository.existsByTaxIdAndDeletedAtIsNull(request.taxId())`
directly on the raw submitted value, before any normalization. The
corrected sequence, all inside the existing `@Transactional` method,
**before** the `Tenant` entity is constructed:

```java
String normalizedTaxId = TaxIdNormalizer.normalize(request.taxId());
if (isBrazil(request.country()) && !CnpjChecksumValidator.isValid(normalizedTaxId)) {
    throw new InvalidTaxIdException();
}
if (tenantRepository.existsByTaxIdAndDeletedAtIsNull(normalizedTaxId)) {
    throw new TenantAlreadyExistsException();
}
// ... Tenant is built using normalizedTaxId, not request.taxId()
```

Shape validation (`@ValidTaxId`/`TaxIdValidator`, REQ-6/REQ-6b) still
runs earlier, at the Bean Validation boundary (`@Valid` on the
controller parameter) — but `TaxIdValidator.isValid` itself is updated
to normalize internally before checking length/shape, since Bean
Validation runs against the raw request body and REQ-6a requires
normalization for the shape check too, not only for checksum/duplicate
checks. This means `TaxIdNormalizer.normalize` is called twice on the
same request (once inside `TaxIdValidator`, once in `createTenant`) —
accepted duplication of a pure, side-effect-free four-line string
operation, not worth threading through the DTO as a second field.

**Why this exact ordering matters (REQ-6d):** normalizing before the
duplicate check means `11.222.333/0001-81` and `11222333000181`
resolve to the identical string `11222333000181` before either
comparison against existing rows — closing the race SPEC's REQ-6d
describes (two submissions differing only in punctuation, both
`existsBy...` calls seeing what looks like a distinct value, both
passing, both hitting the DB unique index and one throwing a raw
`DataIntegrityViolationException` — the existing
`TenantAlreadyExistsException` translation on that exception, task 7's
migration-era addition, still catches this as a last-resort safety
net, but the ordering fix means it should no longer be the *primary*
mechanism a punctuation-only duplicate relies on).

**Test fixtures (already appsec-approved, from real/published CNPJs):**

| CNPJ (unpunctuated) | Valid? |
|---|---|
| `11222333000181` | valid |
| `11222333000180` | invalid (last check digit wrong) |
| `11444777000161` | valid |
| `11444777000160` | invalid |
| `01838723000127` | valid |
| `01838723000100` | invalid |

Plus one alphanumeric-format fixture (a real published example of the
newer Receita Federal alphanumeric CNPJ, with letters in the base and
numeric, checksum-correct check digits) to exercise REQ-6b end to end,
and a duplicate-detection-across-punctuation regression test: two
`createTenant` calls with `11222333000181` and `11.222.333/0001-81`
respectively — second call must raise `TenantAlreadyExistsException`,
never a raw constraint violation.

## Changelog / Amends (2026-08-02, reconciliation)

**Contradiction found and resolved.** This PLAN originally had
`POST /api/tenants` accept only company fields + `adminEmail`, leaving
the first admin's full profile and role to a **separate**,
subsequent call to `addMember` (`tenancy/SPEC.md` REQ-22–REQ-25,
`mandatory-complete-profile/PLAN.md`,
`user-role-selection-at-creation/PLAN.md`). The "Coordination note"
section that used to sit under **API contracts** below flagged this
explicitly as a real mismatch against `knowly-app`'s
`tenant-creation/SPEC.md` REQ-4/REQ-5, which require the screen to call
`POST /api/tenants` **exactly once**, submitting company data, the
first admin's full profile, and their role together.

**Resolved in favor of the frontend SPEC, by product-owner/orchestrator
decision (not this PLAN's own call):** `POST /api/tenants` becomes a
single, atomic, transactional endpoint that creates the `Tenant`
**and** its first `TenantMembership` (`User` + `UserProfile` + one
`Address` + `Contact` row(s), role defaulting to `MEMBER_ADMIN`) in one
database transaction. If any part fails — invalid company fields,
invalid/incomplete first-user profile fields, an invalid role — the
whole call fails and **nothing** is persisted: no orphaned tenant
without a member, no member without a tenant. See the new
`DECISIONS.md` entry "`tenant-creation`: tenant + first admin are one
atomic call, not two" for the full reasoning.

**This does NOT touch or revoke `mandatory-complete-profile` or
`user-role-selection-at-creation` as SPECs.** They remain the sole
source of truth for the mandatory-profile field set
(`MandatoryProfileFieldsDto`/`MandatoryAddressDto`, per that PLAN's
"Package/file structure") and for the `role`
optionality/authorization rule (`user-role-selection-at-creation`
PLAN's default-resolution/authorization helpers). This amendment only
changes **where** (which HTTP call) those already-decided rules apply
when the target is a brand-new tenant's first member — i.e. at
creation time, not via a follow-up `addMember` call.

**`addMember` itself is unchanged.** Adding a second, third, ... member
to an **already-existing** tenant continues exactly as already planned
in `mandatory-complete-profile/PLAN.md` and
`user-role-selection-at-creation/PLAN.md` — same DTO, same endpoint,
same authorization rules. Nothing in this amendment alters that flow;
it only removes the *first* member's creation from ever going through
`addMember` in the first place.

## Final field names (binding for `tenant-crud` and any future work)

| Concept | Java field (`Tenant`/DTOs) | Column (`tenants`) |
|---|---|---|
| Trade name (existing, unchanged) | `name` | `name` |
| Legal name ("razão social") | `legalName` | `legal_name` |
| Fiscal ID ("CNPJ" or equivalent) | `taxId` | `tax_id` |
| Country / fiscal jurisdiction | `country` | `country` |
| Contact email | `contactEmail` | `contact_email` |
| Contact phone | `contactPhone` | `contact_phone` |
| Postal code | `postalCode` | `postal_code` |
| Street | `street` | `street` |
| Number | `number` | `number` |
| Complement (optional) | `complement` | `complement` |
| Neighborhood | `neighborhood` | `neighborhood` |
| City | `city` | `city` |
| State/province | `state` | `state` |

`country` is a single field serving both roles the SPEC describes (the
fiscal-document-format jurisdiction from REQ-6, and the address's
country from REQ-1's structured-address list) — the SPEC lists `country`
in both places but never implies two separate values, and a tenant only
operates under one jurisdiction, so one column is correct, not two.

## Architectural decisions

- **Address fields live as plain columns on `tenants`, not a separate
  `tenant_addresses` 1:1 table** — deliberately diverging from
  `identity-profile-model-v2`'s `Address` entity, and documented here
  precisely because that's the closest precedent and a reader will
  reasonably ask "why not the same shape." `identity-profile-model-v2`
  split `Address` out specifically because a user's address is
  **optional and lazily created** (most users may never submit one) —
  neither is true here: REQ-2 makes every address sub-field except
  `complement` mandatory *at creation*, so a tenant's address row would
  always exist, always be created in the same transaction as the
  tenant, and never have an independent lifecycle. A mandatory,
  always-present 1:1 relation gains nothing from being a separate table
  (no join saved, no optionality modeled) and only adds a second entity,
  a second repository, and a mandatory-insert-order concern to every
  tenant-creation code path. Same underlying rule as the `UserProfile`
  vs. `Address` split in that PLAN (separate table only when the data
  is genuinely independent/optional), applied to a case where the
  answer comes out the other way. **This is a Tier 2 judgment call
  worth its own `DECISIONS.md` entry** (see below) so a future address-
  shaped feature in this codebase checks "is it optional/lazy?" before
  copying either precedent.
- **`cnpj`/`razaoSocial`/`nomeFantasia`/`inscricaoEstadual` are dropped,
  not renamed-in-place or data-migrated.** Verified in
  `V17__add_identity_profile_fields.sql`: all four columns are
  nullable, added speculatively during the `identity-profile-model`
  retrofit, and **no code path ever sets them** —
  `TenantService#createTenant` only ever calls `new Tenant(tenantName)`
  (sets `name` only). There is no real data to preserve or migrate;
  treating this as a real data-migration problem would be inventing
  work the actual column contents don't justify. New migration `V23`
  drops all four columns (and their `tenants_aud` counterparts) and
  adds the new set in the same file.
- **No backfill/default-value strategy needed for existing `tenants`
  rows** — this is a pre-launch system (`PROJECT_STATUS.md`: no
  external consumers, `identity-profile-model-v2`'s own migration
  reasoned the same way for `users.address`) and `tenants` today only
  ever gets rows through `TenantService#createTenant` and the bootstrap
  staff-user migration (`V13`), neither of which populates the new
  fields. **However**, to keep the migration itself always safe to run
  regardless of what a given environment's `tenants` table currently
  contains, the new columns are added `NULL`-able first, backfilled
  with placeholder values for any pre-existing row (`'PENDING_MIGRATION'`
  string sentinels / `'BR'` for `country`, mirroring the shape — not the
  values — `identity-profile-model-v2`'s eager-empty-row precedent
  used), then flipped `NOT NULL` in the same migration file. No pre-
  existing row is *expected* to exist beyond the bootstrap tenant seeded
  by `V13` (if any); this three-step (add nullable → backfill sentinel →
  set `NOT NULL`) shape is just the standard-safe way to add a
  `NOT NULL` column without hardcoding an assumption about what today's
  `tenants` table actually holds in every environment (dev/CI/staging
  may each have different leftover rows).
- **`taxId` uniqueness is a plain unique index on the plaintext
  column** — per SPEC's own non-functional requirements (`taxId`
  identifies a company, not a natural person; no encryption/blind-index
  needed, unlike `UserProfile.cpf`/`rg`). Matches the existing
  (unused) `ux_tenants_cnpj` index's shape, just on the new column name
  and made properly non-partial once `tax_id` is `NOT NULL` (the old
  index was `WHERE cnpj IS NOT NULL` only because the column was
  nullable and never enforced).
- **`taxId` CNPJ-format validation lives in a custom Bean Validation
  class-level `@Constraint`, not a service-layer `if`** — this is a
  genuine divergence from `identity-profile-model-v2`'s "no custom
  `@Constraint` in this codebase" precedent (`DECISIONS.md`'s
  `contacts.type` entry), and is deliberate, not an oversight: that
  precedent's own reasoning was "exactly one conditional rule doesn't
  justify introducing a whole new validation mechanism for a one-off."
  `tenant-creation`'s conditional rule (`taxId` format depends on
  `country`) is structurally identical *in shape* to that same
  precedent, so by that same PLAN's logic this should also be a plain
  service-layer check, not a `@Constraint` — see "Open decision" below
  for why this PLAN nonetheless proposes a `@Constraint` here, flagged
  explicitly as a Tier 2 call the appsec/architect roundtable should
  confirm rather than silently follow precedent.
- **Audit**: `TenantController#createTenant` currently has **no**
  `@AuditLog` annotation at all (verified — `addMember` has one,
  `createTenant` does not). REQ-8 requires tenant creation to be
  audited "the same way every other write is audited" — this is a
  pre-existing gap this feature must close, not new scope: add
  `@AuditLog(action = "tenant.create", resourceType = "Tenant")` to
  `TenantService#createTenant` (service layer, matching `addMember`'s
  placement, not the controller).
- **(New, 2026-08-02 amendment) `createTenant` builds the `Tenant` and
  its first `TenantMembership` in one `@Transactional` service method,
  reusing `addMember`'s own persistence building blocks rather than
  calling `addMember` itself as a nested call.** *Why not just call
  `TenantService.addMember(...)` from inside `createTenant`*: `addMember`
  begins with `requireAdminOfTenantOrStaff(actor, tenantId, ...)` — an
  authorization check against a tenant that, at this exact point in
  `createTenant`, doesn't have an ID yet and has no existing membership
  for anyone to hold; the two methods' preconditions are fundamentally
  different (creating a tenant's *only* member vs. adding an *n*-th
  member to one that already has admins to authorize against). Instead,
  `createTenant` extracts and calls the same lower-level helpers
  `addMember` already uses post-authorization: `UserProfileService`'s
  field-setting/CPF-RG-encryption helpers (from
  `mandatory-complete-profile/PLAN.md`) to persist `UserProfile` +
  `Address` + `Contact` rows, and the same `TenantMembership` row
  construction, with `role` defaulted to `MEMBER_ADMIN` (not `MEMBER`)
  since the first member of a brand-new tenant is definitionally its
  admin (`tenancy` SPEC REQ-10: "creating a tenant shall always include
  designating its first user as tenant admin in the same action").
  Both `createTenant` and `addMember` end up sharing the same
  private `TenantService#persistMemberProfile(User, TenantMembership,
  MandatoryProfileFieldsDto)` helper, extracted from `addMember`'s
  existing post-authorization body — no duplicated persistence logic
  between the two call sites.
- **Role field on `CreateTenantRequestDto` is optional, defaulting to
  `MEMBER_ADMIN`, not `MEMBER`** — deliberately the *opposite* default
  from `AddMemberRequestDto.role` (`user-role-selection-at-creation`
  PLAN, default `MEMBER`). *Why*: `AddMemberRequestDto`'s default
  models "adding an ordinary member to a tenant that already has
  admins" (the common case); `CreateTenantRequestDto`'s first (and, at
  creation time, only) member has no tenant to be a plain member
  *of* yet — `tenancy` REQ-10 requires this exact person to be the
  admin. An explicit `role=MEMBER` on tenant creation is still accepted
  (some staff workflow might genuinely want that, e.g. staff itself
  holding the actual admin role via a separate `addMember` call
  afterward) but is never the implicit default. No new authorization
  gate is needed for the `MEMBER_ADMIN` case here (unlike `addMember`'s
  REQ-25) — the caller of `POST /api/tenants` is already required to be
  staff (`tenancy` REQ-10, unchanged), and staff creating the very
  first admin of a tenant that doesn't yet exist has no "another
  tenant's admin" boundary to cross.
- **`CreateTenantRequestDto`'s existing `adminEmail` field is kept
  (not folded into `MandatoryProfileFieldsDto`)** — the first user's
  login email is account-identity data, not profile data, exactly the
  same split `AddMemberRequestDto` already makes (`email` field
  alongside its own `profile: MandatoryProfileFieldsDto`). This PLAN
  reuses that same shape rather than inventing a new one.

### Open decision: `@Constraint` vs. service-layer `if` for `taxId` format

**Recommendation: custom class-level `@Constraint`
(`@ValidTaxId`) on `CreateTenantRequestDto`, applied via
`@Valid` at the controller boundary (already the case today).**

*Why, despite the precedent leaning the other way*: unlike the
`contacts.type`/value cross-field case (validated deep inside a
service method that's called from multiple write paths — direct add,
edit-request approval — where a `@Constraint` would need to be
duplicated or bypassed in one of those paths anyway), `taxId`/`country`
here both arrive together in exactly one place: `CreateTenantRequestDto`
at `POST /api/tenants`. A `@Constraint` here validates at the exact
same boundary Bean Validation already owns for every other field on
this DTO (`@Email`, `@NotBlank`), producing the same uniform
`MethodArgumentNotValidException` → 400 response shape REQ-3 already
requires ("identifying every missing/invalid field" in one response) —
a service-layer `if` would need its own separate exception + handler
to fold into that same field-level error list, more ceremony here, not
less. **This is a genuinely novel case (first conditional-format
`@Constraint` in this codebase) — flagged for the AppSec/QA gate before
`TASKS.md` execution, and written up in `DECISIONS.md` below since it
sets precedent either way.**

## Data schema

New migration `V23__replace_tenant_legacy_fields_with_full_identification.sql`:

```sql
-- drop the unused, never-populated speculative columns from V17
DROP INDEX IF EXISTS ux_tenants_cnpj;
DROP INDEX IF EXISTS ux_tenants_inscricao_estadual;
ALTER TABLE tenants DROP COLUMN cnpj;
ALTER TABLE tenants DROP COLUMN razao_social;
ALTER TABLE tenants DROP COLUMN nome_fantasia;
ALTER TABLE tenants DROP COLUMN inscricao_estadual;
ALTER TABLE tenants_aud DROP COLUMN cnpj;
ALTER TABLE tenants_aud DROP COLUMN razao_social;
ALTER TABLE tenants_aud DROP COLUMN nome_fantasia;
ALTER TABLE tenants_aud DROP COLUMN inscricao_estadual;

-- add new columns, nullable first (safe-add pattern)
ALTER TABLE tenants ADD COLUMN legal_name VARCHAR(255);
ALTER TABLE tenants ADD COLUMN tax_id VARCHAR(32);
ALTER TABLE tenants ADD COLUMN country VARCHAR(100);
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE tenants ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE tenants ADD COLUMN street VARCHAR(255);
ALTER TABLE tenants ADD COLUMN number VARCHAR(20);
ALTER TABLE tenants ADD COLUMN complement VARCHAR(100);
ALTER TABLE tenants ADD COLUMN neighborhood VARCHAR(100);
ALTER TABLE tenants ADD COLUMN city VARCHAR(100);
ALTER TABLE tenants ADD COLUMN state VARCHAR(100);

-- backfill any pre-existing row (bootstrap tenant, dev/CI leftovers) with
-- sentinel placeholders -- no real identification data exists for these
-- rows, and this feature does not invent one; a future tenant-crud edit
-- can correct the bootstrap tenant's placeholder values like any other
-- tenant's fields (legalName/contactEmail/etc. are all editable there --
-- taxId is not, so the bootstrap tenant's placeholder taxId is
-- permanent unless deleted and recreated, an accepted pre-launch tradeoff)
UPDATE tenants SET
  legal_name = COALESCE(legal_name, name),
  tax_id = COALESCE(tax_id, 'PENDING-' || id),
  country = COALESCE(country, 'BR'),
  contact_email = COALESCE(contact_email, 'unset@example.invalid'),
  contact_phone = COALESCE(contact_phone, '0000000000'),
  postal_code = COALESCE(postal_code, '00000000'),
  street = COALESCE(street, 'unset'),
  number = COALESCE(number, 'unset'),
  neighborhood = COALESCE(neighborhood, 'unset'),
  city = COALESCE(city, 'unset'),
  state = COALESCE(state, 'unset')
WHERE legal_name IS NULL OR tax_id IS NULL OR country IS NULL
   OR contact_email IS NULL OR contact_phone IS NULL OR postal_code IS NULL
   OR street IS NULL OR number IS NULL OR neighborhood IS NULL
   OR city IS NULL OR state IS NULL;

ALTER TABLE tenants ALTER COLUMN legal_name SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN tax_id SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN country SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN contact_email SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN contact_phone SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN postal_code SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN street SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN number SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN neighborhood SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN city SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN state SET NOT NULL;

CREATE UNIQUE INDEX ux_tenants_tax_id ON tenants (tax_id);

ALTER TABLE tenants_aud ADD COLUMN legal_name VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN tax_id VARCHAR(32);
ALTER TABLE tenants_aud ADD COLUMN country VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE tenants_aud ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE tenants_aud ADD COLUMN street VARCHAR(255);
ALTER TABLE tenants_aud ADD COLUMN number VARCHAR(20);
ALTER TABLE tenants_aud ADD COLUMN complement VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN neighborhood VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN city VARCHAR(100);
ALTER TABLE tenants_aud ADD COLUMN state VARCHAR(100);
```

Note: `taxId`'s uniqueness constraint here is unconditional (matches
today's active-only reality — REQ-4/REQ-5 don't yet know about
soft-deleted tenants, since that's `tenant-crud`'s REQ-12 scope, not
this SPEC's). `tenant-crud/PLAN.md` is responsible for changing this to
a partial index (`WHERE deleted_at IS NULL`) once it adds the
soft-delete column — flagged here so that PLAN doesn't miss it.

`Tenant.java` gains the eleven new fields (`legalName`, `taxId`,
`country`, `contactEmail`, `contactPhone`, `postalCode`, `street`,
`number`, `complement`, `neighborhood`, `city`, `state`) as plain
`@Column`-mapped `String`s, replacing the four dropped fields.
`@Audited` already applies to the whole entity — no per-field Envers
change needed beyond the `tenants_aud` DDL above.

## API contracts

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| POST | `/api/tenants` | `CreateTenantRequestDto` (below) — company fields + first admin's `adminEmail` + `profile: MandatoryProfileFieldsDto` + optional `role` | — (unchanged, `200` empty body — matches existing contract, not widened by this feature) | 200, 400 (validation — company field(s) invalid **or** profile field(s) missing/invalid; whole request rejected, nothing persisted), 403 (not staff / lacks `TENANT_CREATE`, or `role=MEMBER_ADMIN` — always allowed here, see decision above), 409 (`taxId` collision, or `adminEmail` already in use) |

`CreateTenantRequestDto` (replaces the current 2-field version, and —
per the 2026-08-02 reconciliation above — now also carries the first
admin's full profile and role in the same request, instead of a bare
`adminEmail` string). Address is nested as its own `@Valid AddressDto
address` for the **company's** address — matches
`identity-profile-model-v2`'s `AddressDto` shape/precedent. The first
admin's own profile (including their own, independent address) reuses
`MandatoryProfileFieldsDto`/`MandatoryAddressDto` **verbatim** from
`mandatory-complete-profile/PLAN.md` — not re-derived, not a new shape:

```java
record CreateTenantRequestDto(
    @NotBlank String name,
    @NotBlank String legalName,
    @ValidTaxId String taxId,        // custom constraint, see "Open decision"
    @NotBlank String country,
    @Email @NotBlank String contactEmail,
    @NotBlank String contactPhone,
    @Valid @NotNull AddressDto address,          // company address
    @Email @NotBlank String adminEmail,          // first admin's login email
    @NotNull @Valid MandatoryProfileFieldsDto profile, // first admin's full profile
    MembershipRole role                          // optional; default MEMBER_ADMIN (see decision above)
) {}

record AddressDto(
    @NotBlank String postalCode,
    @NotBlank String street,
    @NotBlank String number,
    String complement,               // optional, no annotation
    @NotBlank String neighborhood,
    @NotBlank String city,
    @NotBlank String state
) {}
```

`MandatoryProfileFieldsDto`/`MandatoryAddressDto` are imported from
`br.com.conectabyte.knowly.identity.dto`, exactly as
`mandatory-complete-profile/PLAN.md` defines them — this PLAN adds
`CreateTenantRequestDto` as a **third** consumer of that shared DTO,
alongside `CreateStaffUserRequestDto` and `AddMemberRequestDto`, per
that PLAN's own "one shared 'mandatory profile fields' DTO shape"
decision (no new/duplicated field list introduced here).

**Reconciliation note (resolves the prior "Coordination note" in this
section, now removed):** the mismatch previously flagged here — the
frontend `tenant-creation` PLAN assuming one combined endpoint while
this backend PLAN planned two separate calls — is resolved in favor of
the frontend's single-call assumption, by explicit product-owner/
orchestrator decision (see `DECISIONS.md`'s new entry). This backend
PLAN's `CreateTenantRequestDto` now matches what `knowly-app`'s
`tenant-creation/PLAN.md` already independently assumed as its
"best-effort" contract (company fields + `user: {...}` + `role`) —
that frontend PLAN should update its own field naming to match this
PLAN's canonical names (`profile`, not `user`) if it hasn't already,
since this backend PLAN remains the canonical source of the DTO shape
per this file's own header note.

`@ValidTaxId` (new, `br.com.conectabyte.knowly.tenancy.validation`):
class-level-equivalent field constraint reading both `taxId` and
`country` off the DTO (Jakarta supports this via a
`@Constraint(validatedBy = TaxIdValidator.class)` placed at the
class level, `ConstraintValidator<ValidTaxId, CreateTenantRequestDto>`)
— when `country` denotes Brazil (`"BR"`/`"Brazil"`/`"Brasil"`,
case-insensitive — exact accepted literal set decided at
implementation time and asserted in tests, not re-litigated here),
requires `taxId` to reduce to exactly 14 digits after stripping
non-digit punctuation (REQ-6's "14 digits, with or without
punctuation"); otherwise only requires non-blank. Reports the
violation against the `taxId` property path
(`.addPropertyNode("taxId")`) so REQ-3's per-field 400 error still
names `taxId` specifically, not the whole object.

`TenantAlreadyExistsException` (new, 409) thrown by
`TenantService#createTenant` on unique-constraint violation
(`DataIntegrityViolationException` caught and re-thrown as this
domain exception, same convention `ProfileFieldConflictException`
already uses) — mapped in `TenancyExceptionHandler`. A `taxId`
collision and an `adminEmail`-already-exists collision are both
unique-constraint violations caught the same way, distinguished by
which constraint fired (reuses the existing pattern
`StaffUserAlreadyExistsException`/`ProfileFieldConflictException`
already established for "which column collided" disambiguation — no
new mechanism).

**Atomicity (2026-08-02 amendment):** `TenantService#createTenant` is
`@Transactional` end to end — `Tenant` row, `User` row, eager
`UserProfile` row, the mandatory-profile fields written into that
`UserProfile`, the `Address` row, every `Contact` row, and the
`TenantMembership` row are all one transaction. Bean Validation on
`CreateTenantRequestDto` (company fields + `@Valid MandatoryProfileFieldsDto
profile`) rejects the request with `400` *before* the transactional
method is ever entered whenever any field — company or profile — is
missing/invalid, so no partial state is even attempted in the common
case (same "Bean Validation guarantees zero rows for free" reasoning
`mandatory-complete-profile/PLAN.md` already established for
`addMember`/`createStaffUser`). For failures that can only surface
mid-transaction (e.g. a `taxId`/`adminEmail` unique-constraint
violation, which Bean Validation cannot pre-empt), Spring's default
transaction rollback on unchecked exception (`TenantAlreadyExistsException`
extends `RuntimeException`) guarantees the whole transaction — tenant
row included — rolls back together; there is no code path that commits
the `Tenant` row and then fails on the member half, or vice versa.

## Dependencies

None new. `@ValidTaxId` uses Jakarta Bean Validation, already a
`pom.xml` dependency (Hibernate Validator, already used for `@Email`/
`@NotBlank` everywhere in this codebase).

## Package/file structure

New (`br.com.conectabyte.knowly.tenancy`):
- `validation/ValidTaxId.java`, `validation/TaxIdValidator.java`
- `exception/TenantAlreadyExistsException.java`

Modified:
- `Tenant.java` — drop `cnpj`/`razaoSocial`/`nomeFantasia`/
  `inscricaoEstadual`; add the eleven new fields (see above);
  constructor gains the full field set (or a builder — implementation
  detail, not decided here) since `new Tenant(tenantName)` alone is no
  longer sufficient.
- `dto/CreateTenantRequestDto.java` — full field set above, now
  including `profile: MandatoryProfileFieldsDto` and optional `role`
  (2026-08-02 amendment).
- `TenantService.java` — `createTenant` signature grows to accept every
  new company field plus the first admin's `MandatoryProfileFieldsDto`
  and optional `role` (or the DTO itself, implementation's call); method
  becomes `@Transactional`, creating `Tenant` + `User` + `UserProfile` +
  `Address` + `Contact`(s) + `TenantMembership` (`MEMBER_ADMIN` default)
  together; gains `@AuditLog(action = "tenant.create", resourceType =
  "Tenant")`; catches unique-constraint violations on `tax_id` and
  `email` and rethrows `TenantAlreadyExistsException`; extracts a
  private `persistMemberProfile(User, TenantMembership,
  MandatoryProfileFieldsDto)` helper shared with `addMember`'s existing
  post-authorization body (2026-08-02 amendment — see "Architectural
  decisions" above).
- `TenantController.java` — `createTenant` passes the new fields
  through.
- `exception/TenancyExceptionHandler.java` — new `@ExceptionHandler
  (TenantAlreadyExistsException.class)` → 409.
- `src/main/resources/db/migration/V23__replace_tenant_legacy_fields_with_full_identification.sql`
  (new).

**No new files for the profile-persistence itself** — `CreateTenantRequestDto`
imports the existing `identity.dto.MandatoryProfileFieldsDto`/
`MandatoryAddressDto` (`mandatory-complete-profile/PLAN.md`); no new DTO
package/type introduced by this amendment beyond the two new fields on
`CreateTenantRequestDto` itself.

## Testing strategy

- Unit: `TaxIdValidatorTest` — Brazil + 14-digit unpunctuated passes;
  Brazil + punctuated-but-14-digits passes; Brazil + wrong digit count
  fails; non-Brazil + any non-empty string passes; non-Brazil + blank
  fails (still `@NotBlank`-equivalent via the constraint itself, or
  layered with a separate `@NotBlank` — implementation's call, tested
  either way).
- Unit: `TenantServiceTest` — `createTenant` persists every new field;
  `taxId` collision raises `TenantAlreadyExistsException`, no row
  created (mocked repository throwing `DataIntegrityViolationException`);
  audit annotation present (reflection check, matching this codebase's
  existing `@AuditLog` presence-assertion pattern if one exists, or a
  behavioral assertion that the audit event is recorded).
- Integration (`@SpringBootTest`, Testcontainers): full
  `POST /api/tenants` round trip with every mandatory field present
  (company **and** first-admin profile) succeeds and stored data matches
  submitted data (REQ-1 acceptance criterion); missing any one mandatory
  company field → 400 naming it; malformed `contactEmail` → 400;
  duplicate `taxId` → 409, no `Tenant`/`User`/`TenantMembership` row
  created (2026-08-02: assert **all three**, not just `Tenant`, to prove
  atomicity); Brazil + non-14-digit `taxId` → 400; non-Brazil + arbitrary
  non-empty `taxId` → 200; non-staff caller → 403 (existing `tenancy`
  REQ-10 coverage, re-run against the new DTO shape); `V23` migration
  test (`V23MigrationTest`, matching `V17MigrationTest`'s existing
  shape) — seeds a pre-migration `tenants` row with only `name` set,
  runs the migration, asserts sentinel backfill values and `NOT NULL`
  enforcement on all eleven columns, asserts the dropped columns are
  gone from both `tenants` and `tenants_aud`.
- **(New, 2026-08-02 amendment) Atomicity/single-call tests**:
  - `POST /api/tenants` missing any one first-admin `profile` field
    (e.g. no `cpf`) is rejected `400`, and **no** `Tenant` row exists
    afterwards either — proves the whole request fails together, not
    just the member half (SPEC's new REQ-9 below).
  - `POST /api/tenants` with valid company fields and valid `profile`
    but no `role` creates the first membership as `MEMBER_ADMIN` (the
    new default), not `MEMBER`.
  - `POST /api/tenants` with an `adminEmail` that already exists as a
    `User` is rejected `409`, no `Tenant` row created.
  - `POST /api/tenants` succeeding creates exactly one `Tenant`, one
    `User`, one `UserProfile` (complete, `ProfileCompletenessService
    .isComplete` true immediately), one `Address`, at least one
    `Contact`, and one `TenantMembership` with role `MEMBER_ADMIN` — all
    in the same transaction (asserted via repository counts before/
    after, matching REQ-22/REQ-23's existing `addMember` test shape).
  - A `taxId` unique-constraint violation mid-transaction (seed an
    existing tenant with the same `taxId` first) rolls back the entire
    call — no `User`/`TenantMembership` row survives either, confirming
    the rollback isn't scoped only to the `Tenant` insert.

## Deviations from this PLAN (discovered during implementation)

- **CNPJ checksum weight sequences corrected (2026-08-02, CNPJ
  amendment).** This PLAN's first draft of the "Changelog / Amends
  (CNPJ normalization + checksum)" section recorded weights
  `6,5,4,3,2,9,8,7,6,5,4,3` (first digit) / `7,6,5,4,3,2,9,8,7,6,5,4,3`
  (second digit). Implementing `CnpjChecksumValidatorTest` against the
  three real/published CNPJ fixtures in that same section's table
  (`11222333000181`, `11444777000161`, `01838723000127`) showed those
  weights do not reproduce any of the three known-valid values.
  Recomputed and verified: the standard Receita Federal mod-11 weight
  sequences, `5,4,3,2,9,8,7,6,5,4,3,2` (first digit, 12 weights) and
  `6,5,4,3,2,9,8,7,6,5,4,3,2` (second digit, 13 weights), reproduce all
  three fixtures exactly. `CnpjChecksumValidator` and this PLAN's
  algorithm description above use the corrected sequences; the
  alphanumeric fixture (`12ABC34501DE35`) was computed against the
  corrected weights, not the original ones.
- **`TaxIdNormalizer`/`CnpjChecksumValidator` are `public`, not
  package-private as this PLAN's first draft stated** — `TenantService`
  lives in the parent `tenancy` package, a different Java package than
  `tenancy.validation`, so package-private visibility would not compile
  across that boundary. Both classes stay `final` with a private
  constructor (pure static-method utility classes), just not
  package-private.
- **Migration number is `V23`, as this PLAN assumed** — confirmed against
  `ls src/main/resources/db/migration/` before writing it (highest
  existing was `V22`); no renumbering needed.
- **`Tenant(String name)` is kept, not removed**, contrary to this
  PLAN's "Package/file structure" note that it's "no longer sufficient."
  It now delegates to the full constructor with the same
  sentinel-placeholder values `V23`'s migration backfill uses for
  pre-existing DB rows (`legalName = name`, `taxId =
  "PENDING-<seq>-<nanoTime>"` for DB-level uniqueness, `country = "BR"`,
  etc.), instead of being dropped. Rationale: ~50 test fixture files
  across the whole codebase construct a bare `new Tenant(name)` with no
  interest in company identification; TASKS.md's task 8 explicitly lists
  only 6 fixture files as in-scope for updates. Making the single-arg
  constructor still satisfy every new `NOT NULL` column (the same way
  the SQL migration itself backfills pre-existing rows) keeps that
  TASKS.md file list accurate without silently breaking ~44 unrelated
  test files. Real tenant creation (`TenantService#createTenant`) always
  uses the full 13-arg constructor instead.
- **`@ValidTaxId` is placed at the record's type level
  (`@ValidTaxId public record CreateTenantRequestDto(...)`), not inline
  before the `taxId` component** as this PLAN's DTO code sample showed.
  The sample's placement was illustrative, not literal: a
  `ConstraintValidator<ValidTaxId, CreateTenantRequestDto>` (reading both
  `taxId` and `country`) can only receive the *containing object* as its
  validated value, which requires the annotation's `@Target` to be
  `TYPE`, not a record component/field — record components would hand
  the validator a bare `String`, not the DTO. This matches the "Open
  decision" section's own description of the validator's generic
  signature; only the sample's inline placement was updated.
- **`taxId`/`adminEmail` uniqueness is enforced via a proactive
  `existsByTaxId`/`findByEmailIgnoreCase` check before any insert, not
  by inserting and catching `DataIntegrityViolationException`** as
  originally proposed (mirroring `ProfileEditRequestService
  #approveEditRequest`'s pattern). Discovered during implementation:
  unlike that precedent (where the catch happens in a method that is
  itself *not* the `@Transactional` boundary, so the failed insert's
  transaction is already fully rolled back by the time anything else
  touches the session), `createTenant` **is** its own `@Transactional`
  + `@AuditLog` boundary — catching the violation and continuing to run
  in the same still-open transaction/session left Hibernate's
  persistence context holding a failed-insert entity with a null
  identifier, which surfaced as a spurious
  `AssertionFailure: ... has a null identifier` when the audit write's
  own `REQUIRES_NEW` flush later touched the same corrupted session. A
  proactive existence check (same small, accepted TOCTOU window
  `adminEmail`'s check already has) avoids the corrupted-session failure
  entirely and is simpler. `TenantAlreadyExistsException` itself,
  its 409 mapping, and its "disambiguated by which check fired" shape
  are unchanged from this PLAN.
- **`CreationValidationAuditAdvice` gained a third case
  (`TenantController#createTenant` → `"tenant.create.denied"`)**, not
  called out explicitly in this PLAN's "Package/file structure" section.
  REQ-8 requires tenant creation to be "audited... the same way every
  other write is audited" including on rejection; a Bean Validation
  failure on `CreateTenantRequestDto` happens before
  `TenantService#createTenant`'s own `@AuditLog` is ever entered — the
  exact gap this advice already exists to close for `addMember`/
  `createStaffUser` (`mandatory-complete-profile/PLAN.md`). Adding
  `createTenant` as a third case reuses the existing mechanism rather
  than inventing a new one, consistent with REQ-8's "no new audit
  mechanism is introduced."

