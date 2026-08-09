# PLAN — role-permission-management-ui

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Consumes the backend contract from
> `knowly-api/specify/features/role-permission-revoke/PLAN.md` (SPEC's
> requirements 1-2, 11) rather than re-deriving it.

## Architectural decisions

- **New generic, dumb presentational component `PermissionListComponent`**
  at `src/app/shared/permission-list/permission-list.component.ts` (+
  `.model.ts` for its input/output types) — one component serves both
  `Permission` and `GlobalPermission` (SPEC req 1) because both are
  plain string-value enums and the component only ever needs the raw
  string to build i18n keys and emit toggle events; no generic type
  parameter is needed, `readonly value: string`/`granted: boolean` rows
  are enough. Placed under `shared/` next to `shared-list/`, its closest
  existing sibling in shape (input-object-configured, no projected
  content).
- **The component never performs an HTTP call itself** — it renders rows
  from a `rows: PermissionListRow[]` input and, in editable mode, emits
  `(toggle)` with the row's permission value; the caller decides what
  "toggle" means. This is the load-bearing decision that lets the same
  component serve two genuinely different existing interaction models
  without forking behavior per consumer:
  - the role pages (req 6-7/9) call grant/revoke immediately per toggle
    (SPEC req 9's literal wording — "when toggled on/off, the system
    shall call...");
  - the two detail panels (req 4-5) keep their existing, unmodified
    local-pending-Set → single security-phrase-confirmed batch-save flow
    (`ConfirmDialogComponent` + `generateBatchPermissionUpdateToken`) —
    that flow is explicitly out of this SPEC's scope to change (SPEC
    only replaces *how the grid is displayed and tabbed*, not the
    save/confirm mechanics), so `(toggle)` there just mutates the panel's
    existing `pendingPermissions` signal exactly as `onTogglePermission`
    does today.
  A component that called HTTP directly could not serve both without
  either breaking the detail panels' existing batch-confirm UX or
  duplicating the component per consumer — both violate SPEC's "one
  reusable component" requirement (req 1) and the "reusable, not
  duplicated" acceptance criterion.
- **Read-only mode** (req 3) is a `mode: 'editable' | 'readonly'` input,
  not two components — the only difference is whether the toggle control
  renders; name/description rows are identical. (Not currently
  exercised by any of the four consumers listed in the SPEC — all four
  are editable — but req 3 requires the capability to exist on the
  component itself, so it's built in now rather than added on first use.)
- **Descriptions use a new flat i18n namespace `permissions.descriptions.<ENUM>`**,
  mirrored 1:1 with the existing flat `permissions.<ENUM>` namespace
  `translatePermissionLabel` already reads — same lookup shape, new
  namespace, so a second helper `translatePermissionDescription` (same
  file, `shared/permission-labels.ts`) is a one-line sibling of the
  existing function rather than a new pattern. `PROFILE_VIEW`/
  `PROFILE_EDIT` collide once, exactly as their `permissions.<ENUM>` name
  keys already do today — no scoped keys, per the SPEC's explicit note.
- **Tab mechanism**: a local `signal<'personal' | 'permissions'>('personal')`
  per panel, with `role="tablist"`/`role="tab"`/`role="tabpanel"` markup,
  a `tabClass()` helper, and arrow-key `keydown` handling — copying
  `login-page.component.ts`'s existing `activeTab`/`tabClass`/
  `selectTab`/`onTabKeydown` pattern verbatim (the only existing tab
  precedent in this codebase), not a new shared `TabsComponent`. Two
  independent copies (one per panel) rather than extracting a shared
  component now — each panel's tab content differs enough (personal-data
  section vs. permission-list) that a shared wrapper would only save the
  tablist markup/keydown handler, and `login-page` already shows that
  duplication is this codebase's accepted precedent for tabs. If a third
  consumer needs tabs later, extracting then is a cheap, low-risk
  refactor — not deciding that now, Tier 2, flagging so a reviewer can
  object.
- **`staff-user-detail-panel.component.ts`'s new tab signal nests strictly
  inside `viewMode() === 'edit'`** — the existing `viewMode: 'edit' |
  'history'` top-level branch is untouched; "Personal data"/"Permissions"
  are two new tabs *within* the `edit` branch's content, never siblings
  of `history`. `openInEditMode()`/`openInHistoryMode()` keep their
  current signatures; `openInEditMode()` additionally resets the new tab
  signal to `'personal'` so re-opening Edit after visiting Permissions
  doesn't leave a stale tab selected (mirrors resetting `editProfileTrigger`
  behavior already present).
- **Role pages' permission-editing view is reached the same way on both
  pages, for consistency (SPEC req 6/7)**: both pages' existing "edit"
  row action (the pencil icon, `LucideSquarePen`, already present in
  `rowActions`) selects the role (`selectGroup`) and reveals a detail
  panel below the list — the tenant page already has this shape today
  (`selectedGroup()` reveals `tenant-access-group-detail`); the staff
  page's equivalent panel (`access-group-members-panel`) already exists
  too, just without a permissions section. Concretely:
  - **Tenant page**: the existing `grant-permission-form` (`<select>` +
    button) inside `tenant-access-group-detail` is deleted outright and
    replaced by one `<app-permission-list mode="editable">` fed
    `ALL_PERMISSIONS` rows, `granted` derived from the selected group's
    new `permissions` field (from the extended `AccessGroupDto`), gated
    by the page's existing `canGrantPermission()`/new `canRevokePermission()`
    (`TENANT_PERMISSION_GRANT_DELETE`, mirroring `canGrantPermission`'s
    shape) computeds — `mode` is `'editable'` only when at least one of
    grant/revoke is allowed, `'readonly'` otherwise (never hidden
    entirely, since req 6 says the granted set is always shown).
  - **Staff page**: `access-group-members-panel` gains a new
    `<app-permission-list>` section above the existing members list,
    same shape, fed `ALL_GLOBAL_PERMISSIONS` and the selected
    `GlobalAccessGroup`'s new `permissions` field, gated on
    `STAFF_PERMISSION_MANAGE` (the same permission that already gates
    reaching this page at all per the nav guard — see
    `staffGuard`/`canSeeStaffAccessGroups`) for edit vs. read-only. This
    is genuinely new UI (SPEC req 7 says so explicitly), not a
    replacement of anything.
  Both pages' toggle handler calls
  `memberService.grantAccessGroupPermission`/a new
  `revokeAccessGroupPermission` (tenant) or the staff-service equivalents
  on-toggle, immediately (no batch/confirm step — matches SPEC req 9 and
  the backend SPEC's explicit no-confirmation-token decision for revoke).
- **Optimistic toggle with rollback on error (SPEC req 10)**: on toggle,
  the row's `granted` state flips immediately in the page's local view of
  the selected group's permission set (a derived signal, not the raw
  `AccessGroupDto.permissions` array, so a failed call can revert it);
  on a non-2xx response the page reverts that one row's local state and
  sets the page's existing `error` signal's sibling — a new
  `permissionActionError` string signal (not reusing the page-level
  `PageError` used for full-page failures, since a permission-toggle
  failure must not blank out the whole roster/list underneath it) —
  rendered as a small inline text/`role="alert"` message near the
  permission list, consistent with how `ConfirmDialogComponent`'s own
  400-retry path surfaces errors inline rather than via the page's
  full-page error state. This mirrors the existing direct-permission
  grid's principle (never leave a stuck optimistic toggle) while
  necessarily using a different concrete mechanism, because the existing
  grid *isn't* optimistic today (`pendingPermissions` only ever writes
  the batch-confirmed truth) — the role pages are the first place in
  this codebase doing an immediate, single-row optimistic toggle, so
  this rollback approach has no prior exact precedent to copy; recorded
  as a new pattern below.
- **Double-click/in-flight guard (AppSec review finding, 2026-08-08)**:
  a page-level `pendingPermissions = signal<Set<Permission>>(new Set())`
  disables (not just visually, the actual click handler no-ops) a row's
  toggle control while that row's own grant/revoke call is in flight —
  added to `groupPermissions`/its staff equivalent, not a new signal per
  row. Without this, a fast double-click on the same row fires two
  requests (e.g. revoke then grant) that can resolve out of order,
  leaving the displayed state disagreeing with the server's actual
  last-applied call until the next list refresh — not an authorization
  hole (the backend's last-applied call is still what's actually in
  effect either way), but a real correctness/UX gap this PLAN must close
  rather than leave implicit.

## Components and routes

- **New**: `src/app/shared/permission-list/permission-list.component.ts`,
  `permission-list.model.ts` (`PermissionListRow { value: string;
  granted: boolean }`, `PermissionListMode = 'editable' | 'readonly'`).
- **Changed**: `member-detail-panel.component.ts` — inline
  `direct-permissions`/`access-groups`/`effective-permissions`/
  `app-profile-section` sections are wrapped in two tabs; the toggle grid
  inside `direct-permissions` is replaced by `<app-permission-list>` bound
  to `allPermissions`/`pendingPermissions`, `(toggle)` calling the
  existing `onTogglePermission`. No change to `access-groups`/
  `effective-permissions` placement — both stay in the "Personal data"
  tab per SPEC req 4's silence on them (only "direct `Permission` grants"
  is named as moving to "Permissions").
- **Changed**: `staff-user-detail-panel.component.ts` — same treatment,
  nested inside `viewMode() === 'edit'` as described above; `staff-access-groups`/
  `staff-effective-permissions`/`app-profile-section` stay in "Personal
  data"; `staff-audit-trail` (History) is untouched, outside both new
  tabs.
- **Changed**: `tenant-access-group-management-page.component.ts` —
  `grant-permission-form` removed; `<app-permission-list>` added inside
  `tenant-access-group-detail`.
- **Changed**: `access-group-management-page.component.ts` —
  `<app-permission-list>` added inside `access-group-members-panel`
  (new section, page currently has none).
- No new routes — all four are existing screens/panels reached via
  existing navigation; no new guard needed (existing route guards for
  both role pages and both detail panels are unchanged, since visibility
  of the new permission-editing affordance is controlled by the existing
  `canGrantPermission`/`canRevokePermission`/`STAFF_PERMISSION_MANAGE`
  computeds, not a route-level gate).

## Consumed API contracts

Per `knowly-api/specify/features/role-permission-revoke/SPEC.md`
(backend PLAN being written in parallel — contract assumed as specified
there; flagged gaps below):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/tenants/{tenantId}/access-groups` | — | `AccessGroupDto[]` extended with `permissions: Permission[]` | 200 |
| POST | `/api/tenants/{tenantId}/access-groups/{id}/permissions` | `{ permission: Permission }` (existing, unchanged) | — | 200/403/404 |
| DELETE | `/api/tenants/{tenantId}/access-groups/{id}/permissions/{permission}` | — (path param) | — | 200/403/404/409 |
| GET | `/api/staff/access-groups` | — | `GlobalAccessGroupDto[]` extended with `permissions: GlobalPermission[]` | 200 |
| POST | `/api/staff/access-groups/{id}/permissions` | `{ permission: GlobalPermission }` (existing, unchanged) | — | 200/403/404 |
| DELETE | `/api/staff/access-groups/{id}/permissions/{permission}` | — (path param) | — | 200/403/404/409 |

**Gap to flag now, not at integration**: the backend SPEC's acceptance
criteria describe the revoke rejection cases (unknown role, soft-deleted
role, not-currently-granted permission — req 7-8) but don't pin down
the exact status code for "not-currently-granted" (SPEC just says
"reject," not "404" — contrast req 7's explicit "404-equivalent"). This
frontend's error handling (SPEC req 10) treats *any* non-2xx identically
(revert + inline error), so it doesn't need the distinction to satisfy
this SPEC's acceptance criteria — but if the backend PLAN settles on a
409 for that case specifically, confirm it doesn't collide with this
page's existing 400-means-retry-security-phrase convention used
elsewhere on these same pages (`ConfirmDialogComponent`'s
`retryToken`), since this new call path doesn't use that dialog at all
and shouldn't be confused with it. No code change implied either way —
noting so the sibling backend PLAN's status-code choice doesn't
surprise this frontend's tests.

`MemberService`/`StaffUserService` (both existing) each need one new
method:
- `MemberService.revokeAccessGroupPermission(tenantId, accessGroupId, permission): Observable<void>`
  → `DELETE` above.
- `StaffUserService.revokeAccessGroupPermission(accessGroupId, permission): Observable<void>`
  → `DELETE` above.
Both mirror the existing `grantAccessGroupPermission`/
`grantAccessGroupPermission` (staff) methods' shape exactly (same error
propagation, no security-phrase token fetch).

`AccessGroup`/`GlobalAccessGroup` TS interfaces (in `member.service.ts`/
`staff-user.service.ts`) gain `permissions: Permission[]`/
`permissions: GlobalPermission[]` fields, matching the extended DTOs.

## State and data

- `PermissionListComponent`: pure input/output, no injected services, no
  internal signal state beyond none needed (fully controlled by
  `rows`/`mode` inputs) — the simplest possible shape, consistent with
  `shared-list`'s own "configured via input objects" convention, just
  without `shared-list`'s internal sort/search/pagination state since
  this component needs none of that.
- Detail panels: existing `pendingPermissions`/`initialPermissions`/
  `hasPendingPermissionChanges` signals unchanged; a new computed
  `permissionListRows = computed(() => allPermissions.map(p => ({
  value: p, granted: pendingPermissions().has(p) })))` feeds the
  component.
- Role pages: `selectedGroup`'s `permissions` field is the seed; a new
  page-level signal `groupPermissions = signal<Set<Permission>>(new
  Set())` (tenant) / `Set<GlobalPermission>` (staff) is set on
  `selectGroup()` from `group.permissions` and is what the permission-list
  rows/toggle-optimism actually read/write (not `selectedGroup()`
  directly, so a toggle failure can revert this signal alone without
  re-fetching or mutating the cached `groups()` array). A new
  `permissionActionError = signal<'network' | 'permission-denied' |
  null>(null)` per page, per the rollback design above.

## Dependencies

None. No new `package.json` entries — pure Tailwind + existing Lucide
icons (`LucideSquarePen` already imported on both role pages; no new
icon needed for the toggle switch, which reuses the existing
`role="switch"`+two-`<span>` Tailwind pattern already in both detail
panels, just extracted into the new component).

## Testing strategy

- `permission-list.component.spec.ts` (new): renders one row per input
  permission with name+description text content; editable mode renders
  a `role="switch"` per row with `aria-checked` reflecting `granted` and
  `aria-label` equal to the row's translated name (not adjacent text);
  clicking/`Enter`/`Space`-ing a switch emits `(toggle)` with that row's
  value, does not mutate `rows` itself (dumb component, no internal
  state to assert against); read-only mode renders no switch at all,
  same name+description text.
- `member-detail-panel.component.spec.ts` (update): tab switching shows/
  hides "Personal data" vs "Permissions" content, defaulting to
  "Personal data"; permissions tab renders via `app-permission-list`
  and existing toggle/batch-save/security-phrase-confirm assertions
  still pass unmodified (behavior didn't change, only the rendering
  component did) — assert the old inline toggle-grid markup
  (`permission-toggle-*`) is gone, replaced by the new component's
  switches.
- `staff-user-detail-panel.component.spec.ts` (update): same tab
  assertions, plus a regression assertion that selecting "History" mode
  still shows only the audit trail and neither new tab (guards the
  `viewMode`/tab nesting decision above).
- `tenant-access-group-management-page.component.spec.ts` (update):
  `grant-permission-form`/`grant-permission-select` assertions removed;
  new assertions for toggle-on calling
  `grantAccessGroupPermission`/toggle-off calling the new
  `revokeAccessGroupPermission`, and a failure on either leaving the
  row's switch in its prior `aria-checked` state plus a visible inline
  error (`permissionActionError`).
- `access-group-management-page.component.spec.ts` (update): new
  assertions for the permission-list section appearing on
  `selectGroup()`, gated on `STAFF_PERMISSION_MANAGE`, same toggle/
  rollback assertions as the tenant page.
- `permission-labels.spec.ts` (update, if it exists — confirm during
  implementation) or a new small spec for
  `translatePermissionDescription`: falls back to the raw value when a
  description key is missing, mirroring `translatePermissionLabel`'s own
  fallback test.
- Run via `npm test` per task as each component/page changes; full
  `npm run format:check && npm test && npm run build && npm run lint`
  reserved for end-of-feature per repo convention.
