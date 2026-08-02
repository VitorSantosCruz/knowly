# SPEC — Tenant creation: full company identification (backend)

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-02 — Amendment: single-call atomic creation, explicit.**
  Per `knowly-app`'s `tenant-creation/SPEC.md` REQ-4/REQ-5 (approved
  the same day), `POST /api/tenants` must accept the company's
  identification fields (this SPEC), the first admin's full profile
  (`mandatory-complete-profile` SPEC), and the first admin's role
  (`user-role-selection-at-creation` SPEC) in **one** request, and
  either fully succeed or fully fail — no orphaned tenant, no member
  without a tenant. This was previously implicit (`tenancy` SPEC REQ-10
  already required "creating a tenant shall always include designating
  its first user as tenant admin in the same action," which by itself
  already implies one action, not two), but was not stated in this
  SPEC's own requirements, and a prior PLAN.md draft momentarily
  proposed splitting this into two separate calls. New REQ-9 below
  makes the atomicity explicit as this SPEC's own requirement, so it
  can never again drift into two calls without a SPEC change. This
  amendment does not alter REQ-1 through REQ-8 or `mandatory-complete-
  profile`/`user-role-selection-at-creation`'s own scope — it only
  states, as a requirement of *this* SPEC, that their rules apply
  within the same transaction/request this SPEC already governs.

## Context and motivation

Today, creating a tenant (`POST /api/tenants`, staff-only per `tenancy`
SPEC REQ-10) collects only a single free-text `name`. The product owner
considers this insufficient: a "company" cannot be unambiguously
identified — for legal, invoicing, or compliance purposes — by a
display name alone. There is no fiscal identification document, no
contact channel, and no registered address captured anywhere in the
`Tenant` entity.

This SPEC extends tenant creation to capture a company's full
identification: legal name, a fiscal identification document, at least
one contact email and phone, and a complete, structured address. It
does **not** touch `tenancy` SPEC's REQ-10 (staff-only creation, no
self-service signup, first-admin-in-the-same-action) — those rules are
unchanged and are restated here only for context, never reinterpreted.

**Cross-repo note:** the frontend form at `/tenants/new`
(`knowly-app/specify/features/tenant-creation/SPEC.md`) currently
submits only `name` + admin email. Per this monorepo's cross-repo SPEC
placement rule, that frontend screen needs its own SPEC amendment to
collect the new fields defined here — that amendment is **not** part of
this SPEC and is not done by writing this document; it's a follow-up
this SPEC's "Out of scope" section calls out explicitly.

## Why this shape, and why "required at creation" (not deferred, unlike user profiles)

The product owner asked, when raising this request, whether tenant
identification should be symmetric with how user/profile data is
handled — see `identity-profile-model-v2`: a user's account is created
with an eager-but-empty profile row, and every identifying field
(name, CPF, RG, birth date, address, contacts) is filled in later,
never blocking anything, via a self-request-plus-approval flow. That
pattern was chosen there because self-service account creation
shouldn't force a brand-new employee to have every personal document
in hand at the exact moment their account is provisioned.

Tenant creation does not share that constraint, and this SPEC
deliberately does **not** copy that deferred-completion pattern:

- Only staff create tenants (`tenancy` REQ-10) — a rare, deliberate,
  high-touch operational action, not a self-service signup a new
  account holder rushes through. The staff member onboarding a new
  client already has the client's legal/fiscal/contact/address
  information in hand from the sales/contracting process that
  preceded provisioning the tenant in this system.
- The entire reason this data is being collected — legal identity for
  invoicing, contracts, and compliance — is undermined if it can be
  postponed indefinitely; unlike a user's CPF/RG (collected for
  in-app accountability, useful but not blocking any other feature),
  a tenant's fiscal identity is closer to why a business relationship
  can be entered into at all.
- Deferring this to a later "tenant onboarding completion" step would
  mean the system allows fully operational tenants (with real
  articles, chats, members) transacting under a company identity
  nobody has verified — a materially different risk profile than a
  user leaving their own CPF/RG blank while using the product
  normally.

**Decision:** every field this SPEC introduces (except `complemento`,
an address sub-field that is genuinely optional for any address) is
**mandatory at creation**, blocking `POST /api/tenants` until supplied
and valid — not a "create now, complete later" onboarding gate. This is
the one place this SPEC deliberately breaks symmetry with the user
profile pattern, and the reasoning above is the documented justification
the product owner asked for.

## User stories

- As a staff user creating a new tenant, I want to capture the
  company's legal name, fiscal ID, contact details, and full address in
  the same action that creates the tenant, so the tenant is
  unambiguously identifiable from day one, not just by a display name.
- As anyone later needing to invoice, contact, or legally identify a
  tenant, I want that information to already exist and be trustworthy,
  not missing or optional.
- As a staff user, I want a clear, field-by-field validation error if
  I'm missing or mistyping required identification data, so I don't
  create a tenant with incomplete identity by accident.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The `Tenant` entity shall carry, in addition to
  the existing `name` (kept unchanged, and continuing to serve as the
  tenant's trade/display name — e.g. "nome fantasia"): a `legalName`
  (razão social), a `taxId` (the company's fiscal identification
  document), a `country` (country of operation, determining which
  fiscal-document convention applies), a `contactEmail`, a
  `contactPhone`, and a structured address (`postalCode`,
  `street`, `number`, `complement`, `neighborhood`, `city`,
  `state`/`province`, `country`).
- **REQ-2 [Ubiquitous]** `legalName`, `taxId`, `country`, `contactEmail`,
  `contactPhone`, `postalCode`, `street`, `number`, `neighborhood`,
  `city`, and `state`/`province` shall be mandatory on tenant creation.
  `complement` is the only optional field in this set.
- **REQ-3 [Event-Driven]** When `POST /api/tenants` is submitted missing
  any mandatory field from REQ-2, or with `contactEmail` not in a valid
  email format, the system shall reject the request with a 400
  validation error identifying every missing/invalid field, and shall
  not create any row (no partial tenant, no partial address).
- **REQ-4 [Ubiquitous]** `taxId` shall be unique across all tenants,
  enforced at the database level.
- **REQ-5 [Unwanted Behavior]** If `taxId` collides with an existing
  tenant's, then the system shall reject the request with 409 and
  shall not create any row.
- **REQ-6 [Optional Feature]** Where `country` denotes Brazil, the
  system shall validate `taxId` against the CNPJ format (14 digits,
  with or without punctuation) before accepting it; where `country`
  denotes any other value, the system shall only require `taxId` to be
  a non-empty string, with no country-specific format enforced (no
  other country's fiscal-document convention is known to this system
  yet — see "Out of scope").
- **REQ-7 [Ubiquitous]** `tenancy` SPEC's REQ-10 (staff-only creation,
  first-admin-in-the-same-action, no self-service signup) is unchanged
  and continues to apply unmodified — this SPEC adds fields to the
  same creation action, it does not alter who may perform it or when.
- **REQ-8 [Ubiquitous]** The system shall audit tenant creation
  (including the new identification fields) the same way every other
  write is audited today (actor, action, outcome) — no new audit
  mechanism is introduced.
- **REQ-9 [Ubiquitous]** *(New 2026-08-02, making explicit what REQ-7's
  reference to `tenancy` REQ-10 already implies.)* `POST /api/tenants`
  shall accept the company's identification fields (this SPEC), the
  first admin's complete profile (`mandatory-complete-profile` SPEC),
  and the first admin's role (`user-role-selection-at-creation` SPEC)
  in a single request, and shall persist the tenant and its first
  membership atomically: either both are created together, or, if any
  part of the request is invalid or fails, neither is persisted. There
  is no intermediate state where a tenant exists without its first
  admin, or a first-admin's data is persisted without the tenant it
  belongs to.

## Non-functional requirements

- Security/privacy: `legalName`, `taxId`, `contactEmail`,
  `contactPhone`, and the address fields identify a **company**, not a
  natural person — unlike `identity-profile-model`'s `cpf`/`rg`, none
  of these fields need encryption-at-rest or a blind-index-for-
  uniqueness pattern (company registration data, comparable to what's
  already public in a business registry); `taxId`'s uniqueness
  constraint can be a plain unique index on the plaintext column.
- Observability: validation/conflict failures on `POST /api/tenants`
  are logged the same way other request-validation and conflict
  failures already are in this codebase — no new logging mechanism.
- Technical implication (for PLAN, not decided here): this requires a
  new Flyway migration adding non-nullable columns to `tenants`
  (`legal_name`, `tax_id` + unique index, `country`, `contact_email`,
  `contact_phone`, `postal_code`, `street`, `number`, `complement`,
  `neighborhood`, `city`, `state`) — existing rows in `tenants` (if
  any survive from before this feature) will need a backfill or
  default-value strategy; that strategy is a PLAN-level decision, not
  specified here.

## Acceptance criteria

- [ ] `POST /api/tenants` with all mandatory identification fields
      (name, legal name, tax id, country, contact email, contact phone,
      full address except complement) succeeds and creates a tenant
      whose stored data includes all submitted fields.
- [ ] `POST /api/tenants` missing any one mandatory field is rejected
      with 400, naming the missing field(s), and creates no row.
- [ ] `POST /api/tenants` with a malformed `contactEmail` is rejected
      with 400.
- [ ] `POST /api/tenants` with a `taxId` matching an existing tenant's
      is rejected with 409 and creates no row.
- [ ] `POST /api/tenants` with `country` denoting Brazil and a `taxId`
      not matching CNPJ's 14-digit shape is rejected with 400.
- [ ] `POST /api/tenants` with `country` denoting a non-Brazil value and
      any non-empty `taxId` succeeds (no CNPJ-specific check applied).
- [ ] Tenant creation continues to require a staff caller and a
      first-admin designation exactly as `tenancy` REQ-10 already
      specifies — unchanged.
- [ ] Tenant creation is audit-logged like every other write.
- [ ] `POST /api/tenants` submitting company fields, the first admin's
      complete profile, and their role together succeeds and creates
      the tenant, the first admin `User`/`UserProfile`/`Address`/
      `Contact` row(s), and the first `TenantMembership`, all in one
      request.
- [ ] `POST /api/tenants` with valid company fields but an
      incomplete/invalid first-admin profile is rejected and creates
      no `Tenant` row either — not just no membership.

## Out of scope

- **Editing/completing identification after creation.** This SPEC
  requires everything upfront; there is no "tenant onboarding
  completion" gate or partial-tenant state — see the "Why this shape"
  section above for the reasoning. A future SPEC for *editing* an
  existing tenant's identification (e.g. after a legal name change) is
  a separate feature, not covered here.
- **Multiple contacts per tenant.** `contactEmail`/`contactPhone` are
  single required fields, not a list (unlike the user-facing `contacts`
  1:n model) — the product owner asked for "at least one" of each,
  which a single required field already satisfies; a future need for
  multiple tenant-level contacts (e.g. billing contact vs. technical
  contact) is a new, separate decision, not pre-built here.
- **CNPJ checksum validation.** REQ-6 only checks the 14-digit shape,
  not the actual CNPJ check-digit algorithm — mirrors
  `identity-profile-model-v2`'s own accepted scope cut for CPF/RG
  ("no format/checksum validation").
- **Non-Brazilian fiscal-document conventions** (e.g. an EIN/VAT
  number's own format rules) — REQ-6 only defines Brazil's CNPJ
  convention; any other country's format validation is new scope for
  whenever the system actually needs to operate outside Brazil.
- **Address (CEP) lookup/autofill** — plain text inputs only, no
  third-party address-lookup integration.
- **A "legal representative" / KYC contact field** — considered and
  deliberately not added; not requested, and would be new scope beyond
  "identify the company itself."
- **Self-service tenant signup, billing/plan differentiation** — both
  remain out of scope per `tenancy` SPEC, unmodified by this feature.
- **The frontend `/tenants/new` form update** — a required follow-up
  (see "Cross-repo note" above) but not part of this backend SPEC;
  needs its own SPEC amendment in
  `knowly-app/specify/features/tenant-creation/SPEC.md`.
