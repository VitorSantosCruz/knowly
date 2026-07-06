# TASKS — Login screens

> Each task is small enough to be its own commit (Conventional Commits,
> per constitution). Follow TDAD: test first (Red), then minimal code
> (Green), for every task that touches behavior.

- [ ] 1. Add `@jsverse/transloco` dependency; configure it in
      `app.config.ts` with an HTTP loader; add `public/i18n/en.json` and
      `public/i18n/pt-BR.json` with a couple of placeholder keys.
- [ ] 2. Write `theme.service.spec.ts` covering persistence, restoration,
      and `prefers-color-scheme` fallback (Red), then implement
      `ThemeService` (Green).
- [ ] 3. Write `language.service.spec.ts` covering persistence and
      restoration, wrapping `TranslocoService` (Red), then implement
      `LanguageService` (Green).
- [ ] 4. Write specs for `LanguageSwitcherComponent` and
      `ThemeToggleComponent` (Red), then implement both (Green).
- [ ] 5. Implement `AppShellComponent` (hosts both switcher components +
      `<router-outlet>`) and wire it as the root layout; register `/login`
      as a route and redirect `/` to it.
- [ ] 6. Write `auth.service.spec.ts` covering all three endpoint
      calls/response shapes (Red), then implement `AuthService` (Green).
- [ ] 7. Write `login-page.component.spec.ts` cases for REQ-1 (email step
      renders) and REQ-4 (submit navigates to credential step on success)
      (Red), then implement the email step of `LoginPageComponent`
      (Green).
- [ ] 8. Write a `login-page.component.spec.ts` case for REQ-5 (Turnstile
      widget appears on `CAPTCHA_REQUIRED`) (Red), then implement that
      conditional rendering (Green).
- [ ] 9. Write `login-page.component.spec.ts` cases for REQ-6–REQ-9
      (credential step tabs, code/password submission, successful login)
      (Red), then implement the credential step (Green).
- [ ] 10. Write `login-page.component.spec.ts` cases for REQ-10/REQ-11
       (invalid-credentials vs. account-locked tooltips) (Red), then
       implement them (Green).
- [ ] 11. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [ ] 12. Update `PLAN.md` if any decision changed during implementation.
