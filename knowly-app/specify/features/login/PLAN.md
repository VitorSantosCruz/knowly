# PLAN — Login screens

## Architectural decisions

- Single route `/login` (also the default/root redirect) hosts the whole
  flow. The email step and the code/password step are two states of one
  `LoginPageComponent` (a `step` signal: `'email' | 'credential'`), not two
  routes — it's a linear wizard, not independent navigable pages (YAGNI:
  no deep-linking requirement was specified).
- Language and theme are **global**, not login-specific: `LanguageService`
  and `ThemeService` (both signal-based, persisted to `localStorage`) live
  at the app root and are rendered via a small `AppShellComponent` wrapping
  `<router-outlet>`, so the icons and their state exist on every route from
  day one, per REQ-2/REQ-3.
- Theme uses Tailwind v4's class-based dark mode: add
  `@custom-variant dark (&:where(.dark, .dark *));` to `styles.css`, and
  `ThemeService` toggles the `dark` class on `document.documentElement`.
- i18n uses [**Transloco**](https://jsverse.github.io/transloco/) (`@jsverse/transloco`,
  the actively maintained successor in this space — `ngx-translate` has had
  long maintenance gaps). Angular-idiomatic, works well with standalone
  components and Signals (`translationsSignal`/`translate()`), supports
  lazy-loaded per-language JSON dictionaries out of the box.
- Cloudflare Turnstile is integrated by loading its script tag on demand
  (only once the backend returns `CAPTCHA_REQUIRED`) and rendering a plain
  `<div data-sitekey>` — no Angular wrapper package needed for this.
- `AuthService` wraps the three backend calls (`login-request`,
  `login-code/verify`, `login-password/verify`) via `HttpClient`, all at
  relative `/api/auth/...` paths (proxied in dev, same-origin in prod per
  constitution).

## Emergent decisions (recorded during implementation)

- `LoginPageComponent`'s `step` signal has a third value, `'loggedIn'`,
  beyond the two named in the original plan (`'email' | 'credential'`).
  REQ-9 requires the system to "consider the user logged in and navigate
  into the app" once a code/password is verified, but any actual
  post-login screen is explicitly out of scope — so `'loggedIn'` renders
  an empty `data-testid="logged-in"` placeholder for now, giving REQ-9 a
  concrete, testable outcome without inventing out-of-scope UI.
- Turnstile's site key (public by design, unlike the secret key which
  stays server-side) lives in `core/turnstile.config.ts` as a placeholder
  empty-string constant, with a comment marking it as required before any
  environment where CAPTCHA should actually render. No environment-file
  infrastructure was introduced for this single value (YAGNI) — revisit
  if/when other environment-specific config is needed.
- The Code/Password tabs use one shared `#credential-error` element
  (singular, not per-tab) for the REQ-10/REQ-11 tooltip: only one tab is
  ever visible at a time, so a single `role="alert"` paragraph, linked via
  `aria-describedby` on whichever input is currently shown, is sufficient
  and avoids duplicating the same markup per tab. Switching tabs clears
  `errorCode`, so the tooltip never survives into the other tab.
- Tab keyboard navigation (arrow keys, per the SPEC's non-functional
  accessibility requirement) is handled by a single `onTabKeydown` handler
  bound to both tab buttons, toggling between the two known tab values and
  moving focus to the newly active tab button — sufficient for exactly two
  tabs; would need generalizing (e.g. a list of tab ids) if a third tab
  were ever added.
- A shared `FakeTranslocoLoader` test double (`src/app/testing/`) backs
  its translations with the real `public/i18n/*.json` dictionaries
  (imported directly, via `resolveJsonModule` added to
  `tsconfig.spec.json`) instead of returning `{}`. This means component
  specs assert against actual rendered copy, not raw translation keys,
  and a missing/renamed key fails a test instead of silently rendering
  `login.someKey` in production.

## Components and routes

- `AppShellComponent` (root layout): hosts `LanguageSwitcherComponent`,
  `ThemeToggleComponent`, and `<router-outlet>`.
- `LoginPageComponent` (route `/login`, and default redirect from `/`):
  - Email step: email input + submit button + (conditionally) Turnstile
    widget.
  - Credential step: tabs ("Code" / "Password"), each with its own input,
    submit button, and inline tooltip for `INVALID_CREDENTIALS` /
    `ACCOUNT_LOCKED`.
- `LanguageSwitcherComponent`, `ThemeToggleComponent`: small, reusable,
  global.

## Consumed API contracts

Per `knowly/specify/features/authentication/PLAN.md`:

- `POST /api/auth/login-request` → `200 {}` | `400 { code: 'CAPTCHA_REQUIRED' }`
- `POST /api/auth/login-code/verify` → `200 {}` | `401 { code: 'INVALID_CREDENTIALS' }` | `429 { code: 'ACCOUNT_LOCKED' }`
- `POST /api/auth/login-password/verify` → same shape as above

## State and data

- `LoginPageComponent`: local signals for `step`, `email`, `captchaToken`,
  `submitting`, `errorCode`.
- `LanguageService`: thin wrapper around Transloco's `TranslocoService`
  (`setActiveLang`/`getActiveLang`), persists the chosen language to
  `localStorage` under `knowly.lang`, restored on app init.
- `ThemeService`: `signal<'light' | 'dark'>`, persisted to `localStorage`
  under `knowly.theme`, restored on app init (falls back to
  `prefers-color-scheme` if never set).
- Translation dictionaries: `public/i18n/en.json`, `public/i18n/pt-BR.json`,
  loaded lazily by Transloco's HTTP loader per active language.

## Dependencies

- `@jsverse/transloco` (new) — i18n.
- Turnstile: no npm package, plain script tag loaded on demand.

## Testing strategy

- `auth.service.spec.ts`: HTTP calls via `HttpTestingController`, covers
  all three endpoints' success/error shapes.
- `login-page.component.spec.ts`: covers REQ-1, REQ-4–REQ-11 — email
  submission, navigation between steps, tooltip rendering for both error
  codes, Turnstile rendering on `CAPTCHA_REQUIRED`.
- `language.service.spec.ts` / `theme.service.spec.ts`: persistence and
  restoration from `localStorage`.
