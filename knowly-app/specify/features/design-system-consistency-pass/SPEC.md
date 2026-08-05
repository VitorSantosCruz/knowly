# SPEC — Design system consistency pass

## Context and motivation

A user-driven QA pass surfaced that `knowly-app` has drifted from a single
consistent design language even though a good pattern already exists in
parts of the app (`SharedListComponent`'s icon-only row actions, sortable
columns, pagination). Specific complaints, verbatim intent preserved:

- Some actions use text buttons ("Edit", "Delete") where the rest of the
  app already uses icons for the same actions — inconsistent, and the
  newest addition (tenant deletion) introduced this regression itself.
- The tenants list (`select-tenant-page`) has a different layout from the
  members list, even though both are listings of the same conceptual
  shape (search, paginate, click a row, act on a row).
- Deleting a member/staff user today requires opening a large detail
  panel (ambiguously reachable via an "edit" affordance), scrolling past
  an unpaginated audit-trail table, to find the delete action at the very
  bottom. This is confusing and slow.
- There is no way to edit your own profile from `/members` — only from
  `/profile` — a jarring, unexplained path difference.
- The sidebar (`nav-menu`) is always fixed at full width, which is poor
  on smaller screens; it should collapse to an icon-only rail (desktop)
  with hover tooltips, or an off-canvas drawer (mobile), not disappear
  outright.

This SPEC turns those complaints into one coherent, documented standard so
future screens are built consistently instead of each reinventing its own
list/action/detail pattern. A companion backend SPEC
(`knowly-api/specify/features/paginated-audit-trail/SPEC.md`) covers the
one piece of backend surface this pass depends on (paginating the
staff-user audit-trail endpoint).

## User stories

- As any user of the app, I want the same action (edit, delete, view
  history) to always use the same icon, so I don't have to re-learn the
  UI on every screen.
- As any user, I want every listing screen (tenants, members, staff
  users, ...) to look and behave the same way, so switching between them
  doesn't feel like switching apps.
- As an admin, I want to edit, delete, or view a person's history as
  three separate, clearly-labeled actions from the list itself, instead
  of hunting for them inside one large panel.
- As a member, I want to reach my own profile edit screen from
  `/members`, the same place I'd look to edit anyone else's, instead of
  needing to already know `/profile` exists.
- As a user on a small screen, I want the sidebar out of my way by
  default, with a quick way to expand it when I need the labels.

## Requirements (EARS/GEARS)

### Icons

- **REQ-1 [Ubiquitous]** The system shall represent edit, delete, and
  view-history actions with the same icon everywhere they appear:
  `LucideSquarePen` (edit), `LucideTrash` (delete — not `LucideTrash2`),
  `LucideHistory` (view history/audit trail).
- **REQ-2 [Event-Driven]** When the tenants list renders its delete
  action, the system shall use an icon-only button (`LucideTrash`) via
  the shared list-row-action pattern, not a text label.
- **REQ-3 [Unwanted Behavior]** If a screen has a genuine reason to keep a
  text-labeled action (e.g. a rare, high-stakes confirm/cancel step),
  then the system shall keep text there — this is a documented exception,
  not a gap (see "Out of scope").

### List layout

- **REQ-4 [Ubiquitous]** The system shall render every listing screen
  (tenants, members, staff users) through the same shared list component,
  with the same search, pagination, and row-action affordances.
- **REQ-5 [Complex]** Where a listing's data source is server-paginated
  (e.g. the staff all-tenants fallback), while rendering through the
  shared list component, when the user changes page, the system shall
  request the next page from the server rather than paginating an
  in-memory copy.

### Edit / delete / history as three separate actions

- **REQ-6 [Event-Driven]** When an admin clicks the edit icon on a member
  or staff-user row, the system shall open that person's profile directly
  in edit mode, not a general-purpose panel requiring a further click to
  find edit.
- **REQ-7 [Event-Driven]** When an admin clicks the delete icon on a
  member or staff-user row, the system shall open the existing
  security-phrase confirmation dialog directly, without requiring the
  detail panel to be opened first.
- **REQ-8 [Event-Driven]** When an admin clicks the history icon on a
  member or staff-user row, the system shall open that person's audit
  trail as its own paginated view (see companion backend SPEC for the
  paginated endpoint), separate from the edit/delete flows.
- **REQ-9 [Ubiquitous]** The system shall keep permission toggles,
  access-group assignment, and promote/demote inside the existing detail
  panel — only edit-profile, delete, and audit-trail move to list-level
  actions.

### Own-profile edit path from `/members`

- **REQ-10 [Unwanted Behavior]** If the viewer's own row is rendered in
  the members list, then the system shall show a "my profile" action
  that navigates to `/profile`, instead of the edit icon (which stays
  disabled/absent for one's own row, per the existing backend rule that
  self-edit-by-request is never allowed for anyone, including admins).

### Sidebar collapse/expand

- **REQ-11 [Event-Driven]** When the user toggles the sidebar on a
  desktop-width viewport, the system shall switch the sidebar between a
  full width (icon + label) state and a narrow, icon-only state, keeping
  it visible and navigable in both states.
- **REQ-12 [State-Driven]** While the sidebar is in its icon-only state,
  the system shall show a tooltip with the item's label when the pointer
  hovers over an icon.
- **REQ-13 [State-Driven]** While the viewport is mobile-width, the
  system shall render the sidebar collapsed by default as an off-canvas
  overlay (not a persistent icon rail), opened via a toggle and closed by
  backdrop click, route change, or Escape.
- **REQ-14 [Ubiquitous]** The system shall remember the desktop
  collapsed/expanded choice across navigations within the session.

## Non-functional requirements

- Accessibility: icon-only buttons keep an accessible name
  (`aria-label`/`title`) even without visible text; the sidebar toggle
  exposes `aria-expanded`/`aria-controls`; hover tooltips must also be
  reachable via keyboard focus, not mouse-only.
- Responsiveness: sidebar behavior is explicitly specified for both
  desktop and mobile breakpoints (REQ-11 through REQ-13).
- Consistency: no new icon may be introduced for a concept that already
  has one elsewhere in the app (see the icon inventory below).

## Icon inventory (exact `@lucide/angular` exports)

| Action | Icon |
|---|---|
| Edit | `LucideSquarePen` |
| Delete | `LucideTrash` |
| View history/audit | `LucideHistory` |
| Create/add | `LucidePlus` |
| Pagination prev/next | `LucideChevronLeft` / `LucideChevronRight` |
| Sidebar collapse/expand toggle | `LucidePanelLeftClose` / `LucidePanelLeftOpen` |
| Close (dialog/panel) | `LucideX` |

## Acceptance criteria

- [ ] Tenants list and members list render through the same shared list
      component, with visually identical search/pagination/row styling.
- [ ] Tenant delete action is icon-only (`LucideTrash`), matching
      members/staff-user delete.
- [ ] Clicking edit on a member/staff-user row opens directly into edit
      mode; clicking delete opens the confirmation dialog directly;
      clicking history opens a separate paginated audit-trail view.
- [ ] The viewer's own row in `/members` offers a working path to
      `/profile`.
- [ ] Sidebar collapses to an icon-only rail with hover tooltips on
      desktop, and to an off-canvas drawer on mobile; state persists
      across navigation within the session.

## Out of scope

- Reversing REQ-11 of `user-profile-v2` (the backend's unconditional
  self-edit-by-request exclusion) — confirmed backend-enforced, not a
  UI-only restriction; not being touched by this pass.
- Introducing icons for rare, high-stakes confirm/cancel steps
  (`ConfirmDialogComponent`'s security-phrase flow, inline
  promote/demote confirms) — these stay text, deliberately.
- Unifying fonts — the existing two-font system (`Fraunces` for
  `.font-display` headings, `Inter` for body) is an intentional design
  decision, not an inconsistency; any missing page-level `<h1>` gaps
  found during implementation should be fixed for heading-hierarchy
  consistency, but this is not a font change.
- A search icon inside list search inputs — nice-to-have, not part of
  this pass's acceptance criteria.
