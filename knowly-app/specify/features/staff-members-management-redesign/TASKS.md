# TASKS — staff-members-management-redesign

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> TDAD: test first (Red), then minimal code (Green), then
> `npm run format:check && npm test && npm run build && npm run lint`.
> Commit after each numbered task per CLAUDE.md's standing instruction.

## 0. Pre-work / gap check

- [x] 0.1. **Resolved (amendment, 2026-08-02)**: `StaffUserDetailDto`/
      `MemberDetailDto` gain `isLastAdminOfType: boolean`, per
      `staff-rbac-management-operations/PLAN.md`'s amendment. The
      switches-batch/demote/delete tasks below consume it directly — no
      graceful-degradation fallback needed.
- [x] 0.2. **Resolved (amendment, 2026-08-02)**: SPEC.md now specifies
      promotion (REQ-7b–REQ-7e), confirmed by the product owner. Tasks
      5.x/6.x below include the promote-UI implementation.

## 1. `SharedListComponent` core

- [ ] 1.1. Write `shared-list.component.spec.ts`: renders header
      (title + "showing X-Y of Z"), column headers, and rows from
      `columns`/`rows` inputs (Red).
- [ ] 1.2. Implement minimal `SharedListComponent` template/inputs to
      pass 1.1 (Green).
- [ ] 1.3. Write tests for checkbox selection (row + select-all +
      indeterminate state) and `selectionChange` output (Red).
- [ ] 1.4. Implement selection logic (Green).
- [ ] 1.5. Write tests for sortable column header click cycling
      `none → asc → desc → none`, `aria-sort` attribute, `sortChange`
      output (Red).
- [ ] 1.6. Implement sorting (Green).
- [ ] 1.7. Write tests for search input filtering rows client-side
      (Red).
- [ ] 1.8. Implement search filtering (Green).
- [ ] 1.9. Write tests for loading (skeleton rows), empty, no-results
      (with "clear filters"), and error (`ErrorStateComponent`/
      `NoAccessStateComponent`) states (Red).
- [ ] 1.10. Implement the four states (Green).
- [ ] 1.11. Write tests for row-actions rendering (icon buttons,
      `disabled`/`title` when a `disabled(row)` function returns true)
      (Red).
- [ ] 1.12. Implement row-actions column (Green).
- [ ] 1.13. Write tests for responsive column collapse
      (`hidden sm:table-cell` presence on non-essential columns) and
      focus-visible ring classes on interactive elements (Red).
- [ ] 1.14. Implement responsive/a11y classes (Green).
- [ ] 1.15. Add `sharedList.*` i18n keys to `en.json`/`pt-BR.json` per
      SPEC's "i18n keys to add" list.
- [ ] 1.16. Run full verification; commit
      `feat(shared-list): add reusable list/table component`.

## 2. Staff directory migration

- [x] 2.1. Write updated `staff-directory-page.component.spec.ts`
      assertions expecting `app-shared-list` with the staff-directory
      column/row-action config (Red).
- [x] 2.2. Rewrite `StaffDirectoryPageComponent` to consume
      `SharedListComponent` (Green).
- [x] 2.3. Run verification; commit
      `refactor(staff-directory,members): migrate to SharedListComponent`
      (`374a7ae`, combined with Task 3 — small, identical migration).

## 3. Tenant members migration

- [x] 3.1. Write updated `members-page.component.spec.ts` assertions for
      `app-shared-list` usage (Red).
- [x] 3.2. Rewrite `MembersPageComponent` to consume `SharedListComponent`
      (Green).
- [x] 3.3. Run verification; commit (see 2.3 — same commit `374a7ae`).

## 4. Permission label + audit-trail translation maps

- [ ] 4.1. Grep `knowly-api/src/main/java` for every audit action-string
      literal (`@AuditLog`/`AuditService`/`AuditEventPublisher` call
      sites) and list them (working note, not committed as a file).
- [ ] 4.2. Write `permission-labels.spec.ts` (known value → translated
      label; unknown value → raw fallback) (Red).
- [ ] 4.3. Implement `permission-labels.ts` + `permissions.*` i18n keys
      for every `Permission`/`GlobalPermission` enum value per
      `permission-granularity-model/PLAN.md` (Green).
- [ ] 4.4. Write `audit-trail-labels.spec.ts` (known action/action+outcome
      → translated phrase; unknown → raw fallback) (Red).
- [ ] 4.5. Implement `audit-trail-labels.ts` covering every action found
      in 4.1, plus i18n keys (Green).
- [ ] 4.6. Write `audit-timestamp.spec.ts` (ISO input → local compact
      format) (Red).
- [ ] 4.7. Implement `audit-timestamp.ts` (Green).
- [ ] 4.8. Run verification; commit
      `feat(i18n): add permission and audit-trail translation maps`.

## 5. Staff user detail panel reorg

- [x] 5.1–5.2. "Editar perfil" moved into a new top `<header>`, before all
      other sections (REQ-28); `ProfileSectionComponent` gained
      `hideEditToggle`/`editTrigger` inputs so the panel drives its
      `editing` state externally instead of duplicating the toggle.
- [x] 5.3–5.4. Admin-tier (`STAFF_ADMIN`) target: no permission section at
      all, only a "Demover para STAFF" action gated by REQ-7a
      (`viewerIsStaffAdmin`), disabled+explained via the detail
      response's real `isLastAdminOfType` field (REQ-5) — no
      attempt-then-fail round trip. Wired to
      `POST /api/staff/users/{userId}/demote`.
- [x] 5.4a–5.4b. `STAFF` target shows "Promover a STAFF_ADMIN"
      (REQ-7b/7c/7d, never disabled), wired to
      `POST /api/staff/users/{userId}/promote`. **Deviation from
      PLAN.md**: confirmed via a plain inline two-button confirm step,
      not `ConfirmDialogComponent` — the promote/demote endpoints have no
      deletion-confirmation-token endpoint (confirmed against
      `StaffController.java`/`TenantController.java`), so there is no
      `fetchToken` for `ConfirmDialogComponent` to call; `ConfirmDialogComponent`
      remains reserved for the four flows that do have a token endpoint
      (delete, batch-save, permission-revoke, group-unassign).
- [x] 5.5–5.6. Delete action moved into the panel's bottom action area,
      offered for every role, disabled+explained only for the last
      `STAFF_ADMIN` (REQ-9/11), hidden entirely for an admin target when
      the viewer isn't a `STAFF_ADMIN` (REQ-12a). Wired to
      `ConfirmDialogComponent` +
      `POST /api/staff/users/{userId}/deletion-confirmation-token` +
      `DELETE /api/staff/users/{userId}`.
- [x] 5.7–5.8. `STAFF` target renders switches (`role="switch"`), seeded
      from `directPermissions`, toggling only mutates the local
      `pendingPermissions` signal — no HTTP call per toggle (REQ-15/16).
- [x] 5.9–5.10. "Save" hidden with zero pending changes (REQ-19); with
      changes, opens one `ConfirmDialogComponent` for the whole batch
      (REQ-17/18) and submits the full `pendingPermissions()` set to
      `PUT /api/staff/users/{userId}/permissions/batch`.
- [x] 5.11–5.12. Permission names (switches + effective-permissions
      section) rendered via `translatePermissionLabel` (REQ-13/14).
- [ ] 5.13–5.14. **Deferred to Task 4** (audit-trail translation
      map/timestamp formatting) — not in this pass's scope; audit trail
      still renders raw `action`/`outcome`/ISO timestamp, unchanged from
      before this task.
- [x] 5.15–5.16. Inline access-group creation form removed from this
      panel entirely (REQ-24) — no `newAccessGroupName`/`onCreateAccessGroup`
      remain; group creation is deferred to Task 7's standalone screen.
- [x] 5.17. Full verification run (`format:check && test && build &&
      lint`, 581/581 tests green); commit
      `refactor(staff-user-detail): redesign per staff-members-management-redesign`.

## 6. Tenant member detail panel reorg

- [x] 6.1–6.14. Mirrored 5.1–5.12/5.15–5.16 (including the promote
      action, same plain-confirm deviation noted above) for
      `MemberDetailPanelComponent` against the tenant-scope endpoints
      (demote/promote/hard-delete/batch per
      `/api/tenants/{tenantId}/members/{membershipId}/...`), including the
      REQ-6/REQ-10 (`MEMBER_ADMIN`, per-tenant floor, via
      `isLastAdminOfType`) and REQ-7a/7c/12a viewer-gating variants.
      Audit-trail translation (mirrors 5.13/5.14) deferred to Task 4 —
      this panel has no audit-trail section today, so nothing to defer
      there beyond Task 4 itself.
- [x] 6.15. Full verification run; commit
      `refactor(member-detail): redesign per staff-members-management-redesign`.

## 7. Access group management screen

- [ ] 7.1. Write `access-group-management-page.component.spec.ts`:
      create group, list groups via `app-shared-list`, expand/view
      members (Red).
- [ ] 7.2. Implement `AccessGroupManagementPageComponent` (Green).
- [ ] 7.3. Write tests: assign/unassign a `STAFF`/`MEMBER` user to/from a
      group; `STAFF_ADMIN`/`MEMBER_ADMIN` never offered as assignable
      candidates (REQ-23) (Red).
- [ ] 7.4. Implement assignment UI with the role filter (Green).
- [ ] 7.5. Add `/staff/access-groups` route with its permission-specific
      guard (mirroring `staffGuard`'s fixed pattern, checked against
      `GET /api/staff/permissions`).
- [ ] 7.6. Run full verification; commit
      `feat(access-groups): add standalone access-group management screen`.

## 8. Final pass

- [ ] 8.1. Full-repo search for any remaining raw permission-enum or raw
      audit-action rendering outside the new translation maps; fix any
      found.
- [ ] 8.2. Run `npm run format`, then
      `npm run format:check && npm test && npm run build && npm run lint`
      across the whole subproject.
- [ ] 8.3. Update `PLAN.md`/this file if any decision changed during
      implementation.
- [ ] 8.4. Update `PROJECT_STATUS.md` with what shipped (both gaps from
      task 0 — promotion UI and `isLastAdminOfType` — are resolved as of
      this amendment, not open follow-ups).
