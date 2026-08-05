# PLAN — design system consistency pass

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Icon swaps are direct import/usage replacements, no wrapper
  components.** `select-tenant-page.component.ts`'s text delete button
  becomes a `SharedListRowAction` with `icon: LucideTrash`;
  `members-page.component.ts`/`staff-directory-page.component.ts`'s
  existing `LucideTrash2` (delete) is renamed to `LucideTrash` (REQ-1) —
  verified via `@lucide/angular`'s export list that `LucideTrash` exists
  as the outline-trash-can glyph and isn't a deprecated alias, since the
  SPEC explicitly calls out `LucideTrash2` as the wrong icon already in
  use. `LucideHistory` is newly imported wherever the history action is
  added. No central icon module is introduced — matches the
  already-established per-component `imports` array convention.
- **`shared-list.model.ts`/`shared-list.component.ts` gain an optional
  server-pagination mode, not a second component.** A listing that's
  server-paginated already differs from an in-memory one only in *how*
  `visibleRows`/`totalCount` are computed and how a page change is
  signaled — everything else (columns, row actions, search input
  rendering, skeleton rows, empty states) is identical. Two new optional
  inputs are added: `serverPagination: input<{ page: number; totalPages: number; totalElements: number } | null>(null)`
  and `pageChange = output<-1 | 1>()`. When `serverPagination()` is
  non-null: `visibleRows()` returns `rows()` unfiltered/unsorted
  (host is expected to pass already-paginated/filtered rows, matching
  `select-tenant-page`'s and the new audit-trail view's existing
  fetch-per-page pattern), the search input's `(input)` output is
  reused as-is (already just relays the term via `searchChange`, added
  below) so the host can debounce+refetch itself
  (mirrors `tenant-pagination-search`'s existing debounce-in-component
  precedent), and pagination controls render as the same
  `LucideChevronLeft`/`LucideChevronRight` prev/next buttons
  `select-tenant-page` already hand-rolls today, now inside
  `shared-list.component.ts` itself, disabled at `page === 0` /
  `page === totalPages - 1` and hidden entirely at `totalPages <= 1`
  (matches `tenant-pagination-search/PLAN.md`'s already-documented
  deviation). This is a Tier 2 judgment call (extending an existing
  shared component's contract rather than forking a second list
  component) — recorded here rather than left implicit, and is the
  reason a `DECISIONS.md` entry is warranted (see below).
- **`shared-list.component.ts` gains a `searchChange = output<string>()`
  alongside the existing internal `onSearch`**, emitted unconditionally
  (both memory and server-pagination modes), so a server-paginated host
  can react to typed input without the component needing to know
  *how* the host wants to debounce/refetch. In memory-pagination mode
  this is additive (existing hosts ignore it, behavior unchanged).
- **`select-tenant-page.component.ts` migrates to `app-shared-list`**
  (REQ-4), passing `serverPagination` only in the fallback branch and
  `null` in the membership-list branch (which keeps today's client-side,
  unpaginated rendering — REQ-5 only requires the *already-paginated*
  fallback path to stay server-driven, not to introduce pagination
  where none exists today). The one column is `tenantName` (identity
  cell, no secondary line — mirrors `members`/`staffDirectory`'s
  identity-cell column shape for visual consistency, REQ-4). The delete
  row action is
  `computed(() => this.canDeleteTenant() ? [{ icon: LucideTrash, labelKey: 'selectTenant.delete', variant: 'danger', onClick: ... }] : [])`,
  since `SharedListRowAction` has no per-row/global `hidden` flag today
  and adding one for a single all-or-nothing case isn't justified — an
  empty `rowActions()` array already renders no action column at all.
  Row `onClick` (selecting a tenant) has no equivalent in
  `SharedListColumn`/`SharedListRowAction` today (row selection acting
  as *navigation*, not a listed action) — solved by keeping the existing
  `<button>`-per-row selection behavior via a thin wrapper: the identity
  cell's `render()` returns the tenant name as before, and a *second*,
  non-destructive row action (`variant: 'secondary'`, no icon needed
  since it's the primary action) is deliberately **not** added; instead
  `rowId` continues to identify the row and a new
  `rowClick = output<T>()` is added to `SharedListComponent` (row `<tr>`
  gets `(click)="rowClick.emit(row)"`, keeping existing `hover:bg-*`
  affordance as the visual cue it's clickable) — the same shape
  `members-page`'s edit action already conceptually needs (see below),
  generalizing "click a row to act on it" once for both consumers rather
  than solving it twice.
- **Edit/delete/history become three `SharedListRowAction` entries on
  `members-page.component.ts`/`staff-directory-page.component.ts`**,
  replacing the current single row-click-opens-panel behavior (REQ-6,
  REQ-7, REQ-8):
  - Edit (`LucideSquarePen`) calls a new public method on the relevant
    detail-panel component,
    `openInEditMode(id)`, implemented by setting the existing
    `selectedMembershipId`/`selectedUserId` signal **and**
    incrementing `editProfileTrigger` in the same call — reuses
    `ProfileSectionComponent`'s existing `editTrigger` input (already
    wired through `member-detail-panel`/`staff-user-detail-panel`) so
    "open already in edit mode" needs no new state inside the panel
    components themselves, only a page-level helper that does both
    signal writes the row-click handler used to require two separate
    user actions for.
  - Delete (`LucideTrash`) opens `ConfirmDialogComponent` directly from
    the *page* component (`members-page.component.ts`/
    `staff-directory-page.component.ts`), not the detail panel — the
    token-fetch/retry/confirm logic
    (`removalTokenFetcher`/`confirmRemoval`/`generateHardDeleteToken`
    equivalents) already lives on `MemberService`/`StaffUserService`,
    called today from inside the detail-panel components. Rather than
    duplicating that logic at the page level, the *page* components gain
    their own `pendingDelete`/`deleteRetryToken` signals and their own
    `<app-confirm-dialog>` instance calling the same service methods
    directly — no new indirection through the panel component, since
    the panel's own delete flow already fully owns nothing but calling
    the service and reloading `detail()`; the page-level flow reloads
    the list (`loadMembers`/`loadStaffUsers`) instead. The existing
    delete affordance *inside* `member-detail-panel.component.ts` is
    removed (REQ-7's "without requiring the detail panel to be opened
    first" plus REQ-9's list of what stays: delete isn't on that list).
    `staff-user-detail-panel.component.ts`'s equivalent delete button
    and `pendingDelete`/`deleteRetryToken` state are removed identically.
  - History (`LucideHistory`) navigates to a new route (see "Components
    and routes"), not a modal — SPEC's stated recommendation, chosen
    for linkability (a direct URL a staff admin can share/bookmark to a
    specific person's history) and because it avoids needing a second
    modal-overlay pattern in an app that already has exactly one
    (`ConfirmDialogComponent`, deliberately reserved for
    destructive/high-stakes confirms per Out of scope) — introducing a
    second, general-purpose modal shape for a read-only view is more
    architecture than a linkable route requires.
- **`member-detail-panel.component.ts`/`staff-user-detail-panel.component.ts`
  keep the audit-trail table only in the staff-user case for now**
  (backend SPEC's own out-of-scope: members have no audit-trail
  endpoint at all today) — the new `/staff/users/:userId/audit` route
  replaces `staff-user-detail-panel`'s embedded (unpaginated) audit
  table entirely (REQ-8, REQ-9 doesn't list audit-trail as staying).
  Members get no history action/route in this pass, matching the
  backend SPEC's explicit scope limit — flagged here, not silently
  widened.
- **"My profile" row action on `members-page`** (REQ-10): the row
  actions array becomes `computed()`, keyed off a new `ownUserId`
  signal (loaded once via `ProfileService.getOwnProfile()`, same call
  `member-detail-panel.component.ts` already makes independently — kept
  page-local rather than promoted to a shared service, since this is
  the second call site for the same one-shot value and the SPEC doesn't
  ask for a caching layer). For the viewer's own row: the edit action is
  omitted and replaced with a `routerLink`-style action; since
  `SharedListRowAction.onClick` is a plain callback (not a `routerLink`
  binding), the "my profile" action's `onClick` calls
  `this.router.navigateByUrl('/profile')` — no new capability needed on
  the shared-list model. **The delete row-action is omitted from the
  viewer's own row by the same `computed()`** (appsec review,
  2026-08-05: self-removal from a tenant one is currently a member of
  has no legitimate UI path today either, and backend
  `TenantService`'s member-removal flow already rejects a caller
  removing their own membership independently of this UI change — this
  is a defense-in-depth UX clarification, not the actual authorization
  boundary, which stays server-side).
- **Sidebar collapse state lives in a new `SidebarStateService`**
  (`core/sidebar-state.service.ts`), following the established
  private-signal + `.asReadonly()` + explicit setter shape
  (`PermissionsService`/`ActiveTenantService` reference shape): a single
  `collapsed` signal, persisted to `localStorage` under one key
  (`knowly.sidebar.collapsed`), read at service construction and written
  on every `toggle()`/`setCollapsed()` call. This is desktop-only state;
  the mobile off-canvas open/closed state is **separate** (`mobileOpen`
  signal, same service, not persisted — REQ-13 wants it closed by
  default every time, not remembered across a session, since a
  reopened-by-memory drawer covering the screen on next mobile visit
  would be actively bad, unlike the desktop rail width choice REQ-14
  explicitly asks to remember). A new service (over a bare signal in
  `nav-menu.component.ts` itself) is chosen because `app-shell.component.ts`
  also needs to read `collapsed()` to size the `<aside>` — state shared
  across two components is exactly this app's existing trigger for a
  service-with-signal, not a component-local signal.
- **Breakpoint check via `window.matchMedia('(min-width: 768px)')`**
  (Tailwind's `md:` breakpoint, the value already used elsewhere in this
  app's Tailwind classes for the same desktop/mobile split), wrapped in
  a small `viewportIsDesktop` signal on `SidebarStateService`, updated
  on the media query's `change` event — avoids introducing a
  resize-observer library; native `matchMedia` is sufficient for a
  single boolean breakpoint and this app has no existing
  responsive-breakpoint service to extend instead.
- **Tooltip on hover (REQ-12) uses a plain native `title` attribute
  fallback plus a small custom Tailwind-only tooltip, not a new
  library.** Checked first (per SPEC's explicit instruction): no
  existing tooltip pattern exists anywhere in this app today (`grep`
  found none). Given `DECISIONS.md`'s PrimeNG-reversal entry ("no
  component library" is the settled position), a hand-rolled
  `group`/`group-hover:` Tailwind pattern is used — the collapsed nav
  link already needs `group` for its own hover background per
  `linkClass`, so a sibling `<span>` positioned `absolute left-full`
  with `opacity-0 group-hover:opacity-100` (plus `group-focus-visible:opacity-100`
  for keyboard-reachability, satisfying the NFR) is added, shown only
  while `collapsed()` is true. This is the simplest Tailwind-only
  construct that satisfies "reachable via keyboard focus, not
  mouse-only" without adding a CDK overlay or a new dependency — a Tier
  2 judgment call (introducing a new, reusable-looking pattern) worth
  recording, though not a new dependency so not Tier 3.
- **Nav items are never conditionally removed from the DOM between
  collapsed/expanded states — only the label `<span>`'s visibility
  changes (`hidden md:collapsed:sr-only`-style conditional class, not a
  structural `@if`).** This keeps every `data-tour-id`/`data-testid`
  element present and at a stable position in both states, so
  `tour-overlay.component.ts`'s `document.querySelector('[data-tour-id=...]')`-based
  positioning (`tour-overlay.component.ts`'s `position()`) keeps working
  unmodified — no tour-overlay code change is needed at all, since the
  icon (not the label) is what the tour box anchors to regardless of
  collapse state, and the icon is always rendered. This directly avoids
  the "tour force-expands the sidebar" complexity the SPEC flagged as a
  possible requirement — decided against, since it isn't needed once
  nav items remain structurally present.
- **Mobile off-canvas is a fixed-position overlay + backdrop rendered
  conditionally inside `app-shell.component.ts`** (not `nav-menu.component.ts`
  itself, which stays presentation-agnostic about desktop-rail vs.
  mobile-drawer chrome) — `app-shell.component.ts` already owns the
  `<aside>` wrapper and is the natural place to switch between "always
  visible, width-animated `<aside>`" (desktop) and "translate-x
  off-canvas `<aside>` + a `fixed inset-0 bg-black/50` backdrop `<div>`"
  (mobile), both wrapping the same unmodified `<app-nav-menu>`. A new
  mobile toggle button (`LucidePanelLeftOpen`, header area) opens it;
  closing on backdrop click, `Escape` (existing keydown pattern
  precedented by `tour-overlay.component.ts`'s own Escape handler), and
  route change (subscribed via the same `Router.events`/`NavigationEnd`
  pattern `app-shell.component.ts` already uses for `isBareRoute`) all
  call `sidebarState.setMobileOpen(false)`.

## Components and routes

- `knowly-app/src/app/shared/shared-list/shared-list.model.ts` (modified):
  add `SharedListServerPagination { page: number; totalPages: number; totalElements: number }`.
- `knowly-app/src/app/shared/shared-list/shared-list.component.ts` (modified):
  - new inputs: `serverPagination = input<SharedListServerPagination | null>(null)`.
  - new outputs: `pageChange = output<-1 | 1>()`, `searchChange = output<string>()`,
    `rowClick = output<T>()`.
  - `visibleRows()`/`totalCount()` branch on `serverPagination()`
    presence as described above.
  - new prev/next control block (reusing `select-tenant-page`'s existing
    Tailwind classes/`buttonClass('secondary')` styling), rendered below
    the table when `serverPagination()` is non-null.
- `knowly-app/src/app/features/select-tenant/select-tenant-page.component.ts`
  (modified): renders `<app-shared-list>` in place of the hand-rolled
  `<ul>`; keeps its own `page`/`totalPages`/`searchTerm` signals and
  `fetchFallbackTenants()` unchanged, now feeding `serverPagination`/
  `(pageChange)`/`(searchChange)`/`(rowClick)` instead of its own
  buttons/inputs. `canDeleteTenant`-gated delete action becomes a
  `computed(SharedListRowAction[])`. `ConfirmDialogComponent` usage
  unchanged.
- `knowly-app/src/app/features/members/members-page.component.ts`
  (modified): `rowActions` becomes `computed()`, gains delete
  (`LucideTrash`) and history is **not** added here (members have no
  audit-trail endpoint, per backend SPEC scope) — only edit (rewired to
  call `openInEditMode`) + delete (new page-level confirm flow) + the
  own-row "my profile" swap.
- `knowly-app/src/app/features/members/member-detail-panel.component.ts`
  (modified): gains `openInEditMode(): void` (sets
  `editProfileTrigger.update((n) => n + 1)`, callable from the page once
  the panel is already open/selected — the existing `ngOnChanges`-driven
  `@Input()` re-fetch already runs on `membershipId` change, so
  selecting a *different* row's edit action and incrementing the trigger
  in the same page-level call handles both "panel wasn't open yet" and
  "already open, different person" in one code path). Removes its
  delete button, `pendingDelete`/`deleteRetryToken` signals, and
  `deletionTokenFetcher`/`confirmDelete`/`cancelDelete` methods (moved to
  the page).
- `knowly-app/src/app/features/user-management/staff-directory-page.component.ts`
  (modified): `rowActions` gains delete (`LucideTrash`, new page-level
  confirm flow mirroring members) and history (`LucideHistory`,
  navigates to `/staff/users/:userId/audit`) — **the history action is
  itself gated**, not just its destination route: `rowActions` becomes
  `computed()` off `globalPermissionsService.has('AUDIT_TRAIL_VIEW')`
  (the same permission the backend endpoint requires), and the
  `LucideHistory` entry is only included in the array when that's true.
  This mirrors `canDeleteTenant()`'s existing computed-visibility
  pattern in `select-tenant-page.component.ts` (appsec review,
  2026-08-05: relying on `staffGuard` + the route's own 403 handling
  alone would let a staff viewer *without* `AUDIT_TRAIL_VIEW` navigate in
  and see a permission-denied flash before the backend rejects the
  request — the backend gate is still the real boundary, but the action
  shouldn't be offered to someone it will always deny).
- `knowly-app/src/app/features/user-management/staff-user-detail-panel.component.ts`
  (modified): gains `openInEditMode()` identically to
  `member-detail-panel`. Removes its delete button/state (moved to the
  page) **and** its embedded audit-trail section entirely (replaced by
  the new route).
- **New** `knowly-app/src/app/features/user-management/staff-user-audit-page.component.ts`:
  standalone page component, own route
  `/staff/users/:userId/audit`. Reads `userId` via `ActivatedRoute.paramMap`
  (same pattern as `SupportPageComponent`'s existing `:channelId`
  precedent). Renders via `<app-shared-list>` with `serverPagination`
  always set (this view has no other pagination mode), one column
  (`occurredAt` formatted via the existing `formatAuditTimestamp` +
  `translateAuditAction` helpers already used inside
  `staff-user-detail-panel.component.ts`, reused as-is), no row actions.
  Guarded by `staffGuard` (mirrors `/staff/access-groups`'s existing
  guard choice — this view is only ever reachable from
  `staff-directory-page`, itself already behind the same permission
  surface `GET /api/staff/permissions` gates).
- `knowly-app/src/app/app.routes.ts` (modified): adds
  `{ path: 'staff/users/:userId/audit', component: StaffUserAuditPageComponent, canActivate: [staffGuard] }`.
- `knowly-app/src/app/core/sidebar-state.service.ts` (new): `collapsed`,
  `mobileOpen`, `viewportIsDesktop` signals as described above.
- `knowly-app/src/app/layout/nav-menu.component.ts` (modified): injects
  `SidebarStateService`; wraps each item's label in a `<span>` toggled
  via `collapsed()` (visually hidden, not removed) plus the hover-tooltip
  `<span>`; adds the collapse/expand toggle button
  (`LucidePanelLeftClose`/`LucidePanelLeftOpen`, `aria-expanded`,
  `aria-controls="nav-menu"`) at the top of the nav, visible only at
  desktop viewport width (mobile uses `app-shell.component.ts`'s own
  toggle instead, per REQ-13's different affordance).
- `knowly-app/src/app/layout/app-shell.component.ts` (modified): `<aside>`
  width becomes conditional on `sidebarState.collapsed()`
  (`w-64` ↔ a narrow icon-rail width, e.g. `w-[4.5rem]` — exact value
  decided at implementation time from the rendered icon+padding size,
  not a hardcoded guess here); on mobile, `<aside>` becomes
  `fixed inset-y-0 left-0 z-40` with a `translate-x-full`/`translate-x-0`
  toggle driven by `mobileOpen()`, plus a sibling backdrop `<div>` shown
  only when `mobileOpen()` is true; adds a new header-area toggle button
  for mobile, visible only below the `md:` breakpoint.

## Consumed API contracts

Cross-referencing `knowly-api/specify/features/paginated-audit-trail/SPEC.md`
for the contract this pass depends on. **That feature's `PLAN.md` does
not exist yet at the time of writing this PLAN** — the table below is
taken directly from the backend SPEC's own stated response shape
("same shape as `PageResponseDto` used elsewhere") and this frontend's
existing `PageResponse<T>` interface (`active-tenant.service.ts`), not
re-derived independently. If the backend PLAN, once written, settles on
different field names or an additional query param, this table (and
`staff-user.service.ts`'s method signature below) needs a follow-up
correction — flagged here rather than silently assumed compatible.

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| GET | `/api/staff/users/{userId}/audit-trail?page=&size=` | query params `page` (default 0), `size` (default 20, max 100) | `PageResponseDto<AuditEventDto>` (`content`/`page`/`size`/`totalElements`/`totalPages`, events sorted `occurredAt` desc) | 200 |
| GET | (same) | caller lacks the existing view permission | `TenancyErrorResponseDto`-shaped error, identical to today's unpaginated endpoint | 403 |

No other new backend contract is introduced by this pass — the
edit/delete row actions call existing `MemberService`/`StaffUserService`
methods unchanged; only *where* they're called from moves.

## State and data

- `SidebarStateService` (new): `_collapsed = signal(readFromLocalStorage())`,
  `collapsed = _collapsed.asReadonly()`, `toggle()`/`setCollapsed(boolean)`
  (writes through to `localStorage`); `_mobileOpen = signal(false)`,
  `mobileOpen = _mobileOpen.asReadonly()`, `setMobileOpen(boolean)`;
  `viewportIsDesktop` signal wired to a `matchMedia` listener registered
  once in the service constructor (cleaned up via `DestroyRef` — this
  app's existing pattern for service-level subscriptions, mirroring
  other injectable services' constructor-registered effects, e.g.
  `nav-menu.component.ts`'s own `effect()` usage as the closest existing
  precedent for constructor-time reactive wiring in this codebase).
- `members-page.component.ts`/`staff-directory-page.component.ts` each
  gain: `ownUserId = signal<number | null>(null)` (members only, loaded
  once via `ProfileService.getOwnProfile()`), page-level
  `pendingDelete`/`deleteRetryToken` signals (moved from the detail
  panels, same shape).
- `select-tenant-page.component.ts`'s existing `page`/`totalPages`/
  `totalElements`/`searchTerm` signals are unchanged in type/ownership,
  only now feed `shared-list`'s new inputs instead of local template
  markup.
- `staff-user-audit-page.component.ts` (new): `events = signal<AuditEvent[]>([])`,
  `page`/`totalPages`/`totalElements` signals (same shape as
  `select-tenant-page`'s fallback-pagination state), `loading`/`error`
  signals matching the existing `'network' | 'permission-denied' | null`
  convention.

## Dependencies

None. `LucideHistory`, `LucidePanelLeftClose`, `LucidePanelLeftOpen`,
`LucideChevronLeft`, `LucideChevronRight`, `LucideX` are all already
part of the installed `@lucide/angular` package (same package version
already in `package.json`, just previously-unused exports). No new
`package.json` entry — no tooltip/overlay library, no NgRx, no second
queue.

## Testing strategy

Vitest, extending existing specs plus new ones:

- `shared-list.component.spec.ts`: new cases for `serverPagination`
  mode — `visibleRows()` passes `rows()` through unchanged (no
  client-side filter/sort applied), prev/next buttons emit `pageChange`
  with the correct delta and respect the disabled-at-boundary/
  hidden-at-`totalPages<=1` rules already established by
  `tenant-pagination-search`'s tests, `searchChange` emits on input,
  `rowClick` emits the clicked row.
- `select-tenant-page.component.spec.ts`: existing suite adapted to
  assert through `app-shared-list`'s testids instead of the removed
  hand-rolled markup; existing pagination/search/delete test cases
  re-target the new event bindings, behavior otherwise unchanged.
- `members-page.component.spec.ts`/`staff-directory-page.component.spec.ts`:
  new cases — clicking the edit action calls `openInEditMode` on the
  now-open panel (asserted via a spy on the panel's method or by
  asserting `editProfileTrigger`'s effect, e.g. the profile form
  entering edit mode); clicking delete opens `ConfirmDialogComponent`
  directly without any panel-open precondition; (staff only) clicking
  history navigates to `/staff/users/:id/audit`; (members only) the
  viewer's own row shows a `/profile`-navigating action instead of edit.
- `member-detail-panel.component.spec.ts`/`staff-user-detail-panel.component.spec.ts`:
  existing delete-flow tests removed/relocated to the page specs;
  `staff-user-detail-panel`'s audit-trail-section tests removed
  (moved to the new page's spec).
- **New** `staff-user-audit-page.component.spec.ts`: renders paginated
  events from a mocked `StaffUserService.getAuditTrail(userId, page, size)`;
  page-change re-fetches the next page; permission-denied/network error
  states render the existing shared error components.
- **New** `sidebar-state.service.spec.ts`: `toggle()`/`setCollapsed()`
  persist to and read back from `localStorage`; `setMobileOpen()` never
  persists; `viewportIsDesktop` reflects a mocked `matchMedia` result and
  updates on a simulated `change` event.
- `nav-menu.component.spec.ts`: existing `data-testid`/`data-tour-id`
  assertions unchanged (still present regardless of `collapsed()`);
  new cases — toggle button flips `collapsed()` and its
  `aria-expanded` attribute; hover/focus on a collapsed item's link
  reveals its tooltip span (assert via the `group-hover`/
  `group-focus-visible` class presence, not simulated CSS, per this
  app's existing Vitest/JSDOM limitations for pseudo-class assertions).
- `app-shell.component.spec.ts`: existing suite extended — mobile
  viewport (`viewportIsDesktop() === false`) renders the backdrop only
  when `mobileOpen()` is true; backdrop click, `Escape`, and a route
  change each call `setMobileOpen(false)`.

## `DECISIONS.md` entry needed

Extending `SharedListComponent`'s contract with an optional
server-pagination mode is a genuinely new architectural decision (no
existing precedent for a "two data-sourcing modes in one shared
component" shape in this codebase) and will be written into
`DECISIONS.md` per the ADR-writer skill format once TASKS.md
implementation begins — noted here so it isn't dropped between PLAN and
implementation.
