# TASKS — design system consistency pass

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> TDAD: test first (Red), then minimal code (Green), then `npm test`.
> Full verification (`npm run format:check && npm test && npm run build
> && npm run lint`) is required before any task/checkpoint is considered
> committable, per `knowly-app/CLAUDE.md`.

## Dependency note (read before starting)

Tasks are grouped so that everything **except Group 5** (the new
`staff-user-audit-page.component.ts` and its route) is independent of
the backend `paginated-audit-trail` feature and can be implemented and
committed in any order, in parallel with that backend work. **Group 5
depends on `knowly-api`'s `paginated-audit-trail` feature being
implemented and its endpoint (`GET
/api/staff/users/{userId}/audit-trail?page=&size=`) available** — do
not start Group 5 until that endpoint exists (or, at minimum, until
`knowly-api/specify/features/paginated-audit-trail/PLAN.md`'s response
shape is confirmed final, since this PLAN's contract table was
transcribed from the backend SPEC pending that PLAN). All other groups
may proceed now.

Suggested order: Group 1 (shared-list server-pagination mode) first,
since Groups 2, 4, and 5 all consume it. Groups 3, 6, 7 are independent
of Group 1 and of each other.

---

## Group 1 — `SharedListComponent` server-pagination mode

- [ ] 1. Write `shared-list.component.spec.ts` cases (Red): when
      `serverPagination()` is non-null, `visibleRows()` returns `rows()`
      unchanged (no client-side filter/sort applied even if a search
      term is present).
- [ ] 2. Implement `SharedListServerPagination` in `shared-list.model.ts`
      and the `serverPagination` input + `visibleRows()`/`totalCount()`
      branching in `shared-list.component.ts` for task 1's test (Green).
- [ ] 3. Write spec cases (Red): prev/next buttons render only when
      `serverPagination()` is non-null and `totalPages > 1`, are
      disabled at `page === 0` / `page === totalPages - 1`, and emit
      `pageChange` with `-1`/`1`.
- [ ] 4. Implement the prev/next control block and `pageChange` output
      for task 3's tests (Green).
- [ ] 5. Write spec cases (Red): `searchChange` emits the typed term in
      both memory and server-pagination modes.
- [ ] 6. Implement `searchChange` output, wired from the existing
      `onSearch`, for task 5's tests (Green).
- [ ] 7. Write spec cases (Red): `rowClick` emits the clicked row on
      `<tr>` click, in both modes.
- [ ] 8. Implement `rowClick` output for task 7's tests (Green).
- [ ] 9. Run `npm run format:check && npm test && npm run build && npm run lint`,
      confirm green, commit
      (`feat(design-system-consistency-pass): add server-pagination mode to shared-list`).

## Group 2 — `select-tenant-page` migration to `app-shared-list`

- [ ] 10. Write `select-tenant-page.component.spec.ts` cases (Red),
       adapted to `app-shared-list` testids: fallback branch passes
       `serverPagination` with the component's existing
       `page`/`totalPages`/`totalElements` signals; membership-list
       branch passes `null`.
- [ ] 11. Implement the `<app-shared-list>` template swap in
       `select-tenant-page.component.ts` for task 10 (Green) — remove the
       hand-rolled `<ul>`/pagination markup; keep
       `fetchFallbackTenants()` unchanged.
- [ ] 12. Write spec cases (Red): the delete row action is
       `LucideTrash`, icon-only, present only when `canDeleteTenant()` is
       true (empty `rowActions()` array otherwise), and clicking it
       still opens the existing `ConfirmDialogComponent` flow unchanged.
- [ ] 13. Implement `rowActions` as `computed(() => this.canDeleteTenant() ? [...] : [])`
       using `LucideTrash` for task 12 (Green).
- [ ] 14. Write spec cases (Red): clicking a row (`rowClick`) still
       selects/navigates into that tenant, same as the old `<button>`
       click did.
- [ ] 15. Wire `(rowClick)` to the existing row-selection handler for
       task 14 (Green).
- [ ] 16. Write spec cases (Red): `(searchChange)`/`(pageChange)` trigger
       the same debounce+refetch behavior the old hand-rolled
       input/buttons triggered (existing pagination/search test cases
       re-targeted to the new event bindings).
- [ ] 17. Wire `(searchChange)`/`(pageChange)` to the existing
       debounce/refetch logic for task 16 (Green).
- [ ] 18. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`refactor(design-system-consistency-pass): migrate select-tenant-page to shared-list`).

## Group 3 — Members: edit/delete as list actions, own-row "my profile"

- [ ] 19. Write `member-detail-panel.component.spec.ts` case (Red):
       `openInEditMode()` sets the membership signal and increments
       `editProfileTrigger` in one call.
- [ ] 20. Implement `openInEditMode()` on `member-detail-panel.component.ts`
       for task 19 (Green).
- [ ] 21. Write `member-detail-panel.component.spec.ts` cases (Red):
       delete button, `pendingDelete`/`deleteRetryToken` signals, and
       `deletionTokenFetcher`/`confirmDelete`/`cancelDelete` are gone
       (assert absence / removed from the template).
- [ ] 22. Remove the delete affordance and its state/methods from
       `member-detail-panel.component.ts` for task 21 (Green).
- [ ] 23. Write `members-page.component.spec.ts` cases (Red): clicking
       the edit row action (`LucideSquarePen`) calls
       `openInEditMode(id)` on the panel.
- [ ] 24. Implement the edit `SharedListRowAction` on
       `members-page.component.ts`, calling `openInEditMode` for task 23
       (Green).
- [ ] 25. Write `members-page.component.spec.ts` cases (Red): clicking
       the delete row action (`LucideTrash`) opens
       `<app-confirm-dialog>` directly, with no precondition that the
       panel be open, using the page's own
       `pendingDelete`/`deleteRetryToken` signals calling
       `MemberService`'s existing removal-token/confirm methods; on
       confirm, the members list reloads (`loadMembers`).
- [ ] 26. Implement the delete row action + page-level confirm-dialog
       flow on `members-page.component.ts` for task 25 (Green).
- [ ] 27. Write `members-page.component.spec.ts` cases (Red): `ownUserId`
       is loaded once via `ProfileService.getOwnProfile()`; the viewer's
       own row's `rowActions` omits both the edit action and the
       delete action, replaced with a "my profile" action whose
       `onClick` calls `router.navigateByUrl('/profile')`; all other
       rows are unaffected.
- [ ] 28. Implement `ownUserId` signal + the `computed()` `rowActions`
       own-row branch (omitting edit *and* delete, per appsec review)
       on `members-page.component.ts` for task 27 (Green).
- [ ] 29. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`feat(design-system-consistency-pass): move member edit/delete to list actions, add own-row profile link`).

## Group 4 — Staff directory: edit/delete/history as list actions, permission-gated history

- [ ] 30. Write `staff-user-detail-panel.component.spec.ts` case (Red):
       `openInEditMode()` behaves identically to
       `member-detail-panel`'s.
- [ ] 31. Implement `openInEditMode()` on
       `staff-user-detail-panel.component.ts` for task 30 (Green).
- [ ] 32. Write `staff-user-detail-panel.component.spec.ts` cases (Red):
       delete button/state removed; embedded (unpaginated) audit-trail
       section removed entirely.
- [ ] 33. Remove the delete affordance/state and the embedded
       audit-trail section from `staff-user-detail-panel.component.ts`
       for task 32 (Green).
- [ ] 34. Write `staff-directory-page.component.spec.ts` cases (Red):
       edit row action calls `openInEditMode(id)`; delete row action
       opens `<app-confirm-dialog>` directly via page-level
       `pendingDelete`/`deleteRetryToken` signals calling
       `StaffUserService`'s existing methods, reloading
       `loadStaffUsers()` on confirm.
- [ ] 35. Implement the edit + delete row actions and page-level
       confirm-dialog flow on `staff-directory-page.component.ts` for
       task 34 (Green).
- [ ] 36. Write `staff-directory-page.component.spec.ts` cases (Red):
       when `globalPermissionsService.has('AUDIT_TRAIL_VIEW')` is
       `true`, `rowActions()` includes the history action
       (`LucideHistory`) which navigates to
       `/staff/users/:userId/audit`; when it is `false`, the history
       action is absent from `rowActions()` entirely (not merely
       disabled) — this is the appsec-flagged gate: the row action must
       never be offered to a viewer the backend endpoint would reject,
       to avoid a permission-denied flash after navigation.
- [ ] 37. Implement `rowActions` as a `computed()` gated on
       `globalPermissionsService.has('AUDIT_TRAIL_VIEW')` including the
       history action only when true, on
       `staff-directory-page.component.ts`, for task 36 (Green).
- [ ] 38. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`feat(design-system-consistency-pass): move staff-user edit/delete/history to list actions, gate history on AUDIT_TRAIL_VIEW`).

## Group 5 — Staff-user audit page (blocked on backend `paginated-audit-trail`)

> Do not start until `GET /api/staff/users/{userId}/audit-trail?page=&size=`
> is implemented per `knowly-api/specify/features/paginated-audit-trail/PLAN.md`.
> If that PLAN's response field names differ from this feature's PLAN.md
> table (`content`/`page`/`size`/`totalElements`/`totalPages`), stop and
> reconcile both PLAN.md files before writing any code below — do not
> guess a shape that doesn't match the real backend.

- [x] 39. Confirm the backend endpoint's actual response shape matches
       this feature's PLAN.md contract table (or update the table with
       a documented correction if it doesn't) before proceeding.
- [x] 40. Add `getAuditTrail(userId, page, size)` to `staff-user.service.ts`
       (or equivalent) calling the confirmed endpoint, returning
       `PageResponse<AuditEventDto>`. Write its spec (Red) first, then
       implement (Green).
- [x] 41. Write `staff-user-audit-page.component.spec.ts` cases (Red):
       reads `userId` from `ActivatedRoute.paramMap`; renders paginated
       events (one `occurredAt` column, formatted via the existing
       `formatAuditTimestamp`/`translateAuditAction` helpers) through
       `<app-shared-list>` with `serverPagination` always set, no row
       actions.
- [x] 42. Implement `staff-user-audit-page.component.ts` for task 41
       (Green).
- [x] 43. Write spec cases (Red): changing page (`pageChange`) re-fetches
       the next page from the service; `permission-denied`/`network`
       error states render the existing shared error components,
       matching the `'network' | 'permission-denied' | null` convention.
- [x] 44. Implement the page-change refetch and error-state handling for
       task 43 (Green).
- [x] 45. Write `staff-directory-page.component.spec.ts` case (Red)
       (extends task 36/37's suite): clicking the history row action
       navigates to `/staff/users/:userId/audit`.
- [x] 46. Add the `{ path: 'staff/users/:userId/audit', component:
       StaffUserAuditPageComponent, canActivate: [staffGuard] }` route
       to `app.routes.ts` and wire the history action's navigation for
       task 45 (Green).
- [x] 47. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`feat(design-system-consistency-pass): add paginated staff-user audit-trail page`).

## Group 6 — Sidebar collapse/expand (`SidebarStateService`, desktop rail)

- [ ] 48. Write `sidebar-state.service.spec.ts` cases (Red):
       `toggle()`/`setCollapsed()` persist `collapsed` to
       `localStorage` under `knowly.sidebar.collapsed` and read it back
       on construction; `setMobileOpen()` never persists;
       `viewportIsDesktop` reflects a mocked `matchMedia(...)` result
       and updates on a simulated `change` event.
- [ ] 49. Implement `core/sidebar-state.service.ts`
       (`collapsed`/`mobileOpen`/`viewportIsDesktop` signals,
       `toggle()`/`setCollapsed()`/`setMobileOpen()`, `matchMedia`
       listener cleaned up via `DestroyRef`) for task 48 (Green).
- [ ] 50. Write `nav-menu.component.spec.ts` cases (Red): existing
       `data-testid`/`data-tour-id` assertions still pass regardless of
       `collapsed()` (labels hidden via class toggle, not removed from
       the DOM); the collapse/expand toggle button flips `collapsed()`
       and its `aria-expanded` attribute; a collapsed item's tooltip
       `<span>` is present with `group-hover:opacity-100
       group-focus-visible:opacity-100` classes.
- [ ] 51. Implement the label `<span>` visibility toggle, hover-tooltip
       `<span>`, and the collapse/expand toggle button (with
       `aria-expanded`, `aria-controls="nav-menu"`) in
       `nav-menu.component.ts` for task 50 (Green).
- [ ] 52. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`feat(design-system-consistency-pass): add desktop sidebar collapse/expand with hover tooltips`).

## Group 7 — Mobile off-canvas sidebar (`app-shell`)

- [ ] 53. Write `app-shell.component.spec.ts` cases (Red): at
       `viewportIsDesktop() === false`, the backdrop renders only when
       `mobileOpen()` is true; a mobile toggle button (visible only
       below `md:`) calls `setMobileOpen(true)`.
- [ ] 54. Implement the mobile toggle button and conditional backdrop
       `<div>` in `app-shell.component.ts` for task 53 (Green).
- [ ] 55. Write spec cases (Red): backdrop click, `Escape` keydown, and
       a route change (`NavigationEnd`) each call `setMobileOpen(false)`.
- [ ] 56. Implement the three close triggers for task 55 (Green).
- [ ] 57. Write spec cases (Red): the `<aside>` width is conditional on
       `sidebarState.collapsed()` on desktop (`w-64` ↔ narrow icon-rail
       width); on mobile it uses `translate-x-full`/`translate-x-0`
       driven by `mobileOpen()`.
- [ ] 58. Implement the conditional `<aside>` classes for task 57
       (Green).
- [ ] 59. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit
       (`feat(design-system-consistency-pass): add mobile off-canvas sidebar`).

## Group 8 — Icon consistency sweep

- [ ] 60. Write/extend specs (Red) asserting `LucideTrash` (not
       `LucideTrash2`) is used for every delete action across
       `members-page`, `staff-directory-page`, and
       `select-tenant-page` (this may already be satisfied by earlier
       groups — this task is the explicit sweep/verification pass
       required by REQ-1).
- [ ] 61. Fix any remaining `LucideTrash2` usages found by task 60
       (Green) — should be none if Groups 2–4 were done correctly; if
       any exist, correct them here.
- [ ] 62. Run `npm run format:check && npm test && npm run build && npm run lint`,
       confirm green, commit (only if task 61 changed anything;
       otherwise skip the commit)
       (`fix(design-system-consistency-pass): replace remaining LucideTrash2 usages`).

## Documentation

- [ ] 63. Write the `DECISIONS.md` entry for "extending
       `SharedListComponent` with an optional server-pagination mode"
       (per the `adr-writer` skill format), as flagged in PLAN.md's
       final section. Commit alongside Group 1's commit or as its own
       docs commit (`docs: record shared-list server-pagination mode decision`).
- [ ] 64. Update `PLAN.md`'s "Consumed API contracts" section if Group
       5's task 39 found any discrepancy against the real backend
       endpoint shape, and note the correction.
- [ ] 65. Final full-repo verification: run
       `npm run format:check && npm test && npm run build && npm run lint`
       once more after all groups are merged/committed, confirming the
       complete feature is green end-to-end.
