# SPEC — staff-leave-tenant

> The what and the why. No technical implementation details.

## Context and motivation

A ConectaByte staff user can enter a tenant's workspace via
`/select-tenant` (`ActiveTenantService.selectTenant()`, `POST
/api/tenants/active`) and see that tenant's dashboard/data. Per this
app's own "general nav rule" (`PROJECT_STATUS.md` item 5), once inside a
tenant, every staff-only/global-scope nav option disappears and the UI
looks exactly like a regular tenant member's — which is working as
intended. What's missing is any way *back*: there is no "leave tenant"
UI, no client-side method to clear the active tenant, and (per the
companion backend SPEC,
`knowly-api/specify/features/staff-leave-tenant/SPEC.md`) no server-side
endpoint to clear the session's active-tenant state either. Today the
only way out of a tenant is switching straight into a *different* tenant
via `/select-tenant`, or logging out entirely and back in.

This is staff-only. A regular tenant member (`MEMBER`/`MEMBER_ADMIN`)
has no "outside any tenant" view to return to — they always belong to
whichever tenant(s) they're a member of; `nav-menu.component.ts`'s
existing `canSwitchTenant` (`memberships().length !== 1`) already
reflects this asymmetry: it shows the switch-tenant link for a
0-membership staff session (their *only* path to any tenant) or a
multi-membership session, but a single-membership member never sees it,
because they have nowhere else to go. "Leave tenant" is the same shape,
one level narrower: it's only meaningful for a staff session that
currently has **zero** real `TenantMembership` rows (confirmed staff
shape, per `ActiveTenantService`'s own doc comment) but is nonetheless
acting as a tenant (`activeTenantId()` set) — never for a member with a
real membership, admin or not.

## User stories

- As a ConectaByte staff user currently acting as a tenant (via
  `/select-tenant`), I want to leave that tenant context and return to
  the global staff view so that I don't have to switch into an unrelated
  tenant or log out just to get back to staff-only screens (tenant
  create/list, global metrics, staff directory).

## Requirements (EARS/GEARS)

1. **[Ubiquitous]** `ActiveTenantService` shall expose a `leaveTenant()`
   method that calls the backend's `POST /api/tenants/active/clear`
   endpoint and, on success, clears the local `activeTenantId`/
   `activeTenantName`/`activeTenantRole` signals to `null`.
2. **[State-Driven]** While the current session is staff (zero
   memberships, per `ActiveTenantService.list()` returning an empty
   array) and has an active tenant selected, the nav menu shall show a
   "Leave tenant" action, in the same workspace nav group as the existing
   "switch tenant" link.
3. **[Unwanted Behavior]** If the current session has one or more real
   `TenantMembership` rows (a regular member, `MEMBER` or
   `MEMBER_ADMIN`, admin or not), then the nav menu shall never show the
   "Leave tenant" action, regardless of how many memberships or which
   tenant is active — this mirrors `canSwitchTenant`'s existing
   membership-count-based gating, extended one step further (zero
   memberships is a strictly stronger condition than "not exactly one").
4. **[Event-Driven]** When the "Leave tenant" action is activated, the
   system shall call `ActiveTenantService.leaveTenant()` and, on success,
   navigate to `/welcome`.
5. **[Unwanted Behavior]** If `leaveTenant()`'s underlying HTTP call
   fails (network error or non-2xx response, including a 403 the backend
   SPEC defines for a non-staff caller reaching the endpoint some other
   way), then the system shall leave the local active-tenant signals
   unchanged and surface the existing generic error-handling pattern
   already used elsewhere in this app for a failed mutating call — no
   silent partial state (e.g. clearing local signals while the server
   call actually failed).
6. **[Ubiquitous]** The "Leave tenant" action shall require no
   confirmation dialog before executing — consistent with the existing
   "switch tenant" flow (`/select-tenant`), which also applies its effect
   immediately on selection with no confirmation step, and because the
   action is non-destructive and fully reversible (the user can always
   re-select the same tenant from `/select-tenant`).

## Non-functional requirements

- Accessibility: the "Leave tenant" nav item follows the same
  keyboard-navigable, focus-visible, `data-testid`-carrying pattern as
  every other `nav-menu.component.ts` item (e.g. `nav-switch-tenant`) —
  no new accessibility pattern introduced.
- Performance: no new polling or background fetch; `leaveTenant()` is a
  single on-demand HTTP call triggered by user action, same cost profile
  as `selectTenant()`.
- Responsiveness: renders within the existing nav menu's responsive
  layout (desktop sidebar / mobile drawer, whichever the current nav
  menu already supports) — no new breakpoint behavior.

## Acceptance criteria

- [ ] A staff session with zero memberships and an active tenant
      selected sees a "Leave tenant" nav item, positioned alongside
      "switch tenant" in the workspace nav group.
- [ ] A regular tenant member (any membership count, any role) never
      sees a "Leave tenant" nav item.
- [ ] A staff session with zero memberships and **no** active tenant
      selected (already at the global staff view) does not see "Leave
      tenant" (nothing to leave).
- [ ] Activating "Leave tenant" calls `POST /api/tenants/active/clear`,
      and on success clears `activeTenantId`/`activeTenantName`/
      `activeTenantRole` and navigates to `/welcome`.
- [ ] After leaving, the nav menu immediately reflects the global staff
      view (staff-only links reappear, tenant-scoped links disappear) —
      the same "one screen, two contexts" toggle already driven by
      `ActiveTenantService`'s signals elsewhere in the app.
- [ ] A failed `leaveTenant()` call leaves the active-tenant signals and
      current route unchanged, and surfaces this app's existing generic
      error-handling UI for a failed mutating request.
- [ ] No confirmation dialog appears before the action executes.

## Out of scope

- Any equivalent "leave"/"exit" capability for a regular tenant member —
  per REQ-3, a member with a real `TenantMembership` always belongs to
  their tenant(s) and has no global, tenant-less view to return to. If
  that assumption ever changes, it needs its own SPEC.
- Changing how a staff user *enters* a tenant (`/select-tenant`,
  `selectTenant()`) — unchanged by this feature.
- Any new confirmation-dialog UI pattern — per REQ-6, this action
  deliberately has none, consistent with the existing switch-tenant flow.
- Redesigning the nav menu's workspace group beyond adding this one item
  next to the existing "switch tenant" link.
- The backend endpoint itself (`POST /api/tenants/active/clear`) —
  covered by the companion backend SPEC,
  `knowly-api/specify/features/staff-leave-tenant/SPEC.md`.
