# SPEC — Tenant creation (staff)

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-02 — Amendment: full company identification, first-admin
  complete profile, and role selection.** Three backend SPECs landed
  this session that the `/tenants/new` form must now reflect, per the
  cross-repo placement rule and each backend SPEC's own "Out of scope"
  follow-up note:
  - `knowly-api/specify/features/tenant-creation/SPEC.md` — tenant
    creation now requires full company identification (legal name, tax
    ID, contact email/phone, structured address), not just `name`.
  - `knowly-api/specify/features/mandatory-complete-profile/SPEC.md` —
    the first admin created alongside the tenant is a `User` created via
    `TenantService.addMember`, which now rejects the whole request if
    that user's profile isn't complete (full name, birth date, CPF, RG,
    RG issuing body, full address, at least one contact).
  - `knowly-api/specify/features/user-role-selection-at-creation/SPEC.md`
    — the first admin's role (`MEMBER`/`MEMBER_ADMIN`) is now selectable
    at creation instead of being implicitly fixed.
  This amendment adds REQ-7 through REQ-21 below, in sequence after the
  original REQ-1–REQ-6 (unchanged). No prior requirement is reversed or
  reinterpreted; REQ-1 and REQ-4 are extended (not replaced) to reflect
  the larger payload — see the notes inline on each.

## Context and motivation

The backend already supports creating a tenant (`POST /api/tenants`,
staff-only — see `knowly/specify/features/tenancy/SPEC.md` REQ-10), but
the frontend has no screen that calls it. A staff user currently has no
way, from the UI, to provision a new tenant and its first admin — the
only tenant-related screen today (`select-tenant-page`) lets a user
*choose* an existing tenant, not create one.

As of this amendment, `POST /api/tenants` also requires full company
identification data and, because creating a tenant creates its first
member via `TenantService.addMember` in the same action, that member's
profile must be complete at creation time (no partial/pending account is
ever created — see `mandatory-complete-profile`'s REQ-8). The form must
collect everything both backend calls now require, in one staff action,
or the whole thing is rejected.

## User stories

- As a staff user, I want to create a new tenant and designate its
  first admin in one action, so that I can onboard a new client without
  needing direct API/database access.
- As a staff user with no tenant memberships, I want an obvious way to
  reach tenant creation from the screen I land on after login, so I'm
  not stuck with an empty tenant list and no next step.
- As a tenant user (non-staff), I want tenant creation to be invisible
  to me, since I have no way to use it and it isn't part of my role.
- As a staff user provisioning a new client, I want to enter the
  company's legal identity (razão social, tax ID, contact, address) in
  the same screen, so the tenant is properly identified from day one,
  not just by a display name.
- As a staff user provisioning a new client, I want to enter the first
  admin's complete profile (not just an email) in the same screen, so
  the creation isn't rejected midway for a field I didn't know was
  required.
- As a staff user, I want to choose whether the first user is a plain
  `MEMBER` or a `MEMBER_ADMIN`, so I'm not forced into always creating an
  admin (or always a non-admin) for the first person on a new tenant.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall provide a dedicated route
  (`/tenants/new`) with a form to create a tenant, collecting the
  tenant name and the first admin's email. **(Extended by REQ-7–REQ-16
  below: the form now also collects full company identification and the
  first user's complete profile and role — REQ-1 itself is unchanged,
  the form is simply larger.)**
- **REQ-2 [Ubiquitous]** Only staff users shall be able to navigate to
  or use the tenant creation route; this mirrors the backend's
  staff-only enforcement (REQ-10 of `tenancy`) but is enforced again on
  the frontend via a route guard, consistent with `tenant-selection.guard.ts`'s
  existing pattern of guarding tenant-context routes.
- **REQ-3 [Ubiquitous]** The `select-tenant-page` shall show a "create
  tenant" action, visible only to staff users, linking to `/tenants/new`.
- **REQ-4 [Event-Driven]** When a staff user submits the creation form
  with all required fields valid (company identification, first user's
  complete profile, and chosen role — see REQ-7–REQ-16), the system
  shall call `POST /api/tenants` and, on success, navigate to the tenant
  selection page so the newly created tenant is selectable. **(Extended:
  "valid name and admin email" is replaced by "all required fields
  valid," since the payload is now larger — see Changelog.)**
- **REQ-5 [Unwanted Behavior]** If the creation request fails (e.g.
  validation error, admin email already tied to a conflicting role, a
  `taxId` conflict, an incomplete first-user profile, or a non-staff/
  forbidden response), then the system shall show an inline error and
  keep the user on the form with their input intact.
- **REQ-6 [Unwanted Behavior]** If a non-staff user reaches
  `/tenants/new` directly (e.g. via URL), then the system shall redirect
  them away, the same way `tenant-selection.guard.ts` redirects on other
  disallowed tenant-route access.

### Company identification fields (mirrors backend `tenant-creation` REQ-1/REQ-2/REQ-6)

- **REQ-7 [Ubiquitous]** The form shall collect, for the company: trade
  name (`name`, already required by REQ-1), legal name (`legalName`,
  razão social), a fiscal identification document (`taxId`), the
  company's country of operation (`country`), a contact email
  (`contactEmail`), a contact phone (`contactPhone`), and a structured
  address (postal code, street, number, complement, neighborhood, city,
  state/province, country).
- **REQ-8 [Ubiquitous]** `legalName`, `taxId`, `country`, `contactEmail`,
  `contactPhone`, and every address field except complement (postal
  code, street, number, neighborhood, city, state/province, address
  country) shall be required client-side, mirroring backend `tenant-
  creation` REQ-2's mandatory set exactly. Complement is optional.
- **REQ-9 [Event-Driven]** When the form is submitted with one or more
  of REQ-8's required fields missing, or `contactEmail` not in a valid
  email format, the system shall show a field-level error message next
  to each invalid/missing field (not a single generic banner) and shall
  not call the API.
- **REQ-10 [Optional Feature]** Where the selected company country
  denotes Brazil, the system shall validate `taxId` client-side against
  the CNPJ shape (14 digits, with or without punctuation) before
  allowing submission; where it denotes any other country, the system
  shall only require `taxId` to be non-empty, applying no
  country-specific format check — mirrors backend REQ-6 exactly,
  including that same scope cut (no other country's document format is
  known to this system).
- **REQ-11 [Unwanted Behavior]** If `POST /api/tenants` rejects the
  request with 409 due to a `taxId` conflict, then the system shall show
  that error next to the `taxId` field specifically (not a generic
  banner), consistent with REQ-5's "keep the user on the form" behavior.

### First admin's complete profile (mirrors backend `mandatory-complete-profile` REQ-8)

- **REQ-12 [Ubiquitous]** The form shall collect, for the first tenant
  member (replacing the bare "admin email" field from the original
  REQ-1 with a complete profile): full name (`fullName`), birth date
  (`birthDate`), CPF (`cpf`), RG (`rg`), RG issuing body
  (`rgOrgaoIssuer`/`rgOrgaoEmissor`), a structured address (postal code,
  street, number, complement, neighborhood, city, state/province,
  country), and at least one contact (type + value, e.g. email or
  phone) — the email address originally required by REQ-1 is satisfied
  by this contact list (an `EMAIL`-type contact), not a separate field.
- **REQ-13 [Ubiquitous]** Every field in REQ-12 except the address's
  complement shall be required client-side; at least one contact row
  shall be required (the "add contact" control starts with one empty
  row, matching the pattern used for repeatable rows elsewhere, per
  `user-profile-v2 SPEC.md`'s contacts-list editor).
- **REQ-14 [Event-Driven]** When the form is submitted with one or more
  of REQ-13's required first-user fields missing, or with zero contacts
  entered, the system shall show a field-level error next to each
  invalid/missing field (or, for "zero contacts," next to the contacts
  section) and shall not call the API.
- **REQ-15 [Unwanted Behavior]** If `POST /api/tenants` rejects the
  request because the first user's profile is incomplete (per backend
  `mandatory-complete-profile` REQ-8), then the system shall map the
  returned field-level errors, where the response identifies them, back
  onto the corresponding first-user form fields; where the response does
  not identify specific fields, the system shall fall back to REQ-5's
  generic inline error.
- **REQ-16 [Ubiquitous]** The company's address section and the first
  user's address section shall be two independent form sub-sections,
  not a shared/prefilled input — a company's registered address and its
  first admin's personal address are different data (different backend
  entities: `tenants` columns vs. the user's `addresses` row) and there
  is no basis to assume they coincide. Both sub-sections shall present
  the same field layout (postal code, street, number, complement,
  neighborhood, city, state/province, country) and, at the PLAN level,
  should be implemented as two instances of one reusable address-fields
  UI unit rather than duplicated markup — this reuses presentation only,
  not data, consistent with `user-profile-v2`'s own structured-address
  field set.

### Role selection (mirrors backend `user-role-selection-at-creation` REQ-6/REQ-9)

- **REQ-17 [Ubiquitous]** The form shall let the staff user choose the
  first tenant member's role: `MEMBER` or `MEMBER_ADMIN`.
- **REQ-18 [Optional Feature]** Where the staff user does not explicitly
  choose a role, the system shall default the selection to `MEMBER_ADMIN`
  in the form's initial state — a newly provisioned tenant's very first
  user with no other member yet is, in practice, always intended as its
  administrator; this is a form-level default only; the underlying
  backend field itself remains optional and backend-default `MEMBER`
  applies only if the request omitted the field entirely. This is a
  UX decision (not a backend rule) made to reduce the common case of the
  staff user having to remember to change a default — the staff user can
  still switch it to `MEMBER` before submitting.
- **REQ-19 [Ubiquitous]** The role choice shall be submitted as `role`
  on the `POST /api/tenants` request body, exactly as accepted by
  `TenantService.addMember`'s `role` parameter per backend
  `user-role-selection-at-creation` REQ-6.

### Form structure decision

- **REQ-20 [Ubiquitous]** The form shall remain a **single scrollable
  page** organized into clearly labeled sections (company identification,
  first user profile, role, address sub-sections per REQ-16) — not a
  multi-step/wizard flow. Decision, documented per this amendment's
  request to justify it: no multi-step/wizard pattern exists anywhere
  else in this codebase today (checked: no matching precedent under
  `knowly-app/specify/features/**`), and introducing one here — for a
  rare, staff-only, high-touch operation performed by an internal user
  who already has all the data in hand (per backend `tenant-creation`'s
  own "why this shape" reasoning) — would add a new UX pattern with no
  existing convention to follow, more implementation risk than a long
  single-page form with fieldsets, and no clear benefit for this
  audience (unlike a multi-session self-service signup flow, which this
  is explicitly not — see backend `tenancy` REQ-10). If a second
  long-form use case appears later and a step pattern is introduced
  then, this decision should be revisited together with that precedent.
- **REQ-21 [Ubiquitous]** Each of the three sections in REQ-20 shall
  have a visible heading and shall independently show its own
  field-level validation errors (per REQ-9, REQ-14) inline, so a staff
  user scrolling a long form can immediately see which section has a
  problem without scrolling to find a summary elsewhere.

## Non-functional requirements

- Accessibility: form fields have associated labels, errors are
  announced via existing form-error conventions used elsewhere in the
  app (see `members-page` forms); this now includes the repeatable
  contacts-list rows and two structured-address sub-sections — every
  added/removed row remains keyboard-operable and its label/error
  association is preserved, matching `user-profile-v2`'s own
  accessibility requirement for its contacts editor.
- Responsiveness: usable at mobile width, consistent with other
  narrow-form pages (e.g. `select-tenant-page`'s `max-w-md` pattern) —
  though this form, being materially longer, is not expected to fit a
  `max-w-md` container; a wider single-column layout is expected at the
  PLAN level, still usable at mobile width via full-width sections.

## Acceptance criteria

- [ ] A staff user can open `/tenants/new`, fill in company
      identification, the first user's complete profile (including at
      least one contact), select a role, submit, and lands back on
      tenant selection with the new tenant listed.
- [ ] A non-staff user cannot see the "create tenant" link and is
      redirected away if they navigate to `/tenants/new` directly.
- [ ] Submitting with any required company-identification field missing
      shows a field-level error for that field, without calling the API.
- [ ] Submitting with any required first-user profile field missing, or
      with zero contacts, shows a field-level error, without calling the
      API.
- [ ] Submitting with a non-Brazil company country and any non-empty
      `taxId` does not trigger the CNPJ-shape check.
- [ ] Submitting with a Brazil company country and a `taxId` not
      matching the 14-digit CNPJ shape shows a field-level error on
      `taxId`, without calling the API.
- [ ] The role selector defaults to `MEMBER_ADMIN` but can be changed to
      `MEMBER` before submit; the chosen value is sent as `role`.
- [ ] A backend error (e.g. 403, 409 on `taxId`, 400 on an incomplete
      first-user profile) on submit shows an inline error — field-level
      where the response identifies the field, generic otherwise — and
      preserves the entered values.
- [ ] The company address sub-section and the first user's address
      sub-section accept independent values (filling one does not
      prefill or constrain the other).

## Out of scope

- Any change to backend tenant-creation rules (staff-only stays
  staff-only; REQ-10 of `tenancy` is not being revisited).
- Editing or deleting existing tenants.
- Bulk/CSV tenant import.
- CEP-to-address lookup/autofill for either address sub-section — plain
  text inputs only, mirroring both backend SPECs' own "no address
  lookup" scope cut.
- CPF/RG format/checksum validation beyond what's already decided
  elsewhere in the app — this form applies no new client-side CPF/RG
  format check, since none is specified by `mandatory-complete-profile`
  or `identity-profile-model-v2` (required-non-blank only).
- CNPJ check-digit validation — REQ-10 only checks the 14-digit shape,
  mirroring backend REQ-6's own scope cut.
- Promotion of an existing member to `MEMBER_ADMIN` after creation — out
  of scope per backend `staff-rbac-management-operations`, unaffected by
  REQ-17–REQ-19 (those cover only the moment of first-user creation).
- Multiple contacts required, or multiple company contacts (billing vs.
  technical) — the form requires exactly "at least one" contact for the
  first user (REQ-13) and a single `contactEmail`/`contactPhone` pair
  for the company (REQ-7), matching each backend SPEC's own scope.
- A multi-step/wizard version of this form — considered and explicitly
  rejected for now, see REQ-20's documented reasoning.
