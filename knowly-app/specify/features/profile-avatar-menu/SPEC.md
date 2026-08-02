# SPEC — profile-avatar-menu

> The what and the why. No technical implementation details.

## Context and motivation

Today, "My profile" lives as a link inside the sidebar nav menu's
"Account" section (`nav-menu.component.ts`, `data-testid="nav-my-profile"`,
routes to `/profile`), and the logout control lives as a standalone
icon button in the app shell's top header cluster
(`logout-button.component.ts`, per the `logout` SPEC's REQ-1: "displayed
in the app shell's fixed corner icon cluster"). The product owner wants
to consolidate both "identity"-flavored actions — viewing your own
profile, and ending your session — behind a single, familiar affordance:
a clickable user avatar in the top header, opening a dropdown with both
options. This follows a pattern common to most web apps (avatar → "my
account" menu) and reduces sidebar clutter by removing an item that
isn't really "navigation to a section" so much as "acting on my own
identity."

The avatar itself reuses data this app already has: `ProfileService`'s
`UserProfile.avatarUrl` (populated via the existing
`POST /api/users/me/profile/avatar` upload flow, already surfaced in
`OwnProfilePageComponent`'s "Change avatar" control via
`avatar-upload.component.ts`) — no new backend field or endpoint is
needed.

**Scope-change note for validation before implementation**: this SPEC
moves the logout control's *location* out of the header's standalone
icon cluster and into this new dropdown. The existing, already-approved
`logout` SPEC's REQ-1 explicitly describes the current fixed-corner
placement — that requirement will need a corresponding amendment once
this SPEC is approved and implemented (its "how it's triggered" behavior,
REQ-2 through REQ-5, is unaffected and is reused as-is via the same
`AuthService.logout()` call). Flagging this explicitly rather than
silently editing the other SPEC's text — please confirm this relocation
before PLAN work starts.

## User stories

- As a logged-in user, I want to find "my profile" and "logout" in one
  predictable place (my avatar) instead of two unrelated locations
  (sidebar vs. header), matching a pattern I already know from other
  apps.
- As a user without a profile photo, I still want to see *something*
  recognizable as "my account" in the header, not a broken image or
  nothing at all.
- As a keyboard/screen-reader user, I want the avatar menu to behave
  like the other dropdowns already in this app (e.g. the help menu), so
  its behavior is predictable.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The app shell's top header shall display the
  current user's avatar, visible only while the user is logged in (same
  visibility condition as the existing header controls it sits beside).
- **REQ-2 [State-Driven]** While the current user's profile
  (`ProfileService.getOwnProfile()`'s `UserProfile.avatarUrl`) has a
  non-null avatar URL, the avatar control shall render that image.
- **REQ-3 [Unwanted Behavior]** If the current user's `avatarUrl` is
  null (or fails to load), then the avatar control shall render a
  generic user icon in its place, never a broken image or empty space.
- **REQ-4 [Event-Driven]** When the user clicks the avatar, the system
  shall open a dropdown menu, positioned and structured the same way
  `help-menu.component.ts` already does (`signal<boolean>` open/closed
  state, an absolutely-positioned list under the trigger, `role="menu"`
  on the list and `role="menuitem"` on each entry).
- **REQ-5 [Ubiquitous]** The dropdown shall list exactly two entries, in
  this order: "My profile" and "Logout" ("Sair" in `pt-BR`), each shown
  with an illustrative icon (a user/profile icon for "My profile", a
  log-out/exit-door icon for "Logout" — reusing the same `lucideLogOut`
  icon already used by `logout-button.component.ts`).
- **REQ-6 [Event-Driven]** When the user selects "My profile" from the
  dropdown, the system shall navigate to `/profile` and close the
  dropdown.
- **REQ-7 [Event-Driven]** When the user selects "Logout" from the
  dropdown, the system shall call `AuthService.logout()` and, regardless
  of the response, navigate to `/login` and clear local logged-in state
  — the exact same behavior already specified by the `logout` SPEC's
  REQ-2, reused rather than reimplemented.
- **REQ-8 [Ubiquitous]** The sidebar nav menu (`nav-menu.component.ts`)
  shall no longer show a "My profile" entry in its "Account" section —
  this SPEC's avatar dropdown is its only remaining entry point.
- **REQ-9 [Ubiquitous]** The app shell's header shall no longer show a
  standalone logout icon button separate from the avatar dropdown — this
  SPEC's dropdown is its only remaining entry point (see the scope-change
  note above regarding the `logout` SPEC's REQ-1).

## Non-functional requirements

- Accessibility: the avatar trigger has an accessible name (not
  icon/image-only with no label for assistive tech) and is
  keyboard-operable, consistent with the `logout` SPEC's existing
  accessibility requirement for the control it replaces. Each dropdown
  entry is keyboard-reachable and has an accessible name (its visible
  label already satisfies this).
- Design: consistent with the established design-system standard, and
  visually consistent with the existing `help-menu.component.ts`
  dropdown (same open/close interaction shape, same panel styling
  conventions).
- Internationalization: both dropdown entry labels are translated
  (`en`/`pt-BR`) via the existing `transloco` mechanism, reusing the
  already-existing `profile.myProfile` and `logout.label` translation
  keys rather than introducing duplicate ones.
- Security: this is presentation-only — no new authorization logic. The
  avatar image itself is not sensitive; `AuthService.logout()`'s
  existing CSRF handling is unchanged and untouched by this SPEC.

## Acceptance criteria

- [ ] While logged in, the app shell's top header shows a clickable
      avatar.
- [ ] A user with an avatar photo on file sees that photo in the header;
      a user with none sees a generic user icon instead.
- [ ] Clicking the avatar opens a dropdown with exactly two entries. "My
      profile" first, "Logout" second, each with its own icon.
- [ ] Selecting "My profile" navigates to `/profile` and closes the
      dropdown.
- [ ] Selecting "Logout" calls `AuthService.logout()` and navigates to
      `/login`, identically to the current standalone logout button's
      behavior.
- [ ] The sidebar's "Account" section no longer shows a "My profile"
      link.
- [ ] The header no longer shows a standalone logout icon button outside
      the avatar dropdown.
- [ ] The avatar dropdown is keyboard-operable and uses `role="menu"`/
      `role="menuitem"`, matching the existing help-menu pattern.

## Out of scope

- Any change to how the avatar photo itself is uploaded/changed — that
  flow (`avatar-upload.component.ts`, `POST /api/users/me/profile/avatar`)
  already exists inside the "My profile" page and is untouched by this
  SPEC.
- Any new backend endpoint or data field — this consumes the existing
  `ProfileService.getOwnProfile()`/`UserProfile.avatarUrl` as-is.
- Adding further entries to the dropdown beyond "My profile" and
  "Logout" (e.g. account settings, theme, language) — those already
  have their own dedicated header controls (`app-theme-toggle`,
  `app-language-switcher`) and are not being folded into this menu.
- Closing the dropdown on an outside click/blur — no existing dropdown
  in this codebase (`help-menu.component.ts`) implements that behavior
  today; this SPEC does not introduce a new interaction pattern beyond
  what's already established. Flag separately if this is wanted, since
  it would also apply retroactively to the help menu for consistency.
- Formally amending the `logout` SPEC's REQ-1 text — flagged above for
  the product owner's confirmation, not silently edited as part of this
  SPEC.
