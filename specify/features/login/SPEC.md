# SPEC — Login screens

## Context and motivation

The entry point of the app: identify the user by email, then let them
complete login with either a one-time code or a one-time password, per the
backend contract described in `knowly/specify/features/authentication/SPEC.md`.

## User stories

- As a user, I want to open the app and immediately see a simple login
  screen, without confusion about where to start.
- As a user, I want to pick my language and light/dark theme before I even
  log in, and have that choice stick throughout the whole app.
- As a user, I want a clear (but not overly specific) message when my code
  or password is wrong, without the app leaving me guessing what happened.

## Requirements (EARS/GEARS)

### Login screen (email step)

- **REQ-1 [Ubiquitous]** The system shall display, at the app's root route,
  a login screen with a centered email input as its primary action.
- **REQ-2 [Ubiquitous]** The system shall display a language-selector icon
  and a light/dark theme-toggle icon together in a fixed corner of the
  screen.
- **REQ-3 [State-Driven]** While the user has selected a language or theme,
  the system shall apply that choice on every screen in the app, including
  after a page reload (persisted in `localStorage`).
- **REQ-4 [Event-Driven]** When the user submits a valid-looking email
  address, the system shall call the backend's login-request endpoint and,
  on the generic success response, navigate to the code/password screen
  (REQ-5).
- **REQ-5 [Unwanted Behavior]** If the backend responds with a
  "captcha required" error code, then the system shall render the
  Turnstile challenge on the login screen and only resubmit the email once
  it's solved.

### Code / password screen

- **REQ-6 [Ubiquitous]** The system shall display a screen visually
  consistent with the login screen, containing two tabs: "Code" and
  "Password".
- **REQ-7 [Ubiquitous]** The "Code" tab shall contain an input for the
  one-time code sent by email.
- **REQ-8 [Ubiquitous]** The "Password" tab shall contain an input for the
  user's current one-time password.
- **REQ-9 [Event-Driven]** When the user submits the code or password and
  the backend confirms it's correct, the system shall consider the user
  logged in and navigate into the app.
- **REQ-10 [Unwanted Behavior]** If the backend responds with the generic
  "invalid credentials" error code, then the system shall show a tooltip
  near the submitted field indicating something is wrong, without
  clearing the input.
- **REQ-11 [Unwanted Behavior]** If the backend responds with the
  "locked, try again later" error code, then the system shall show a
  tooltip indicating the account is temporarily locked, distinct from the
  generic invalid-credentials tooltip.

## Non-functional requirements

- Localization: all backend error codes are mapped to localized strings on
  the frontend (see `knowly-app` constitution — backend never sends
  user-facing free text).
- Accessibility: tab navigation between "Code"/"Password" is
  keyboard-operable (arrow keys / standard ARIA tabs pattern); tooltips are
  announced to screen readers (`aria-live` or `aria-describedby`).
- Responsiveness: both screens usable on mobile viewport (≥360px wide).
- Security: never log the submitted code/password to the browser console;
  never include them in error reports.

## Acceptance criteria

- [ ] Opening the app shows the login screen with the email field
      centered.
- [ ] Language and theme icons are present, and the selected values persist
      across navigation and page reload.
- [ ] Submitting any syntactically valid email navigates to the
      code/password screen (regardless of whether the email is registered).
- [ ] Wrong code or password shows a generic "something's wrong" tooltip
      without clearing the field.
- [ ] A locked account shows a distinct "try again later" tooltip.
- [ ] Correct code or password logs the user into the app.
- [ ] When the backend requests a CAPTCHA, the Turnstile widget appears
      before the email can be resubmitted.

## Out of scope

- Any screen/content beyond successful login (e.g. a post-login welcome
  screen) — covered by future features.
- Tenant selection — this feature only gets the user logged in; tenant
  context is resolved later.
- Logout and session-expiry UI.
