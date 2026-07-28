# TASKS — user-profile (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: test first (Red), minimal code (Green), `npm test`, repeat.

## Foundations

- [x] 1. Add `PROFILE_EDIT` to `core/permission.ts` (`Permission` union +
      `ALL_PERMISSIONS`) and `core/global-permission.ts`
      (`GlobalPermission` union + `ALL_GLOBAL_PERMISSIONS`).
- [x] 2. Write `profile.service.spec.ts` covering `getOwnProfile()` →
      `GET /api/users/me/profile` (Red).
- [x] 3. Create `core/profile.service.ts` with `ProfileFields`,
      `UserProfile`, `ProfileEditRequest`, `ProfileEditRequestStatus`
      types and `getOwnProfile()` (Green).
- [x] 4. Extend `profile.service.spec.ts` for `getProfile(userId)` →
      `GET /api/users/{id}/profile` (Red); implement (Green).
- [x] 5. Extend `profile.service.spec.ts` for `directEdit(userId,
      fields)` → `PUT /api/users/{id}/profile` (Red); implement
      (Green).
- [x] 6. Extend `profile.service.spec.ts` for `submitEditRequest(fields)`
      → `POST /api/users/me/profile/edit-requests` (Red); implement
      (Green).
- [x] 7. Extend `profile.service.spec.ts` for `listEditRequests()` →
      `GET /api/profile-edit-requests` (Red); implement (Green).
- [x] 8. Extend `profile.service.spec.ts` for `approveEditRequest(id)` /
      `rejectEditRequest(id)` → their respective `POST .../approve` /
      `.../reject` calls (Red); implement (Green).

## Shared form component

- [x] 9. Write `profile-fields-form.component.spec.ts`: renders
      `fullName`/`address`/`rg`/`cpf`/`phone` inputs, never an `email`
      input (Red).
- [x] 10. Create `shared/profile-fields-form.component.ts` implementing
      the above (Green).
- [x] 11. Extend the spec: submitting the form emits `submitted` with
      the entered values, never including `email` in the emitted object
      (Red); implement (Green).
- [x] 12. Extend the spec: `[disabled]=true` prevents submission/emits
      nothing (Red); implement (Green).

## Own-profile screen

- [x] 13. Write `own-profile-page.component.spec.ts`: on init, calls
      `GET /api/users/me/profile` and renders the returned fields with
      `email` shown read-only (Red).
- [x] 14. Create `features/profile/own-profile-page.component.ts`
      implementing the load + render (Green).
- [x] 15. Extend the spec: a `STAFF_ADMIN`-shaped session (all global
      permissions) submitting the form calls `PUT
      /api/users/{ownUserId}/profile` and applies the result
      immediately (Red); implement `hasDirectEditRight` computed +
      submit branch (Green).
- [x] 16. Extend the spec: a tenant `ADMIN` membership session submits
      via the same `PUT` branch (Red); implement (Green — extends the
      `hasDirectEditRight` computed).
- [x] 17. Extend the spec: a plain session (no admin role) submitting
      the form calls `POST /api/users/me/profile/edit-requests` instead
      (Red); implement the else-branch (Green).
- [x] 18. Extend the spec: after a successful edit-request submission,
      the screen shows the pending-approval state and disables
      resubmission (REQ-4) (Red); implement `pending` signal (Green).
- [x] 19. Extend the spec: a 409 on the edit-request call shows the
      "already pending" message and sets the same pending state (REQ-5)
      (Red); implement (Green).
- [x] 20. Extend the spec: a 409 on the direct-edit `PUT` call shows a
      conflict message naming the returned conflicting field(s) and
      leaves the form's entered values unchanged (REQ-6) (Red);
      implement (Green).
- [x] 21. Add the `/profile` route to `app.routes.ts` (no guard);
      extend/create a routing test confirming it resolves to
      `OwnProfilePageComponent`.

## Profile section on existing detail panels

- [x] 22. Write `profile-section.component.spec.ts`: given `[userId]`,
      renders `GET /api/users/{id}/profile`'s fields (`fullName`,
      `address`, `rg`, `cpf`, `phone`, `email`) (Red).
- [x] 23. Create `features/user-management/profile-section.component.ts`
      implementing the load + render with its own
      `profile`/`profileError` signals (Green).
- [x] 24. Extend the spec: a 403 renders `app-no-access-state` scoped to
      this section only (REQ-9) (Red); implement the `catchError` →
      403-classify block (Green).
- [x] 25. Extend the spec: `[canEdit]=true` reveals an edit toggle
      rendering `ProfileFieldsFormComponent`; submitting calls `PUT
      /api/users/{id}/profile` and refreshes the section on success
      (REQ-10) (Red); implement (Green).
- [x] 26. Extend the spec: a 409 on that call shows the conflict
      message (REQ-11) (Red); implement (Green).
- [x] 27. Extend the spec: `[canEdit]=false` never renders the edit
      toggle (Red); implement the gate (Green).
- [x] 28. Write a new case in `staff-user-detail-panel.component.spec.ts`:
      the panel renders `<section data-testid="profile-section">`
      alongside its existing three/four sections, each independently
      (Red); wire `ProfileSectionComponent` into
      `StaffUserDetailPanelComponent` with a host-computed `canEdit`
      (`viewerIsStaffAdmin() ||
      globalPermissionsService.has('PROFILE_EDIT')`) (Green).
- [ ] 29. **Deferred — not implemented this iteration.** Write a new case
      in `member-detail-panel.component.spec.ts`: same panel-composition
      assertion for `MemberDetailPanelComponent` (Red); wire
      `ProfileSectionComponent` in with its host-computed `canEdit`
      (`viewerIsMemberAdminOfThisTenant() ||
      permissionsService.has('PROFILE_EDIT')`), including the new
      `tenantId` input plumbing from `MembersPageComponent` (Green).
      Blocked: verified against the shipped backend that neither
      `MemberDto` nor `MemberDetailDto` exposes a `userId` field (only
      `membershipId`), so there is no way to resolve the target user id
      `GET /api/users/{id}/profile` needs without a backend contract
      change — out of this frontend-only feature's scope. See
      `PLAN.md`'s "Deviations from this PLAN" section and
      `PROJECT_STATUS.md`'s `user-profile` row for the tracked follow-up.

## Edit-request inbox

- [x] 30. Write `profile-edit-requests-inbox-page.component.spec.ts`: on
      init, calls `GET /api/profile-edit-requests` and renders each row
      (requester id, proposed fields, submission date) (Red).
- [x] 31. Create
      `features/profile-edit-requests/profile-edit-requests-inbox-page.component.ts`
      implementing the load + render (Green).
- [x] 32. Extend the spec: zero requests renders the distinct empty
      state (REQ-18), not the loading/error state (Red); implement
      (Green).
- [x] 33. Extend the spec: approving a row calls `POST
      .../approve` and removes it from the list on success (REQ-13)
      (Red); implement (Green).
- [x] 34. Extend the spec: rejecting a row calls `POST .../reject` and
      removes it from the list on success (REQ-14) (Red); implement
      (Green).
- [x] 35. Extend the spec: a 409 on approve (uniqueness conflict) shows
      the conflict message and leaves the row visible/pending (REQ-15)
      (Red); implement (Green).
- [x] 36. Extend the spec: a 403 or non-uniqueness 409 on approve/reject
      shows the existing error/permission-denied state and refreshes
      the list so the stale row disappears (REQ-16) (Red); implement
      (Green).
- [x] 37. Add the `/profile-edit-requests` route to `app.routes.ts` (no
      guard); extend/create a routing test confirming it resolves to
      `ProfileEditRequestsInboxPageComponent`.

## Navigation

- [x] 38. Write a new case in `nav-menu.component.spec.ts`: "My profile"
      renders whenever `authService.isLoggedIn()` is true, regardless
      of tenant/permission state (Red); implement the always-visible
      `accountGroup` in `nav-menu.component.ts` (Green).
- [x] 39. Extend `nav-menu.component.spec.ts`: the edit-request inbox
      link appears for a tenant/global `PROFILE_EDIT` holder, a tenant
      `ADMIN` membership, and a `STAFF_ADMIN`-shaped session (Red);
      implement the conditional item on `overviewGroups` (Green).
- [x] 40. Extend `nav-menu.component.spec.ts`: the edit-request inbox
      link is absent for a session with none of the above (Red);
      confirm the gate is exhaustive (Green — likely already covered by
      task 39's implementation; add the negative assertion).

## Final verification

- [x] 41. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 42. Update `PLAN.md` if any decision changed during
      implementation (per this repo's standing convention — see
      `staff-global-dashboard/PLAN.md`'s own precedent for this kind of
      note, if applicable).
- [x] 43. Update `PROJECT_STATUS.md`: mark `user-profile` (frontend
      half of item 13) as shipped; add a note flagging the two accepted
      rough edges from PLAN.md (edit-request inbox shows only `User
      #{id}`, no display name; `PROFILE_EDIT` inbox nav gating only
      reflects the active tenant, not every membership) as small,
      scoped follow-ups for a future iteration.
