# SPEC — user-profile-v2 (frontend)

> The what and the why. No technical implementation details.

> **Amendment (2026-08-02, product owner decision — country-agnostic
> identity/address model).** Major, final-direction product decision,
> mirroring the same-day backend amendment in
> `identity-profile-model-v2/SPEC.md` (read that document's amendment in
> full — this SPEC does not restate its reasoning, only its UI
> consequences). The backend's Brazil-only structured fields (`cpf`,
> `cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/`estado`/
> `pais`) are replaced by a single, W3C/libphonenumber-style,
> **100% country-agnostic** field set — same DTO shape and form
> structure for every country, only labels/masks/validation differ by
> the selected country. Concretely, for this frontend SPEC:
>
> 1. **`cpf` → `taxId` everywhere in this SPEC** (form field, DTO
>    property, profile-section display, edit-request payload/diff
>    display). Label is now **country-driven** — "CPF" when
>    `countryCode === 'BR'`, "SSN" for `'US'`, "NINO" for `'GB'`, etc. —
>    rather than a fixed "CPF" label. See the backend SPEC's amendment
>    for the field-naming justification (`Tenant.taxId` precedent).
> 2. **The structured-address block is replaced**, not extended:
>    `cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/
>    `estado`/`pais` become `addressLine1`/`addressLine2` (optional)/
>    `city`/`stateRegion` (optional)/`postalCode`/`countryCode`. Field
>    *labels* are country-driven (e.g. `postalCode` reads "CEP" for
>    `BR`, "ZIP Code" for `US`, "Postcode" for `GB"); the fields
>    themselves — which ones render, in what order — do **not** change
>    by country, only their labels/masks/validation do.
> 3. **A new `countryCode` selector** drives the label/mask/validation of
>    `taxId` and the address block. This is a genuinely new form control
>    — `user-profile`/the pre-amendment `user-profile-v2` had no country
>    concept at the user level (unlike the existing tenant-creation form,
>    which already has one).
> 4. **Phone/WhatsApp contact entries gain a country-code (DDI)
>    component**, libphonenumber-style. Per the backend SPEC's REQ-3c,
>    this is encoded as part of `value` in E.164 format
>    (`+5511912345678`), not a separate DTO field — the form control for
>    a `PHONE`/`WHATSAPP` contact becomes a country/DDI selector (or
>    prefix input) paired with the national-number input, composed
>    client-side into one E.164 string before it's placed in
>    `contacts[].value`. This mirrors REQ-3c's flagged judgment call
>    (Judgment call 7 in the backend SPEC) — PLAN.md may revisit this if
>    a two-field DTO proves easier to validate/mask.
>
> Every requirement, DTO-shape reference, and acceptance criterion below
> that mentions `cpf` or the old Brazil-only address field names is
> struck through and marked **~~(superseded 2026-08-02 — see
> country-agnostic identity/address model amendment)~~**, per this
> repo's "collected, then removed" traceability discipline — not
> silently rewritten in place. The mask-as-you-type amendment further
> below (REQ-21/22/23) is **not reversed** by this amendment — it now
> applies to `taxId`'s BR-specific mask (unchanged, just renamed) plus,
> newly, a **country-driven** mask choice: a `taxId`/`postalCode` mask is
> only applied when `countryCode` has a known mask defined for it (e.g.
> `BR`); for any other country, the field renders as a plain, unmasked
> text input, still readable/typo-catchable on its own since no
> country-agnostic universal punctuation convention exists.
>
> **This is flagged as a genuine schema-shaped decision, not a pure
> screen-composition judgment call** — same caution level this SPEC
> already applies to the RG/birth_date removals below. Unlike a plain
> rename, the address restructuring is **lossy** on the backend side (see
> that SPEC's migration note); this frontend SPEC does not need its own
> migration story (the frontend has no persisted state of its own), but
> its form must render/submit exactly what the retrofitted backend DTOs
> now define — this SPEC's acceptance criteria are updated accordingly.

> **Amendment (2026-08-02, product owner decision — RG removal):** per
> `identity-profile-model-v2/SPEC.md`'s own 2026-08-02 RG-removal
> amendment (LGPD data-minimization), `rg`/`rgOrgaoEmissor` are removed
> entirely from this SPEC — no field, no input, no display anywhere.
> This makes the masking amendment directly below moot for RG
> specifically: REQ-21's "RG is masked only insofar as a reasonable
> display grouping can be applied... a best-effort digit-grouping mask is
> acceptable" clause no longer applies to anything, since there is no
> `rg` field left to mask. Struck-through text below is marked
> **~~(superseded 2026-08-02 — RG removed)~~** rather than deleted, per
> this repo's "collected, then removed" traceability discipline. CPF/CEP/
> phone masking (the rest of REQ-21) is entirely unaffected. **(`cpf`/
> `cep` themselves are further superseded 2026-08-02, same day, third
> amendment — see the country-agnostic identity/address model amendment
> at the very top of this SPEC, now `taxId`/`postalCode`.)**

> **Amendment (2026-08-02, product owner decision, direct instruction —
> birth_date removal):** in the same live conversation as the RG
> decision above, the product owner directly instructed: "tira data de
> nascimento também" (take out birth date too), per
> `identity-profile-model-v2/SPEC.md`'s own companion amendment (same
> date, same reasoning). `birthDate` is removed entirely from this
> SPEC — no field, no input, no display anywhere, in the own-profile
> screen, the other-user detail-panel section, or any DTO reference.
> This reverses this SPEC's own earlier framing of `birthDate` as one of
> the fields that "now requires the self-edit-request flow, with no
> exception for `MEMBER_ADMIN`/`STAFF_ADMIN`" (see Context and
> motivation below) — that framing no longer applies because the field
> no longer exists at all, not because its edit rule changed again.
> Struck-through text below is marked **~~(superseded 2026-08-02 —
> birth_date removed)~~**, same traceability discipline as RG.

> **Amendment (2026-08-02, product owner decision):** the "no masked
> input" line in this SPEC's original "Out of scope"/non-functional
> requirements is reversed for display-only mask-as-you-type formatting
> on ~~`rg`,~~ `cpf`, `cep`, and phone-type contact values — see new REQ-21
> below. The value **submitted** to the backend is unaffected: this
> remains plain/unmasked (digits only, or whatever shape the DTO already
> documents) exactly as before — only what's rendered in the input while
> typing changes. This does **not** reopen client-side CPF~~/RG~~
> format/checksum *validation* — that stays out of scope here; checksum
> validation now happens server-side only, per
> `identity-profile-model-v2/SPEC.md`'s companion amendment. CEP-to-address
> autofill/lookup is also still explicitly out of scope — masking a CEP
> field's display is not the same as looking up an address from it.
> **(RG struck throughout this amendment 2026-08-02, same day, second
> amendment — see amendment above; RG no longer exists as a field.)**
> `birthDate` was never a masked field, so it is unaffected by this
> particular amendment — its removal is covered entirely by the
> birth_date-removal amendment above. **(`cpf`/`cep` superseded
> 2026-08-02, same day, third amendment — see country-agnostic
> identity/address model amendment at the very top of this SPEC: now
> `taxId`/`postalCode`, mask now country-conditional rather than
> always-Brazilian.)**

## Context and motivation

The already-shipped frontend feature `user-profile`
(`knowly-app/specify/features/user-profile/{SPEC,PLAN,TASKS}.md`, 321/321
tests green, committed) was built against `identity-profile-model`'s
**old flat contract** (`fullName`/`address` free-text string/`rg`/`cpf`/
`phone` on `GET/PUT /api/users/{id}/profile`). That backend contract has
since been redesigned — see `knowly-api/specify/features/
identity-profile-model-v2/{SPEC,PLAN}.md` and `DECISIONS.md`'s
"`identity-profile-model` retrofit" entry — into three tables
(`user_profiles`/`addresses`/`contacts`), a smaller LGPD-minimized field
set (now further narrowed by the 2026-08-02 RG-removal and
birth_date-removal amendments above, and further restructured into a
country-agnostic shape by the 2026-08-02 third amendment above), and a
materially different per-field permission model (`avatar_url` is now the
*only* directly self-editable field; everything else, ~~including
`birth_date`,~~ requires the self-edit-request flow, with no exception
for `MEMBER_ADMIN`/`STAFF_ADMIN` editing their own record — a real
capability removal from what today's shipped frontend currently
assumes). **(`birth_date` reference struck 2026-08-02 — birth_date
removed entirely, see amendment above; it is no longer a field this
permission model applies to at all.)**

The product owner confirmed (2026-07-28) this redesign proceeds and that
the already-shipped frontend must be retrofitted to match, not
deferred. This SPEC is that retrofit. It supersedes `user-profile`'s
SPEC for every requirement whose backend contract changed; requirements
whose UI behavior doesn't depend on the changed fields (e.g. the
edit-request inbox's approve/reject mechanics, the "hidden, not
shown-then-blocked" nav rule) are restated here unchanged so this SPEC
is independently complete.

This SPEC does not re-derive any backend rule — every permission name
and who-can-view/edit-whom rule is carried over verbatim from
`identity-profile-model-v2/SPEC.md`. Only UI layout, route/component
naming, and screen composition are this SPEC's own judgment calls.

**Backend contract carried over (read-only reference, not re-litigated
here — see `identity-profile-model-v2/PLAN.md` for the authoritative
DTO shapes):**

| Endpoint | Purpose |
|---|---|
| `GET /api/users/me/profile` | Caller's own full profile |
| `GET /api/users/{id}/profile` | Another user's profile, permission-gated |
| `PUT /api/users/{id}/profile` | Direct edit of non-avatar fields (admin/permission-holder-on-*other*-user only — never self) |
| `POST /api/users/me/profile/edit-requests` | Self-submitted edit request for any non-avatar field |
| `POST /api/users/me/profile/avatar` | Self, unconditional avatar upload (multipart) |
| `GET /api/profile-edit-requests` | List pending requests the caller may act on |
| `POST /api/profile-edit-requests/{id}/approve` | Approve, applies fields + contact changes |
| `POST /api/profile-edit-requests/{id}/reject` | Reject, discards proposed changes |

Fields, now: `fullName`, ~~`cpf`~~`taxId`, ~~`rg`, `rgOrgaoEmissor`,~~ ~~`birthDate`,~~
~~`address` (structured: `cep`/`logradouro`/`numero`/`complemento`/
`bairro`/`cidade`/`estado`/`pais`)~~ `address` (structured, country-agnostic:
`addressLine1`/`addressLine2` (optional)/`city`/`stateRegion`
(optional)/`postalCode`/`countryCode`), `countryCode` (new, at the
`user_profiles` level, drives `taxId`'s and `address`'s label/mask/
validation), `contacts` (list of `{type, value, label, isPrimary}`, up
to 5, `type` one of `PHONE`/`WHATSAPP`/`EMAIL`/`OTHER`; `PHONE`/
`WHATSAPP` `value` is E.164, DDI encoded as its leading `+<country
code>`) — all request-only, never direct-self-edit. `avatarUrl` —
self-edit, dedicated upload endpoint, never part of the request/
edit-request DTOs. `email` — read-only everywhere, unchanged.
**(`rg`/`rgOrgaoEmissor` struck 2026-08-02 — RG removed entirely, see
amendment above. `birthDate` struck 2026-08-02 — birth_date removed
entirely, see amendment above. `cpf`/old address column names struck
2026-08-02, same day, third amendment — see country-agnostic
identity/address model amendment at top of this SPEC.)**

**Who can directly edit whose non-avatar fields (carried over exactly):**
- `MEMBER_ADMIN` of a tenant: any *other* member of that tenant — no
  longer including self (this is the behavior change from `user-profile`).
- `STAFF_ADMIN`: any *other* user — no longer including self.
- Tenant-scoped `PROFILE_EDIT` holder (not `MEMBER_ADMIN`): any *other*
  member of that tenant, never self (unchanged from `user-profile`).
- Global-scoped `PROFILE_EDIT` holder (`STAFF`, not `STAFF_ADMIN`): any
  *other* user, never self (unchanged).
- **Anyone, including admins, editing their own non-avatar fields**:
  always submits a pending edit request (no direct-edit branch exists
  for self anymore, for anyone).
- **Anyone editing their own `avatarUrl`**: always direct, unconditional,
  no approval step, regardless of role/permission.

**Who can view whose record (unchanged from `user-profile`):** self
always; tenant-scoped `PROFILE_VIEW` holder → any member of that tenant;
global-scoped `PROFILE_VIEW` holder → any user; `MEMBER_ADMIN` → any
member of their tenant; `STAFF_ADMIN` → any user.

## User stories

- As any user, I want to change my own avatar immediately, without
  anyone else's approval.
- As any user — even a `MEMBER_ADMIN`/`STAFF_ADMIN` — I want my
  submission of any other profile field (name, tax id, ~~birth date,~~
  address, contacts) to become a pending request, and to understand
  clearly that this is now true for me too, not just for lower-privilege
  users. **(RG struck from this field list 2026-08-02 — RG removed
  entirely. `birth date` struck 2026-08-02 — birth_date removed
  entirely, no longer a field to submit at all. "CPF" superseded
  2026-08-02, third amendment — now "tax id.")**
- As a user with no pending request, I want to enter a structured
  address (~~CEP, street, number, etc.~~ address line 1/2, city, state or
  region, postal code) instead of one free-text field. **(Superseded
  2026-08-02, third amendment — the address block is now
  country-agnostic, not CEP/street/number-specific.)**
- As a user, I want to manage a list of up to 5 contacts (phone,
  WhatsApp, email, other), mark one as primary per type, and remove ones
  I no longer use.
- As a holder of the applicable edit right, I want to approve or reject
  a pending request that now includes contact add/update/remove entries,
  not just flat field values.
- As a returning user of the already-shipped screens, I don't want to
  relearn a different app — the inbox, nav gating, section placement,
  and error-handling shapes stay the same; only the form fields and the
  self-edit-vs-request boundary change.
- **As a user typing my ~~CPF~~tax id, ~~CEP~~postal code, or phone number,
  I want to see it formatted with the usual punctuation as I type where
  my country has a known convention (e.g. `123.456.789-00`,
  `(11) 91234-5678` for Brazil)**, so the input is readable and I can
  catch typos, even though what's actually submitted behind the scenes
  stays plain. **(RG struck from this story 2026-08-02 — RG removed
  entirely. "CPF"/"CEP" superseded 2026-08-02, third amendment — now
  "tax id"/"postal code," mask now country-conditional rather than
  always-Brazilian.)**
- **(Added 2026-08-02, third amendment)** As a user from any country, I
  want to select my country and see the tax-id/address/phone fields
  labeled and masked appropriately for that country (or plainly, if no
  specific convention is known), rather than being shown Brazil-specific
  labels regardless of where I'm from.

## Requirements (EARS/GEARS)

### Own profile — view and edit

- **REQ-1 [Ubiquitous]** The own-profile screen shall show `fullName`,
  ~~`cpf`~~`taxId`, ~~`rg`, `rgOrgaoEmissor`,~~ ~~`birthDate`,~~ ~~a structured
  address (`cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/
  `estado`/`pais`)~~ a `countryCode` selector, a structured address
  (`addressLine1`/`addressLine2` (optional)/`city`/`stateRegion`
  (optional)/`postalCode`), and a list of up to 5 contacts
  (`type`/`value`/`label`/`isPrimary`, `PHONE`/`WHATSAPP` entries
  showing a country/DDI selector alongside the national number) — all
  editable only via the pending-request flow — plus `email` (read-only)
  and `avatarUrl` (directly editable, see REQ-8). **(`rg`/`rgOrgaoEmissor`
  struck 2026-08-02 — RG removed entirely. `birthDate` struck 2026-08-02
  — birth_date removed entirely. `cpf`/old address column names
  superseded 2026-08-02, third amendment — see country-agnostic
  identity/address model amendment at top of this SPEC; `countryCode`
  and the phone-DDI selector are new controls, not renames.)**
- **REQ-1a [Ubiquitous] (added 2026-08-02, third amendment)** The
  `taxId` field's label, and the `postalCode`/`addressLine1`/
  `addressLine2`/`city`/`stateRegion` labels, shall be derived from the
  currently-selected `countryCode` (e.g. `taxId` reads "CPF" for `BR`,
  "SSN" for `US`, "NINO" for `GB"; `postalCode` reads "CEP"/"ZIP
  Code"/"Postcode" respectively). Where no specific label mapping is
  defined for a given `countryCode`, the field shall fall back to a
  generic label (e.g. "Tax ID," "Postal Code") rather than showing a
  blank or incorrect country-specific one. The exact label/mask mapping
  table (which countries get bespoke labels vs. the generic fallback) is
  left to PLAN.md, not enumerated here.
- **REQ-2 [Event-Driven]** When the caller submits the own-profile
  form's non-avatar fields, the system shall always call `POST
  /api/users/me/profile/edit-requests`, regardless of the caller's
  role or permissions — there is no direct-edit branch for a caller's
  own non-avatar fields, for anyone. **This replaces `user-profile`'s
  old REQ-2/REQ-3 branching logic** (which called `PUT` directly for
  `MEMBER_ADMIN`/`STAFF_ADMIN`) with a single unconditional path.
- **REQ-3 [State-Driven]** While the caller has an unresolved
  profile-edit request pending, the own-profile screen shall show a
  visible "pending approval" state and disable resubmission of the
  non-avatar form. (Unchanged shape from `user-profile` REQ-4, still
  client-side-only per Judgment call 3 below.)
- **REQ-4 [Unwanted Behavior]** If `POST
  /api/users/me/profile/edit-requests` is rejected with 409 (an
  unresolved request already exists), then the system shall show a
  clear, non-technical "already pending" message and enter the REQ-3
  state. (Unchanged from `user-profile` REQ-5.)
- **REQ-5 [Ubiquitous]** The own-profile screen shall never render an
  input for `email`, and shall never submit it in the edit-request
  payload. (Unchanged from `user-profile` REQ-7.)
- **REQ-6 [Ubiquitous]** The contacts section of the form shall let the
  caller add, edit, and remove entries up to a maximum of 5, select a
  `type` from the four supported values, and mark at most one entry per
  `type` as primary.
- **REQ-6a [Ubiquitous] (added 2026-08-02, third amendment)** Where a
  contact entry's `type` is `PHONE` or `WHATSAPP`, the form shall render
  a country/DDI selector (or prefix input) alongside the national-number
  input, and shall compose the two into a single E.164-format string
  (`+<country code><national number>`) as the entry's `value` before
  submission — never submitting the DDI and national number as separate
  values in the payload (per the backend SPEC's REQ-3c, `contacts` has
  no separate DDI column). The reverse (splitting a stored E.164 `value`
  back into its DDI/national-number parts for display/editing) shall
  also be handled, so an existing contact can be re-edited without
  showing the raw `+55...` string as a single opaque field.
- **REQ-7 [Unwanted Behavior]** If the caller attempts to add a 6th
  contact, then the system shall prevent the addition client-side with a
  clear message before ever calling the backend, matching the backend's
  own REQ-3a cap.

### Own avatar — direct, unconditional edit

- **REQ-8 [Event-Driven]** When the caller selects a new avatar image
  and confirms, the system shall call `POST
  /api/users/me/profile/avatar` (multipart) immediately — no pending
  state, no approval step, regardless of role or permission — and
  update the displayed avatar on success.
- **REQ-9 [Unwanted Behavior]** If the avatar upload is rejected (400,
  unsupported type or too large), then the system shall show a clear,
  non-technical message and leave the previous avatar displayed.

### Viewing/editing another user's profile

- **REQ-10 [Optional Feature]** Where the viewer holds an applicable
  view right over a given user, the tenant members list's/staff
  directory's existing detail panel for that user shall show a profile
  section with the new field set (`fullName`/~~`cpf`~~`taxId`/~~`rg`/
  `rgOrgaoEmissor`/~~~~`birthDate`/~~`countryCode`/structured address (new
  shape)/contacts/`email`), sourced from `GET /api/users/{id}/profile`.
  `avatarUrl` is displayed here too (read-only — REQ-8 is self-only, so
  no other viewer can edit someone else's avatar). **(`rg`/
  `rgOrgaoEmissor` struck 2026-08-02 — RG removed entirely. `birthDate`
  struck 2026-08-02 — birth_date removed entirely. `cpf`/old address
  shape superseded 2026-08-02, third amendment.)**
- **REQ-11 [Unwanted Behavior]** If `GET /api/users/{id}/profile` is
  rejected with 403 for a given detail panel, then only that panel's
  profile section shall show the existing non-technical permission-
  denied state, without affecting any other section on that panel.
  (Unchanged from `user-profile` REQ-9.)
- **REQ-12 [Optional Feature]** Where the viewer additionally holds an
  applicable direct-edit right over that same *other* user (per
  `identity-profile-model-v2`'s REQ-12/13), the profile section shall
  let them edit that other user's non-avatar fields inline and call
  `PUT /api/users/{id}/profile` on submit, refreshing the section on
  success. **This inline edit affordance shall never appear when the
  viewer is looking at their own row** — even a `MEMBER_ADMIN`/
  `STAFF_ADMIN` viewing themself in the same list they manage others
  from sees the section in view-only mode, matching REQ-2's
  "no self-direct-edit for anyone" rule.
- **REQ-13 [Unwanted Behavior]** If that direct-edit call (REQ-12) is
  rejected with 409 (uniqueness conflict on ~~`cpf`~~`taxId`), then the
  profile section shall show a clear, non-technical message naming the
  conflicting field(s) without exposing the other user's data.
  (Unchanged shape from `user-profile` REQ-11; `rg` struck from the
  conflict-field example 2026-08-02 — RG removed entirely, no `rg`
  uniqueness constraint exists any more. `cpf` superseded 2026-08-02,
  third amendment — renamed `taxId`.)

### Edit-request approval inbox

- **REQ-14 [Ubiquitous]** The edit-request inbox shall list every
  pending request the caller may act on, sourced from `GET
  /api/profile-edit-requests`, showing per request: requester identity,
  the proposed field values (including the structured address, in its
  new country-agnostic shape, and `countryCode`), the proposed contact
  changes (add/update/remove, per entry — `PHONE`/`WHATSAPP` entries
  showing the composed E.164 value in a readable, DDI-plus-number
  display, not a raw `+55...` string), and submission date. (Extends
  `user-profile` REQ-12 with the new contact-changes display.
  **Updated 2026-08-02, third amendment** for the new address shape and
  E.164 contact display.)
- **REQ-15 [Event-Driven]** When the caller approves a request, the
  system shall call `POST /api/profile-edit-requests/{id}/approve` and
  remove that request from the visible list on success. (Unchanged.)
- **REQ-16 [Event-Driven]** When the caller rejects a request, the
  system shall call `POST /api/profile-edit-requests/{id}/reject` and
  remove that request from the visible list on success. (Unchanged.)
- **REQ-17 [Unwanted Behavior]** If an approve call is rejected with 409
  (a ~~`cpf`~~`taxId` uniqueness conflict), then the system shall show a
  clear, non-technical message naming the conflicting field(s), leave
  the request visible and still pending, and not remove it from the
  list. (Unchanged shape from `user-profile` REQ-15; `rg` struck
  2026-08-02 — RG removed entirely. `cpf` superseded 2026-08-02, third
  amendment — renamed `taxId`.)
- **REQ-18 [Unwanted Behavior]** If an approve or reject call is
  rejected with 403 or a non-conflict 409 (already resolved/cancelled by
  someone else, or by the migration per `identity-profile-model-v2`
  REQ-25), then the system shall show the existing non-technical
  error/permission-denied state and refresh the list so the stale
  request no longer appears. (Unchanged shape from `user-profile`
  REQ-16.)
- **REQ-19 [Unwanted Behavior]** If the caller holds no applicable edit
  right anywhere, then the inbox's entry point (nav link and/or route)
  shall not be shown. (Unchanged from `user-profile` REQ-17.)
- **REQ-20 [Ubiquitous]** The edit-request inbox shall show an explicit
  empty state when the list is empty, distinct from loading or error.
  (Unchanged from `user-profile` REQ-18.)

### Masked input — display-only (added 2026-08-02, product owner decision)

- **REQ-21 [Ubiquitous]** The own-profile form's ~~`cpf`, `cep`~~`taxId`,
  `postalCode` (within the structured address), and any contact entry
  whose `type` is `PHONE`/`WHATSAPP` shall apply mask-as-you-type display
  formatting **where the currently-selected `countryCode` has a known
  mask defined** (e.g. Brazilian punctuation: `taxId` `000.000.000-00`,
  `postalCode` `00000-000`, phone `(00) 00000-0000`/`(00) 0000-0000`
  when `countryCode === 'BR'`) as the caller types, in every place these
  fields are editable — the own-profile form, the other-user inline edit
  form (REQ-12), and any other reuse of `ProfileFieldsFormComponent`.
  **Where `countryCode` has no known mask defined, the field shall render
  as a plain, unmasked text input** — this is new behavior as of the
  2026-08-02 third amendment (the pre-amendment version of this
  requirement assumed Brazilian formatting unconditionally). ~~`rg` is
  masked only insofar as a reasonable display grouping can be applied
  consistently (no standardized national format exists for RG the way
  CPF has one — see amendment note above and `identity-profile-model-v2`'s
  companion amendment for the same caveat); a best-effort digit-grouping
  mask is acceptable, it need not represent a "correct" RG shape the way
  the CPF/CEP/phone masks do.~~ **(superseded 2026-08-02, same day,
  second amendment — RG removed entirely; there is no `rg` field left to
  mask, this clause is moot.)** **(`cpf`/`cep` superseded 2026-08-02,
  same day, third amendment — now `taxId`/`postalCode`; mask is now
  country-conditional, not unconditionally Brazilian; the exact
  per-country mask table is left to PLAN.md, see REQ-1a.)**
- **REQ-22 [Ubiquitous]** Regardless of the mask applied on display, the
  value submitted in the `POST/PUT` request body for these fields shall
  remain exactly what `identity-profile-model-v2`'s DTOs already
  document (unmasked/plain, or E.164 for phone-type contacts per
  REQ-6a) — masking is a rendering concern only and shall never change
  what's sent over the wire. (This is *not* a frontend validation change
  — see REQ-23.)
- **REQ-23 [Ubiquitous]** This masking behavior is display formatting
  only and does **not** constitute client-side ~~CPF~~tax-id format or
  checksum validation — the form shall not block submission or show a
  validation error based on the mask failing to fully populate or the
  value failing a checksum; that check happens server-side only, and
  only where `country_code == "BR"` (see
  `identity-profile-model-v2/SPEC.md`'s companion amendment). **(`RG`
  struck from this requirement's title concern 2026-08-02 — RG removed
  entirely, no RG field exists to validate or not-validate. "CPF"
  superseded 2026-08-02, third amendment — now "tax-id," and the
  server-side check is now country-conditional.)**

## Non-functional requirements

- Design: follows "Ink & Signal," reuses existing shared components — no
  new component library, no new dependency. (Unchanged.)
- Accessibility: the own-profile form (including the new structured
  address fields, `countryCode` selector, and contacts list add/remove/
  edit controls, including the phone/WhatsApp DDI selector), the inline
  edit form on another user's profile section, the avatar upload
  control, and the inbox's approve/reject actions are all fully
  keyboard-operable with clear focus states. (Extends `user-profile`'s
  bar to the new contacts/address UI; **updated 2026-08-02, third
  amendment** to explicitly cover the new `countryCode`/DDI selectors.)
  Masking (REQ-21) must not break screen-reader announcement of the
  field's value or keyboard editing (e.g. caret position after a mask
  character is inserted/removed) — no new dependency is introduced
  solely to satisfy this; if the team's existing hand-rolled approach
  can't cleanly guarantee this, flag it before PLAN.md, don't silently
  ship a regression.
- Security: this SPEC is never the real authorization boundary — every
  underlying call is independently re-enforced server-side. (Unchanged.)
- Security: ~~`cpf`~~`taxId` values are never logged to the browser console
  and never included in any client-side error message beyond "this
  field conflicts." (Unchanged — applies equally to the masked display
  value, which is still `taxId` data. `rg` struck 2026-08-02 — RG
  removed entirely. `cpf` superseded 2026-08-02, third amendment —
  renamed `taxId`.)
- No pagination on the edit-request inbox. (Unchanged.)
- **No ~~CPF/CEP~~tax-id/postal-code/phone format or checksum
  *validation* on the frontend** — masking (REQ-21/22/23) is
  display-only and does not imply or require validation; the frontend
  never blocks submission based on checksum correctness. **Amended
  2026-08-02:** masked *input* is no longer out of scope (see
  REQ-21/22/23) — only the earlier "no masked input" line is reversed;
  "no format/checksum validation" and "no CEP-to-address lookup/autofill"
  both still stand exactly as originally scoped. Plain text inputs
  (behind the mask) are still what's rendered and edited — the mask
  changes *display* formatting only, not the underlying input mechanism.
  **(RG struck 2026-08-02, same day, second amendment — RG removed
  entirely, this line previously read "CPF/RG format or checksum
  validation." "CPF"/"CEP" superseded 2026-08-02, same day, third
  amendment — now "tax-id"/"postal-code," and no country-agnostic
  autofill/lookup is introduced by this amendment either.)**

## Acceptance criteria

- [ ] A user can view their own profile with the new field set;
      `email` read-only, `avatarUrl` directly editable, everything else
      request-only.
- [ ] Submitting the own-profile non-avatar form always creates a
      pending request, for every role including `MEMBER_ADMIN`/
      `STAFF_ADMIN` — no session ever sees an immediate-apply path for
      those fields anymore.
- [ ] Avatar upload applies immediately for every session, with no
      pending state.
- [ ] A second non-avatar submission while one is pending is prevented
      client-side and shows the "already pending" message on 409.
- [ ] The contacts UI enforces the 5-entry cap client-side and supports
      per-type primary selection.
- [ ] A viewer with an applicable view right sees the new profile
      section (including structured address, contacts, read-only
      avatar) on the relevant detail panel; without it, sees a
      section-scoped permission-denied state.
- [ ] A viewer with an applicable edit right over an *other* user can
      edit that user's non-avatar fields inline; the same viewer never
      sees an edit affordance on their own row in that same list.
- [ ] The edit-request inbox lists every pending request including its
      contact changes; approve/reject work as before; a uniqueness
      conflict on approve keeps the row and shows a clear message; a
      stale/403 result refreshes the list.
- [ ] A caller with no applicable edit right anywhere does not see the
      inbox's nav link/route.
- [ ] An empty inbox shows the distinct empty state.
- [ ] `email` is never rendered as editable or submitted in any payload.
- [ ] Typing into the `taxId`, `postalCode`, and phone-type contact
      fields shows standard mask punctuation live **when the selected
      `countryCode` has a known mask** (e.g. `BR`); for a `countryCode`
      with no known mask, the field renders and behaves as a plain
      unmasked input. The submitted payload for all of these fields is
      unmasked/plain (or E.164 for phone-type contacts) in every case,
      verified by inspecting the actual request body, not just the
      displayed value. **(The former "`rg` field shows a best-effort
      digit grouping" clause is struck 2026-08-02 — RG removed entirely,
      no `rg` field or input exists. "CPF"/"CEP" superseded 2026-08-02,
      third amendment — now `taxId`/`postalCode`, mask now
      country-conditional.)**
- [ ] Masking never blocks submission and never triggers a client-side
      validation error on its own (checksum/format correctness is not
      checked client-side).
- [ ] No `rg`/`rgOrgaoEmissor` field, input, or display exists anywhere
      in this feature's screens. (Added 2026-08-02.)
- [ ] No `birthDate` field, input, or display exists anywhere in this
      feature's screens. (Added 2026-08-02.)
- [ ] Selecting a different `countryCode` updates the `taxId`/
      `postalCode`/address-block labels and mask behavior live, without
      requiring a page reload. **(Added 2026-08-02, third amendment.)**
- [ ] A `PHONE`/`WHATSAPP` contact entry's country/DDI selector and
      national-number input are composed into a single E.164 `value` on
      submit, and an existing E.164 `value` is correctly split back into
      its DDI/national-number parts for display/re-editing. **(Added
      2026-08-02, third amendment.)**
- [ ] No `cpf`/old Brazil-only address field name (`cep`/`logradouro`/
      `numero`/`complemento`/`bairro`/`cidade`/`estado`/`pais`) is
      rendered, submitted, or referenced anywhere in this feature's
      screens or DTOs. **(Added 2026-08-02, third amendment.)**
- [ ] `npm run format:check && npm test && npm run build` all pass.

## Out of scope

- Everything already out of scope for `user-profile` and unaffected by
  the field-set change: chat display nickname, role/permission
  management UI, a generic in-app notification inbox, audit-trail
  viewing, **client-side** tax-id/postal-code/phone format/checksum
  validation, bulk approve/reject, pagination of the inbox, any new
  backend endpoint beyond what `identity-profile-model-v2` already
  defines. **(Amended 2026-08-02: masked *input display* is no longer
  out of scope — see REQ-21/22/23 above. Only client-side format/
  checksum *validation* remains excluded; validation is a backend-only
  concern now, per `identity-profile-model-v2/SPEC.md`'s companion
  amendment. `RG` struck from this line's field list 2026-08-02, same
  day, second amendment — RG removed entirely. "CPF/CEP" superseded
  2026-08-02, same day, third amendment — now "tax-id/postal-code.")**
- **Postal-code-to-address autofill/lookup** (a real third-party
  integration, not requested, not part of `identity-profile-model-v2`'s
  own scope either). Unaffected by the masking amendment — masking a
  postal-code field's *display* is not the same as looking up an address
  from it. **(Renamed from "CEP-to-address" 2026-08-02, third amendment
  — same exclusion, country-agnostic field name.)**
- **Multiple addresses per user** — backend confirms a single current
  address only; no UI for "add a second address" exists or is implied.
- **Avatar image cropping/editing UI** — a raw file picker/upload only;
  no client-side crop/resize tool.
- **`social_name`/`gender`/`nationality`~~/`rg`/`rgOrgaoEmissor`~~
  fields** — confirmed cut from the data model entirely; no UI renders
  them. **(`rg`/`rgOrgaoEmissor` added to this line 2026-08-02, product
  owner decision — LGPD data-minimization; see amendment at top of this
  SPEC. Previously these were mandatory fields under REQ-1/REQ-10;
  whether already-collected RG data is deleted outright is a
  data-handling mechanic for the `data-architect-dba` agent/PLAN.md, not
  decided here.)** **Note (2026-08-02, third amendment): `countryCode` is
  deliberately not the same thing as the excluded `nationality` field —
  see the identical note in the backend SPEC's REQ-5.**
- **`birthDate` as a field anywhere in this feature's screens** (added
  2026-08-02, product owner decision, direct instruction — LGPD
  data-minimization; see second amendment at top of this SPEC). This
  reverses this SPEC's earlier framing of `birthDate` as a
  request-only, non-`avatar_url` field subject to the self-edit-request
  flow — that framing is now moot, the field does not exist at all.
  Whether already-collected `birthDate` data is deleted outright is a
  data-handling mechanic for the `data-architect-dba` agent/PLAN.md, not
  decided here.
- **A dedicated `contacts.countryCode`/DDI DTO field** (added
  2026-08-02, third amendment) — the DDI is composed client-side into
  the E.164 `value` string (REQ-6a), not submitted as a separate field;
  introducing one is a backend-schema decision (see that SPEC's
  Judgment call 7), not assumed here.
- **A bespoke label/mask table covering every ISO country** (added
  2026-08-02, third amendment) — this SPEC requires the mechanism
  (country-driven label/mask, generic fallback where none is defined)
  but does not enumerate which specific countries get bespoke labels
  beyond Brazil; that table is a PLAN-level content decision, not
  product scope decided here.

## Judgment calls (Tier 2 — frontend-only; flag any of these before
PLAN.md work starts if they should instead be reconsidered)

1. **Route/component structure carried over unchanged from
   `user-profile`** (`/profile` own-profile, `/profile-edit-requests`
   inbox, `ProfileSectionComponent` embedded in the two existing detail
   panels) — the already-shipped structural decisions (SPEC judgment
   calls 1-3 in `user-profile/SPEC.md`) are about screen composition,
   not about the changed field set/permission model, so they're kept as
   the least-disruptive retrofit rather than re-litigated.
2. **`ProfileFieldsFormComponent` is retrofitted in place, not
   replaced**, to render the new field set (structured address block,
   contacts list editor) instead of the old flat `address`/`phone`
   inputs — still the single shared form used by both the own-profile
   screen and the other-user inline edit, per the same reuse rationale
   `user-profile`'s judgment call 5 already established. The component
   grows materially (address sub-fields + a repeatable contacts list),
   accepted as proportionate rather than splitting it, since "same
   fields, same validation, same conflict-handling copy" still holds
   for the *new* field set just as it did for the old one. **(Note,
   2026-08-02, third amendment: the component grows again to add the
   `countryCode` selector and the phone/WhatsApp DDI selector — still
   accepted as proportionate, not a reason to split the component.)**
3. **REQ-3's pending-state detection stays client-side-only, reactive to
   a 409** — same accepted rough edge `user-profile`'s judgment call 4
   already documented (no backend "do I have a pending request" read
   endpoint exists in `identity-profile-model-v2` either); not
   re-litigated, since nothing about the retrofit closes this gap.
4. **Avatar upload gets its own small, separate UI affordance** (e.g. an
   "change avatar" button/file input near the profile header) rather
   than being folded into `ProfileFieldsFormComponent`'s submit flow —
   because REQ-2/REQ-8 are now two structurally different submission
   paths (always-request vs. always-direct-multipart) for the *same*
   screen, conflating them into one form/one submit button would make
   that split invisible to the user exactly where clarity matters most
   (this is the field that behaves differently from every other field on
   the same screen).
5. **The other-user detail-panel inline edit affordance (REQ-12) is
   hidden, not merely disabled, when the viewer is looking at their own
   row** — chosen over `user-profile`'s previous behavior (which allowed
   an admin's own row to show the edit affordance) because REQ-2 removed
   that capability entirely at the backend; showing a disabled/
   would-be-rejected control for the viewer's own row would be confusing
   given every other row in the same list *does* show it. Determining
   "is this the viewer's own row" reuses the same own-`userId` value
   `OwnProfilePageComponent` already reads off `GET
   /api/users/me/profile`, fetched once and compared against each row's
   `userId` (already present on `MemberDto`/`MemberDetailDto`/staff
   directory entries per `user-profile`'s already-resolved deviation).
6. **The specific mask library/implementation approach for REQ-21 is
   left to PLAN.md** (e.g. a small hand-rolled directive vs. an existing
   dependency) — per this repo's "no new component library, no new
   dependency" default (`DECISIONS.md`'s PrimeNG-removal entry), prefer
   a hand-rolled mask directive/pipe consistent with the rest of this
   app's Tailwind-only, no-component-library convention; introducing a
   masking library would be a fresh Tier 3 dependency decision, not
   assumed here. **(Note, 2026-08-02, third amendment: this now also
   covers the country-driven mask *selection* logic, not just the mask
   mechanism itself — still a hand-rolled approach, no new dependency
   assumed.)**
7. **RG removal (2026-08-02) needs no new judgment call of its own** —
   it only removes one field/input from the form, the profile-section
   display, and REQ-21's masking scope; no other judgment call above
   (route structure, component reuse, pending-state detection, avatar
   affordance placement, hidden-vs-disabled inline edit, mask library
   choice) is affected by this narrowing.
8. **birth_date removal (2026-08-02, direct instruction, same day as the
   RG removal) needs no new judgment call of its own either** — same
   reasoning as judgment call 7: it only removes one non-masked field/
   input from the form and the profile-section display; no other
   judgment call above is affected.
9. **(Added 2026-08-02, third amendment) Whether the `countryCode`
   selector for `taxId`/address is a single shared control (one
   selection drives both) or two independent selectors (address country
   vs. tax-residence country) is left to PLAN.md**, mirroring the
   identical open question flagged in the backend SPEC's Judgment call
   6 — this SPEC does not mandate a UI shape for it, only that both
   `user_profiles.countryCode` and the address's `countryCode` end up
   populated correctly regardless of whether one or two controls produce
   them.
10. **(Added 2026-08-02, third amendment) The exact phone/DDI selector
    UI (a searchable country dropdown showing flag + calling code, a
    plain `+55` text prefix input, or something else) is left to
    PLAN.md** — this SPEC only requires that the composed result is a
    valid E.164 string and that editing an existing E.164 value
    correctly re-populates both the selector and the national-number
    input.
</content>
