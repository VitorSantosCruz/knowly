# SPEC — navigation-menu

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-01**: Added REQ-7 through REQ-11 (and matching acceptance
  criteria) fixing a reported bug: a `MEMBER` with low/no tenant
  permissions, or a `STAFF` user without the tenant-list global
  permission, could end up with no visible way to see the app logo, log
  out, or reach their own tenant. REQ-1 through REQ-6 and all prior
  acceptance criteria/out-of-scope items are unchanged — nothing
  pre-existing was reinterpreted or removed.
- **2026-08-01 (clarification)**: Added REQ-12 and REQ-13 (and matching
  acceptance criteria): plain tenant members (`MEMBER`/`MEMBER_ADMIN`)
  must never see "Create tenant" or "leave tenant" options (staff-only
  concepts; a member's only context-change path is the REQ-9
  tenant-switch listing), and a `STAFF` user's "Create tenant" option
  must disappear while that staff user is acting inside an active
  tenant session, even if they hold `TENANT_CREATE` — tightening REQ-3's
  "staff session" to explicitly exclude "acting inside a tenant."
  REQ-1 through REQ-11 and all prior acceptance criteria/out-of-scope
  items are unchanged — nothing pre-existing was reinterpreted or
  removed.

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

**2026-08-01 addition:** a second bug was reported and is fixed by
REQ-7 through REQ-11 below: a `MEMBER` user could land on a minimal
pre-tenant-context layout (no sidebar, no logo, no avatar/logout menu)
with no way to see the logo or log out, even though every `MEMBER`
always belongs to at least one tenant. Separately, a `STAFF` user's
visibility of the tenant-switch/list menu item did not correctly account
for the case where staff lack the global tenant-list permission but are
themselves a member of a tenant (or lack both, in which case the item
must not appear at all).

**2026-08-01 clarification:** two ambiguities were flagged in the above
and are resolved by REQ-12 and REQ-13 below. First, "Create tenant" and
"leave tenant" are staff-only concepts and must never appear for a plain
tenant member (`MEMBER`/`MEMBER_ADMIN`) — a member's only way to change
context is the REQ-9 tenant-switch listing, never a "leave" action.
Second, REQ-3's "while viewing a staff session" condition did not
explicitly say whether a `STAFF` user currently acting inside a tenant
session (i.e. "acting as" that tenant) still counts as "viewing a staff
session" for the purpose of showing "Create tenant" — it must not: that
user must leave the tenant context back to the staff area before
"Create tenant" becomes visible again, regardless of holding
`TENANT_CREATE`.

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
- As a `MEMBER` with little or no permission in any of my tenants, I
  still want to always be able to see the app logo and log out — I
  should never be stranded on a screen with no way out.
- As a `STAFF` user who is also a member of a tenant but does not hold
  the global tenant-list permission, I want to be able to enter my own
  tenant and leave back to the staff area, without being shown a
  listing of tenants I have no right to browse.
- As a plain tenant member (`MEMBER`/`MEMBER_ADMIN`), I never want to see
  "Create tenant" or "leave tenant" options in my menu — those are
  staff-only concepts and would only confuse or mislead me about what I
  can actually do.
- As a `STAFF` user currently acting inside a tenant session, I want
  "Create tenant" to be hidden until I return to the staff area, so I'm
  not offered a staff-only action while I'm effectively operating as a
  tenant member.

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

### Added 2026-08-01 — member logo/logout visibility, staff tenant-list gating, and member/staff create-tenant-leave-tenant option gating

- **REQ-7 [Ubiquitous]** The app shall always show the system logo to
  any logged-in `MEMBER`, regardless of that member's permission level
  in any tenant they belong to and regardless of how many tenants they
  belong to.
- **REQ-8 [Ubiquitous]** The app shall always offer a way to log out to
  any logged-in `MEMBER`, regardless of that member's permission level
  in any tenant they belong to and regardless of how many tenants they
  belong to.
- **REQ-9 [State-Driven]** While a `MEMBER` belongs to more than one
  tenant, the menu shall show the tenant-switch listing so the member
  can switch between their memberships, regardless of their permission
  level in any of them.
- **REQ-10 [Complex]** Where the caller is a `STAFF` user, while that
  user holds the global tenant-list permission, or is a member of at
  least one tenant, the menu shall show a tenant-selection menu item;
  if the tenant-list permission is held, that item shall offer the full
  tenant listing plus the ability to enter and leave any tenant; if only
  a tenant membership is held (without the tenant-list permission), that
  item shall offer only entering the staff user's own tenant(s) and
  leaving back to the staff area, without a listing of other tenants.
- **REQ-11 [Unwanted Behavior]** If a `STAFF` user holds neither the
  global tenant-list permission nor any tenant membership, then the
  tenant-selection menu item shall not appear at all.
- **REQ-12 [Unwanted Behavior]** If the caller's active session is a
  tenant session with role `MEMBER` or `MEMBER_ADMIN`, then the menu
  shall not show "Create tenant" or "leave tenant" options — these are
  staff-only concepts; the only way such a caller can change context is
  the REQ-9 tenant-switch listing.
- **REQ-13 [State-Driven]** While a `STAFF` user is currently acting
  inside an active tenant session (i.e. "acting as" that tenant,
  regardless of role held within it), the menu shall not show "Create
  tenant", regardless of whether that user holds `TENANT_CREATE` —
  clarifying and tightening REQ-3's "viewing a staff session" condition
  to explicitly exclude this state; the option becomes visible again
  only once the user leaves that tenant session back to the staff area.

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
- [x] A `MEMBER` with zero permissions in every tenant they belong to
      still sees the logo and can still log out.
- [x] A `MEMBER` who belongs to exactly one tenant still sees the logo
      and can still log out (no regression from the multi-tenant case).
- [x] A `MEMBER` who belongs to more than one tenant sees the
      tenant-switch listing regardless of their permission level in any
      of those tenants.
- [x] A `STAFF` user holding the global tenant-list permission sees the
      tenant-selection menu item with the full listing and can enter and
      leave any tenant.
- [x] A `STAFF` user without the tenant-list permission, but who is a
      member of at least one tenant, sees the tenant-selection menu item
      but only with the ability to enter their own tenant and leave back
      to the staff area — no listing of other tenants.
- [x] A `STAFF` user with neither the tenant-list permission nor any
      tenant membership does not see the tenant-selection menu item at
      all.
- [x] A `MEMBER` or `MEMBER_ADMIN` in an active tenant session never
      sees a "Create tenant" or "leave tenant" option in the menu,
      regardless of their permission level.
- [x] A `STAFF` user granted `TENANT_CREATE` sees "Create tenant" while
      in the staff area, but that option disappears the moment they
      enter an active tenant session ("acting as" that tenant), and
      reappears only after they leave that tenant session back to the
      staff area.

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
- The search/listing UX a `STAFF` user would get if they *did* hold the
  tenant-list permission (pagination, filters, etc.) is already covered
  by existing tenant-listing behavior and is not re-specified here.
  What a `STAFF` user *without* that permission but *with* a tenant
  membership sees when searching/listing (as opposed to just entering
  their own tenant) is explicitly out of scope for this change — only
  the menu item's visibility and the enter/leave-own-tenant capability
  are in scope (REQ-10/REQ-11).
- Identifying or fixing the specific component/route responsible for
  the previously-reported minimal "welcome" pre-tenant layout is a PLAN-
  level concern, not a SPEC-level one; this SPEC only fixes the required
  *behavior* (REQ-7/REQ-8), not which file currently violates it.
- Whether a plain tenant member should ever be offered any kind of
  "leave this tenant entirely" (membership-removal) action is a
  separate, unrelated feature (membership self-removal) and is not
  decided or implied by REQ-12 — REQ-12 only says the *option* must not
  appear in this menu, not that such a capability should or shouldn't
  exist elsewhere.
