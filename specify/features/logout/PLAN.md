# PLAN — Logout

## Architectural decisions

- **CSRF wiring (new for this repo):** the backend now issues a readable
  `XSRF-TOKEN` cookie (`CookieCsrfTokenRepository.withHttpOnlyFalse()`,
  see `knowly`'s `SecurityConfig`) and expects it echoed back as the
  `X-XSRF-TOKEN` header on state-changing requests — the standard
  Spring Security ↔ Angular pairing. Enable this via Angular's built-in
  `withXsrfConfiguration()` on `provideHttpClient` in `app.config.ts`; its
  defaults (`XSRF-TOKEN` cookie name, `X-XSRF-TOKEN` header name) already
  match the backend, so no custom names need configuring.
- **Logged-in signal:** a `signal<boolean>(false)` on `AuthService`
  (`isLoggedIn`, exposed read-only via `.asReadonly()`), set to `true` at
  the end of `verifyCode`/`verifyPassword` on success, and to `false` in a
  new `logout()` method. No separate store/context — this is the
  service that already owns every other auth HTTP call.
- **`AuthService.logout()`:** `POST /api/auth/logout`, then (in the
  subscribe/finalize, regardless of outcome) sets `isLoggedIn.set(false)`.
  Navigation to `/login` is the caller's (component's) responsibility,
  consistent with how `verifyCode`/`verifyPassword` callers already
  navigate after a successful `Observable` completes — `AuthService`
  stays free of `Router` dependencies.
- **UI:** new `LogoutButtonComponent` (standalone, in `src/app/shared/`
  next to `theme-toggle.component.ts`/`language-switcher.component.ts`),
  same icon-button visual style as its siblings. Renders `@if
  (authService.isLoggedIn())`. Added to `AppShellComponent`'s fixed corner
  cluster, calls `authService.logout()` then `router.navigateByUrl('/login')`
  on completion.
- **Test approach:** `AuthService` unit tests (Vitest + `HttpTestingController`)
  for `logout()` behavior and the signal transitions; a component test for
  `LogoutButtonComponent` asserting it's hidden/shown based on the signal
  and that clicking it calls `logout()` + navigates.
- **Icon:** an inline SVG (a plain "exit through a door" glyph — arrow
  leaving a rectangle), not an emoji; paired with a `transloco`-driven
  `aria-label`/`title` (`logout.label`), matching `HelpMenuComponent`'s use
  of real i18n strings rather than `ThemeToggleComponent`'s emoji shortcut,
  since a logout affordance needs to read clearly across locales and isn't
  a fun/casual toggle like theme.
- **bfcache bug fix:** discovered during manual testing — after logging
  out, pressing the browser's "back" button showed the previous
  authenticated screen, because a bfcache restore repaints the last DOM
  snapshot without re-running Angular/guards at all (no JS executes, so
  neither the `isLoggedIn` signal nor `tenantSelectionGuard`'s backend call
  ever get a chance to run). Fixed with a small `installBfcacheReload()`
  helper (`core/bfcache-reload.ts`) registered in `main.ts`, listening for
  `pageshow` with `event.persisted === true` and forcing `location.reload()`
  — the only reliable, cross-browser fix for this class of bug (response
  cache-control headers alone don't consistently prevent bfcache document
  restores in the way they prevent HTTP caching).
