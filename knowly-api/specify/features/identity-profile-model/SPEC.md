# SPEC — identity-profile-model

## Context and motivation

Today's `User` and `Tenant` entities are effectively anonymous — no field
identifies the real-world person behind an account (`User` has only
`email`), and no field identifies the real-world company behind a tenant
(`Tenant` has only `name`). This feature introduces the first complete
identity records for both: full personal-data fields for `User` (name,
address, RG, CPF, phone) and full legal-entity fields for `Tenant` (CNPJ
and other Brazilian company identifiers), plus the permission model that
governs who may view or edit that data, and a self-service change-request
flow for users who hold no edit rights over their own record.

This is backend-only. No `knowly-app/` screens are covered by this SPEC.

CPF/RG are sensitive personal data under LGPD. The retention and
at-rest-encryption approach below was already confirmed by the product
owner (2026-07-26) and is treated here as settled, not re-litigated:
- CPF/RG are encrypted at rest via a JPA `AttributeConverter`; the
  encryption key is managed externally (secrets manager or
  environment-injected), never hardcoded or committed.
- Decryption happens only in memory, only for a caller holding the
  relevant view/edit permission for that record.
- Retention is indefinite while the `User` record exists — no automatic
  expiry or anonymization job. Deletion is manual/on-demand only.

**A new Tier 3 issue was found while writing this SPEC and has since been
resolved — confirmed 2026-07-26, see "Resolved" section below.** Standard
randomized-IV encryption is incompatible with enforcing DB-level
uniqueness on the same encrypted field, so REQ-2/REQ-3 below are
written implementation-agnostically pending that decision.

## User stories

- As a `MEMBER_ADMIN`/`STAFF_ADMIN`, I want to edit my own profile
  without needing any extra grant, so basic self-service isn't blocked
  by my own admin role.
- As a user holding the "edit profiles" permission, I want to correct
  other people's personal data, but not be able to use that same
  permission to rewrite my own identity fields.
- As a plain member/staff user with no edit-profiles permission, I still
  want to submit a correction to my own profile, even though it needs
  someone else's approval before it takes effect.
- As any user, I want to see only my own profile detail (and only my own
  chat display nickname).
- As the product owner, I want CPF/RG/email/phone/address enforced
  globally unique at the database level.
- As the product owner, I want every tenant's company record to carry a
  real CNPJ and the other identifiers needed for a real Brazilian
  company, not just a display name.

## Requirements (EARS/GEARS)

### User personal-data fields

- **REQ-1 [Ubiquitous]** The `User` entity shall carry `fullName`,
  `address`, `rg`, `cpf`, and `phone` fields in addition to its existing
  `email` field.
- **REQ-2 [Ubiquitous]** The system shall enforce, at the database level,
  global uniqueness — across every `User` row regardless of tenant — of:
  `email` (already enforced today, unchanged), `address`, `rg`, `cpf`,
  and `phone`. (Exact mechanism for `rg`/`cpf`, given encryption — see
  "Flagged for human decision.")
- **REQ-2a [Optional Feature]** Where a `User` row has not yet had a
  given uniqueness-enforced field populated, the system shall allow
  multiple such rows to coexist with that field left unset.
- **REQ-3 [Ubiquitous]** The system shall store `cpf` and `rg` encrypted
  at rest via a JPA `AttributeConverter`, using an encryption key sourced
  from external configuration.
- **REQ-4 [Event-Driven]** When `cpf` or `rg` is shown to a caller, the
  system shall decrypt it in memory only, and only if that caller holds
  the permission required to view that record's personal data.
- **REQ-5 [Ubiquitous]** The system shall retain `User` personal-data
  fields (including `cpf`/`rg`) indefinitely for as long as the `User`
  record exists; deletion happens only via an explicit, manual action,
  out of scope for this SPEC.

### Tenant company-record fields

- **REQ-6 [Ubiquitous]** The `Tenant` entity shall carry `cnpj`,
  `razaoSocial`, and `nomeFantasia` fields in addition to its existing
  `name` field, plus an optional `inscricaoEstadual`.
- **REQ-7 [Ubiquitous]** The system shall enforce, at the database level,
  global uniqueness of `Tenant.cnpj` across every tenant.
- **REQ-7a [Optional Feature]** Where `Tenant.inscricaoEstadual` is
  populated, the system shall enforce its global uniqueness; where
  absent, no constraint applies.
- **REQ-7b [Optional Feature]** Where a `Tenant` row has not yet had
  `cnpj` populated, the system shall allow multiple such rows to coexist
  with `cnpj` unset.

### Profile visibility

- **REQ-8 [Ubiquitous]** The system shall let any authenticated user
  retrieve their own full profile detail and their own chat display
  nickname.
- **REQ-9 [Unwanted Behavior]** If a user attempts to view another
  user's full profile detail without the applicable view/edit
  permission, then the system shall reject the request.
- **REQ-10 [Optional Feature]** Where a caller holds tenant-scoped
  `PROFILE_VIEW`, the system shall let them view any member of that
  tenant's full profile detail.
- **REQ-10a [Optional Feature]** Where a caller holds global-scoped
  `PROFILE_VIEW` (staff only), the system shall let them view any
  `User`'s full profile detail, regardless of tenant.
- **REQ-10b [Complex]** Where a caller is `MEMBER_ADMIN` of a tenant, the
  system shall let them view any member of that tenant's full profile
  detail as part of their general admin power, no separate grant
  required.
- **REQ-10c [Optional Feature]** Where a caller is `STAFF_ADMIN`, the
  system shall let them view any `User`'s full profile detail, no
  separate grant required.

### Profile editing

- **REQ-11 [Complex]** Where a caller is `MEMBER_ADMIN` of a tenant, the
  system shall let them directly edit any member of that tenant's
  personal-data fields (including their own record), taking effect
  immediately, no separate permission required, no exception for self.
- **REQ-12 [Optional Feature]** Where a caller is `STAFF_ADMIN`, the
  system shall let them directly edit any `User`'s personal-data fields
  (including their own), taking effect immediately.
- **REQ-13 [Optional Feature]** Where a caller holds tenant-scoped
  `PROFILE_EDIT` (and is not `MEMBER_ADMIN` acting under REQ-11), the
  system shall let them directly edit any *other* member of that
  tenant's personal-data fields.
- **REQ-13a [Unwanted Behavior]** If a caller holding only tenant-scoped
  `PROFILE_EDIT` attempts to directly edit their own personal-data
  fields, then the system shall reject the direct edit — that holder
  must use the self-requested-edit flow (REQ-15) for their own record.
- **REQ-14 [Optional Feature]** Where a caller holds global-scoped
  `PROFILE_EDIT` (staff, not `STAFF_ADMIN`), the system shall let them
  directly edit any *other* `User`'s personal-data fields.
- **REQ-14a [Unwanted Behavior]** If a caller holding only global-scoped
  `PROFILE_EDIT` attempts to directly edit their own personal-data
  fields, then the system shall reject the direct edit.
- **REQ-15 [Event-Driven]** When a user with no applicable direct-edit
  right submits a change to their own personal-data fields, the system
  shall create a pending profile-edit request rather than applying the
  change immediately.
- **REQ-16 [Event-Driven]** When a pending profile-edit request is
  created, the system shall create an in-app notification — reusing the
  `Notification` mechanism from `tenant-membership-acceptance` (new
  `type` value(s)) — addressed to every holder of the applicable edit
  right over the requester's record.
- **REQ-17 [Event-Driven]** When a holder of the applicable edit right
  approves a pending request, the system shall apply the proposed field
  values and mark the request resolved (approved).
- **REQ-18 [Event-Driven]** When a holder of the applicable edit right
  rejects a pending request, the system shall discard the proposed
  values and mark the request resolved (rejected).
- **REQ-19 [Unwanted Behavior]** If a user attempts to approve/reject a
  request they don't hold the applicable edit right over, then the
  system shall reject the action.
- **REQ-20 [Unwanted Behavior]** If a user attempts to submit a new
  request while already having an unresolved one, then the system shall
  reject the new submission.
- **REQ-21 [Unwanted Behavior]** If approving a request would violate
  the global uniqueness constraints (REQ-2/REQ-2a), then the system
  shall reject the approval.
- **REQ-22 [Ubiquitous]** Nobody — regardless of role or permission —
  may use any mechanism in this SPEC to change a role or permission
  grant; this feature governs personal-data fields only.

## Non-functional requirements

- Security: `cpf`/`rg` are never logged, never included in an audit
  event's `metadata` in plaintext, never returned to a caller lacking
  the applicable right.
- Security: the encryption key is never hardcoded or committed.
- Observability: every direct edit, request submission/approval/
  rejection, and permission-denied attempt emits `@AuditLog` (actor,
  action, outcome — field *names* changed, never plaintext CPF/RG).
- Consistency: reuses `tenant-membership-acceptance`'s `Notification`
  entity rather than building a second mechanism.

## Acceptance criteria

- [ ] `User` gains `fullName`/`address`/`rg`/`cpf`/`phone`; `Tenant`
      gains `cnpj`/`razaoSocial`/`nomeFantasia`(/`inscricaoEstadual`).
- [ ] DB-level uniqueness rejects a duplicate `cpf`/`rg`/`phone`/
      `address`/`email` across users, and duplicate `cnpj`/
      `inscricaoEstadual` across tenants, even bypassing app validation.
- [ ] Existing rows remain valid with new fields unset.
- [ ] `cpf`/`rg` are stored encrypted (verified at the raw DB column).
- [ ] Own-profile fetch returns full detail; other-profile fetch without
      the applicable permission is rejected.
- [ ] `MEMBER_ADMIN` can view/edit any member of their tenant (including
      self); cannot reach outside their tenant via this mechanism.
- [ ] `STAFF_ADMIN` can view/edit any user, including self.
- [ ] A tenant/global `PROFILE_EDIT` holder (not admin) can edit others
      directly but is rejected editing their own record directly.
- [ ] A user with no edit right can submit a self-edit request; it
      doesn't apply until approved.
- [ ] Approve applies + resolves; reject discards + resolves.
- [ ] A second pending request is rejected while one is unresolved.
- [ ] An approval violating uniqueness fails cleanly.
- [ ] Every relevant action is audit-logged.
- [ ] No endpoint in this feature changes a role or permission grant.

## Out of scope

- Frontend screens.
- User/role/tenant-membership management (items 5/8).
- Automatic/scheduled deletion, expiry, or anonymization of any field.
- Redesigning the `Notification` mechanism (only new `type` values).
  Depends on `tenant-membership-acceptance` being implemented.
- Chat display nickname's own data model (item 14).
- CPF/RG format/checksum validation.
- Bulk backfill of missing data for pre-existing rows.
- Rate-limiting profile-edit-request submissions.

## Decisions / judgment calls

1. **Tenant fields**: `cnpj` (required, unique), `razaoSocial` (required),
   `nomeFantasia` (required), `inscricaoEstadual` (optional, unique when
   present) — CNPJ as the uniqueness anchor; the other two names are
   legitimately distinct pieces of information every registered company
   has; state tax registration is optional since some entities are
   legally exempt. Municipal registration and a separate company address
   were not requested and are not included.
2. **`PROFILE_VIEW`/`PROFILE_EDIT` as two new permission pairs**
   (tenant-scoped `Permission` + global-scoped `GlobalPermission`) —
   `User` isn't itself tenant-owned (one person, multiple tenants), so a
   single tenant permission can't express "staff may edit any user
   globally." View and edit kept as separate grants.
3. **Retrofit migration**: new columns nullable, partial unique indexes
   (`WHERE <col> IS NOT NULL`) rather than backfilling placeholders or
   forcing `NOT NULL` immediately — mirrors the existing
   `onboarding_completed_at`-style precedent.
4. **`fullName` is NOT DB-uniqueness-enforced** — `PROJECT_STATUS.md`'s
   own confirmed text enumerates only email/address/rg/cpf/phone as
   unique, not full name; two different real people can share a name,
   so global uniqueness on it isn't required by the actual confirmed
   text, even though an earlier paraphrase suggested otherwise. `address`
   uniqueness is kept exactly as literally stated, despite being a
   realistic edge case (shared households), because it's explicitly
   enumerated in the source document.
5. No other new Tier 3 conflict found regarding scope/retention/basic
   encryption — those were already confirmed and are treated as settled.

## Resolved: CPF/RG uniqueness + encryption mechanism (confirmed 2026-07-26)

**Decision: use a blind index.** The product owner confirmed accepting
the equality-revealing tradeoff described below. `cpf`/`rg` are stored
encrypted (REQ-3, randomized-IV AES via the `AttributeConverter`) *plus*
a second, indexed `cpfBlindIndex`/`rgBlindIndex` column holding a keyed
HMAC-SHA256 of the normalized plaintext, computed with a **different**
key than the encryption key (both externally managed, never hardcoded).
The blind-index columns carry the actual DB-level unique constraints for
REQ-2; the encrypted columns are never used for equality comparison.
PLAN.md should specify: the two independent keys (encryption key,
HMAC key) and how each is sourced from external config; normalization
rule before hashing (e.g. strip non-digits from CPF/RG so equivalent
formatted/unformatted input hashes identically); that the blind-index
column is populated alongside every write to the encrypted field, never
independently editable.

## Original analysis (retained for context — decision above is final)

**Randomized-IV encryption (the standard choice for a JPA
`AttributeConverter`, e.g. AES-GCM) is fundamentally incompatible with
REQ-2's DB-level uniqueness requirement on `cpf`/`rg`** — the same
plaintext encrypts to different ciphertext every time, so a plain unique
index on the encrypted column cannot detect a duplicate. Supporting both
"encrypted at rest" and "globally unique, enforced by the database"
requires an additional mechanism — most standardly a **blind index**: a
second, indexed column storing a keyed HMAC (different key than the
encryption key) of the normalized plaintext, used only for equality/
uniqueness checks, never decrypted back. This is a well-established
pattern, but it is a genuinely new kind of data exposure the confirmed
"encrypt CPF/RG at rest" decision never addressed: a blind index reveals
*that* two records share the same CPF/RG (equality), even though it
never reveals the value itself. **This SPEC does not decide the
resolution** — REQ-2/REQ-3 are written implementation-agnostically so
PLAN can carry the actual mechanism, but PLAN should not pick blind-
indexing (or any equality-revealing alternative, e.g. deterministic
encryption, which has the same property with weaker guarantees) without
this being confirmed by the product owner first.
