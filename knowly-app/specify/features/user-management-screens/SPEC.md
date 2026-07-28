# SPEC — User management screens

## Context and motivation

`knowly-app` already has `user-management` — a tenant-scoped members
screen (list, add, remove, permission/access-group detail) built against
the `tenancy` backend. Separately, the backend now has a full staff-side
API surface (`staff-rbac-split`'s per-user detail/grant/access-group
endpoints, `staff-user-provisioning`'s create endpoint, and
`staff-user-listing`'s `GET /api/staff/users` listing/search endpoint) —
but nothing in the frontend exposes any of it. A staff user today has no
screen to see who else is on the staff roster, create a new staff
account, or inspect/manage a staff user's global permissions.

This feature closes that gap with **one screen component that behaves
differently depending on context**, per the rule confirmed by the user
2026-07-26 (`PROJECT_STATUS.md` item 5) and the general nav pattern
`navigation-menu` already established (staff-only nav items disappear
entirely once inside a tenant):

- **Inside a tenant** (an active tenant is selected): the screen shows
  that tenant's members only — this context reuses the existing
  `user-management` feature exactly as it works today. This SPEC does
  not change `user-management`'s behavior; it only clarifies where it
  sits relative to the new staff context and enforces that a staff user
  inside a tenant sees *only* this tenant view, never the staff view.
- **Outside any tenant** (staff, no active tenant selected): the screen
  shows the global staff directory — list/search staff users, view a
  staff user's permission/access-group detail, and create a new staff
  user via the existing `staff-user-provisioning` endpoint.

The screen is never both at once, and which one renders is driven purely
by whether the session currently has an active tenant — the same signal
`navigation-menu`/`welcome-screen` already use to distinguish "inside a
tenant" from "staff, no tenant." This is also where the
`role-model-refinement` **STAFF ceiling** becomes a UI concern for the
first time: a `STAFF` (non-`STAFF_ADMIN`) viewer of the staff directory
must not be offered management actions (create staff user, edit
permissions, assign access groups) against a `STAFF`/`STAFF_ADMIN` row,
since the backend will reject those calls with a 403 regardless of what
the UI shows — this SPEC makes the UI reflect that ceiling up front
instead of surfacing dead-end actions.

**Backend note**: no new backend SPEC accompanies this frontend SPEC.
The listing/search gap flagged in `PROJECT_STATUS.md` item 5 is already
closed by the pre-existing `knowly-api/specify/features/staff-user-listing/`
feature (`GET /api/staff/users`, `GlobalPermission.STAFF_USER_VIEW`,
already implemented — only its final batched verify/commit pass is
pending per its own TASKS.md). This screen consumes that endpoint plus
`staff-rbac-split`'s and `staff-user-provisioning`'s existing endpoints
as-is.

## User stories

- As a staff user outside any tenant, I want to see a list of all staff
  users so I know who's on the team.
- As a staff user outside any tenant, I want to search that list by
  email so I can find a specific account without scrolling.
- As a `STAFF_ADMIN` (or a `STAFF` user granted `STAFF_USER_CREATE`), I
  want to create a new staff user by email from this screen, without
  needing to call the API directly.
- As a staff user with the right global permission, I want to open a
  staff user's detail view to see their direct permissions, access
  groups, and effective permission set.
- As a `STAFF_ADMIN` or a permitted `STAFF` user, I want to grant/revoke
  a staff user's global permissions and assign/unassign their access
  groups from that detail view.
- As a `STAFF` user (not `STAFF_ADMIN`), I want management actions
  against a `STAFF`/`STAFF_ADMIN` row to be visibly unavailable, not a
  button that fails with an error after I click it.
- As a staff user who switches into a tenant, I want the screen at that
  point to show only that tenant's members — never the staff directory —
  so I can't accidentally (or intentionally) reach staff/global user
  management from inside a tenant's context.
- As a tenant admin (`MEMBER_ADMIN`) with no staff role at all, I want
  the screen to behave exactly as `user-management` does today — nothing
  about this feature should change my experience.

## Requirements (EARS/GEARS)

- **REQ-1 [State-Driven]** While the session has an active tenant
  selected, the user-management screen shall render the existing
  `user-management` tenant-members view (list/add/remove members,
  per-member permission/access-group detail) unchanged, scoped to that
  tenant only.
- **REQ-2 [State-Driven]** While the session is staff with no active
  tenant selected, the user-management screen shall render the staff
  directory view instead of the tenant-members view.
- **REQ-3 [Unwanted Behavior]** If the session has an active tenant
  selected, then the staff directory view (list of staff users, staff
  user detail, staff user creation) shall not be reachable through this
  screen by any route or link — matching the general nav rule that every
  staff-only/global-scope option disappears entirely once inside a
  tenant.
- **REQ-4 [Ubiquitous]** The staff directory view shall list every
  `STAFF`/`STAFF_ADMIN` user (id, email, global role), sourced from
  `GET /api/staff/users`.
- **REQ-5 [Event-Driven]** When a search term is entered in the staff
  directory view, the system shall call `GET /api/staff/users?email=`
  with that term and refresh the visible list to the filtered result.
- **REQ-6 [Event-Driven]** When an authorized staff user submits an email
  to create a new staff user, the system shall call the existing staff
  user creation endpoint and, on success, refresh the directory list to
  include the new account.
- **REQ-7 [Ubiquitous]** The staff directory view shall let an authorized
  staff user open a staff user's detail view showing their direct global
  permissions, global access groups, and resulting effective permission
  set, clearly distinguishing all three — mirroring `user-management`'s
  existing member-detail pattern.
- **REQ-8 [Event-Driven]** When an authorized staff user toggles a
  specific global permission for a staff user in the detail view, the
  system shall call the grant or revoke endpoint accordingly and update
  the effective set shown, without a page reload.
- **REQ-9 [Ubiquitous]** The staff directory view shall let an authorized
  staff user create a named global access group, list existing ones, and
  assign or unassign a staff user to/from one from that user's detail
  view.
- **REQ-10 [State-Driven]** While the viewing session's global role is
  `STAFF` (not `STAFF_ADMIN`), the staff directory and detail views shall
  disable or hide every management action (create staff user, grant/
  revoke permission, assign/unassign access group) that targets a row
  whose global role is `STAFF` or `STAFF_ADMIN` — mirroring the backend's
  `role-model-refinement` ceiling so the UI never offers an action the
  backend will reject.
- **REQ-11 [State-Driven]** While the viewing session's global role is
  `STAFF_ADMIN`, no REQ-10 restriction shall apply — every management
  action is available exactly as it is today.
- **REQ-12 [Unwanted Behavior]** If any staff directory/detail action is
  denied by the backend (403) — including a management action against a
  `STAFF`/`STAFF_ADMIN` target that REQ-10 failed to pre-emptively hide
  (e.g. a permission granted/revoked between page load and the click) —
  then the system shall show the same clear, non-technical
  "you don't have permission for this" message `user-management` already
  shows for its own 403s, rather than a raw error.
- **REQ-13 [Unwanted Behavior]** If the caller holds no global permission
  to view the staff directory at all (`STAFF_USER_VIEW` not granted, and
  not `STAFF_ADMIN`), then the screen's staff-context entry point
  (nav link and/or route) shall not be shown, consistent with
  `navigation-menu`'s existing "hidden, not shown-then-blocked" rule.

## Non-functional requirements

- Design: follows the established design-system standard ("Ink & Signal"
  palette, hand-rolled Tailwind components, no component library — see
  `DECISIONS.md`), consistent with `user-management`'s existing look.
- Accessibility: the staff directory list, search input, and permission
  toggles are operable by keyboard, with clear focus states — matching
  `user-management`'s existing accessibility bar.
- Security: this is UI-only filtering/hiding (REQ-3, REQ-10, REQ-13) —
  every underlying action is independently re-enforced server-side
  (`@RequiresGlobalPermission`, the `role-model-refinement` ceiling); this
  SPEC must never be treated as the actual authorization boundary, only
  as not showing dead ends, per the same principle `navigation-menu`
  already established.
- No pagination: `GET /api/staff/users` is unpaginated by its own SPEC
  (`staff-user-listing`, confirmed out of scope there,
  `PROJECT_STATUS.md` item 11 tracks pagination project-wide) — the
  staff directory view renders the full returned list, the same
  unbounded-list pattern `user-management`'s tenant member list already
  uses.

## Acceptance criteria

- [x] Inside an active tenant, the screen shows exactly `user-management`'s
      existing tenant-members behavior (list/add/remove members, member
      detail with direct/group/effective permissions) — unchanged.
- [x] Outside any tenant (staff session), the screen shows the staff
      directory instead — never both views, never the tenant view.
- [x] The staff directory nav entry/route is not shown at all once a
      staff user switches into a tenant, and reappears once they leave
      back to the tenant list.
- [x] The staff directory lists every `STAFF`/`STAFF_ADMIN` user (id,
      email, global role).
- [x] Searching by email filters the staff directory list via
      `GET /api/staff/users?email=`.
- [x] An authorized staff user can create a new staff user by email; the
      directory list refreshes to include them.
- [x] Opening a staff user's detail view shows direct permissions, access
      groups, and effective permissions as distinct sections.
- [x] Toggling a permission grants or revokes it and the effective set
      updates without a page reload.
- [x] Creating a global access group makes it available to assign to
      staff users; assigning/unassigning updates the staff user's access
      groups and effective permissions.
- [x] A `STAFF` (non-`STAFF_ADMIN`) viewer sees create/grant/revoke/
      assign/unassign actions disabled or hidden against any
      `STAFF`/`STAFF_ADMIN` row; a `STAFF_ADMIN` viewer sees them fully
      available.
- [x] A 403 from any staff-directory action (including one REQ-10 didn't
      pre-emptively catch) shows the existing non-technical
      permission-denied message, not a raw error.
- [x] A caller without `STAFF_USER_VIEW` (and not `STAFF_ADMIN`) does not
      see the staff directory entry point at all.

## Out of scope

- Any change to `user-management`'s own tenant-member behavior — reused
  as-is; this feature only clarifies its place alongside the new staff
  context and enforces the never-both-contexts rule.
- Bulk actions (creating/granting for multiple staff users at once) —
  matches `user-management`'s existing out-of-scope line.
- Editing an existing staff user's global role (`STAFF` ↔ `STAFF_ADMIN`
  promotion/demotion) — no backend mechanism exists for this
  (`staff-user-provisioning`'s own "Out of scope"); not addressed here.
- Deactivating/removing a staff user — no backend endpoint exists for
  this today (`staff-user-provisioning`'s own "Out of scope"); not
  addressed here.
- Staff user profile fields (name, address, documents) or any profile
  edit/view UI — belongs to the separate, not-yet-implemented
  `identity-profile-model` feature (`PROJECT_STATUS.md` item 13); this
  screen only ever shows `id`/`email`/`globalRole`/permissions/access
  groups, matching `staff-user-listing`'s and `staff-rbac-split`'s
  response shapes exactly.
- Audit trail viewing for a staff or tenant user — a separate,
  not-yet-built concern (`PROJECT_STATUS.md` item 6's staff global
  dashboard mentions a member-listing screen with audit-trail viewing;
  that is a distinct, later feature, not this one).
- Pagination on the staff directory list — matches `staff-user-listing`'s
  own out-of-scope line; `PROJECT_STATUS.md` item 11 tracks this
  project-wide.
- Any new backend endpoint — this feature consumes the existing
  `GET /api/staff/users` (`staff-user-listing`), `POST /api/staff/users`
  (`staff-user-provisioning`), and `staff-rbac-split`'s per-user detail/
  grant/access-group endpoints exactly as they exist today; no backend
  SPEC accompanies this frontend SPEC (see the "Context and motivation"
  section above for why).
