# SPEC — welcome-screen

## Context and motivation

There was no real landing screen: post-login navigation (and the tenant
picker) sent everyone straight to `/dashboard` — a metrics-heavy,
permission-gated screen (`onboarding-dashboard`). Two real bugs came out
of this: a fresh login for a multi-membership user or a staff account
(0 memberships) landed on `/select-tenant` instead of any kind of
"welcome," and staff — who have no tenant, so no metrics to show — had
no home screen at all beyond a screen full of permission-denied widgets.

This feature adds a proper landing screen (`/welcome`): a friendly,
tenant-branded (or staff-generic) greeting with no sensitive information
and no permission-gated content, reachable by anyone who's authenticated
regardless of role or tenant state. The metrics dashboard
(`onboarding-dashboard`) becomes a screen you navigate *to* (via the nav
menu), not the landing screen itself — see that SPEC's amended REQ-1/
REQ-6.

## User stories

- As any user (staff or tenant member) right after logging in, I want a
  clear, friendly welcome that explains what knowly does, without seeing
  any permission errors or sensitive data.
- As a tenant member, I want that welcome to greet me by my tenant's
  name and offer a way to the actual metrics dashboard.
- As staff (no tenant), I want a welcome screen too, not the tenant
  picker or an empty/broken dashboard.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When a session has no active tenant (the
  staff case), `/welcome` shall show a generic, tenant-independent
  greeting — no tenant name, no sensitive information, no
  permission-gated widgets.
- **REQ-2 [Event-Driven]** When a session has an active tenant,
  `/welcome` shall greet the user by that tenant's name and offer a link
  to the metrics dashboard (`/dashboard`).
- **REQ-3 [Event-Driven]** When a user reaches `/welcome` for the first
  time after establishing an active tenant, the onboarding tour
  (`onboarding-dashboard` REQ-1–REQ-5, unchanged) shall start here
  instead of on `/dashboard`.
- **REQ-4 [Ubiquitous]** `/welcome` is reached: right after login
  (single-membership or staff), right after explicit tenant selection
  (`/select-tenant`), and as the root (`/`) redirect target for an
  already-authenticated session.
- **REQ-5 [Unwanted Behavior]** If a session has a pending multi-tenant
  selection (more than one membership, none active), then the tenant
  selection guard shall redirect to `/select-tenant` instead of
  `/welcome` — unchanged from `select-tenant`'s existing REQ-1.

## Non-functional requirements

- Security: no sensitive or tenant-internal data is ever shown here —
  by design, this screen works identically for a not-yet-permissioned
  new staff/tenant user.
- Design: consistent with the established design-system standard.

## Acceptance criteria

- [x] A staff session (0 memberships) lands on `/welcome` after login,
      not `/select-tenant`, and sees a generic greeting with no
      dashboard link.
- [x] A tenant member (active tenant set) sees their tenant's name and a
      link to `/dashboard`.
- [x] The onboarding tour auto-starts on `/welcome`, not `/dashboard`.
- [x] Login, tenant selection, and the root-route redirect (for an
      already-valid session) all land on `/welcome`.

## Out of scope

- The metrics dashboard's own content/behavior — unchanged, see
  `onboarding-dashboard`.
- Any staff-specific dashboard/metrics content on `/welcome` itself —
  a future, separate concern if it comes up.
