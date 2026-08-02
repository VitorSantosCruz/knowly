# SPEC — Staff/members management screen redesign

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-02 (amendment)**: Added REQ-7b through REQ-7e specifying a
  "promote to `STAFF_ADMIN`"/"promote to `MEMBER_ADMIN`" action on the
  user detail screen, closing the gap PLAN.md flagged (promotion was
  previously unaddressed by any requirement). Confirmed by the product
  owner: symmetric to the existing demote action, same visibility gate
  (REQ-7a), no last-admin disable rule.
- **2026-08-02**: Initial draft, covering frontend-only aspects of a
  large user-reported redesign request for the "Membros"
  (staff directory + tenant members) screens. See "Amends prior SPECs"
  below for the one explicit scope change this introduces relative to
  `user-management-screens`.

## Context and motivation

`knowly-app`'s "Membros" screen (`user-management`/`user-management-screens`:
`MembersPageComponent` for tenant members, `StaffDirectoryPageComponent` +
`StaffUserDetailPanelComponent` for the staff directory) works correctly
but has drifted visually and structurally from the rest of the app —
inconsistent with, among others, the tenant list screen. The product
owner asked for a batch of concrete UI/UX corrections across list
layout, permission editing ergonomics, destructive-action safety rules,
audit-trail readability, and information architecture (where access
groups are managed). This SPEC captures the **frontend-only** shape of
that request. Everything that requires new/changed backend behavior
(role demotion, delete-guard rules, batched permission save, the
security-phrase gate on grant) is explicitly out of scope here and
tracked as a backend-SPEC dependency (see "Out of scope").

## Amends prior SPECs

- **`user-management-screens` REQ-9** currently states that "creating a
  named global access group" and assigning/unassigning staff users to it
  happens **from a staff user's detail view**. Item 8 of this request
  moves access-group management (create, list, and membership
  assignment) to its **own, user-independent screen/section** — access
  groups are a standalone entity that may have zero or many members, and
  `STAFF_ADMIN`/`MEMBER_ADMIN` accounts never participate in them at all
  (they already hold every permission implicitly). This SPEC's REQ-20
  through REQ-24 supersede `user-management-screens` REQ-9's "from that
  user's detail view" placement; nothing else in that SPEC changes.
- No other existing SPEC's "Out of scope" line is reversed by this
  document. Everything else here is either a new requirement or a
  refinement of unspecified visual/structural detail.

## Reference list layout (REQ-1)

The product owner supplied a reference screenshot (2026-08-02) of the
target list layout/structure to standardize every list screen against.
Structure, not literal colors/branding, is what's being adopted — the
actual visual treatment (palette, typography, spacing) must still follow
Knowly's own "Ink & Signal" design system, not copy the reference's
theme verbatim. The reference shows:

- Header row: screen title on the left, a live "showing X–Y of Z" count
  on the right.
- A leading selection checkbox column (row selection for bulk actions).
- A primary identity column combining avatar + primary name (bold) +
  secondary line (e.g. email) stacked in one cell.
- Several sortable data columns (each header has a sort affordance),
  content type varying by screen (phone/location/company here; role/
  tenant/permissions-summary for staff or member lists).
- A status column rendered as a colored pill/badge (e.g. green
  "Online"/gray "Offline" in the reference — for this project's lists,
  the equivalent is things like active/inactive, or a role badge).
- A trailing row-actions column (edit/delete icon buttons, right-aligned).
- Rows are dense but readable, alternating implied by hover state rather
  than zebra-striping (confirm against Ink & Signal's existing table/list
  conventions — do not invent zebra-striping if the design system doesn't
  already use it elsewhere).

This is now unblocked for PLAN — `design-system-ui-ux` should translate
this structure into a concrete component spec using Knowly's actual
tokens/palette before `software-architect` writes the Angular component
PLAN.

## Reference list layout — Ink & Signal translation

Design spec for the shared list component named by this document as
**`SharedListComponent`** (`app-shared-list`, `knowly-app/src/app/shared/`)
— `frontend-engineer`/`software-architect` may rename it in PLAN.md if a
more idiomatic name emerges, but the visual contract below does not
change with the name. This section is written to be reusable by every
list screen migrating to it (staff directory, tenant members, tenant
list, and any future list), not just the members screens this feature
touches first.

### Prior art audited (reuse, don't reinvent)

- `StaffDirectoryPageComponent`
  (`knowly-app/src/app/features/user-management/staff-directory-page.component.ts`)
  and `MembersPageComponent`
  (`knowly-app/src/app/features/members/members-page.component.ts`) both
  hand-roll a bare `<table>`/`<tbody>` with no header row, no sort
  affordance, no status badge, no checkbox column, and — per the
  screen's own bug history — a data-bearing action shipped as unstyled
  inline text rather than a button. Neither is reusable as-is; both are
  full migration targets, not partial donors.
- `TopArticlesTableComponent`
  (`knowly-app/src/app/features/dashboard/top-articles-table.component.ts`)
  is a dark-surface (`bg-gradient-to-br from-ink-900 to-ink-950`)
  dashboard widget, not a light-surface data table — its search-input
  styling is worth copying as-is for the shared list's search field, but
  its table itself is out of scope for reuse (different surface
  contract).
- `TicketStatusBadgeComponent`
  (`knowly-app/src/app/features/support/ticket-status-badge.component.ts`)
  is the canonical pill/badge pattern already in production: a
  `Record<Status, string>` color map + `Record<Status, string>` i18n-key
  map, rendered as `rounded-full px-2 py-1 text-xs font-medium` with a
  `data-testid` and `[attr.data-status]`. The status column below reuses
  this exact pattern/shape (new instance per screen, e.g. an
  `active/inactive` badge and a role badge — do not force one shared
  enum across unrelated status types).
- `buttonClass()`
  (`knowly-app/src/app/shared/button-classes.ts`) is the canonical
  button-styling helper (`primary`/`secondary`/`danger`, `ghost`,
  `rounded`) — the row-actions column and any bulk-action toolbar use
  `buttonClass('secondary', { ghost: true, rounded: true })` for icon
  buttons and `buttonClass('danger', { ghost: true })` for delete,
  never a bespoke `class="..."` string.
- Confirmed by grep across `knowly-app/src`: **no existing list/table
  anywhere in the app uses zebra-striping.** Rows are either unstyled or
  use a hover background only (e.g. `hover:bg-ink-50 dark:hover:bg-ink-800`
  pattern already used on interactive rows/menu items). The reference
  screenshot's "hover state, not zebra-striping" instruction matches the
  established convention exactly — do not introduce zebra-striping here.

### Layout structure

```
┌─────────────────────────────────────────────────────────────┐
│ Title (h2, font-display)          "Mostrando 1–10 de 42"     │  header row
├─────────────────────────────────────────────────────────────┤
│ [search input]                          [bulk action button] │  toolbar (optional row,
│                                                                │  only when search/bulk apply)
├──┬──────────────────────┬────────┬────────┬────────┬────────┤
│☐ │ Avatar Name           │ Col A ⇅│ Col B ⇅│ Status │ Actions│  column header row
│  │       secondary line  │        │        │        │        │
├──┼──────────────────────┼────────┼────────┼────────┼────────┤
│☐ │ [av] Jane Doe         │  ...   │  ...   │ [pill] │ ✎ 🗑   │  data row (hover only)
│  │      jane@x.com       │        │        │        │        │
└──┴──────────────────────┴────────┴────────┴────────┴────────┘
```

### Tokens (light / dark)

- **Outer container**: `rounded-2xl border border-ink-200/70
  bg-white shadow-lg shadow-slate-200/60 dark:border-ink-800/70
  dark:bg-ink-900 dark:shadow-none` — matches the established card
  contract (`login-page.component.ts`'s `cardClass`) rather than
  inventing a new elevation.
- **Header row (title + count)**: container `flex items-center
  justify-between px-6 py-4 border-b border-ink-200/70
  dark:border-ink-800/70`. Title: `text-lg font-display font-semibold
  text-ink-900 dark:text-white`. Count: `text-sm text-ink-500
  dark:text-ink-400` (never lighter — matches the WCAG-contrast floor
  already established).
- **Toolbar row** (search + bulk action, shown only while present):
  `flex items-center justify-between gap-3 px-6 py-3 border-b
  border-ink-200/70 dark:border-ink-800/70`. Search input reuses
  `top-articles-table.component.ts`'s input styling, light-surface
  variant already used in `staff-directory-page.component.ts`:
  `rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm
  text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500
  focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white`.
  Bulk-action button (enabled only when ≥1 row selected): `buttonClass('primary')`.
- **Column header row**: `bg-ink-50/60 dark:bg-ink-950/40` (a step
  lighter/darker than the row background, never the same value — this
  is the one place a distinct background is intentional, it is a header,
  not a zebra stripe). Cells: `px-4 py-3 text-xs font-semibold uppercase
  tracking-wide text-ink-500 dark:text-ink-400`. Sortable column headers
  additionally get `cursor-pointer select-none inline-flex items-center
  gap-1 hover:text-ink-700 dark:hover:text-ink-200 transition-colors
  duration-fast ease-fluid` and a Lucide `LucideChevronsUpDown` (idle) /
  `LucideChevronUp` / `LucideChevronDown` (active-sort) icon, `h-3.5 w-3.5`,
  plus `role="columnheader" aria-sort="none|ascending|descending"`.
- **Checkbox column**: `w-10 px-4` cell, native `<input type="checkbox">`
  styled `h-4 w-4 rounded border-ink-300 text-signal-600
  focus:ring-signal-500 dark:border-ink-600 dark:bg-ink-800` (the same
  accent-color pattern as existing form checkboxes — verify against
  `profile-fields-form.component.ts` if a checkbox exists there and
  align, do not invent a second checkbox style). Header checkbox is the
  "select all visible rows" control with `aria-label`
  (`sharedList.selectAll` key) since it has no visible text label.
- **Data row**: `border-b border-ink-100 last:border-b-0
  hover:bg-ink-50 dark:border-ink-800/50 dark:hover:bg-ink-800/60
  transition-colors duration-fast ease-fluid`. Cell padding: `px-4 py-3`.
  No zebra-striping (confirmed above).
- **Identity cell** (avatar + name + secondary line): `flex items-center
  gap-3`. Avatar: `h-9 w-9 rounded-full object-cover` image or, for the
  no-photo fallback, the existing initials-avatar pattern from
  `avatar-upload.component.ts`
  (`flex h-9 w-9 items-center justify-center rounded-full bg-ink-100
  text-xs font-medium text-ink-600 dark:bg-ink-800 dark:text-ink-300`).
  Name: `text-sm font-semibold text-ink-900 dark:text-white`. Secondary
  line (email/etc.): `text-xs text-ink-500 dark:text-ink-400` directly
  below the name, no extra margin beyond `leading-tight` on the wrapper.
- **Data column cell** (generic, e.g. role/tenant/phone): `text-sm
  text-ink-700 dark:text-ink-300`.
- **Status pill column**: reuses the `TicketStatusBadgeComponent` shape
  exactly — `rounded-full px-2 py-1 text-xs font-medium` plus a
  per-screen color map. Canonical color assignments for this feature's
  two concrete statuses (extend, do not redefine, for future screens):
  - Active / enabled: `bg-emerald-100 text-emerald-700
    dark:bg-emerald-900/40 dark:text-emerald-400` (matches
    `articles-page.component.ts`'s existing "published"-equivalent
    green, the only precedent for a positive/active state in the app).
  - Inactive / disabled: `bg-ink-100 text-ink-600 dark:bg-ink-800
    dark:text-ink-400` (matches `TicketStatusBadgeComponent`'s
    `CLOSED` neutral treatment).
  - `STAFF_ADMIN`/`MEMBER_ADMIN` role badge: `bg-signal-100
    text-signal-800 dark:bg-signal-900/30 dark:text-signal-400` (matches
    `TicketStatusBadgeComponent`'s `OPEN`/brand-accent treatment, signals
    elevated privilege).
  - `STAFF`/`MEMBER` role badge: `bg-ink-100 text-ink-600
    dark:bg-ink-800 dark:text-ink-400` (same neutral as inactive — role
    is conveyed by label text, not a third color, keeping the palette
    from sprawling).
- **Row-actions column**: `w-20 px-4 text-right`, icon buttons via
  `buttonClass('secondary', { ghost: true, rounded: true })` for edit
  (Lucide `LucideSquarePen`) and `buttonClass('danger', { ghost: true,
  rounded: true })` for delete (Lucide `LucideTrash2`), `h-4 w-4` icons,
  `gap-1` between the two buttons, each with `aria-label` (translated,
  e.g. `sharedList.actions.edit`/`sharedList.actions.delete`) since
  they're icon-only. A disabled action (per REQ-5/REQ-6/REQ-9/REQ-10)
  keeps `buttonClass`'s built-in `disabled:pointer-events-none
  disabled:opacity-50` and exposes its explanation via
  `title`/`aria-describedby`, not by hiding the button.

### States

- **Loading**: replace the row area (header row + toolbar stay visible)
  with 5 skeleton rows: `animate-pulse` blocks — `h-9 w-9 rounded-full
  bg-ink-100 dark:bg-ink-800` for the avatar slot, `h-3 w-32 rounded
  bg-ink-100 dark:bg-ink-800` / `h-2 w-20 rounded bg-ink-100
  dark:bg-ink-800` for the name/secondary-line pair, similar bars for
  data/status/actions cells — no literal "…" text (the current
  `staff-directory-page.component.ts`/`top-articles-table.component.ts`
  loading state), that pattern is out of contract for the shared
  component even though it's tolerated in the not-yet-migrated screens.
- **Empty** (zero rows, no active filter): centered `py-12 text-center`
  block, muted icon (Lucide `LucideInbox`, `h-8 w-8 text-ink-300
  dark:text-ink-600 mx-auto mb-3`) + `text-sm text-ink-500
  dark:text-ink-400` message, transloco key
  `sharedList.empty.<screen-scoped-key>` — no generic hardcoded English
  string.
- **Empty with active filter** (search/filter yields zero rows):
  same layout, distinct copy (`sharedList.noResults`) plus a "clear
  filters" text button (`buttonClass('secondary', { ghost: true })`).
- **Error**: delegate to the existing `ErrorStateComponent`/
  `NoAccessStateComponent` (already used by every fetcher in this
  codebase — `top-articles-table.component.ts`,
  `staff-directory-page.component.ts`) rendered in place of the row
  area; do not build a new error visual.

### Responsive behavior (mobile, `<640px`/`sm:`)

- Below `sm:`, the column-header row and any data columns beyond the
  identity cell collapse: hide non-essential `<th>`/`<td>` via
  `hidden sm:table-cell`, keep checkbox, identity cell, status pill, and
  actions column visible (matches the reference's implicit priority:
  identity + status + actions are what a user scans on a phone).
  Sortable-column affordances that get hidden this way must also be
  reachable through a `sm:hidden` "sort by" `<select>` control above the
  list (reuses the existing native-select styling pattern from
  filter/search rows elsewhere in the app, not a new dropdown component).
- Row padding drops from `px-4 py-3` to `px-3 py-2.5` below `sm:` to
  keep rows dense on small screens without shrinking tap targets below
  44px (row-actions buttons stay `h-9 w-9` minimum tap area regardless
  of viewport).

### Accessibility

- Table semantics: native `<table>` with `<thead>`/`<tbody>`,
  `scope="col"` on header cells — do not replace with `<div>` grids,
  which is why the responsive column-hiding above uses
  `hidden sm:table-cell` rather than a flex/grid reflow.
- Row selection announces state via the checkbox's native semantics;
  the header "select all" checkbox additionally sets
  `[indeterminate]` (via a `ViewChild`/property binding, not an
  attribute) when some-but-not-all visible rows are selected.
- Sortable headers: `aria-sort` on the active column only
  (`ascending`/`descending`), `aria-sort="none"` on the rest; keyboard
  activation via native `<button>` inside the header cell (not a `<span
  role="button">`, matching this SPEC's REQ-2-adjacent bar of "don't
  build custom interactive semantics where a native element exists").
- Focus states: every interactive element (checkbox, sortable header
  button, row-action icon buttons, pagination controls) gets a visible
  `focus-visible:ring-2 focus-visible:ring-signal-500
  focus-visible:ring-offset-2 dark:focus-visible:ring-offset-ink-900`
  ring — the existing app-wide focus contract, not a component-local
  one.
- Live region: the "showing X–Y of Z" count is wrapped in
  `aria-live="polite"` so pagination/filter changes are announced,
  matching the tour/dynamic-content precedent already established
  elsewhere in the app.

### i18n keys to add (both `public/i18n/en.json` and `public/i18n/pt-BR.json`)

`sharedList.selectAll`, `sharedList.showingRange` (with `{{from}}`/
`{{to}}`/`{{total}}` params), `sharedList.actions.edit`,
`sharedList.actions.delete`, `sharedList.empty.default`,
`sharedList.noResults`, `sharedList.clearFilters`, `sharedList.sortBy`
— plus one `sharedList.empty.<screen>` entry per screen that adopts the
component (e.g. `sharedList.empty.staffDirectory`,
`sharedList.empty.tenantMembers`, `sharedList.empty.tenants`) so the
empty-state copy stays contextual instead of generic across very
different lists.

## User stories

- As a staff user managing members/staff, I want every list screen in
  the app (staff directory, tenant members, tenant list) to look and
  behave the same way, so the product feels coherent instead of
  patched together screen by screen.
- As a `STAFF_ADMIN`/`MEMBER_ADMIN` viewing another admin's row, I don't
  want to see individual permission checkboxes for them, since they
  already have every permission — I only want the option to demote them,
  and I want to understand why that option is unavailable when they're
  the last one.
- As a staff user deleting an account, I want the delete action
  available except when it would leave the system with zero
  `STAFF_ADMIN`s or zero `MEMBER_ADMIN`s, and I want to understand why
  when it's blocked.
- As any user reading a permission name, I want to see it in plain
  Portuguese, not a raw backend enum.
- As a staff user editing a regular `STAFF`/`MEMBER`'s permissions, I
  want a modern switch/toggle control, matching the rest of the app's
  design system, instead of checkboxes.
- As a staff user changing several permissions at once, I want to
  confirm once at the end, not once per permission.
- As a staff user changing any permission, granted or revoked, I want an
  extra explicit security-phrase confirmation step before it takes
  effect, since getting either direction wrong for the wrong person is a
  security risk.
- As a staff user, I want access groups to be manageable as their own
  entity (create one, see who's in it, add/remove members) independent
  of any single user's detail screen.
- As anyone reading the audit trail, I want timestamps in my own local
  time, in a compact format, and action names translated into readable
  Portuguese instead of raw event codes.
- As anyone on a user's detail screen, I want "Editar perfil" visible at
  the top of the screen, not buried after the entire audit trail.

## Requirements (EARS/GEARS)

### List layout standardization

- **REQ-1 [Ubiquitous]** Every list screen in the system (staff
  directory, tenant members, tenant list, and any future list screen)
  shall use one shared list layout component providing the same header,
  search input, pagination controls, and row-action affordances.
- **REQ-2 [Unwanted Behavior]** If a list screen is implemented or
  restyled outside that shared component, then it shall be treated as a
  defect against this requirement, not an acceptable local variation.

### Editing `STAFF_ADMIN`/`MEMBER_ADMIN` permissions

- **REQ-3 [Ubiquitous]** The user detail screen shall not present
  individual permission checkboxes/switches for a user whose role is
  `STAFF_ADMIN` or `MEMBER_ADMIN`.
- **REQ-4 [Ubiquitous]** The only permission-related action available
  for a `STAFF_ADMIN` user shall be "demote to `STAFF`"; the only one
  available for a `MEMBER_ADMIN` user shall be "demote to `MEMBER`."
- **REQ-5 [State-Driven]** While the target user is the only
  `STAFF_ADMIN` in the system, the "demote to `STAFF`" action shall be
  disabled and shall show an explanation that at least one `STAFF_ADMIN`
  must remain.
- **REQ-6 [State-Driven]** While the target user is the only
  `MEMBER_ADMIN` of the tenant, the "demote to `MEMBER`" action shall be
  disabled and shall show an explanation that at least one `MEMBER_ADMIN`
  must remain for that tenant.
- **REQ-7 [Event-Driven]** When an authorized user confirms a demote
  action on a target that is not the last admin of that role, the system
  shall call the (not-yet-built) demote endpoint and refresh the
  target's displayed role/permissions on success.
- **REQ-7a [Unwanted Behavior]** If the signed-in user viewing a
  `STAFF_ADMIN` target is not themselves a `STAFF_ADMIN`, then the
  "demote to `STAFF`" action shall not be shown at all (not merely
  disabled) — the same applies to a `MEMBER_ADMIN` target when the
  viewer is not a `STAFF_ADMIN` or that tenant's `MEMBER_ADMIN`. A
  `STAFF`/`MEMBER` viewer holding broad granted permissions is still not
  an admin and shall not see the action.

### Promoting a `STAFF`/`MEMBER` user

- **REQ-7b [Ubiquitous]** The user detail screen shall offer a "promote to
  `STAFF_ADMIN`" action for a `STAFF` user, and a "promote to
  `MEMBER_ADMIN`" action for a `MEMBER` user, symmetric to the demote
  action specified for admin-tier users in REQ-4/REQ-7a.
- **REQ-7c [Unwanted Behavior]** If the signed-in user viewing a `STAFF`
  target is not themselves `STAFF_ADMIN`, then the "promote to
  `STAFF_ADMIN`" action shall not be shown at all (not merely disabled)
  — the same applies to a `MEMBER` target's "promote to `MEMBER_ADMIN`"
  action when the viewer is not `STAFF_ADMIN` or that tenant's
  `MEMBER_ADMIN`, mirroring REQ-7a's visibility rule exactly. A
  `STAFF`/`MEMBER` viewer holding broad granted permissions is still not
  an admin and shall not see the action.
- **REQ-7d [Ubiquitous]** The promote action shall never be disabled on a
  "last admin"/admin-count basis — unlike demote (REQ-5/REQ-6), promotion
  is never blocked by any admin-count check.
- **REQ-7e [Event-Driven]** When an authorized user confirms a promote
  action, the system shall call the promote endpoint and refresh the
  target's displayed role/permissions on success.

### Deleting a user

- **REQ-8 [Ubiquitous]** The user detail/list screen shall offer a
  delete action for a `STAFF_ADMIN`, `STAFF`, `MEMBER_ADMIN`, or `MEMBER`
  user.
- **REQ-9 [State-Driven]** While the target user is the only
  `STAFF_ADMIN` in the system, the delete action shall be disabled with
  an explanation that at least one `STAFF_ADMIN` must remain.
- **REQ-10 [State-Driven]** While the target user is the only
  `MEMBER_ADMIN` of the tenant, the delete action shall be disabled with
  an explanation that at least one `MEMBER_ADMIN` must remain for that
  tenant.
- **REQ-11 [Ubiquitous]** The delete action for a `STAFF` or `MEMBER`
  user shall never be disabled on the "last one of this role" basis —
  that restriction applies only to `STAFF_ADMIN`/`MEMBER_ADMIN`, per
  REQ-9/REQ-10.
- **REQ-12 [Event-Driven]** When an authorized user confirms deleting a
  user whose deletion is not blocked by REQ-9/REQ-10, the system shall
  call the (not-yet-built) delete endpoint and remove the user from the
  visible list on success.
- **REQ-12a [Unwanted Behavior]** If the signed-in user viewing a
  `STAFF_ADMIN`/`MEMBER_ADMIN` target is not themselves an admin of the
  matching tier (per REQ-7a's rule), then the delete action for that
  target shall not be shown at all, regardless of any granted permission
  the viewer holds. This restriction does not apply to `STAFF`/`MEMBER`
  targets — see REQ-11.

### Human-readable permission names

- **REQ-13 [Ubiquitous]** Every place a permission (tenant or global) is
  displayed in the UI shall show a translated, human-readable PT-BR
  label (e.g. `TENANT_CREATE` → "Criar conta"/"Criar tenant") sourced
  from the app's existing i18n mechanism, never the raw enum value.
- **REQ-14 [Unwanted Behavior]** If a permission value has no entry in
  the translation map, then the system shall fall back to showing the
  raw enum value rather than failing to render, so an untranslated
  future permission never breaks the screen.

### Permission editing UX for non-admin (`STAFF`/`MEMBER`) users

- **REQ-15 [Ubiquitous]** Editing an individual permission for a
  non-admin (`STAFF` or `MEMBER`) user shall use a switch/toggle control
  matching the app's existing design system, replacing the current
  checkbox control.
- **REQ-16 [Ubiquitous]** Toggling any number of permission switches on
  the screen shall only change local, unsaved UI state — no request is
  sent to the backend until the user explicitly saves.
- **REQ-17 [Event-Driven]** When the user clicks "Save" after toggling
  one or more permissions, the system shall present exactly one
  confirmation step covering the full batch of changes, not one
  confirmation per changed permission, and shall submit the batch to the
  (not-yet-built) batched-save endpoint only after that single
  confirmation.
- **REQ-18 [Ubiquitous]** Whenever the pending batch of changes includes
  at least one newly granted permission, one revoked permission, or
  both, when the user confirms the batch save, the system shall require
  the existing security-phrase confirmation mechanism (the same pattern
  already used for destructive/sensitive actions, e.g.
  `ConfirmDialogComponent`) once for the whole batch before submitting.
- **REQ-19 [Unwanted Behavior]** If the pending batch contains no
  changes at all, then the system shall not offer the "Save" action, so
  the security-phrase step is never triggered with nothing to confirm.

### Access groups as an independent entity

- **REQ-20 [Ubiquitous]** Access group management (create a group, list
  existing groups, view/add/remove a group's members) shall live in its
  own screen/section, independent of any single user's detail view.
- **REQ-21 [Ubiquitous]** An access group shall be creatable and shall
  continue to exist with zero members.
- **REQ-22 [Ubiquitous]** The access-group management screen/section
  shall let an authorized user assign or unassign a `STAFF`/`MEMBER`
  user to/from a group.
- **REQ-23 [Unwanted Behavior]** If an attempt is made to assign a
  `STAFF_ADMIN` or `MEMBER_ADMIN` user to an access group, then the UI
  shall not offer that action — those roles are never eligible for
  access-group membership, per their implicit full-access status.
- **REQ-24 [Unwanted Behavior]** If a user's detail view is opened, then
  it shall not offer access-group creation inline — that action exists
  only on the screen introduced by REQ-20 (see "Amends prior SPECs").

### Audit trail readability

- **REQ-25 [Ubiquitous]** Every audit-trail timestamp shown in the UI
  shall be rendered in the viewing user's local timezone, in a compact
  format (e.g. `02/08/2026 02:23:30`), never a raw UTC/ISO string.
- **REQ-26 [Ubiquitous]** Every audit-trail action+outcome pair (e.g.
  `auth.logout` + `SUCCESS`) shall be shown as one readable PT-BR phrase
  (e.g. "Login realizado com sucesso" / "Logout") sourced from a
  translation map keyed by action and, where the meaning differs by
  outcome, by action+outcome together.
- **REQ-27 [Unwanted Behavior]** If an audit-trail action or
  action+outcome pair has no entry in that translation map, then the
  system shall fall back to showing the raw action string (and, if
  present, the raw outcome) rather than failing to render. This fallback
  exists only as a safety net for an action introduced after this
  translation map ships — it is not a substitute for coverage.
- **REQ-27a [Ubiquitous]** The translation map shall contain an entry for
  every audit action (and every action+outcome pair whose meaning
  differs by outcome) that the backend can emit as of this feature's
  implementation — enumerated by inventorying every audit-log call site
  in `knowly-api` (e.g. `AuditService`/`AuditEventPublisher` call sites,
  grep for the action-string literals passed to it), not guessed from
  the UI's own sample data. The REQ-27 fallback is expected to never
  actually be exercised by a real event during this feature's testing;
  if it is, that surfaces a missed entry to add, not an acceptable
  steady-state outcome.
- **REQ-27b [Event-Driven]** When a new audit action is added to the
  backend after this feature ships, the corresponding PR shall add its
  translation-map entry in the same change — keeping coverage complete
  going forward, not just at this feature's launch.

### User detail screen layout

- **REQ-28 [Ubiquitous]** The "Editar perfil" action shall be positioned
  in the header/top area of the user detail screen, visible without
  scrolling, regardless of how much audit-trail or permission content
  the rest of the screen contains.

## Non-functional requirements

- Design: follows the established "Ink & Signal" design system,
  hand-rolled Tailwind components, no component library — see
  `DECISIONS.md`.
- i18n: new translation maps (permission labels, audit-trail
  action/outcome phrases) are added to the existing Transloco
  `en`/`pt-BR` JSON files (`knowly-app/public/i18n/`), following the
  existing key-nesting conventions already used elsewhere in those
  files — not a new, separate i18n mechanism.
- Security: REQ-2/REQ-9/REQ-10/REQ-23's restrictions are UI-only
  guidance; the corresponding backend guard (last-admin protection,
  staff ceiling, access-group role eligibility) must independently
  enforce the same rule server-side — this SPEC never treats hiding an
  action in the UI as the actual security boundary.
- Accessibility: switches (REQ-15) and every other interactive control
  introduced here are operable by keyboard with visible focus states,
  matching the app's existing accessibility bar.

## Acceptance criteria

- [ ] Staff directory, tenant members, and tenant list all render via
      the one shared list layout component (pending the reference
      design — see "Open item" above).
- [ ] A `STAFF_ADMIN`/`MEMBER_ADMIN` row shows no permission
      checkboxes/switches — only a demote action.
- [ ] The demote action is disabled with an explanatory message when the
      target is the last `STAFF_ADMIN` (system-wide) or the last
      `MEMBER_ADMIN` (per tenant).
- [ ] A `STAFF` row/detail view offers a "promote to `STAFF_ADMIN`"
      action and a `MEMBER` row/detail view offers a "promote to
      `MEMBER_ADMIN`" action, gated by the same admin-viewer visibility
      rule as demote, never disabled on a last-admin basis.
- [ ] Delete is available for `STAFF_ADMIN`, `STAFF`, `MEMBER_ADMIN`, and
      `MEMBER`, except disabled (with explanation) when the target is
      the last `STAFF_ADMIN` or last `MEMBER_ADMIN` of the tenant.
- [ ] Permission names are shown as translated PT-BR labels everywhere,
      with raw-enum fallback for untranslated values.
- [ ] Non-admin permission editing uses switches, not checkboxes.
- [ ] Multiple permission toggles are batched and sent only on "Save,"
      with exactly one confirmation for the whole batch.
- [ ] Any non-empty batch of permission changes (grants, revokes, or
      both) requires the existing security-phrase confirmation mechanism
      once before saving.
- [ ] Access-group creation/listing/membership management exists on its
      own screen/section, not inside a user's detail view; a user's
      detail view no longer offers inline group creation.
- [ ] `STAFF_ADMIN`/`MEMBER_ADMIN` users are never offered as
      assignable to an access group.
- [ ] Audit-trail timestamps render in the viewer's local timezone in
      compact format; action+outcome pairs render as readable PT-BR
      phrases, with raw-value fallback for untranslated entries.
- [ ] "Editar perfil" is visible at the top of the user detail screen
      without scrolling.

## Out of scope

- **Backend: demote endpoint** (`STAFF_ADMIN`→`STAFF`,
  `MEMBER_ADMIN`→`MEMBER`) — needs its own backend SPEC; this frontend
  SPEC only specifies how the UI calls and reacts to it once it exists.
- **Backend: last-admin delete guard** (blocking deletion of the sole
  `STAFF_ADMIN`/`MEMBER_ADMIN`) — needs its own backend SPEC.
- **Backend: batched permission-save endpoint** — needs its own backend
  SPEC; today's grant/revoke endpoints are one-call-per-permission.
- **Backend: security-phrase gate enforced server-side on the grant
  path** — needs its own backend SPEC; REQ-18 only specifies the
  frontend UX around the existing client-side confirmation component,
  not a new server-side enforcement mechanism.
- The exact visual reference for the shared list layout (REQ-1) — to be
  supplied by the product owner before PLAN.md for this feature starts.
- Any change to tenant isolation, permission-check enforcement, or the
  `STAFF` ceiling (`role-model-refinement`) — this SPEC is UI-only and
  relies on those existing/forthcoming backend guarantees unchanged.
- Pagination of the audit trail or of any list beyond what
  `user-management-screens`/`staff-audit-trail-view` already specify —
  not addressed here.
- Any change to `MembersPageComponent`'s tenant-member data source or
  `StaffUserService`'s existing endpoints beyond consuming the new
  backend endpoints listed above once they exist.
