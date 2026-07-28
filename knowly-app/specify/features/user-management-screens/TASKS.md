# TASKS — User management screens

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 1. Shared prerequisites

- [x] 1. Test: `ActiveTenantService.activeTenantResolved()` is `false`
      before `fetch()` and `true` after `fetch()` resolves, including
      the existing "no active membership found, preserve prior value"
      branch (Red).
- [x] 2. Implement `activeTenantResolved` on `ActiveTenantService`
      (Green).
- [x] 3. Add `'STAFF_USER_VIEW'` to `GlobalPermission` and
      `ALL_GLOBAL_PERMISSIONS` in `core/global-permission.ts`.
- [x] 4. Add `GlobalRole` type (`'STAFF' | 'STAFF_ADMIN'`) to
      `core/staff-user.service.ts` (created in task 5).

## 2. Staff user service

- [x] 5. Test: `StaffUserService.list()` calls `GET /api/staff/users`;
      `list(email)` calls `GET /api/staff/users?email=...` (Red).
- [x] 6. Implement `StaffUserService.list` (Green).
- [x] 7. Test: `StaffUserService.create(email)` calls
      `POST /api/staff/users` (Red).
- [x] 8. Implement `StaffUserService.create` (Green).
- [x] 9. Test: `StaffUserService.getDetail(userId)` calls
      `GET /api/staff/users/{userId}/permissions` (Red).
- [x] 10. Implement `StaffUserService.getDetail` (Green).
- [x] 11. Test: `StaffUserService.grantPermission`/`revokePermission`
       call `POST`/`DELETE /api/staff/users/{userId}/permissions[/{permission}]`
       (Red).
- [x] 12. Implement both (Green).
- [x] 13. Test: `StaffUserService.listAccessGroups`/`createAccessGroup`
       call `GET`/`POST /api/staff/access-groups` (Red).
- [x] 14. Implement both (Green).
- [x] 15. Test: `StaffUserService.grantAccessGroupPermission` calls
       `POST /api/staff/access-groups/{id}/permissions` (Red).
- [x] 16. Implement it (Green).
- [x] 17. Test: `StaffUserService.assignAccessGroup`/`unassignAccessGroup`
       call `POST`/`DELETE /api/staff/users/{userId}/access-groups/{id}`
       (Red).
- [x] 18. Implement both (Green).

## 3. Screen switch (REQ-1, REQ-2, REQ-3)

- [x] 19. Test: `UserManagementPageComponent` shows a loading state while
       `!activeTenantService.activeTenantResolved()` (Red).
- [x] 20. Test: once resolved, it renders `MembersPageComponent` when
       `activeTenantId()` is non-null, and `StaffDirectoryPageComponent`
       when it's null (Red).
- [x] 21. Implement `UserManagementPageComponent` (Green).
- [x] 22. Point the `/members` route at `UserManagementPageComponent`
       instead of `MembersPageComponent` in `app.routes.ts`
       (`tenantSelectionGuard` unchanged).

## 4. Staff directory list + search + create (REQ-4, REQ-5, REQ-6, REQ-12)

- [x] 23. Test: `StaffDirectoryPageComponent` renders the staff list
       (id/email/globalRole) on load via `StaffUserService.list()` (Red).
- [x] 24. Implement the list rendering (Green).
- [x] 25. Test: entering a search term calls `list(email)` and refreshes
       the visible list (Red).
- [x] 26. Implement the search wiring (Green).
- [x] 27. Test: submitting the create-staff-user form calls
       `StaffUserService.create` and refreshes the list on success (Red).
- [x] 28. Implement the create form (Green).
- [x] 29. Test: the create form is hidden/disabled when the viewer is
       neither `viewerIsStaffAdmin` nor holds `STAFF_USER_CREATE` (Red).
- [x] 30. Implement that gating (Green).
- [x] 31. Test: a 403 from list/search/create renders
       `NoAccessStateComponent` (Red).
- [x] 32. Implement error handling reusing `NoAccessStateComponent`/
       `ErrorStateComponent` (Green).

## 5. Staff user detail panel (REQ-7, REQ-8, REQ-9, REQ-10, REQ-11, REQ-12)

- [x] 33. Test: selecting a staff user shows
       `StaffUserDetailPanelComponent` with direct permissions, access
       groups, and effective permissions as distinct sections (Red).
- [x] 34. Implement `StaffUserDetailPanelComponent` (Green).
- [x] 35. Test: toggling a permission calls grant/revoke and re-fetches
       the detail, updating the effective set shown (Red).
- [x] 36. Implement the toggle wiring (Green).
- [x] 37. Test: creating a global access group makes it available to
       assign (Red).
- [x] 38. Implement access-group creation (Green).
- [x] 39. Test: assigning/unassigning an access group updates the staff
       user's access groups and effective permissions shown (Red).
- [x] 40. Implement assign/unassign wiring (Green).
- [x] 41. Test: every management control (grant/revoke toggle, assign/
       unassign) is disabled when `viewerIsStaffAdmin` is `false`, and
       fully enabled when `true` (Red).
- [x] 42. Implement the `viewerIsStaffAdmin` computed + wiring it through
       `StaffDirectoryPageComponent` as an `@Input` to the panel (Green).
- [x] 43. Test: a 403 from any panel action renders
       `NoAccessStateComponent` (Red).
- [x] 44. Implement that error handling (Green).

## 6. Nav entry gating (REQ-13)

- [x] 45. Test: the `members` nav entry appears for
       `TENANT_MEMBER_MANAGE` (existing, unchanged), for `STAFF_USER_VIEW`
       alone, and for a `STAFF_ADMIN`-shaped ("all permissions") response;
       absent otherwise (Red).
- [x] 46. Update `nav-menu.component.ts`'s visibility condition to
       `permissionsService.has('TENANT_MEMBER_MANAGE') ||
       globalPermissionsService.has('STAFF_USER_VIEW')` (Green).

## 7. i18n and design

- [x] 47. Add staff-directory i18n keys to `public/i18n/en.json` /
       `pt-BR.json` (list, search, create form, detail panel labels,
       reusing `members.*`-style keys where the copy is generic enough).
- [x] 48. Apply the established design-system standard to the staff
       directory list and detail panel, matching `members`'s look.

## 8. Final verification

- [x] 49. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [x] 50. Update `PLAN.md`'s "Emergent decisions" if anything changed.
- [x] 51. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
