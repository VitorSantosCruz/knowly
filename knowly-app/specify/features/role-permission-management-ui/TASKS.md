# TASKS — role-permission-management-ui

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Constraints: run `npm run format` before each commit; the full
> `npm run format:check && npm test && npm run build && npm run lint`
> is reserved for the final task only — use a scoped `npm test -- <file>`
> per task in between. Commit each completed task separately
> (Conventional Commits). Update `PLAN.md` if any decision changes
> during implementation.

## 1. i18n content — permission descriptions

- [ ] 1.1. Add `permissions.descriptions.<ENUM>` keys to
      `public/i18n/en.json` for every `Permission` and `GlobalPermission`
      value listed in SPEC.md's copy tables, verbatim (English copy
      already approved). No test — pure content; verify by grepping the
      resulting JSON parses and every enum value from
      `core/permission.ts` (`ALL_PERMISSIONS`) and the `GlobalPermission`
      equivalent has a matching key.
- [ ] 1.2. Add the same keys, translated to Portuguese (pt-BR), to
      `public/i18n/pt-BR.json`. Verify key-for-key parity with `en.json`
      (same key set, no extras/missing) via a quick diff of sorted keys.
- [ ] 1.3. `npm run format` on both JSON files; commit
      (`feat(role-permission-management-ui): add permission description
      i18n keys`).

## 2. `translatePermissionDescription` helper

- [ ] 2.1. **Red**: add a test (new `permission-labels.spec.ts` if none
      exists, else extend it) asserting `translatePermissionDescription`
      returns the translated `permissions.descriptions.<ENUM>` string for
      a known value, and falls back to the raw value when the key is
      missing — mirroring `translatePermissionLabel`'s own fallback test.
- [ ] 2.2. **Green**: implement `translatePermissionDescription` in
      `shared/permission-labels.ts` as a one-line sibling of
      `translatePermissionLabel`, same lookup shape against
      `permissions.descriptions.<value>`.
- [ ] 2.3. Run `npm test -- permission-labels`; format; commit
      (`feat(role-permission-management-ui): add
      translatePermissionDescription helper`).

## 3. `PermissionListComponent`

- [ ] 3.1. Create `shared/permission-list/permission-list.model.ts`:
      `PermissionListRow { value: string; granted: boolean }`,
      `PermissionListMode = 'editable' | 'readonly'`. No test (pure
      types).
- [ ] 3.2. **Red**: `permission-list.component.spec.ts` — renders one row
      per input `rows` entry with name (via `translatePermissionLabel`)
      + description (via `translatePermissionDescription`) text content,
      in `readonly` mode: no `role="switch"` anywhere.
- [ ] 3.3. **Green**: scaffold `PermissionListComponent` (`rows: input<
      PermissionListRow[]>`, `mode: input<PermissionListMode>`) rendering
      the row list with name+description only, no toggle yet. Run
      `npm test -- permission-list`.
- [ ] 3.4. **Red**: extend the spec — `editable` mode renders a
      `role="switch"` per row with `aria-checked` reflecting `granted`
      and `aria-label` equal to the row's translated *name* (not the
      description or adjacent text).
- [ ] 3.5. **Green**: add the switch control (reusing the existing
      two-`<span>` Tailwind `role="switch"` pattern already duplicated in
      `member-detail-panel.component.ts`/
      `staff-user-detail-panel.component.ts`), gated on `mode() ===
      'editable'`. Run `npm test -- permission-list`.
- [ ] 3.6. **Red**: extend the spec — clicking a switch, and pressing
      `Enter`/`Space` while it's focused, each emit `(toggle)` with that
      row's `value`; the component does not mutate `rows()` itself
      (assert the input array reference/contents are unchanged after the
      emit — dumb component, no internal state).
- [ ] 3.7. **Green**: add the `toggle = output<string>()` and click/
      keydown handlers emitting it, no local state mutation. Run
      `npm test -- permission-list`.
- [ ] 3.8. `npm run format`; commit (`feat(role-permission-management-ui):
      add reusable PermissionListComponent`).

## 4. `member-detail-panel.component.ts` — tabs + `PermissionListComponent`

- [ ] 4.1. **Red**: in `member-detail-panel.component.spec.ts`, add
      assertions that the panel renders a `role="tablist"` with
      "Personal data"/"Permissions" tabs (in that order), defaulting to
      "Personal data" (`activeTab` signal), and that clicking/arrow-
      keying the "Permissions" tab shows the permissions content while
      hiding the personal-data sections (and vice versa).
- [ ] 4.2. **Green**: add the `activeTab = signal<'personal' |
      'permissions'>('personal')`/`tabClass`/`selectTab`/`onTabKeydown`
      pattern (copied from `login-page.component.ts`) to
      `MemberDetailPanelComponent`; wrap the existing `access-groups`/
      `effective-permissions`/`app-profile-section` sections plus the
      admin-tier-actions/demote/promote section under the "Personal
      data" tabpanel, unchanged in content. Run
      `npm test -- member-detail-panel`.
- [ ] 4.3. **Red**: extend the spec — the old inline toggle-grid markup
      (`permission-toggle-*` buttons rendered directly in the template)
      is gone; the "Permissions" tab instead renders `app-permission-list`
      fed rows derived from `allPermissions`/`pendingPermissions`, and
      the existing toggle/batch-save/security-phrase-confirm flow
      (`onTogglePermission`, `member-save-permissions-button`,
      `app-confirm-dialog` batch dialog) still passes unmodified when
      driven via the new component's switches instead of the old
      buttons.
- [ ] 4.4. **Green**: move `direct-permissions` section's toggle grid into
      the "Permissions" tabpanel, replace the `@for` toggle-button markup
      with `<app-permission-list mode="editable" [rows]="permissionListRows()"
      (toggle)="onTogglePermission($event)" />` per PLAN, adding the
      `permissionListRows = computed(...)` per PLAN's "State and data"
      section. Run `npm test -- member-detail-panel`.
- [ ] 4.5. `npm run format`; commit (`feat(role-permission-management-ui):
      add Personal data/Permissions tabs to member-detail-panel`).

## 5. `staff-user-detail-panel.component.ts` — same tab treatment, nested in Edit mode

- [ ] 5.1. Read the current file's `viewMode`/History-Edit structure
      (commit 44e70fc) before editing, to confirm the exact branch to
      nest inside.
- [ ] 5.2. **Red**: add a regression test asserting that when
      `viewMode() === 'history'`, neither "Personal data" nor
      "Permissions" tab content renders (only the audit trail) — guards
      against the tab nesting leaking into History mode.
- [ ] 5.3. **Green**: confirm (no code change expected) this already
      holds given the panel's current structure; if it doesn't, fix the
      branch nesting so History stays untouched. Run
      `npm test -- staff-user-detail-panel`.
- [ ] 5.4. **Red**: extend the spec — inside `viewMode() === 'edit'`, the
      panel renders "Personal data"/"Permissions" tabs (in that order,
      defaulting to "Personal data"); `openInEditMode()` resets the tab
      signal to `'personal'`.
- [ ] 5.5. **Green**: add the same `activeTab`/`tabClass`/`selectTab`/
      `onTabKeydown` pattern as task 4, nested strictly inside the
      existing `viewMode() === 'edit'` branch; wrap `staff-access-groups`/
      `staff-effective-permissions`/`app-profile-section` under "Personal
      data"; have `openInEditMode()` additionally call
      `activeTab.set('personal')`. Run
      `npm test -- staff-user-detail-panel`.
- [ ] 5.6. **Red**: extend the spec — the old direct-`GlobalPermission`
      toggle-grid markup is gone from "Personal data"; "Permissions" tab
      renders `app-permission-list` wired to this panel's equivalent of
      `pendingPermissions`/`onTogglePermission`, and the existing batch-
      save/security-phrase-confirm flow still passes unmodified.
- [ ] 5.7. **Green**: replace the toggle grid with `<app-permission-list>`
      inside the "Permissions" tabpanel, mirroring task 4.4's shape for
      `GlobalPermission`. Run `npm test -- staff-user-detail-panel`.
- [ ] 5.8. `npm run format`; commit (`feat(role-permission-management-ui):
      add Personal data/Permissions tabs to staff-user-detail-panel`).

## 6. `MemberService`/`StaffUserService` — new revoke-on-role methods

- [ ] 6.1. **Red**: `member.service.spec.ts` — add a test that
      `revokeAccessGroupPermission(tenantId, accessGroupId, permission)`
      issues `DELETE
      /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions/{permission}`
      and resolves on 2xx, propagates errors on non-2xx, mirroring
      `grantAccessGroupPermission`'s existing test shape. Also assert the
      `AccessGroup` interface's `permissions: Permission[]` field is
      present (type-level; can be a compile-time check via a fixture
      object satisfying the interface).
- [ ] 6.2. **Green**: add `revokeAccessGroupPermission` to
      `MemberService` (confirm request/path shape against
      `knowly-api/specify/features/role-permission-revoke/PLAN.md`
      before finalizing) and extend the `AccessGroup` interface with
      `permissions: Permission[]`. Run `npm test -- member.service`.
- [ ] 6.3. **Red**: `staff-user.service.spec.ts` — same test shape for
      `StaffUserService.revokeAccessGroupPermission(accessGroupId,
      permission)` against `DELETE
      /api/staff/access-groups/{accessGroupId}/permissions/{permission}`,
      and the `GlobalAccessGroup` interface's `permissions:
      GlobalPermission[]` field.
- [ ] 6.4. **Green**: add the method + interface field to
      `StaffUserService`. Run `npm test -- staff-user.service`.
- [ ] 6.5. `npm run format`; commit (`feat(role-permission-management-ui):
      add access-group permission revoke methods`).

## 7. Tenant roles page — replace grant-only control with `PermissionListComponent`

- [ ] 7.1. **Red**: `tenant-access-group-management-page.component.spec.ts`
      — remove/replace the `grant-permission-form`/`grant-permission-select`
      assertions with: selecting a role renders `app-permission-list`
      seeded from `group.permissions`; toggling a row on calls
      `grantAccessGroupPermission`; toggling a row off calls the new
      `revokeAccessGroupPermission`.
- [ ] 7.2. **Green**: delete `grant-permission-form` from
      `tenant-access-group-detail`; add `<app-permission-list>` per
      PLAN's "Role pages" section, backed by the new
      `groupPermissions = signal<Set<Permission>>(new Set())` set on
      `selectGroup()`, `mode` derived from
      `canGrantPermission()`/new `canRevokePermission()`
      (`TENANT_PERMISSION_GRANT_DELETE`) computeds. Run
      `npm test -- tenant-access-group-management-page`.
- [ ] 7.3. **Red**: extend the spec — a failed grant/revoke call leaves
      the toggled row's switch in its prior `aria-checked` state
      (optimistic rollback) and shows the new inline
      `permissionActionError` message (`role="alert"`) near the list,
      without blanking the page's roster/list underneath.
- [ ] 7.4. **Green**: implement the optimistic-toggle-with-rollback
      handler per PLAN — flip `groupPermissions` immediately, revert on
      non-2xx, set `permissionActionError`. Run
      `npm test -- tenant-access-group-management-page`.
- [ ] 7.5. **Red**: extend the spec — a second click on the same row
      while its first grant/revoke call is still in flight is a no-op
      (control disabled/click ignored) until the first call resolves.
- [ ] 7.6. **Green**: add the page-level `pendingPermissions =
      signal<Set<Permission>>(new Set())` in-flight guard per PLAN,
      adding/removing the row's permission value around the HTTP call
      and gating the toggle handler/control's disabled state on it. Run
      `npm test -- tenant-access-group-management-page`.
- [ ] 7.7. `npm run format`; commit (`feat(role-permission-management-ui):
      replace tenant role grant control with PermissionListComponent`).

## 8. Staff/global roles page — new permission-editing section

- [ ] 8.1. **Red**: `access-group-management-page.component.spec.ts` —
      selecting a role reveals a new permission-list section inside
      `access-group-members-panel`, seeded from
      `group.permissions`/`ALL_GLOBAL_PERMISSIONS`, gated on
      `STAFF_PERMISSION_MANAGE` (editable vs. readonly); toggle-on calls
      `grantAccessGroupPermission`, toggle-off calls
      `revokeAccessGroupPermission`.
- [ ] 8.2. **Green**: add `<app-permission-list>` to
      `access-group-members-panel` per PLAN, with the equivalent
      `groupPermissions = signal<Set<GlobalPermission>>(new Set())` and
      mode computed. Run `npm test -- access-group-management-page`.
- [ ] 8.3. **Red**: extend the spec — same failed-call rollback +
      inline `permissionActionError` assertion as task 7.3, scoped to
      this page.
- [ ] 8.4. **Green**: implement the same optimistic rollback handler. Run
      `npm test -- access-group-management-page`.
- [ ] 8.5. **Red**: extend the spec — same in-flight double-click guard
      assertion as task 7.5, scoped to this page.
- [ ] 8.6. **Green**: implement the same `pendingPermissions` in-flight
      guard. Run `npm test -- access-group-management-page`.
- [ ] 8.7. `npm run format`; commit (`feat(role-permission-management-ui):
      add permission editing to staff/global roles page`).

## 9. Full verification and PLAN sync

- [ ] 9.1. Run `npm run format:check && npm test && npm run build &&
      npm run lint`; fix anything red.
- [ ] 9.2. Update `PLAN.md`'s "Testing strategy"/other sections if any
      decision changed during implementation (e.g. exact
      `permissionActionError` value shape, status-code handling once the
      backend's revoke endpoint is integrated against).
- [ ] 9.3. Commit any final fixups (`fix(role-permission-management-ui):
      ...` or `chore(role-permission-management-ui): ...` as
      appropriate).
