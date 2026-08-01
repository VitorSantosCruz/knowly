# TASKS — profile-avatar-menu

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. Write `avatar-menu.component.spec.ts`: "is hidden when not logged in"
      (REQ-1) — Red.
- [x] 2. Implement `AvatarMenuComponent` skeleton (`@if (authService.isLoggedIn())`
      gate, `toSignal(profileService.getOwnProfile()...)` for `avatarUrl`, no
      markup yet beyond the gate) — Green.
- [x] 3. Write spec: renders the avatar `<img>` when `avatarUrl` is non-null,
      and renders the `LucideUser` fallback when it is null (REQ-2/REQ-3) — Red.
- [x] 4. Implement the `<img>`/fallback markup — Green.
- [x] 5. Write spec: the `<img>`'s `(error)` event also triggers the
      `LucideUser` fallback (REQ-3, "or fails to load") — Red.
- [x] 6. Implement `imageFailed` signal + `(error)` binding, gate the image
      on `avatarUrl() && !imageFailed()` — Green.
- [x] 7. Write spec: clicking the trigger toggles `open()`, renders
      `role="menu"` with exactly two `role="menuitem"` entries in order
      ("My profile", "Logout"), each with its own icon; trigger has an
      accessible name (REQ-4/REQ-5, NFR a11y) — Red.
- [x] 8. Implement the dropdown trigger + panel markup, copied from
      `help-menu.component.ts`'s shape (`signal<boolean>` open, `role="menu"`/
      `role="menuitem"`, `LucideUser`/`LucideLogOut` icons, `profile.myProfile`/
      `logout.label` transloco keys) — Green.
- [x] 9. Write spec: selecting "My profile" navigates to `/profile` and
      closes the dropdown (`open()` back to `false`) (REQ-6) — Red.
- [x] 10. Implement the "My profile" menuitem's click handler
      (`router.navigateByUrl('/profile')` + `this.open.set(false)`) — Green.
- [x] 11. Write spec: selecting "Logout" calls `AuthService.logout()` and
      navigates to `/login` on the **success/complete** path (REQ-7) — Red.
- [x] 12. Write spec: selecting "Logout" also navigates to `/login` on the
      **error** path (REQ-7) — Red (both together, since task 11's minimal
      code would trivially pass with only `complete` wired; this task exists
      explicitly to force the `error` branch to be implemented too — appsec
      flagged this: preserve *both* `complete`/`error` navigation branches
      when inlining the logout call from `logout-button.component.ts`, do not
      drop the `error` handler while inlining).
- [x] 13. Implement the "Logout" menuitem's click handler:
      `this.authService.logout().subscribe({ complete: () =>
      this.router.navigateByUrl('/login'), error: () =>
      this.router.navigateByUrl('/login') })` — both branches present,
      mirroring `logout-button.component.ts`'s original shape exactly — Green
      for tasks 11 and 12 together.
- [x] 14. Update `app-shell.component.ts`: import `AvatarMenuComponent`,
      replace `<app-logout-button />` (and its preceding divider `<span>`)
      with `<app-avatar-menu />`; remove the `LogoutButtonComponent` import.
- [x] 15. Add a smoke assertion to `app-shell.component.spec.ts` confirming
      `app-avatar-menu` renders (and `app-logout-button` does not).
- [x] 16. Delete `logout-button.component.ts` and
      `logout-button.component.spec.ts` (logic absorbed into
      `AvatarMenuComponent` per tasks 11-13).
- [x] 17. Update `nav-menu.component.ts`: remove the "Account" section
      block (the `nav-my-profile` `<a>` and its enclosing `<div>`/`<ul>`,
      ~lines 207-224) per REQ-8.
- [x] 18. Update `nav-menu.component.spec.ts`: invert the existing
      `'always shows "My profile"...'` assertion to confirm `nav-my-profile`
      is now absent (`toBeFalsy()`), per REQ-8's acceptance criterion.
- [x] 19. Run `npm run format`, then
      `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green.
- [x] 20. Update `PLAN.md` if any decision changed during implementation.
