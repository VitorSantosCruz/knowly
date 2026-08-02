# TASKS — user-profile-v2 (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> TDAD: test first (Red), minimal code (Green), `npm test`, repeat.
> **Do not start task 1 until `identity-profile-model-v2` (backend)
> has reached its DTO-finalization checkpoint** — see PLAN.md's
> sequencing note.

## Foundations — service/types retrofit

- [x] 1. Update `profile.service.spec.ts`'s existing type-shape
      assertions and mocked responses to the new `ProfileFields`/
      `UserProfile`/`ProfileEditRequest`/`Address`/`Contact`/
      `ContactChange` shapes (Red — existing tests now fail against the
      old flat shape); update `core/profile.service.ts`'s types to
      match (Green).
- [x] 2. Extend `profile.service.spec.ts`: `submitEditRequest(fields,
      contactChanges)` posts both in the `ProfileEditRequestFieldsDto`
      shape (Red); update the method signature (Green).
- [x] 3. Write a new case: `uploadAvatar(file)` posts `FormData` to
      `POST /api/users/me/profile/avatar` and returns the updated
      `UserProfile` (Red); implement `ProfileService.uploadAvatar`
      (Green).
- [x] 4. Extend `profile.service.spec.ts`: `directEdit(userId, fields,
      contactChanges)` gains the second parameter (Red); update the
      method (Green).

## Shared form component — address + contacts retrofit

- [x] 5. Update `profile-fields-form.component.spec.ts`'s existing
      render assertions from flat `address`/`phone` inputs to the new
      structured address fieldset (Red); retrofit
      `shared/profile-fields-form.component.ts`'s template/inputs
      (Green).
- [x] 6. Write a new case: the contacts editor renders existing
      contacts, supports adding up to 5 total, and blocks a 6th
      client-side with a clear message before any submit (REQ-7) (Red);
      implement the contacts list editor (Green).
- [x] 7. Extend the spec: setting a contact's `isPrimary` clears any
      other contact of the same `type` client-side (one-per-type, REQ-6)
      (Red); implement (Green).
- [x] 8. Extend the spec: submitting with a mix of an unchanged existing
      contact, an edited existing contact, a newly added contact, and a
      removed original contact emits the correctly diffed
      `contactChanges` array (`ADD`/`UPDATE`/`REMOVE`, matching only
      what actually changed) (Red); implement the submit-time diff
      (Green).
- [x] 9. Confirm (existing coverage from `user-profile`, re-verify still
      true) the form never renders/emits `email`, and `[disabled]=true`
      still prevents submission.

## Avatar upload component

- [x] 10. Write `avatar-upload.component.spec.ts`: renders the given
      `[avatarUrl]` or a placeholder when null (Red); create
      `shared/avatar-upload.component.ts` (Green).
- [x] 11. Extend the spec: selecting a file via the native file input
      emits `fileSelected` with that `File` (Red); implement (Green).

## Own-profile screen retrofit

- [x] 12. Rewrite the existing `own-profile-page.component.spec.ts`
      cases asserting a `STAFF_ADMIN`/tenant-`ADMIN` session calls `PUT`
      directly on submit — replace with an assertion that **every**
      session (including those two) calls `POST .../edit-requests`
      (Red — this inverts the old behavior); remove
      `OwnProfilePageComponent`'s `hasDirectEditRight` computed and the
      `PUT`-branch entirely (Green).
- [x] 13. Confirm (existing coverage, re-verify) the pending-state
      (REQ-3) and "already pending" 409 message (REQ-4) still work,
      now exercised uniformly for every session type rather than only
      non-admin ones.
- [x] 14. Write a new case: `AvatarUploadComponent` is rendered on this
      page; selecting a file calls `profileService.uploadAvatar` and
      updates the displayed avatar immediately, independent of the
      non-avatar form's pending state (REQ-8) (Red); wire
      `AvatarUploadComponent` into `OwnProfilePageComponent` (Green).
- [x] 15. Extend the spec: a 400 on avatar upload shows a clear message
      and leaves the previous avatar displayed (REQ-9) (Red); implement
      `avatarError` handling (Green).
- [x] 16. Update the own-profile screen's field rendering assertions
      for the new structured address/contacts/birthDate/rgOrgaoEmissor
      fields (Red/Green as needed alongside task 5's form retrofit).

## Profile section on detail panels — self-exclusion fix

- [x] 17. Update `profile-section.component.spec.ts`'s field-render
      assertions for the new shape; add a case rendering the read-only
      avatar `<img>` regardless of `[canEdit]` (Red); retrofit
      `features/user-management/profile-section.component.ts` (Green).
- [x] 18. Write a new case: `[ownUserId]` equal to `[userId]` hides the
      inline edit toggle even when `[canEdit]=true` (Red — this is the
      resolved deviation from `user-profile/PLAN.md`); add the
      `ownUserId` input and narrow the internal edit-toggle gate to
      `canEdit() && userId !== ownUserId()` (Green).
- [x] 19. Update `staff-user-detail-panel.component.spec.ts`: assert
      `ownUserId` is threaded into `ProfileSectionComponent`, sourced
      from one `profileService.getOwnProfile()` call per panel-open
      (Red); wire it into `StaffUserDetailPanelComponent` (Green).
- [x] 20. Update `member-detail-panel.component.spec.ts`: same
      `ownUserId` wiring for `MemberDetailPanelComponent` (Red);
      implement (Green).
- [x] 21. Extend `profile-section.component.spec.ts`: inline edit submit
      now calls `directEdit(userId, fields, contactChanges)` with both
      arguments (Red); implement (Green).

## Edit-request inbox retrofit

- [x] 22. Update
      `profile-edit-requests-inbox-page.component.spec.ts`'s row
      rendering assertions to include the structured proposed address
      and the `proposedContactChanges` list (action badge + type/value/
      label per entry) (Red); retrofit
      `features/profile-edit-requests/profile-edit-requests-inbox-page.component.ts`'s
      template (Green).
- [x] 23. Confirm (existing coverage, re-verify unchanged) approve/
      reject success removal, empty state, 409-uniqueness-keeps-row,
      403/stale-409-refreshes-list all still pass against the new row
      shape.

## Final verification

- [x] 24. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 25. Update `PLAN.md`'s "Deviations from this PLAN" section if any
      decision changed during implementation.
- [x] 26. Update `PROJECT_STATUS.md`: mark `user-profile-v2` shipped;
      annotate `user-profile`'s existing row as superseded (not
      deleted, matching the `primeng-migration`→`primeng-removal`
      precedent); confirm whether the previously-flagged "requester
      shown as `User #{id}` only" and "inbox nav gating only reflects
      active tenant" rough edges are still accurate and re-flag them if
      so (this retrofit does not resolve either).

## Follow-up (2026-07-30): requester identity + "any tenant" nav gate

- [x] 27. Update `profile.service.spec.ts`'s flushed
      `ProfileEditRequest` fixtures to include `requesterName`/
      `requesterEmail` (Red — new fields fail type check); add both to
      `core/profile.service.ts`'s `ProfileEditRequest` interface
      (Green).
- [x] 28. Add cases to
      `profile-edit-requests-inbox-page.component.spec.ts`: renders
      `requesterName` when present; falls back to `requesterEmail` when
      `requesterName` is null; falls back to the existing
      `"User #{id}"` string when both are null (Red); implement
      `requesterDisplayName()` + template `@if`/`@else` chain and the
      new `profileEditRequests.requesterNamed` i18n key in `en`/`pt-BR`
      (Green).
- [x] 29. Add cases to `permissions.service.spec.ts`: `hasInAnyTenant()`
      defaults to `false`; `fetchInAnyTenant(permission)` calls `GET
      /api/tenants/permissions/any-tenant?permission=X` and
      `hasInAnyTenant()` reflects the flushed `granted` value; a 401/
      error is treated as `false` rather than an unhandled error (Red);
      implement both methods on `PermissionsService` (Green).
- [x] 30. Update `nav-menu.component.spec.ts`'s `flush()` helper to
      always expect/flush the new any-tenant request; add/update cases
      so `canSeeProfileEditRequests` reflects a grant from
      `hasInAnyTenant('PROFILE_EDIT')` regardless of the active tenant's
      own permission set, including the 0-membership staff case (Red);
      wire `nav-menu.component.ts`'s `canSeeProfileEditRequests` to
      `hasInAnyTenant('PROFILE_EDIT')` instead of the previous
      active-tenant-only `has('PROFILE_EDIT')` check, and call
      `fetchInAnyTenant('PROFILE_EDIT')` once at session-start alongside
      `permissions.fetch()`/`globalPermissionsService.fetch()` (Green).
- [x] 31. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 32. Update `PROJECT_STATUS.md` marking both rough edges closed;
      update this PLAN.md's follow-up section noting frontend
      consumption is done.

## Amendment (2026-08-02) — REQ-21/22/23 masked input

- [x] 33. Write `input-mask.directive.spec.ts` (new): given
      `[appInputMask]="'cpf'"` on a bare `<input>`, typing
      `12345678900` reformats the displayed value to
      `123.456.789-00` as each digit is typed (Red); implement the
      minimum `InputMaskDirective` (`shared/input-mask.directive.ts`)
      CPF formatting for task 33's test to pass (Green).
- [x] 34. Extend the spec: `'cep'` mask formats `01310100` to
      `01310-100`; `'phone'` mask formats an 11-digit sequence to
      `(00) 00000-0000` and a 10-digit sequence to `(00) 0000-0000`
      (Red); implement both patterns (Green).
- [x] 35. Extend the spec: the directive emits `(appInputMaskChange)`
      with the **unmasked, digits-only** value on every keystroke,
      regardless of the masked display string (Red — asserts the
      emitted value, not the DOM value); implement the output (Green).
- [x] 36. Extend the spec: deleting a character mid-string (not at the
      end) keeps the caret at the edited position after the mask is
      reapplied, not jumped to the end (Red); implement the
      caret-offset fix-up via `setSelectionRange` (Green). Covers
      SPEC.md's masking-accessibility non-functional requirement.
- [x] 37. Update `profile-fields-form.component.spec.ts`: existing
      render/selector assertions for `profile-field-cpf`,
      `profile-address-field-cep`, and `profile-contact-value-*`
      (type `PHONE`/`WHATSAPP`) must still pass **unmodified in
      selector/DOM shape** (regression guard — confirm before wiring
      the directive in, then re-confirm green after); add new cases:
      typing an unmasked digit string into the `cpf`/`cep`/phone-type
      contact-value inputs displays the masked string, but
      `localFields()`/`contacts()` (read at submit) stay plain/unmasked
      (REQ-22) (Red); wire `[appInputMask]` onto the `cpf`, `cep`, and
      conditionally-per-row phone-type contact-value inputs in
      `shared/profile-fields-form.component.ts`'s template, replacing
      their `(input)` handler with `(appInputMaskChange)` (Green). `rg`
      and `rgOrgaoEmissor` inputs are explicitly confirmed unchanged
      (no mask applied), per PLAN.md's amendment.
- [x] 38. Extend the spec: switching a contact row's `type` from
      `PHONE` to `EMAIL` via the `<select>` stops reformatting further
      keystrokes in that row's value input (mask only applies while
      `type` is `PHONE`/`WHATSAPP`) (Red); confirm the conditional
      `[appInputMask]` binding handles this without extra code, or
      adjust if it doesn't (Green).
- [x] 39. Confirm no client-side format/checksum validation is
      introduced — submitting a mask-incomplete value (e.g. a CPF
      typed only halfway) is not blocked and shows no validation error
      (REQ-23); add a regression test if none already covers this.
- [x] 40. Run `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green, including every pre-existing
      `profile-fields-form.component.spec.ts` case passing unmodified.
- [x] 41. Update `PLAN.md`'s amendment section if any decision changed
      during implementation; update `PROJECT_STATUS.md` noting
      REQ-21/22/23 shipped and that `bootstrap-profile-completion`
      inherits this masking automatically via the shared component.

## Amendment — country-agnostic identity/address model (2026-08-02)

> Delta only, per PLAN.md's matching amendment section. **Task 42 is a
> hard gate: do not start task 43+ until `identity-profile-model-v2`'s
> backend amendment (that feature's TASKS.md task 37) is confirmed
> frozen.**

- [x] 42. Confirm `identity-profile-model-v2`'s backend amendment DTO
      shapes (`taxId`, `countryCode`, restructured `AddressDto`) are
      frozen/shipped before proceeding — per this PLAN's own sequencing
      dependency note. **Note:** proceeded against the applied migration
      files (`V26__remove_rg_and_birth_date_fields.sql`/
      `V27__country_agnostic_identity_address.sql`) and the backend
      PLAN's amendment text as ground truth, per explicit direction —
      the backend implementation was still in progress concurrently
      when this frontend work landed, so `knowly-api`'s DTOs weren't
      yet renamed in its working tree. No file conflicts, disjoint
      subprojects.
- [x] 43. Write `country-field-config.spec.ts` asserting `BR`/`US`/`GB`
      lookups return their documented labels/masks and an unknown code
      falls back to `DEFAULT` (Red); write
      `shared/country-field-config.ts` (Green).
- [x] 44. Update `core/profile.service.ts`'s `Address`/`ProfileFields`
      interfaces to the new shape (`addressLine1`/`addressLine2`/
      `city`/`stateRegion`/`postalCode`/`countryCode`; `taxId` renamed
      from `cpf`; new `countryCode` on `ProfileFields`); update
      `profile.service.spec.ts` fixtures accordingly (Red/Green).
- [x] 45. Extend `InputMaskDirective`/`formatMaskedValue` with a
      `country` parameter, looking up the mask pattern via a small
      `(mask, country)` pattern table instead of a hardcoded Brazilian
      pattern (Red: `BR`+`taxId` still masks per the existing
      regression fixtures; `GB`+`postalCode`, no mask defined, passes
      the raw value through unmasked; Green: implementation). Renamed
      the mask keys `'cpf'`/`'cep'` → `'taxId'`/`'postalCode'`
      throughout. **Deviation (Tier 2):** the table is nested `Map`s,
      not `Record`s (avoids an ESLint `security/detect-object-injection`
      warning on dynamic-key lookups); also added a concrete US
      `SSN`/`ZIP` pattern, matching `CountryFieldConfig`'s `US` entry
      having `hasTaxIdMask`/`hasPostalCodeMask: true`.
- [x] 46. Write `phone-ddi-input.component.spec.ts` — folded into
      `profile-fields-form.component.spec.ts`'s "phone/WhatsApp contact
      rows" + "submitting a mix of..." describe blocks (covers render,
      compose-on-change, and round-trip through the shared form, which
      is `PhoneDdiInputComponent`'s only real consumer) rather than a
      separate top-level spec file duplicating the same assertions
      (Red/Green); implement `shared/phone-ddi-input.component.ts`
      (Green). **Deviation (Tier 2, bugfix caught during TDAD):** the
      naive "always resync `ddi`/`number` from the parent's round-
      tripped `value`" effect fought a manually-typed DDI whose digit
      length differs from `ddiLengthFor(countryCode)`'s guess — same
      class of self-fighting bug the masking directive's
      `formatMaskedValue` fix already worked around. Fixed by tracking
      `lastEmitted` and skipping the resync when the incoming `value`
      is this component's own last emission (not a genuinely external
      change).
- [x] 47. Update `profile-fields-form.component.spec.ts`: existing
      `cpf`/`cep`-labeled assertions renamed to `taxId`/`postalCode`;
      old 8-field address assertions replaced with the new 6-field
      shape (Red); update `shared/profile-fields-form.component.ts`'s
      template/fields accordingly (Green).
- [x] 48. Add the `countryCode` `<select>` to
      `ProfileFieldsFormComponent`, wired to
      `CountryFieldConfig`-driven labels for `taxId`/`postalCode`/
      address-line fields (Red: selecting a different `countryCode`
      updates labels/mask behavior live, no reload; Green:
      implementation). **Deviation (Tier 2, bugfix):** a plain
      `[value]` binding on the `<select>` silently failed to select the
      matching `<option>` in tests (Angular evaluates the select's own
      property binding before its `@for`-generated `<option>` children
      exist in the DOM on the very first change-detection pass) — fixed
      by binding `[selected]` per-`<option>` instead of `[value]` on
      the `<select>` itself.
- [x] 49. Wire `PhoneDdiInputComponent` into the contacts list editor,
      conditionally per row on `type === 'PHONE' || type === 'WHATSAPP'`
      (Red: a `PHONE`/`WHATSAPP` row shows the DDI+number inputs, an
      `EMAIL`/`OTHER` row shows the plain value input; Green:
      implementation).
- [x] 50. Confirm/extend `own-profile-page.component.spec.ts`,
      `profile-section.component.spec.ts`,
      `profile-edit-requests-inbox-page.component.spec.ts` for the
      renamed/restructured fields (regression pass — no new behavior
      expected beyond the field-shape rename in these three). Also
      updated `tenant-creation`'s already-shipped
      `tenant-create-page.component.ts`/`active-tenant.service.ts`
      (first-admin's `MandatoryProfileFieldsDto`-mirroring
      `CreateTenantProfile` shape) and `bootstrap-profile-completion`'s
      `complete-profile-page.component.ts`, since both consume the same
      renamed/restructured field set — not originally listed as a task
      here but required by the same rename to keep the app buildable.
- [x] 51. Run `npm run format:check && npm test && npm run build &&
      npm run lint` and confirm everything is green, including every
      pre-existing masking/contacts test from the prior amendment.
      623/623 tests green, `format:check`/`build`/`lint` all clean.
- [x] 52. Update `PLAN.md`'s amendment section for any decision that
      changed during implementation (especially the DDI-length
      heuristic in `PhoneDdiInputComponent`, if it proves inadequate);
      update `PROJECT_STATUS.md` noting this amendment shipped and that
      `bootstrap-profile-completion`'s shared-component reuse inherits
      the new fields automatically.
