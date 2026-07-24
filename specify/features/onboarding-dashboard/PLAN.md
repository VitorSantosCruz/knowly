# PLAN — Onboarding and metrics dashboard

## Architectural decisions

- No third-party tour library. A small in-house `TourService` (signal-based:
  `active`, `stepIndex`, `steps`) + a single `TourOverlayComponent` rendered
  once at the app shell level, positioned via `getBoundingClientRect()` on
  each step's target element (looked up by a `data-tour-id` attribute on
  the highlighted element, not a CSS class — avoids clashing with Tailwind
  utility classes). Justification: the tour is simple (fixed linear steps,
  no branching, no drag/drop), and a library would add a dependency for
  behavior this project can own outright — consistent with the
  constitution's "no speculative abstractions" rule.
- `LoginPageComponent`'s existing `'loggedIn'` placeholder step (recorded
  as an emergent decision in `login/PLAN.md`, since any real post-login
  destination was out of scope for that feature) is replaced by an actual
  `router.navigateByUrl('/dashboard')` — this feature is exactly what that
  placeholder was reserved for.
- A tiny `AuthHttpInterceptor` (functional `HttpInterceptorFn`) redirects to
  `/login` on any `401` response from `/api/**`, application-wide. This
  feature is the first one that needs authenticated routes to exist at
  all, so it's introduced here rather than invented ad hoc per feature.
- `OnboardingService` wraps the two new backend endpoints (see Consumed
  API Contracts) with a signal (`completed: boolean | null`, `null` while
  loading). The dashboard route resolves this once on entry; the tour
  starts automatically only when it resolves to `false`.
- Dashboard widgets are independent components (`ArticleCountCardComponent`,
  `ArticleUsageListComponent`, `ConversationsCardComponent`,
  `MessagesCardComponent`), each owning its own fetch/loading/error state
  (REQ-8/REQ-9's "one slow metric must not block the others" — a single
  aggregate endpoint would force one shared loading/error state across
  unrelated widgets, which is the opposite of what's asked).

## Emergent decisions

- `LoginPageComponent`'s `Step` type dropped `'loggedIn'` entirely
  (rather than keeping it as dead code) — the component now navigates
  away via `Router.navigateByUrl('/dashboard')` instead of ever
  reaching a third render state, so the type and its template branch
  became unreachable.
- The per-widget fetch/loading/error logic planned as "each widget owns
  its own state" is implemented as one shared `createMetricFetcher(http,
  url)` function (`core/metric-fetcher.ts`) returning signals, called
  from each widget's constructor — not a class/service, since each
  widget needs its own independent instance rather than a shared
  singleton. This avoided writing near-identical subscribe/loading/error
  handling four times while still giving every widget fully independent
  state, per REQ-8/9's "one slow metric must not block the others."
- The trace id shown in `ErrorStateComponent` (REQ-9) is parsed from the
  standard `traceparent` response header (`version-traceId-spanId-flags`
  format) — the constitution states every response carries one via
  OpenTelemetry propagation but doesn't name the header; this is the
  W3C Trace Context standard header name, and the backend prerequisite
  (task 0) needs to actually expose it on error responses for this to
  work end-to-end.
- No dedicated "main navigation" element exists yet in `AppShellComponent`
  (there's no app nav menu, only the top-right icon row). The tour's
  first step (`main-nav`) targets that icon row via `data-tour-id`
  as a stand-in; this should be revisited once a real navigation menu
  feature exists.
- `AppShellComponent`'s spec needed `provideHttpClient`/
  `provideHttpClientTesting` added, since `HelpMenuComponent` →
  `TourService` → `OnboardingService` now pulls `HttpClient` into every
  shell render — not anticipated in the original plan, which treated
  the shell as UI-only.

## Components and routes

- New route `/dashboard` (`DashboardPageComponent`), the landing screen
  after login/tenant-selection.
- `LoginPageComponent`: `'loggedIn'` step now performs a real navigation
  to `/dashboard` instead of rendering a placeholder.
- `TourOverlayComponent`: one instance at `AppShellComponent` level (so it
  can render over any route), driven entirely by `TourService`. Renders:
  a dimmed backdrop with a cut-out around the current step's target
  element, a tooltip-style card (title/body/next/back/skip), and traps
  focus within itself while active (REQ-5, and the keyboard-only
  non-functional requirement).
- `HelpMenuComponent`: small dropdown/menu in the shell's top bar (next to
  the language/theme icons), containing "Restart tour" (REQ-4). Future
  features may add more items here; this one only adds the tour entry.
- Dashboard widget components listed above, composed inside
  `DashboardPageComponent`.
- `NoAccessStateComponent` / `ErrorStateComponent`: small shared
  presentational components for REQ-9/REQ-10, reused across all four
  widgets rather than each widget hand-rolling its own error/no-access
  markup.

## Consumed API contracts

None of these exist in `knowly` yet — they're the backend prerequisite
for this feature, to be implemented as their own small SPEC/PLAN/TASKS
cycle in that repo before this frontend feature can work end-to-end
(Vitest tests here only need the *shape*, via `HttpTestingController`,
so frontend implementation is not blocked on the backend existing first).

- `GET /api/users/me/onboarding-status` → `200 { completed: boolean }`
- `POST /api/users/me/onboarding-complete` → `200 {}` — called once, on
  tour finish *or* explicit skip (REQ-3 doesn't distinguish the two).
- `GET /api/tenants/metrics/articles` → `200 { totalCount: number }`
- `GET /api/tenants/metrics/articles/usage` → `200 { articles: Array<{ id: number, title: string, useCount: number }> }`
  (already sorted most-used first, per REQ-7)
- `GET /api/tenants/metrics/conversations` → `200 { startedCount: number }`
- `GET /api/tenants/metrics/messages` → `200 { sentCount: number, receivedCount: number }`
- All four metrics endpoints: `403 { code: 'PERMISSION_DENIED' }` on the
  new `Permission.DASHBOARD_VIEW` (needs adding to the backend's
  `Permission` enum) — read separately per widget so REQ-10's "no access"
  state can apply per-widget too, not just to the whole dashboard.
- All backend errors otherwise carry a trace id header (existing
  OpenTelemetry propagation, per the constitution) — the frontend reads
  it from the response for REQ-9.

## State and data

- `TourService`: `active: Signal<boolean>`, `stepIndex: Signal<number>`,
  fixed `steps: TourStep[]` array (id, `data-tour-id` target, i18n keys
  for title/body) defined in the service itself — not configurable per
  route, per REQ-2's "fixed sequence".
- `OnboardingService`: `completed: Signal<boolean | null>`, fetched once
  per app session (not re-fetched on every navigation to `/dashboard`).
- Each dashboard widget component: local signals for `data`, `loading`,
  `error` (`'network' | 'permission-denied' | null`), and `traceId`.
- New i18n keys for tour step copy and dashboard labels in
  `public/i18n/en.json` / `pt-BR.json`.

## Dependencies

None new. Reuses `HttpClient`, Transloco, and existing Tailwind/signal
patterns already established by the login feature.

## Testing strategy

- `tour.service.spec.ts`: step sequencing (next/back/skip), completion
  marks `active = false`.
- `tour-overlay.component.spec.ts`: renders the current step's copy,
  keyboard next/back/esc-to-skip, focus trapped while active (REQ-5 and
  the accessibility non-functional requirement).
- `onboarding.service.spec.ts`: fetch via `HttpTestingController`, both
  endpoints' request/response shapes.
- `dashboard-page.component.spec.ts`: REQ-1 (tour auto-starts when
  `completed === false`), REQ-6 (doesn't auto-start when already
  completed), REQ-11 (link to articles screen present).
- One spec per widget component: loading → success, loading → network
  error (with trace id rendered), loading → permission-denied (distinct
  "no access" state) — covering REQ-8/9/10 without duplicating the same
  three scenarios by hand across four near-identical test files (shared
  test helper building the three `HttpTestingController` responses).
- `auth.interceptor.spec.ts`: a `401` response triggers navigation to
  `/login`.
