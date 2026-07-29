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
