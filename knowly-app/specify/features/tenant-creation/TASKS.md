# TASKS — Tenant creation (staff)

> Atomic, sequential, verifiable tasks derived from PLAN.md.

## Original scope (shipped)

- [x] 1. Write `staff.guard.spec.ts` covering REQ-2/REQ-6 (allow on
      `listAllTenants()` success, redirect to `/select-tenant` on
      error) — Red.
- [x] 2. Implement `staff.guard.ts` — Green.
- [x] 3. Write `active-tenant.service.spec.ts` case for `createTenant()`
      posting `{ name, adminEmail }` to `/api/tenants` — Red.
- [x] 4. Implement `createTenant()` in `active-tenant.service.ts` —
      Green.
- [x] 5. Write `tenant-create-page.component.spec.ts` covering: renders
      form; client-side validation blocks empty/invalid submit (REQ-1);
      successful submit calls the service and navigates to
      `/select-tenant` (REQ-4); service error shows inline error and
      keeps entered values (REQ-5) — Red.
- [x] 6. Implement `TenantCreatePageComponent` — Green.
- [x] 7. Wire the route: add `/tenants/new` to `app.routes.ts` with
      `staffGuard` (REQ-1, REQ-2, REQ-6).
- [x] 8. Write `select-tenant-page.component.spec.ts` case: "create
      tenant" link appears only on the staff (listAllTenants-success)
      path (REQ-3) — Red.
- [x] 9. Implement the link in `select-tenant-page.component.ts` —
      Green.
- [x] 10. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 11. Update `PLAN.md` if any decision changed during
       implementation; update `PROJECT_STATUS.md`'s feature table and
       "Next up" section.
- [x] 12. Commit.

## Amendment for REQ-7–REQ-21 (2026-08-02)

- [x] 13. **Blocking checkpoint — resolved 2026-08-02.** Confirmed the
      `POST /api/tenants` request body shape against the now-finalized
      `knowly-api/specify/features/tenant-creation/PLAN.md` (and its
      cross-referenced `mandatory-complete-profile/PLAN.md`,
      `user-role-selection-at-creation/PLAN.md`): the company address
      uses the backend's English `AddressDto`
      (`postalCode`/`street`/`number`/`complement`/`neighborhood`/
      `city`/`state`), while the first user's address uses
      `MandatoryAddressDto`'s Portuguese names
      (`cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/
      `estado`/`pais`) — the two sub-sections genuinely serialize
      differently despite rendering identically. PLAN.md's "Coordination
      checkpoint — resolved" and "Consumed API contracts" sections are
      updated accordingly.
- [x] 14. Write `address-fields.component.spec.ts`: renders the 8 fields
      bound to a `FormGroup`'s current value; shows a field-level error
      for an invalid+touched control; two independent component
      instances with two different `FormGroup`s never share state — Red.
- [x] 15. Implement `AddressFieldsComponent` — Green.
- [x] 16. Write `contacts-list-editor.component.spec.ts`: starts with one
      empty row (REQ-13); add-row appends a control pair; remove-row
      removes a row; submitting with zero rows surfaces a
      "must have at least one" error state (REQ-14) — Red.
- [x] 17. Implement `ContactsListEditorComponent` — Green.
- [x] 18. Convert `TenantCreatePageComponent` to Reactive Forms
      (`FormBuilder.group`, nested `companyAddress`/`userAddress`
      `FormGroup`s, `contacts` `FormArray`, `role` control defaulting to
      `'MEMBER_ADMIN'`) — update `tenant-create-page.component.spec.ts`
      for the new field set and section headings (REQ-20, REQ-21); keep
      existing REQ-1/REQ-4/REQ-5 cases passing under the new form shape
      — Red then Green.
- [x] 19. Write new `tenant-create-page.component.spec.ts` cases:
      missing-required-company-field blocks submit with field-level
      errors, no API call (REQ-8, REQ-9); malformed `contactEmail` blocks
      submit (REQ-9) — Red.
- [x] 20. Implement the company-identification section's validators —
      Green.
- [x] 21. Write cases: missing-required-first-user-field or zero
      contacts blocks submit with field-level errors, no API call
      (REQ-13, REQ-14) — Red.
- [x] 22. Implement the first-user-profile section's validators
      (including wiring `ContactsListEditorComponent`'s `FormArray`) —
      Green.
- [x] 23. Write cases: Brazil `country` + non-14-digit `taxId` (with/
      without punctuation) blocks submit with a field-level `taxId`
      error; non-Brazil `country` + any non-empty `taxId` does not block
      submit (REQ-10) — Red.
- [x] 24. Implement the conditional CNPJ-shape validator on `taxId`,
      re-evaluated when `country` changes — Green.
- [x] 25. Write cases: role selector defaults to `MEMBER_ADMIN`; switching
      to `MEMBER` and submitting sends `role: 'MEMBER'` (REQ-17–19) — Red.
- [x] 26. Implement the role `<select>` control — Green.
- [x] 27. Write `active-tenant.service.spec.ts` case: `createTenant()`
      posts the exact `CreateTenantRequest` payload (confirmed per task
      13 — company address English-named, first-user address
      Portuguese-named) to `/api/tenants` — Red.
- [x] 28. Update `createTenant()`'s signature/body in
      `active-tenant.service.ts` — Green.
- [x] 29. Write cases: a 409 response identifying a `taxId` conflict sets
      the `taxId` control's error and preserves all other entered values
      (REQ-11); a 400 response identifying incomplete first-user-profile
      field(s) maps onto the matching control(s), falling back to the
      generic banner when the response doesn't identify a field (REQ-15);
      any other error shows the generic banner and preserves entered
      values (REQ-5) — Red.
- [x] 30. Implement the submit-error-to-field-error mapping — Green.
- [x] 31. Write case: company-address and user-address sections accept
      independent values — filling one `AddressFieldsComponent` instance
      does not affect the other's bound `FormGroup` (REQ-16) — Red (this
      should already pass by construction once tasks 14–22 are done;
      write it explicitly as a regression guard rather than skipping it).
- [x] 32. Run `npm run format:check && npm test && npm run build && npm run lint`
       and confirm everything is green.
- [x] 33. Update `PLAN.md`'s "Deviations from this PLAN" section if any
       decision changed during implementation (in particular, task 13's
       resolved API contract); update `PROJECT_STATUS.md`'s feature
       table.
- [x] 34. Commit.

## Amendment for REQ-22–REQ-26 (CNPJ checksum mirror, 2026-08-02)

- [x] 35. Write test cases in
      `tenant-create-page.component.spec.ts` (or a new
      `isValidCnpj.spec.ts` if extracted): `isValidCnpj` returns `true`
      for the six fixture values from backend PLAN.md's table (three
      valid, three invalid) plus one alphanumeric-format fixture — Red.
- [x] 36. Implement `isValidCnpj` and wire it into `taxIdValidator`'s
      Brazil branch (checked only after the existing shape check
      passes) — Green.
- [x] 37. Write cases: a Brazil `taxId` with correct 14-character shape
      but a wrong check digit shows the checksum-specific field error
      and blocks submit (REQ-23); a checksum-correct `taxId`, punctuated
      or not, allows submit; non-Brazil country never runs the checksum
      check regardless of `taxId` content (REQ-24) — Red.
- [x] 38. Implement the checksum-specific error message lookup branch
      (`taxIdCnpjChecksum`) and add its i18n key — Green.
- [x] 39. Write case: a submit response shaped `{ status: 400, code:
      'INVALID_TAX_ID' }` sets the `taxId` control's error and preserves
      all other entered values (REQ-26) — Red.
- [x] 40. Implement the `INVALID_TAX_ID` branch in the submit-error
      mapping, alongside the existing `TENANT_ALREADY_EXISTS` branch —
      Green.
- [x] 41. Confirm no client-side normalization is applied to `taxId`
      before submit — write a regression test asserting the exact
      punctuated string the user typed is what gets sent to
      `createTenant()` (REQ-25) — should already pass by construction;
      write it explicitly as a guard rather than skipping it.
- [x] 42. Run `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green.
- [x] 43. Update `PLAN.md`'s changelog with any deviation discovered
      during implementation; update `PROJECT_STATUS.md`'s feature table
      if relevant.
- [x] 44. Commit.
