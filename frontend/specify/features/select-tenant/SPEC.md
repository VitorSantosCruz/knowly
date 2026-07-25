# SPEC — Select tenant

## Context and motivation

The backend's `tenancy` feature has always required a multi-membership
user to explicitly pick an active tenant after login (session left in a
"selection pending" state, every tenant-scoped endpoint returning
`409 TENANT_SELECTION_REQUIRED` until they do) — `onboarding-dashboard`'s
own SPEC even referenced "immediately after tenant selection for a
multi-membership user" as a trigger for the first-run tour. But the
screen where that selection actually happens was never built: a
multi-membership user landed on `/dashboard` after login with no active
tenant, and every tenant-scoped widget failed with a generic error.

## User stories

- As a user who belongs to more than one tenant, I want to be asked
  which one to use right after logging in, so the rest of the app
  actually works instead of failing on every tenant-scoped screen.
- As a user who belongs to exactly one tenant, I want the app to just
  use it without asking me anything (already the case server-side).

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When a multi-membership user (more than one
  active-eligible membership, none yet active) reaches any tenant-scoped
  route, the system shall redirect them to `/select-tenant` instead of
  loading that route.
- **REQ-2 [Ubiquitous]** The `/select-tenant` screen shall list every
  tenant the user is a member of, by name.
- **REQ-3 [Event-Driven]** When the user picks a tenant, the system
  shall set it as the session's active tenant and navigate to
  `/dashboard`.
- **REQ-4 [Optional Feature]** Where a user has only one membership (or
  one already marked active), the system shall never show this screen —
  matches the backend's own single-membership auto-select.
- **REQ-5 [State-Driven]** *(Amended 2026-07-25 — reverses the original
  version of this requirement, which sent a 0-membership session to
  `/select-tenant`.)* While a session has zero tenant memberships (the
  staff case), the system shall let every tenant-independent route
  (starting with `/dashboard`) load directly, never redirecting to
  `/select-tenant` — staff land on the dashboard/welcome screen like any
  other session, and pick a tenant to act as later via the nav menu's
  "switch tenant" link (`navigation-menu`) rather than being forced
  through tenant selection before they can do anything.
- **REQ-6 [Optional Feature]** Where a staff session (0 memberships)
  navigates to `/select-tenant` directly (e.g. via the "switch tenant"
  link), the screen shall fall back to `GET /api/tenants` (every tenant
  in the system) instead of showing an empty list.
- **REQ-7 [Unwanted Behavior]** If the all-tenants fallback request
  itself fails (e.g. 403 for a non-staff user who somehow reached this
  screen with 0 memberships), then the screen shall show an empty
  state rather than an unhandled error.

## Non-functional requirements

- Design: consistent with the established design-system standard.
- Security: relies entirely on the backend's existing
  `TENANT_SELECTION_REQUIRED`/active-membership enforcement — this
  screen doesn't introduce any new authorization logic, only the UI to
  resolve the state the backend already gates on.

## Acceptance criteria

- [x] A route guard redirects any tenant-scoped route to
      `/select-tenant` when no membership is active and more than one
      exists.
- [x] `/select-tenant` lists all of the user's tenants.
- [x] Picking one sets it active and navigates to `/dashboard`.
- [x] A single-membership user never sees this screen.
- [x] A 0-membership (staff) session is let through to `/dashboard`
      directly, never redirected to `/select-tenant` (amended
      2026-07-25 — previously this criterion required the opposite).
- [x] `/select-tenant` falls back to listing every tenant in the
      system when memberships is empty, letting a staff user pick one
      to act as (now reached via the nav menu's "switch tenant" link,
      not forced at login).

## Out of scope

- Any switch-tenant UI beyond the nav menu link already built in
  `navigation-menu` — this SPEC only covers the `/select-tenant` screen
  itself and when it's reached, not menu design.
