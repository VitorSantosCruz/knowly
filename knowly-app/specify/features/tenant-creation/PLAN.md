# PLAN — Tenant creation (staff)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Consumes
> `knowly-api/specify/features/tenant-creation/PLAN.md` (now
> **FINALIZED** — this is the canonical source of the `POST
> /api/tenants` contract, see "Consumed API contracts" below), and, via
> that PLAN's own cross-references,
> `knowly-api/specify/features/mandatory-complete-profile/PLAN.md` and
> `knowly-api/specify/features/user-role-selection-at-creation/PLAN.md`
> for the shared `MandatoryProfileFieldsDto`/`MandatoryAddressDto`
> field names.

## Changelog

- **2026-08-02 — Amendment for REQ-7–REQ-21.** The original PLAN
  (component/route/guard/service shape) shipped and is unchanged in its
  mechanics. This amendment grows the form's field set, the request
  body, and the component's internal structure.
- **2026-08-02 — Coordination checkpoint resolved.** The prior version
  of this PLAN carried a "Coordination flag" with two open risks
  (English vs. Portuguese address field names; `rgOrgaoEmissor` vs.
  `rgOrgaoIssuer`) because no backend PLAN.md existed yet. The backend
  `tenant-creation` PLAN.md is now finalized and is the single source
  of truth for the contract. Both risks are resolved below; TASKS.md's
  task 13 (the blocking checkpoint) is closed by this update.

## Coordination checkpoint — resolved

1. **Address field names differ per address — each matches its own
   backend DTO exactly, confirmed by direct read of both backend
   PLANs.** The two address sub-sections on this form serialize to
   **two different field-name sets** even though they render
   identically via the same `AddressFieldsComponent`:
   - **Company address** → backend `AddressDto` → English:
     `postalCode`, `street`, `number`, `complement`, `neighborhood`,
     `city`, `state` (no `country` sub-field — company `country` is
     already a top-level `CreateTenantRequestDto` field, reused for
     both the fiscal jurisdiction and the address's country, per that
     PLAN's "Final field names" table note).
   - **First user's address** → `MandatoryAddressDto` → Portuguese:
     `cep`, `logradouro`, `numero`, `complemento`, `bairro`, `cidade`,
     `estado`, `pais`.
   `AddressFieldsComponent` itself stays field-name-agnostic (see
   "Architectural decisions" below) so this divergence is absorbed at
   the parent component's serialization boundary, not by forking the
   presentational component.
2. **`rgOrgaoEmissor` confirmed** — `mandatory-complete-profile/PLAN.md`'s
   `MandatoryProfileFieldsDto` uses `rgOrgaoEmissor`, matching this
   PLAN's prior assumption; `rgOrgaoIssuer` (SPEC REQ-12's alternate
   spelling) is confirmed to be prose-only, not a real field.

## Architectural decisions

- No `knowly-api/` change — this PLAN only consumes the now-finalized
  `POST /api/tenants` contract.
- `staffGuard`, the `/tenants/new` route, and the "create tenant" link on
  `select-tenant-page` are unchanged from the original PLAN.
- **`TenantCreatePageComponent` uses Angular Reactive Forms**
  (`FormGroup`/`FormBuilder`/`FormArray`) — see `DECISIONS.md`'s
  "`tenant-creation` (frontend): the long-form staff screen adopts
  Reactive Forms + two extracted address/contacts components" entry for
  the full reasoning (Tier 2, already recorded; not repeated here).
- **New reusable presentational component `AddressFieldsComponent`**
  (`shared/address-fields.component.ts`) — takes a `[formGroup]` input
  built by the parent with whatever field names that address instance
  needs, and a `fields` input describing which control names/labels/
  translation keys to render (8 rows: postal code, street, number,
  complement, neighborhood, city, state, and — company address only —
  no separate country control, since company country is a top-level
  field per the resolved checkpoint above). **Deliberately field-name-
  agnostic** (not hardcoded to `postalCode`/`street`/...) precisely
  because the two real instances on this form bind to differently-named
  `FormGroup`s (English for company, Portuguese for the first user, per
  the resolved checkpoint) — the component only needs each control's
  name to look it up in the bound `FormGroup`, it never needs to know
  the name in advance. This satisfies SPEC REQ-16 ("reuse presentation
  only, not data") without requiring the two backend shapes to match.
- **New reusable presentational component `ContactsListEditorComponent`**
  (`shared/contacts-list-editor.component.ts`) — a `[formArray]` input
  (`FormArray` of `{ type, value }` groups matching
  `MandatoryProfileFieldsDto.contacts`' `ContactDto` shape), add/remove-
  row buttons, starts with one empty row (REQ-13). Unchanged from the
  prior version of this PLAN.
- Role selector: a plain `<select>` bound to a `role` form control,
  default value `'MEMBER_ADMIN'` (REQ-18), options `MEMBER`/
  `MEMBER_ADMIN` — submitted as `role` at the top level of the request
  body, matching the backend PLAN's `CreateTenantRequestDto.role`
  (`MembershipRole`, optional, default `MEMBER_ADMIN` server-side too —
  the frontend default and backend default now agree, so an omitted
  `role` would behave identically either way; the form still always
  sends an explicit value since the control always has one).

## Components and routes

```
app.routes.ts
└── /tenants/new (unchanged route, staffGuard)   TenantCreatePageComponent
    ├── section: company identification
    │   └── AddressFieldsComponent (companyAddress FormGroup, English field names)
    ├── section: first user profile
    │   ├── AddressFieldsComponent (userAddress FormGroup, Portuguese field names)
    │   └── ContactsListEditorComponent (contacts FormArray)
    └── section: role selection (<select>, top-level form control)
```

- `select-tenant-page.component.ts`, `staff.guard.ts`: unchanged.
- `app.routes.ts`: no change beyond what's already wired (same route).

## Consumed API contracts

**Now authoritative** — this is the finalized
`knowly-api/specify/features/tenant-creation/PLAN.md` contract
(`CreateTenantRequestDto`), reusing
`mandatory-complete-profile/PLAN.md`'s `MandatoryProfileFieldsDto`/
`MandatoryAddressDto` verbatim for the first user's profile/address.

| Method | Path | Request | Response | Status codes handled here |
|---|---|---|---|---|
| POST | `/api/tenants` | `CreateTenantRequest` (below) | `200`, empty body (unchanged/not widened) → navigate to `/select-tenant` | 200 success; 400 (company or profile field invalid/missing — field-level mapped where identifiable, generic banner otherwise, REQ-9/REQ-14/REQ-15); 403 (non-staff/lacks `TENANT_CREATE`, defense in depth); 409 with a `taxId`-identifying body → field-level error on `taxId` (REQ-11); 409 with an `adminEmail`-identifying body → field-level error on the first-user email contact row; 409 otherwise → generic banner |
| GET | `/api/tenants` (`listAllTenants()`) | — | unchanged | reused unmodified by `staffGuard` |

Exact request body (matches backend `CreateTenantRequestDto` field for
field — see backend PLAN's "API contracts" section for the Java record):

```ts
interface CreateTenantRequest {
  name: string;
  legalName: string;
  taxId: string;
  country: string;
  contactEmail: string;
  contactPhone: string;
  address: {
    postalCode: string;
    street: string;
    number: string;
    complement: string | null; // optional
    neighborhood: string;
    city: string;
    state: string;
  }; // company address — backend AddressDto, English names, no `country` sub-field
  adminEmail: string; // first admin's login email (account identity, not profile)
  profile: {
    fullName: string;
    birthDate: string; // ISO yyyy-MM-dd
    cpf: string;
    rg: string;
    rgOrgaoEmissor: string;
    address: {
      cep: string;
      logradouro: string;
      numero: string;
      complemento: string | null; // optional
      bairro: string;
      cidade: string;
      estado: string;
      pais: string;
    }; // first user's address — backend MandatoryAddressDto, Portuguese names
    contacts: { type: 'EMAIL' | 'PHONE' | 'WHATSAPP' | 'OTHER'; value: string }[];
  };
  role: 'MEMBER' | 'MEMBER_ADMIN'; // optional server-side; always sent explicitly
}
```

**Confirmed field-by-field against the backend PLAN**: `name`,
`legalName`, `taxId`, `country`, `contactEmail`, `contactPhone`,
`address.{postalCode,street,number,complement,neighborhood,city,state}`
match the backend `CreateTenantRequestDto`/`AddressDto` records
verbatim (backend PLAN lines 349–370). `adminEmail`, `profile`, `role`
match the same record's remaining three fields verbatim. `profile`'s
inner shape (`fullName`, `birthDate`, `cpf`, `rg`, `rgOrgaoEmissor`,
`address` with Portuguese sub-fields, `contacts`) matches
`mandatory-complete-profile/PLAN.md`'s `MandatoryProfileFieldsDto`/
`MandatoryAddressDto` verbatim.

`ActiveTenantService.createTenant(request: CreateTenantRequest):
Observable<void>` replaces the original two-positional-arg signature
(`name`, `adminEmail`) with this single typed request object.

## State and data

- `TenantCreatePageComponent`: one top-level `FormGroup`
  (`FormBuilder.group(...)`) with:
  - flat controls: `name`, `legalName`, `taxId`, `country`,
    `contactEmail`, `contactPhone`, `adminEmail`, `role` (defaults to
    `'MEMBER_ADMIN'`, REQ-18).
  - nested `FormGroup` `companyAddress`: `postalCode`, `street`,
    `number`, `complement`, `neighborhood`, `city`, `state`.
  - nested `FormGroup` `userProfile`: `fullName`, `birthDate`, `cpf`,
    `rg`, `rgOrgaoEmissor`, plus its own nested `FormGroup`
    `userProfile.address`: `cep`, `logradouro`, `numero`,
    `complemento`, `bairro`, `cidade`, `estado`, `pais`; and its own
    `FormArray` `userProfile.contacts` (bound to
    `ContactsListEditorComponent`, starts with one empty row).
  - `submitting`/`errorMessage` signals, carried over unchanged.
  - On submit: builds `CreateTenantRequest` by reading each control
    group and assembling the nested `address`/`profile.address`/
    `profile.contacts` objects with the field names above (two distinct
    `toAddressPayload()`-style mapping calls — one English, one
    Portuguese — not a single shared mapper, since the two shapes
    genuinely differ).
  - On 400/409 with field-identifying error data: maps onto the
    matching `FormControl.setErrors(...)` (REQ-11, REQ-15); otherwise
    falls back to the generic banner (REQ-5).
- `ActiveTenantService.createTenant(request: CreateTenantRequest):
  Observable<void>` — same pattern as the original, now typed against
  the finalized payload above.
- On success: unchanged, navigate to `/select-tenant` (REQ-4).

## Dependencies

None new — `ReactiveFormsModule` ships with `@angular/forms`, already a
project dependency (see `DECISIONS.md` entry referenced above for the
Tier 2 pattern-adoption reasoning; not a Tier 3 dependency addition).

## Testing strategy

Original three test files (`staff.guard.spec.ts`,
`active-tenant.service.spec.ts`, `tenant-create-page.component.spec.ts`,
`select-tenant-page.component.spec.ts`) are retained; the following are
added/updated:

- `address-fields.component.spec.ts` (new): renders the address fields
  it's told to render, bound to whatever control names its input
  `FormGroup` has (test with both an English-named and a
  Portuguese-named `FormGroup` to prove field-name-agnosticism); shows
  a field-level error when a bound control is invalid and touched; two
  independent instances never share state.
- `contacts-list-editor.component.spec.ts` (new): starts with one empty
  row (REQ-13); add-row appends a control pair; remove-row removes it;
  submitting with zero rows surfaces a "must have at least one" error
  state (REQ-14).
- `tenant-create-page.component.spec.ts` (updated):
  - renders all three sections with headings (REQ-20, REQ-21).
  - submit blocked with any REQ-8 company field missing, or malformed
    `contactEmail` — field-level errors shown, no API call (REQ-9).
  - submit blocked with any REQ-13 first-user field missing, or zero
    contacts — field-level errors shown, no API call (REQ-14).
  - non-Brazil `country` + non-empty `taxId` of any shape does not block
    submit; Brazil `country` + non-14-digit `taxId` blocks submit with a
    field-level error on `taxId`, with/without punctuation accepted
    (REQ-10).
  - role selector defaults to `MEMBER_ADMIN`, can be switched to
    `MEMBER`, submitted value flows into the request body's `role`
    (REQ-17–19).
  - successful submit calls `createTenant` with the **exact**
    `CreateTenantRequest` shape above (asserted field by field,
    including the company address's English keys and the user address's
    Portuguese keys in the same payload) and navigates to
    `/select-tenant` (REQ-4).
  - a 409 response shaped as a `taxId` conflict sets the `taxId`
    control's error and preserves all other entered values (REQ-11); a
    400 response shaped as first-user-profile-incomplete maps onto the
    matching first-user field(s) where identifiable, falls back to the
    generic banner otherwise (REQ-15); any other error shows the
    generic banner and preserves entered values (REQ-5).
  - company address and first-user address sections accept independent
    values (filling one does not affect the other's `FormGroup`)
    (REQ-16).
- `active-tenant.service.spec.ts` (updated): `createTenant()` posts the
  exact `CreateTenantRequest` body to `/api/tenants`.

## Deviations from this PLAN (discovered during implementation)

_(none yet — update as implementation proceeds, per TASKS.md's own
closing task.)_
</content>
