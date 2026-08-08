# TASKS — tenant-access-group-management

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> TDAD: test first (Red), then minimal code (Green), then
> `npm run format:check && npm test && npm run build && npm run lint`.
> Commit after each numbered task per CLAUDE.md's standing instruction.

## 1. `member.service.ts` — five new methods

- [x] 1.1. Write `member.service.spec.ts` case for
      `batchAssignAccessGroups(tenantId, membershipId, accessGroupIds)`:
      `POST /api/tenants/{tenantId}/members/{membershipId}/access-groups:batch`
      with `{ accessGroupIds }` body, `204` response (Red).
- [x] 1.2. Implement `batchAssignAccessGroups` in `member.service.ts`
      (Green).
- [x] 1.3. Write `member.service.spec.ts` case for
      `generateAccessGroupDeletionToken(tenantId, accessGroupId)`:
      `GET /api/tenants/{tenantId}/access-groups/{accessGroupId}/deletion-confirmation-token`,
      response `{ word }` mapped to the returned string — assert the verb
      is `GET`, distinct from this file's other `POST`-shaped token
      methods (Red).
- [x] 1.4. Implement `generateAccessGroupDeletionToken` in
      `member.service.ts` (Green).
- [x] 1.5. Write `member.service.spec.ts` case for
      `deleteAccessGroup(tenantId, accessGroupId, word)`:
      `DELETE /api/tenants/{tenantId}/access-groups/{accessGroupId}` with
      `{ word }` body, `204` response (Red).
- [x] 1.6. Implement `deleteAccessGroup` in `member.service.ts` (Green).
- [x] 1.7. Write `member.service.spec.ts` case for
      `grantAccessGroupPermission(tenantId, accessGroupId, permission)`:
      `POST /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions`
      with `{ permission }` body, `204` response (Red).
- [x] 1.8. Implement `grantAccessGroupPermission` in `member.service.ts`
      (Green).
- [x] 1.9. Run `npm run format:check && npm test && npm run build && npm run lint`;
      commit `feat(member-service): add tenant access-group bulk-assign, delete, and grant-permission clients`.

## 2. `tenantAccessGroupManagementGuard`

- [x] 2.1. Write `tenant-access-group-management.guard.spec.ts`: caller
      with `TENANT_ACCESS_GROUP_VIEW` in the `GET /api/tenants/permissions`
      response → guard resolves `true` (Red).
- [x] 2.2. Implement `tenantAccessGroupManagementGuard` in
      `core/tenant-access-group-management.guard.ts` (minimal — permission
      present path only) (Green).
- [x] 2.3. Write test: permission absent from the response → guard
      resolves a `UrlTree` to `/select-tenant` (Red).
- [x] 2.4. Extend the guard to redirect on missing permission (Green).
- [x] 2.5. Write test: `GET /api/tenants/permissions` errors (network
      failure) → guard resolves the same `/select-tenant` `UrlTree`
      rather than letting the observable error propagate into Router as
      a failed navigation (the AppSec-required `catchError` case) (Red).
- [x] 2.6. Add the `catchError(() => of(router.parseUrl('/select-tenant')))`
      pipe before the `map` in the guard (Green).
- [x] 2.7. Write test confirming the guard checks specifically
      `TENANT_ACCESS_GROUP_VIEW` and ignores the presence of any other
      permission in the same response (Red — should already pass if 2.2/2.4
      implemented correctly; add if not already covered).
- [x] 2.8. Run full verification; commit
      `feat(tenant-access-group-guard): add TENANT_ACCESS_GROUP_VIEW route guard`.

## 3. `MemberAccessGroupAssignmentComponent` (checkbox-matrix)

- [x] 3.1. Write `member-access-group-assignment.component.spec.ts`:
      renders one checkbox per `allGroups` input, pre-checked for ids
      present in `assignedGroupIds` (Red).
- [x] 3.2. Implement the component's template/inputs (`allGroups`,
      `assignedGroupIds`) (Green).
- [x] 3.3. Write test: toggling checkboxes and submitting emits
      `submitted` with exactly the checked id set, including the
      single-checked-id case (Red).
- [x] 3.4. Implement toggle state + `submitted` output (Green).
- [x] 3.5. Run full verification; commit
      `feat(access-groups): add MemberAccessGroupAssignmentComponent checkbox-matrix`.

## 4. `TenantAccessGroupManagementPageComponent` — list, create, roster

- [x] 4.1. Write `tenant-access-group-management-page.component.spec.ts`:
      on init, calls `listAccessGroups(tenantId)` and renders the result
      via `SharedListComponent` (REQ-1) (Red).
- [x] 4.2. Implement the component shell + group list load (Green).
- [x] 4.3. Write test: list load 403 → `NoAccessStateComponent` rendered,
      no further requests fired (REQ-2/16 permission-denied branch)
      (Red).
- [x] 4.4. Implement the 403→`NoAccessStateComponent` branch (Green).
- [x] 4.5. Write test: list load non-403 failure →
      `ErrorStateComponent` (network branch, REQ-16) (Red).
- [x] 4.6. Implement the network-error branch (Green).
- [x] 4.7. Write test: with `TENANT_ACCESS_GROUP_CREATE`, submitting the
      create-group form calls `createAccessGroup` and refreshes the list
      (REQ-4) (Red).
- [x] 4.8. Implement the create-group form + submit handler (Green).
- [x] 4.9. Write test: without `TENANT_ACCESS_GROUP_CREATE`, the
      create-group control is not offered/does not submit (REQ-5) (Red).
- [x] 4.10. Implement the permission gate on the create control (Green).
- [x] 4.11. Write test: no group selected → roster/detail section is not
      rendered at all (REQ-12) (Red).
- [x] 4.12. Implement the `selectedGroup()`-gated conditional rendering
      (Green).
- [x] 4.13. Write test: selecting a group calls `list(tenantId)` once
      plus `getDetail(tenantId, membershipId)` once per member, and
      renders the current roster vs. not-yet-assigned candidates split
      by that group's id (REQ-3) (Red).
- [x] 4.14. Implement the roster fetch (`forkJoin` over member details,
      cached in a `Map<membershipId, MemberDetail>`) and the
      roster/candidates `computed()`s (Green).
- [x] 4.15. Write test: selecting a second group within the same screen
      visit re-filters the cached `memberDetails` map without calling
      `getDetail` again (assert call count unchanged) (Red).
- [x] 4.16. Implement the cache-reuse path (Green).
- [x] 4.17. Run full verification; commit
      `feat(tenant-access-groups): add list, create, and roster-detail view`.

## 5. `TenantAccessGroupManagementPageComponent` — grant permission

- [x] 5.1. Write test: with `TENANT_PERMISSION_GRANT_CREATE`, submitting
      the grant-permission control calls
      `grantAccessGroupPermission(tenantId, groupId, permission)` and
      reflects the grant against the selected group (REQ-6) (Red).
- [x] 5.2. Implement the grant-permission control + submit handler
      (Green).
- [x] 5.3. Write test: without `TENANT_PERMISSION_GRANT_CREATE`, the
      grant-permission control is not offered/does not submit (part of
      REQ-17's per-action gating) (Red).
- [x] 5.4. Implement the permission gate on the grant control (Green).
- [x] 5.5. Write test: grant-permission request 403 →
      `NoAccessStateComponent`/permission-denied outcome, consistent with
      every other gated action (Red).
- [x] 5.6. Implement the 403 branch for the grant action (Green).
- [x] 5.7. Run full verification; commit
      `feat(tenant-access-groups): add grant-permission-to-group action`.

## 6. Single assign/unassign (existing confirm-token flow)

- [x] 6.1. Write test: with `TENANT_PERMISSION_GRANT_CREATE`, choosing a
      single candidate + single group calls
      `assignAccessGroup(tenantId, membershipId, accessGroupId)` and
      re-fetches the roster after success (REQ-7, REQ-10's re-fetch rule)
      (Red).
- [x] 6.2. Implement the single-assign path (Green).
- [x] 6.3. Write test: with `TENANT_PERMISSION_GRANT_DELETE`, unassigning
      a roster member opens `ConfirmDialogComponent`, fetches the token
      via `generateAccessGroupUnassignmentToken`, and on confirmed submit
      calls `unassignAccessGroup` then re-fetches the roster (REQ-8)
      (Red).
- [x] 6.4. Implement the unassign confirm-dialog round trip (Green).
- [x] 6.5. Write test: wrong confirmation word on unassign → dialog
      stays open for retry, no `unassignAccessGroup` call made (Red).
- [x] 6.6. Implement the wrong-word retry path (Green).
- [x] 6.7. Write test: unassign request 403 → permission-denied outcome,
      roster unchanged (Red).
- [x] 6.8. Implement the 403 branch for unassign (Green).
- [x] 6.9. Write test: without `TENANT_PERMISSION_GRANT_CREATE`/
      `TENANT_PERMISSION_GRANT_DELETE` respectively, assign/unassign
      controls are not offered (REQ-17's per-action gating, mirrors 4.9)
      (Red).
- [x] 6.10. Implement the permission gates on assign/unassign controls
      (Green).
- [x] 6.11. Run full verification; commit
      `feat(tenant-access-groups): add single member assign/unassign`.

## 7. Bulk-assign wiring (`MemberAccessGroupAssignmentComponent`)

- [x] 7.1. Write test: opening the multi-group assignment entry point for
      a candidate renders `MemberAccessGroupAssignmentComponent` with
      that member's current group ids as `assignedGroupIds` and the full
      `groups()` list as `allGroups` (Red).
- [x] 7.2. Implement the entry point + component wiring (Green).
- [x] 7.3. Write test: a `submitted` event with more than one id calls
      `batchAssignAccessGroups(tenantId, membershipId, ids)` and
      re-fetches the roster after success — never patches roster state
      optimistically (REQ-9/10) (Red).
- [x] 7.4. Implement the bulk-assign submit handler (Green).
- [x] 7.5. Write test: a `submitted` event with exactly one id reuses the
      same handler and still results in a correct roster re-fetch
      (confirms REQ-7's single-assign path composes with this component
      without special-casing "exactly one" — per PLAN, wire it through
      the existing single-assign service call, not the batch endpoint,
      when only one id is selected) (Red).
- [x] 7.6. Implement the single-vs-batch call selection based on selected
      id count (Green).
- [x] 7.7. Write test: batch request `400` (partial validation failure)
      → generic/network error branch (not permission-denied), roster
      re-fetched showing only backend-confirmed state (REQ-10) (Red).
- [x] 7.8. Implement the 400 branch for the batch call (Green).
- [x] 7.9. Write test: batch request 403 → permission-denied outcome,
      no assignments reflected (REQ-11) (Red).
- [x] 7.10. Implement the 403 branch for the batch call (Green).
- [x] 7.11. Run full verification; commit
      `feat(tenant-access-groups): wire multi-group bulk assignment`.

## 8. Delete-group action (new confirm-token flow)

- [x] 8.1. Write test: with `TENANT_ACCESS_GROUP_DELETE`, requesting
      delete on a group opens `ConfirmDialogComponent`, fetches the token
      via `generateAccessGroupDeletionToken`, and on confirmed submit
      calls `deleteAccessGroup` (REQ-13) (Red).
- [x] 8.2. Implement the delete confirm-dialog round trip (Green).
- [x] 8.3. Write test: successful delete removes the group from the list
      and, if it was the selected group, clears the selection and closes
      the roster/detail section (REQ-14) (Red).
- [x] 8.4. Implement the post-delete list/selection update (Green).
- [x] 8.5. Write test: wrong confirmation word on delete → dialog stays
      open for retry, no `deleteAccessGroup` call made (Red).
- [x] 8.6. Implement the wrong-word retry path (Green).
- [x] 8.7. Write test: delete request 403 → permission-denied outcome,
      group remains in the list (REQ-15) (Red).
- [x] 8.8. Implement the 403 branch for delete (Green).
- [x] 8.9. Write test: without `TENANT_ACCESS_GROUP_DELETE`, the delete
      control is not offered (REQ-17's per-action gating, mirrors 4.9)
      (Red).
- [x] 8.10. Implement the permission gate on the delete control (Green).
- [x] 8.11. Run full verification; commit
      `feat(tenant-access-groups): add delete-group action`.

## 9. Route registration

- [x] 9.1. Write/extend `app.routes.spec.ts` (or the closest existing
      route-config test precedent) asserting `tenants/access-groups`
      registers with `canActivate: [tenantSelectionGuard,
      tenantAccessGroupManagementGuard]` in that exact order (Red).
- [x] 9.2. Add the route to `app.routes.ts` per PLAN.md's exact
      `loadComponent`/`canActivate` block (Green).
- [x] 9.3. Run full verification; commit
      `feat(routes): register /tenants/access-groups route`.

## 10. Nav-menu entry

- [x] 10.1. Write/extend `nav-menu.component.spec.ts`: the tenant
      access-groups link renders only when
      `permissionsService.has('TENANT_ACCESS_GROUP_VIEW')` is true, mirroring
      the existing `/members` link's gating (Red).
- [x] 10.2. Add the nav-menu entry (link + icon + gating) in
      `nav-menu.component.ts` (Green).
- [x] 10.3. Run full verification; commit
      `feat(nav-menu): add tenant Access groups link (TENANT_ACCESS_GROUP_VIEW)`.

## 11. Final pass

- [x] 11.1. Full-repo search for any leftover TODO/stub related to this
      feature (e.g. an unused import from an earlier deviation); fix any
      found.
- [x] 11.2. Run `npm run format`, then
      `npm run format:check && npm test && npm run build && npm run lint`
      across the whole subproject; confirm all green.
- [x] 11.3. Update `PLAN.md` if any decision changed during
      implementation (deviations noted inline, same convention as
      `staff-members-management-redesign/PLAN.md`'s "Deviations from this
      PLAN" entries).
- [x] 11.4. Update `PROJECT_STATUS.md` with what shipped (new screen,
      new guard, new service methods, nav entry) and note the two
      backend dependencies (`tenant-access-group-bulk-and-delete`) this
      feature consumed.
