# SPEC — navigation-menu

> The what and the why. No technical implementation details.

## Context and motivation

There is currently no real navigation in this app: `app-shell.component.ts`
only has a fixed corner cluster (help, language, theme, logout) — moving
between `/dashboard`, `/articles`, `/conversations`, `/members` (and, for
staff, `/tenants/new`) requires knowing the URL, since nothing links
between them. This SPEC adds an actual navigation menu, filtered to what
the current user can actually do — mirroring the backend's
`staff-rbac-split`/tenant `Permission` model rather than showing
everything and letting a 403 explain why.

This also fixes a real bug uncovered while auditing this: `staffGuard`
(`core/staff.guard.ts`) decides "is this a staff account allowed to
create a tenant" by checking whether `GET /api/tenants` (list every
tenant) succeeds. That was a reasonable stand-in under the backend's old
model, where any staff account could do *everything* unconditionally —
but the backend's `staff-rbac-split` feature means a `STAFF` user's
access is now individually granted per action. A `STAFF` user granted
only `TENANT_CREATE` (not `TENANT_ACT_AS_ANY`) would be wrongly blocked
from `/tenants/new` under the current guard, because listing all tenants
requires a *different* permission than creating one. This SPEC replaces
that heuristic with the backend's real `GET /api/staff/permissions`
signal (added by `staff-rbac-split`), checked per-action like the
existing tenant-side `PermissionsService` already does.

## User stories

- As any logged-in user, I want a menu that lets me get to the sections
  I actually have access to, without needing to know URLs.
- As a tenant member, I want the menu to only show sections my tenant
  role/permissions actually grant me — matching what already happens
  page-by-page (a 403 today, a hidden link now).
- As staff, I want the menu (and gated actions like tenant creation) to
  reflect my *actual* granted global permissions, not an all-or-nothing
  guess based on an unrelated API call succeeding.
- As a multi-tenant user, I want a way to switch tenants from within the
  app, not just at initial login (explicitly deferred by `select-tenant`
  to a later feature — this is that feature).

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The app shell shall show a navigation menu
  listing every section the current session can access.
- **REQ-2 [State-Driven]** While viewing a tenant-scoped session, the
  menu shall show `Dashboard`/`Conversations`/`Articles`/`Members`
  filtered by the caller's effective tenant permissions (existing
  `GET /api/tenants/permissions`, existing `PermissionsService`) —
  `Members` requires `TENANT_MEMBER_MANAGE`, `Articles` requires
  `ARTICLE_VIEW`, `Conversations` requires `CONVERSATION_USE`,
  `Dashboard` requires `DASHBOARD_VIEW`.
- **REQ-3 [State-Driven]** While viewing a staff session, the menu shall
  show staff-only sections (currently just "Create tenant") filtered by
  the caller's effective global permissions
  (`GET /api/staff/permissions`, per `staff-rbac-split`) — "Create
  tenant" requires `TENANT_CREATE`.
- **REQ-4 [Event-Driven]** When a multi-membership user is in an active
  tenant session, the menu shall offer a way to switch to a different
  membership, reusing the existing `/select-tenant` screen/flow rather
  than a new switching mechanism.
- **REQ-5 [Unwanted Behavior]** If the caller holds none of the
  permissions gating a given section, then that section shall not
  appear in the menu at all (not shown-then-blocked).
- **REQ-6 [Ubiquitous]** `staffGuard` (and any other staff-only route
  gating) shall check the specific global permission the guarded route
  actually requires (via `GET /api/staff/permissions`), not whether an
  unrelated endpoint happens to succeed.

## Non-functional requirements

- Design: consistent with the established design-system standard.
- Performance: permissions (`GET /api/tenants/permissions` and/or
  `GET /api/staff/permissions`) are fetched once per session
  establishment, not per menu render — reuses the existing
  `PermissionsService` caching pattern for the tenant side, mirrored for
  the new staff side.
- Security: this is UI-only filtering — every action already re-enforces
  its own permission server-side (`RequiresPermission`/
  `RequiresGlobalPermission` aspects); this SPEC must not be treated as
  the actual authorization boundary, only as not showing dead ends.

## Acceptance criteria

- [x] The app shell shows a navigation menu with links to every
      accessible section.
- [x] A tenant member without `TENANT_MEMBER_MANAGE` does not see a
      "Members" link; one with it does.
- [x] A `STAFF` user without `TENANT_CREATE` does not see "Create
      tenant"; a `STAFF_ADMIN`, or a `STAFF` user granted it, does.
- [x] A multi-membership user can switch tenants from the menu without
      logging out.
- [x] `staffGuard` allows `/tenants/new` for a `STAFF` user granted only
      `TENANT_CREATE` (previously incorrectly blocked, since it checked
      `TENANT_ACT_AS_ANY` via the `GET /api/tenants` side-channel
      instead).

## Out of scope

- Any change to the backend's permission model itself — this consumes
  `staff-rbac-split`'s existing `GET /api/staff/permissions` endpoint
  as-is.
- A staff-side "user management" screen (creating/listing staff users,
  managing their global permissions) — a separate, later roadmap item
  (`knowly`'s `PROJECT_STATUS.md` item 5).
- Mobile-specific navigation patterns (hamburger menu, etc.) — follow
  whatever the existing design-system standard already prescribes for
  responsive layout; not a new decision for this SPEC to make.
