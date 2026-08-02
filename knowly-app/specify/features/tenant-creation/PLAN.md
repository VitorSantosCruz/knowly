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

- **2026-08-02 — Amendment for REQ-22–REQ-26 (CNPJ checksum mirror).**
  Adds a pure `isValidCnpj` function and wires it into the existing
  `taxIdValidator` (already conditional on `isBrazil(country)`, see the
  component's current code), plus a new `INVALID_TAX_ID` branch in the
  existing submit-error-mapping `if` chain, alongside the current
  `TENANT_ALREADY_EXISTS` → `taxId` mapping.

### `isValidCnpj` (new, pure function, same file as `isBrazil`/`taxIdValidator`)

```ts
function isValidCnpj(value: string): boolean {
  const chars = value.toUpperCase();
  if (chars.length !== 14) return false;

  const charValue = (c: string) => c.charCodeAt(0) - 48;
  const weightedSum = (base: string, weights: number[]) =>
    base
      .split('')
      .reduce((sum, c, i) => sum + charValue(c) * weights[i], 0);
  const expectedDigit = (sum: number) => {
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };

  const base12 = chars.slice(0, 12);
  const digit1 = expectedDigit(
    weightedSum(base12, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3]),
  );
  if (digit1 !== charValue(chars[12])) return false;

  const digit2 = expectedDigit(
    weightedSum(base12 + digit1, [7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3]),
  );
  return digit2 === charValue(chars[13]);
}
```

Exact mirror of backend `CnpjChecksumValidator` (same weight arrays,
same `charCodeAt(0) - 48` alphanumeric adjustment as
`Character.toUpperCase(c) - 48`, same `remainder < 2 ? 0 : 11 -
remainder` rule) — **not test-shared with the backend** (different
language, no code-sharing mechanism between the two subprojects exists
or is being introduced here); test fixtures are the same six values
from backend PLAN.md's table, duplicated into this file's own test,
which is an accepted, deliberate duplication (small, pure,
side-effect-free function; REQ-24 already establishes this mirror is
non-authoritative, so the two implementations are allowed to drift and
be independently tested without a shared-source-of-truth mechanism).

`taxIdValidator` (existing function) gains one more branch, only
reached once REQ-10's shape check already passed:

```ts
if (isBrazil(country)) {
  const digits = value.replace(/\D/g, ''); // existing shape check unchanged
  if (digits.length !== 14) {
    return { cnpjShape: true };
  }
  if (!isValidCnpj(value.replace(/[.\-/]/g, ''))) {
    return { cnpjChecksum: true };
  }
}
```

Note: the existing shape check still operates on `digits` (numeric-only
count), unaffected by REQ-22; `isValidCnpj` receives the
punctuation-stripped-but-still-alphanumeric value (letters preserved)
so it can validate the newer alphanumeric CNPJ format too, matching
backend REQ-6b/REQ-6c's own "strip punctuation, keep letters" handling.
No normalization is applied to what's actually submitted (REQ-25) —
`isValidCnpj`'s stripped copy is a local variable used only for the
checksum call, never written back to the form control's value.

`showFlatError`/error-message-lookup (existing function around line
347) gains one more branch:

```ts
if (name === 'taxId' && control?.errors?.['cnpjChecksum']) {
  return 'tenantCreate.taxIdCnpjChecksum';
}
```

### Submit-error mapping (existing `if` chain around line 430)

```ts
if (error.status === 409 && code === 'TENANT_ALREADY_EXISTS') {
  this.form.get('taxId')?.setErrors({ conflict: true });
  this.form.get('taxId')?.markAsTouched();
  // ...existing code, unchanged
}
if (error.status === 400 && code === 'INVALID_TAX_ID') {
  this.form.get('taxId')?.setErrors({ invalidTaxId: true });
  this.form.get('taxId')?.markAsTouched();
  // falls through to generic banner suppression the same way the
  // existing 409 branch already does — no new pattern introduced
}
```

A new `taxIdCnpjChecksum` and `taxIdInvalid` (or equivalent) i18n key
pair is added alongside the existing `taxIdCnpjShape` key, same
translation file, same pattern.

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

- **`country` is a free-text input, not a `<select>`** — this PLAN never
  specified a control type for `country`; REQ-10's Brazil detection
  (`isBrazil()`) matches case-insensitively against `'brazil'`,
  `'brasil'`, `'br'`. Conservative, documented-in-code decision (not a
  product ambiguity PLAN/SPEC needed to resolve) since neither SPEC nor
  PLAN mandates a closed country list here.
- **409/400 error-body field mapping is best-effort, not backend-
  confirmed line-for-line.** `TenancyExceptionHandler` (backend,
  `knowly-api/src/main/java/br/com/conectabyte/knowly/tenancy/exception/TenancyExceptionHandler.java`)
  returns `TenancyErrorResponseDto(code)` for `TenantAlreadyExistsException`
  as `409 TENANT_ALREADY_EXISTS` for **both** `taxId` and `adminEmail`
  collisions — the response body carries no field discriminator. Per
  REQ-11's explicit "taxId conflict → taxId field error" requirement,
  `TENANT_ALREADY_EXISTS` is mapped unconditionally to the `taxId`
  control (conservative choice, documented in code); an `adminEmail`
  collision will incorrectly surface on `taxId` until/unless the backend
  disambiguates the code. For 400s, no dedicated `MethodArgumentNotValidException`
  handler was found in `tenancy`'s exception package, so the mapping
  assumes an `errors: [{ field }]` array shape (`field` optionally
  prefixed `profile.`) and falls back to the generic banner (REQ-15's own
  documented fallback) whenever that shape isn't present — this is the
  conservative, spec-compliant default given the shape wasn't nailed down
  by either PLAN.
</content>
