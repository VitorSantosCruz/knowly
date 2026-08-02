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

- [ ] 2.1. Write updated `staff-directory-page.component.spec.ts`
      assertions expecting `app-shared-list` with the staff-directory
      column/row-action config (Red).
- [ ] 2.2. Rewrite `StaffDirectoryPageComponent` to consume
      `SharedListComponent` (Green).
- [ ] 2.3. Run verification; commit
      `refactor(staff-directory): migrate to SharedListComponent`.

## 3. Tenant members migration

- [ ] 3.1. Write updated `members-page.component.spec.ts` assertions for
      `app-shared-list` usage (Red).
- [ ] 3.2. Rewrite `MembersPageComponent` to consume `SharedListComponent`
      (Green).
- [ ] 3.3. Run verification; commit
      `refactor(members): migrate to SharedListComponent`.

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

- [ ] 5.1. Write tests: "Editar perfil" rendered in top header region,
      before permission/audit sections (Red).
- [ ] 5.2. Move "Editar perfil" trigger into new header area (Green).
- [ ] 5.3. Write tests: admin-tier (`STAFF_ADMIN`) target shows no
      permission checkboxes, only a "Demover para STAFF" action, gated by
      REQ-7a viewer-role visibility, disabled+explained per REQ-5 driven
      by the detail response's `isLastAdminOfType` field (no attempt-
      then-fail round trip) (Red).
- [ ] 5.4. Implement admin-tier view + demote action wiring to
      `POST /api/staff/users/{userId}/demote`, reading `isLastAdminOfType`
      straight off the already-fetched detail response (Green).
- [ ] 5.4a. Write tests: `STAFF` target shows a "Promover a STAFF_ADMIN"
      action, gated by REQ-7c viewer-role visibility, never disabled
      (REQ-7d) (Red).
- [ ] 5.4b. Implement promote action wiring to
      `POST /api/staff/users/{userId}/promote`, confirmed via
      `ConfirmDialogComponent`, refreshing detail on success (REQ-7e)
      (Green).
- [ ] 5.5. Write tests: delete action available for all roles, disabled
      +explained for last-`STAFF_ADMIN` target (REQ-9) via
      `isLastAdminOfType`, gated by REQ-12a viewer-role visibility for
      admin targets (Red).
- [ ] 5.6. Implement delete action wired to existing
      `ConfirmDialogComponent` +
      `POST /api/staff/users/{userId}/deletion-confirmation-token` +
      `DELETE /api/staff/users/{userId}` (Green).
- [ ] 5.7. Write tests: non-admin (`STAFF`) target renders switches (not
      checkboxes) seeded from `directPermissions`, toggling changes only
      local state, no HTTP call per toggle (Red).
- [ ] 5.8. Implement switches + local `pendingPermissions` signal
      (Green).
- [ ] 5.9. Write tests: "Save" hidden/disabled with zero pending changes
      (REQ-19); with changes, clicking Save opens one confirm dialog for
      the whole batch (REQ-17/18) (Red).
- [ ] 5.10. Implement Save + batch confirm dialog wiring to
      `POST /api/staff/users/{userId}/permissions/batch/deletion-confirmation-token`
      + `PUT /api/staff/users/{userId}/permissions/batch` (Green).
- [ ] 5.11. Write tests: permission labels rendered via
      `permission-labels.ts` translation, not raw enum (REQ-13) (Red).
- [ ] 5.12. Wire translation map into the switches list and any
      remaining permission display (Green).
- [ ] 5.13. Write tests: audit trail renders local-format timestamps and
      translated action+outcome phrases, raw fallback for unknown (Red).
- [ ] 5.14. Wire `audit-timestamp.ts`/`audit-trail-labels.ts` into the
      existing audit-trail rendering (Green).
- [ ] 5.15. Write tests: inline group-creation affordance removed from
      this panel (REQ-24) (Red).
- [ ] 5.16. Remove inline group creation from this component (Green).
- [ ] 5.17. Run full verification; commit
      `refactor(staff-user-detail): redesign per staff-members-management-redesign`.

## 6. Tenant member detail panel reorg

- [ ] 6.1–6.14. Mirror tasks 5.1–5.14 (including 5.4a/5.4b's promote
      action) for `MemberDetailPanelComponent` against the tenant-scope
      endpoints (demote/promote/hard-delete/batch per
      `/api/tenants/{tenantId}/members/{membershipId}/...`), including
      the REQ-6/REQ-10 (`MEMBER_ADMIN`, per-tenant floor, via
      `isLastAdminOfType`) and REQ-7a/7c/12a viewer-gating variants.
- [ ] 6.15. Run full verification; commit
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
