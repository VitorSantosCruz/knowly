# TASKS — Logout

> Each task is small enough to be its own commit (Conventional Commits,
> per constitution). Follow TDAD: test first (Red), then minimal code
> (Green), for every task that touches behavior.

- [x] 1. Enable `withXsrfConfiguration()` on `provideHttpClient` in
      `app.config.ts` (backend already sends the matching `XSRF-TOKEN`
      cookie / expects `X-XSRF-TOKEN` header — see `knowly`'s
      `SecurityConfig`, which was updated in tandem to actually issue that
      cookie via `CookieCsrfTokenRepository.withHttpOnlyFalse()`, since no
      authenticated non-exempt endpoint had ever been called from a real
      browser before logout). No test needed — this is Angular's built-in
      mechanism; covered indirectly by task 3's expectations, and manually
      by the fact that `logout()`'s POST needs a real CSRF token once
      deployed.
- [x] 2. Write `auth.service.spec.ts` cases for `isLoggedIn` transitions
      (false initially, true after `verifyCode`/`verifyPassword` success,
      false after `logout()`) and for `logout()` POSTing to
      `/api/auth/logout` (Red), then implement on `AuthService` (Green).
- [x] 3. Write `logout-button.component.spec.ts` (Red): hidden when
      `isLoggedIn()` is false, visible when true, clicking it calls
      `AuthService.logout()` and navigates to `/login` on completion; then
      implement `LogoutButtonComponent` (Green).
- [x] 4. Add `<app-logout-button />` to `AppShellComponent`'s fixed corner
      cluster.
- [x] 5. Replace the initial emoji icon with a proper inline SVG + a
      `transloco`-driven `logout.label` string (`en.json`/`pt-BR.json`) —
      user feedback: the emoji read as unrecognizable/off-brand for a
      logout affordance.
- [x] 6. Bug found in manual testing: after logout, pressing "back"
      showed the previous authenticated screen (browser bfcache restore
      bypasses Angular/guards entirely). Write `bfcache-reload.spec.ts`
      (Red), then implement `installBfcacheReload()` and register it in
      `main.ts` (Green).
- [x] 7. Run `npm run format && npm run format:check && npm test && npm run build`
      and confirm everything's green.
- [ ] 8. Update `PROJECT_STATUS.md` (`login` row / add `logout` row) and
      commit.
