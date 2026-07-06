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
