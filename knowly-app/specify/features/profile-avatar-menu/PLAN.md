# PLAN — profile-avatar-menu

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New standalone component `AvatarMenuComponent` at
  `src/app/shared/avatar-menu.component.ts`, selector `app-avatar-menu`
  — follows the existing `-menu` naming convention used by
  `help-menu.component.ts` (a `-button` suffix, per
  `logout-button.component.ts`, is reserved for a single-action
  control; this is a multi-entry dropdown, so it takes the `-menu`
  shape/name instead).
- Its open/close state, panel markup (`role="menu"`/`role="menuitem"`,
  `signal<boolean>`, absolute positioning under the trigger,
  `data-testid` pattern) is copied near-verbatim from
  `help-menu.component.ts` rather than introducing a new dropdown
  interaction shape — REQ-4 explicitly requires this, and the SPEC's
  out-of-scope section confirms no outside-click-to-close behavior is
  wanted (matching `help-menu.component.ts`'s current behavior exactly).
- `AvatarMenuComponent` owns its own `ProfileService.getOwnProfile()`
  call (fetched once in the component, e.g. via `toSignal`) rather than
  taking `avatarUrl` as an `input()` from `AppShellComponent` — the
  shell has no other reason to know about profile data, and every other
  header control (`HelpMenuComponent`, `LogoutButtonComponent`) is
  already self-contained this way (owns its own service injection, no
  parent wiring). Keeps `AppShellComponent`'s template a flat list of
  self-sufficient controls, unchanged in that respect.
- `LucideUser` (already imported and used as a generic-user icon in
  `dashboard-page.component.ts`) is reused for the "no avatar"
  fallback and for the "My profile" menu entry's icon — no new icon
  import pattern introduced.
- `logout-button.component.ts` is deleted, not kept as an internal
  helper. Its only remaining logic — `AuthService.logout()` then
  navigate to `/login` regardless of outcome — is a five-line
  `protected logout()` method, cheap enough to duplicate directly
  inside `AvatarMenuComponent` rather than keep a second component
  around whose sole remaining call site is one menuitem's click
  handler; keeping it would leave a component with no template/UI
  reason to exist (REQ-9 removes its rendered form entirely).
- Avatar image load failure (REQ-3, "or fails to load") is handled via
  the `<img>` element's native `(error)` event flipping a local
  `signal<boolean>` (`imageFailed`) that gates the fallback icon —
  mirrors the same null-check pattern `avatar-upload.component.ts`
  already uses for the null case, extended with one `(error)` binding
  for the load-failure case that component doesn't need to handle.

## Components and routes

- **New**: `AvatarMenuComponent` (`src/app/shared/avatar-menu.component.ts`).
  No new route — rendered inside `AppShellComponent`'s authenticated
  header, replacing `<app-logout-button />`.
- **Changed**: `AppShellComponent`
  (`src/app/layout/app-shell.component.ts`) — removes the
  `LogoutButtonComponent` import/usage and the `<span class="mx-1 h-5
  w-px ...">` divider that currently separates it from the icon
  cluster (no longer needed once the avatar trigger sits at the end of
  the same cluster); adds `AvatarMenuComponent` import and
  `<app-avatar-menu />` in its place, same header slot (after
  `app-theme-toggle`).
- **Changed**: `NavMenuComponent`
  (`src/app/layout/nav-menu.component.ts`) — removes the entire
  "Account" `<div class="mt-4 ... border-t ...">` block (lines
  ~207-224: the `nav-my-profile` `<a>` and its enclosing category
  list), per REQ-8. No replacement entry needed — the avatar dropdown
  is the sole remaining entry point.
- **Deleted**: `LogoutButtonComponent`
  (`src/app/shared/logout-button.component.ts` and its spec).

## Consumed API contracts

No new backend endpoint. Reuses two already-existing contracts,
consistent with the SPEC's "no new backend field or endpoint" framing:

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | — | `UserProfile` (`{ userId, email, fields, avatarUrl }`) | 200 |
| POST | `/api/auth/logout` | — | — (existing `logout` SPEC's REQ-2 contract, unchanged) | 200 (any response navigates to `/login` regardless per REQ-7) |

## State and data

- `AvatarMenuComponent` holds two local signals: `open` (dropdown
  open/closed, mirrors `HelpMenuComponent.open`) and `imageFailed`
  (flips true on the `<img>`'s `(error)` event, gates the `LucideUser`
  fallback alongside the existing null-`avatarUrl` check).
- `avatarUrl` itself is read via `toSignal(this.profileService
  .getOwnProfile().pipe(map(p => p.avatarUrl)), { initialValue: null })`
  — a one-shot fetch on component construction, matching the "fetched
  once, used for the session's lifetime" shape `OwnProfilePageComponent`
  already uses for the same field; no new shared/global signal service
  is introduced since no other component needs this value (unlike
  `PermissionsService`/`ActiveTenantService`, which are consumed from
  multiple unrelated places).
- No new global/injectable state. `AuthService.logout()` and
  `Router.navigateByUrl('/login')` are called directly from the
  component, identical to `logout-button.component.ts`'s current
  logic.

## Dependencies

None. `@lucide/angular`'s `LucideUser` and `LucideLogOut` are already
present in `package.json` and already imported elsewhere in this
codebase (`dashboard-page.component.ts`, `nav-menu.component.ts`/
`logout-button.component.ts` respectively) — no new package.

## Security considerations

This SPEC is presentation-only (per its own NFR), but since it touches
the logout call site directly, the following is called out explicitly
for the appsec review this PLAN goes through before TASKS.md:

- The `AuthService.logout()` call itself, its CSRF handling, and its
  "regardless of response, navigate to `/login` and clear local state"
  contract are **unchanged** — only the DOM location of the click
  handler that triggers it moves (from a standalone header button into
  a dropdown menuitem). No new HTTP call, no new request shape, no new
  CSRF exemption.
- `AvatarMenuComponent` is rendered only inside `AppShellComponent`'s
  authenticated-header branch (the `@else` branch gated implicitly by
  routing — bare routes like `/login` never render it), matching
  REQ-1's "visible only while logged in" — same visibility gate the
  removed `LogoutButtonComponent` already relied on
  (`authService.isLoggedIn()`), reused as-is rather than invented.
- The avatar `<img src>` is populated exclusively from
  `ProfileService.getOwnProfile()`'s server-returned `avatarUrl` (the
  caller's own profile, scoped server-side to the authenticated
  session) — no user-controllable URL parameter or query string is
  ever read into this binding, so there is no reflected/open-redirect
  or arbitrary-image-source concern introduced by this component.
- No new authorization check is added or removed: "My profile"
  navigates to `/profile`, an already-guarded route unaffected by this
  change; nothing in this PLAN alters `PermissionAspect`,
  `SecurityConfig`, or any guard.

## Testing strategy

- **New**: `src/app/shared/avatar-menu.component.spec.ts` covering:
  - renders the avatar `<img>` when `avatarUrl` is non-null.
  - renders the `LucideUser` fallback when `avatarUrl` is null, and
    also after the `<img>`'s `(error)` event fires.
  - clicking the trigger toggles `open`/renders the `role="menu"` list
    with exactly two `role="menuitem"` entries in order ("My profile",
    "Logout"), each with its own icon.
  - selecting "My profile" navigates to `/profile` and closes the menu
    (`open()` back to `false`).
  - selecting "Logout" calls `AuthService.logout()` and navigates to
    `/login` (covering both the success and error/`complete` paths,
    mirroring `logout-button.component.spec.ts`'s existing coverage
    for that call, which moves here).
  - accessible name present on the trigger (keyboard-operable per the
    NFR) — asserted the same way the removed
    `logout-button.component.spec.ts` already asserted `aria-label`.
- **Changed**: `nav-menu.component.spec.ts` — removes the assertion at
  line ~277 (`expect(... 'nav-my-profile' ...).toBeTruthy()`) and adds
  its inverse (`nav-my-profile` query returns `null`), per REQ-8's
  acceptance criterion.
- **Changed**: `app-shell.component.spec.ts` — no existing assertion
  currently references `app-logout-button` or `app-avatar-menu`
  (verified: no such assertions exist today), so no removal is needed;
  add a smoke assertion that `app-avatar-menu` renders in the
  authenticated-header branch, matching this spec's existing coverage
  style for its other header children.
- **Deleted**: `logout-button.component.spec.ts` (component deleted;
  its assertions are absorbed into the new
  `avatar-menu.component.spec.ts` per above).
