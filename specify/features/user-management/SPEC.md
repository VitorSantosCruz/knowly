# SPEC — User management

## Context and motivation

The tenancy backend already enforces deny-by-default permissions
(direct grants and access groups, per `knowly/specify/features/tenancy/SPEC.md`)
and exposes a full contract for managing them, but nothing in
`knowly-app` lets an admin actually use it — there's no screen to see who's
in a tenant, add or remove someone, or grant/revoke a permission. This
feature is that screen: a tenant admin's (or staff's) way to manage their
own tenant's people and access, reachable from the dashboard.

## User stories

- As a tenant admin, I want to see everyone in my tenant and their role,
  so I know who has access at all.
- As a tenant admin, I want to add a new person by email and pick their
  role, without needing to contact support.
- As a tenant admin, I want to remove someone's access without deleting
  their history.
- As a tenant admin, I want to grant or revoke a specific permission for
  one person directly, and see exactly which permissions they currently
  have (direct or via an access group) — not guess.
- As a tenant admin, I want to define a named access group once and
  assign it to multiple people, instead of repeating the same grants.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall show a members screen, reachable
  from the dashboard, listing every active member of the active tenant
  with their email and role.
- **REQ-2 [Event-Driven]** When an admin submits an email and role to add
  a member, the system shall call the add-member endpoint and refresh
  the list on success.
- **REQ-3 [Event-Driven]** When an admin removes a member, the system
  shall call the remove-member endpoint and remove them from the visible
  list, without any confirmation step beyond a single explicit "remove"
  action (backend soft-deletes; nothing is destructively lost).
- **REQ-4 [Ubiquitous]** The system shall let an admin open a member's
  detail view showing their direct permissions, their access groups, and
  the resulting effective permission set, clearly distinguishing all
  three.
- **REQ-5 [Event-Driven]** When an admin toggles a specific permission for
  a member in the detail view, the system shall call the grant or revoke
  endpoint accordingly and update the effective set shown, without a
  page reload.
- **REQ-6 [Ubiquitous]** The system shall let an admin create a named
  access group, list existing ones, and assign or unassign a member
  to/from one from the member detail view.
- **REQ-7 [Unwanted Behavior]** If any of these actions is denied by the
  backend (403), then the system shall show a clear, non-technical
  "you don't have permission for this" message rather than a raw error.
- **REQ-8 [Unwanted Behavior]** If the active tenant has no members visible
  (a state that shouldn't normally happen since the caller themselves is
  always a member) or the list is merely small, then the system shall
  still render the screen normally — no special empty-state handling
  needed beyond what an empty list naturally renders as.

## Non-functional requirements

- Design: follows the established design-system standard (slate/indigo
  palette, 8pt spacing grid, consistent card/button/input states).
- Accessibility: the member list and permission toggles are operable by
  keyboard, with clear focus states.

## Acceptance criteria

- [ ] The members screen lists all active members of the active tenant
      with email and role.
- [ ] Adding a member with a valid email and role refreshes the list to
      include them.
- [ ] Removing a member removes them from the visible list.
- [ ] Opening a member's detail view shows direct permissions, access
      groups, and effective permissions as distinct sections.
- [ ] Toggling a permission grants or revokes it and the effective set
      updates without a page reload.
- [ ] Creating an access group makes it available to assign to members.
- [ ] Assigning/unassigning an access group updates the member's access
      groups and effective permissions.
- [ ] A 403 from any action shows a clear permission-denied message, not
      a raw error.

## Out of scope

- Bulk actions (removing/granting for multiple members at once).
- Editing an existing member's role after creation (only add/remove is
  covered — a "change role" affordance can reuse the add-member endpoint,
  which already supports upsert-by-email server-side, but isn't
  surfaced as its own UI action here).
- Tenant creation itself (staff-only, no self-service UI planned).
- Any staff-specific cross-tenant management UI (a distinct future
  support-dashboard audience, per the tenancy SPEC's own out-of-scope).
