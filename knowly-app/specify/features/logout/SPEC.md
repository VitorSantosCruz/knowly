# SPEC — Logout

## Context and motivation

The backend now exposes `POST /api/auth/logout`
(`knowly/specify/features/authentication/SPEC.md`, REQ-16), but the
frontend has no way to trigger it — once logged in, a user has no way to
end their session from the UI. This feature adds that entry point.

## User stories

- As a logged-in user, I want a visible way to log out, so I can end my
  session on a shared or public machine.
- As a user who just logged out, I want to land back on the login screen
  with no way to accidentally reuse the old session.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall display a logout control,
  visible only while the user is logged in. As of the `profile-avatar-menu`
  feature, this control lives as an entry inside the user avatar's
  dropdown menu in the app shell's header (see
  `specify/features/profile-avatar-menu/SPEC.md`, REQ-5/REQ-7/REQ-9),
  rather than as a standalone icon button in the fixed corner cluster.
- **REQ-2 [Event-Driven]** When the user activates the logout control, the
  system shall call `POST /api/auth/logout` and, regardless of the
  response, navigate to `/login` and clear any local logged-in state.
- **REQ-3 [Ubiquitous]** The system shall track a minimal client-side
  "logged in" signal, set to true on successful code/password verification
  (`AuthService.verifyCode`/`verifyPassword`) and false initially and after
  logout — used only to decide whether to render the logout control
  (REQ-1), not as an authorization mechanism (the backend session cookie
  remains the source of truth).
- **REQ-4 [Unwanted Behavior]** If a page reload happens while logged in,
  then the logged-in signal resets to false and the logout control is
  hidden until the next successful verification — acceptable per the
  login feature's existing scope (session-restoration-on-reload is out of
  scope there too).
- **REQ-5 [Unwanted Behavior]** If the browser restores a page from its
  back/forward cache (e.g. the user presses "back" right after logging
  out), then the system shall force a full reload rather than let the
  cached, still-authenticated-looking DOM snapshot remain visible — a
  bfcache restore repaints the last in-memory page without re-running any
  navigation/guard logic, which would otherwise let a logged-out user
  briefly see the previous logged-in screen.

## Non-functional requirements

- Accessibility: the logout control is keyboard-operable and has an
  accessible name (not icon-only with no label for screen readers).
- Security: the logout request includes the CSRF token like any other
  authenticated state-changing request (see PLAN.md for how that's
  obtained — this is the first authenticated POST from this frontend, so
  CSRF wiring is being introduced here).

## Acceptance criteria

- [x] After logging in, a logout icon/button appears in the fixed corner
      cluster alongside the language/theme/help icons.
- [x] Clicking it calls `POST /api/auth/logout`, then navigates to
      `/login`.
- [x] After logout, reloading or navigating back does not show the logout
      button again (until logging in again).
- [x] Before logging in (fresh load, on `/login`), the logout button is
      not shown.
- [x] Restoring a page from the back/forward cache (bfcache) triggers a
      full reload instead of showing the stale cached DOM.

## Out of scope

- Full client-side auth-state restoration on page reload (e.g. an
  endpoint to check "am I still logged in") — the login feature already
  scoped this out; the logged-in signal here is intentionally minimal and
  in-memory only.
- Session-expiry UI (e.g. warning before timeout) — separate concern,
  already out of scope in the login SPEC.
