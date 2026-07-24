# SPEC — Onboarding and metrics dashboard

## Context and motivation

Today, `knowly-app` has no screen after login at all — the tenancy
backend (`knowly/specify/features/tenancy/SPEC.md`) establishes who the
user is and which tenant they're acting in, but nothing in the frontend
does anything with that yet. This feature is the first thing a tenant
user sees once logged in and (if applicable) once they've picked an
active tenant: a guided first-run tour, always reachable again from a
help menu, and the metrics dashboard it lands on — quantities of
articles, per-article usage, conversations started, messages
sent/received. Staff users and the future support/account dashboard are
explicitly out of scope here (see tenancy SPEC's own out-of-scope note).

## User stories

- As a tenant user logging in for the first time, I want the app to walk
  me through its main areas so I'm not dropped into an empty screen with
  no context.
- As a returning user, I want to land directly on a dashboard that tells
  me, at a glance, how the knowledge base is actually being used.
- As any user, I want to replay the tour whenever I want, without it
  being forced on me again automatically.
- As a user without permission to view usage metrics, I don't want to see
  a broken or empty dashboard — I want a clear, non-alarming explanation
  of why it's not available to me.

## Requirements (EARS/GEARS)

### Onboarding tour

- **REQ-1 [Event-Driven]** When a user reaches the app for the first time
  after having an active tenant established (immediately after login for
  a single-membership user, or immediately after tenant selection for a
  multi-membership user), the system shall automatically start a guided,
  spotlight-style tour over the main screen.
- **REQ-2 [Ubiquitous]** The tour shall consist of a fixed sequence of
  steps, each highlighting one area of the main layout (main navigation,
  where articles live, where user/permission management lives, where bot
  personality/speed configuration lives, and the help menu itself) with a
  short explanation and next/back/skip controls.
- **REQ-3 [Event-Driven]** When the user finishes or explicitly skips the
  tour, the system shall record that this user has completed onboarding
  (persisted per-user, not per-browser) so it doesn't start automatically
  again on a future login.
- **REQ-4 [Ubiquitous]** The help menu shall always contain a "Restart
  tour" action, available regardless of whether onboarding was already
  completed, that replays the exact same tour on demand.
- **REQ-5 [State-Driven]** While the tour is active, the system shall
  block interaction with anything outside the highlighted element and its
  step controls, so the user can't wander off mid-tour into a confusing
  half-explained state.

### Metrics dashboard

- **REQ-6 [Ubiquitous]** The system shall show the dashboard as the
  landing screen once a tenant user has an active tenant and (on their
  very first visit) has gone through REQ-1's tour.
- **REQ-7 [Ubiquitous]** The dashboard shall display, at minimum: total
  article count, per-article usage (a ranked list, most-used first),
  count of knowledge-base conversations started, and count of messages
  sent and received, all scoped to the active tenant.
- **REQ-8 [Event-Driven]** When the dashboard loads, the system shall
  fetch this data from the backend's tenant-scoped metrics endpoint(s)
  and render loading state per widget while the request is in flight.
- **REQ-9 [Unwanted Behavior]** If the metrics request fails, then the
  system shall show an error state on the dashboard (not a blank or
  broken layout) including the response's trace id, per this project's
  observability convention, without leaking any raw backend error detail.
- **REQ-10 [Unwanted Behavior]** If the backend denies the metrics
  request because the user lacks the required permission, then the
  system shall show a plain, non-alarming "you don't have access to this"
  state instead of an error state — this is an expected, permission-based
  outcome, not a failure.
- **REQ-11 [Ubiquitous]** From the dashboard, the user shall be able to
  navigate to the articles list/edit screen (a distinct future feature —
  this dashboard only needs to link to it, not implement it).

## Non-functional requirements

- Accessibility: the tour must be fully keyboard-navigable (tab/enter/esc
  to skip) and each step's highlighted element must be announced via
  `aria-live` for screen readers, since it changes focus context
  non-linearly.
- Performance: each dashboard widget fetches/loads independently — a slow
  or failing metric must not block the others from rendering.
- Responsiveness: the dashboard must remain usable (metrics readable,
  no horizontal scroll) down to a single-column mobile layout.
- Design: follows the established design-system standard already applied
  to the login screens and emails (slate/indigo palette, 8pt spacing
  grid, consistent card/button states, WCAG-contrast text).

## Acceptance criteria

- [ ] A first-time tenant user, right after landing in their tenant
      context, sees the guided tour start automatically.
- [ ] Finishing or skipping the tour marks onboarding complete for that
      user; logging in again does not restart it automatically.
- [ ] The help menu's "Restart tour" replays the same tour at any time,
      for a user who has already completed onboarding.
- [ ] The dashboard shows article count, per-article usage, conversations
      started, and messages sent/received, scoped to the active tenant.
- [ ] A metrics request failure shows an error state with a visible trace
      id, not a blank screen.
- [ ] A permission-denied response shows a distinct "no access" state,
      not treated as an error.
- [ ] The tour is operable with keyboard only.

## Out of scope

- The actual article list/edit screen and user-management screen (later
  features this dashboard only links out to).
- The staff/support account dashboard (tenancy SPEC's own out-of-scope
  item — entirely different audience and data shape).
- Bot personality/response-speed configuration screen itself (later
  feature; only referenced as a tour stop).
- Backend metrics endpoints' internal implementation (owned by the
  `knowly` repo; this SPEC only defines the contract this frontend
  feature expects from them — see PLAN.md).
