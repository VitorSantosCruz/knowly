# PLAN — welcome-screen

## Architectural decisions

- New `features/welcome/welcome-page.component.ts`, route `/welcome`,
  guarded by the existing `tenantSelectionGuard` (unchanged logic other
  than the amendment in `select-tenant`'s SPEC: 0 memberships now passes
  through instead of redirecting).
- Onboarding-tour trigger (`OnboardingService`/`TourService` effect)
  moved from `DashboardPageComponent` to `WelcomePageComponent`.
- Tour step target ids (`articles-nav-link`, `user-management-nav-link`)
  moved from `DashboardPageComponent`'s own link to the global
  `NavMenuComponent` (`navigation-menu`), since that component is now
  present on every authenticated screen including `/welcome` — avoids
  the tour depending on the specific screen the user happens to land on.
- Greeting content: `ActiveTenantService#activeTenantName` (existing
  signal) decides staff-generic vs. tenant-branded copy.
- Navigation targets updated to `/welcome` (was `/dashboard`):
  `LoginPageComponent`'s two post-verify `navigateByUrl` calls,
  `SelectTenantPageComponent#onSelect`, `rootRedirectGuard`.
- `AuthService.checkSession()` (new): resyncs `isLoggedIn` against a
  real backend call (`GET /api/staff/permissions`, succeeds for any
  authenticated user) — needed because `rootRedirectGuard` must decide
  `/welcome` vs `/login` for a page load with no prior in-memory state
  (e.g. a reload), and `NavMenuComponent` needs the same resync so it
  doesn't hide itself after a reload.
- `rootRedirectGuard` + `RootRedirectPlaceholderComponent`: the `''`
  route now resolves to `/welcome` or `/login` based on
  `checkSession()`, replacing the previous unconditional
  `redirectTo: 'login'`.

## Data schema

None — frontend-only, no backend change.

## API contracts (consumed)

Reuses existing endpoints only: `GET /api/tenants/memberships`,
`GET /api/staff/permissions`, `GET /api/users/me/onboarding-status`.

## Dependencies

None new.

## Package/file structure

- `src/app/features/welcome/welcome-page.component.ts` (new)
- `src/app/features/dashboard/dashboard-page.component.ts` (modify: drop tour-trigger)
- `src/app/layout/nav-menu.component.ts` (modify: tour-id attrs, session resync)
- `src/app/core/auth.service.ts` (modify: add `checkSession()`)
- `src/app/core/root-redirect.guard.ts` (new)
- `src/app/core/root-redirect-placeholder.component.ts` (new)
- `src/app/core/tenant-selection.guard.ts` (modify: 0-membership passthrough)
- `src/app/app.routes.ts` (modify: `/welcome` route, `''` guard)
- `src/app/features/login/login-page.component.ts`,
  `src/app/features/select-tenant/select-tenant-page.component.ts`
  (modify: navigate to `/welcome`)

## Testing strategy

- `welcome-page.component.spec.ts` (new): staff vs. tenant greeting,
  dashboard-link presence, tour auto-start.
- `dashboard-page.component.spec.ts` (modify): tour tests removed.
- `nav-menu.component.spec.ts`, `root-redirect.guard.spec.ts`,
  `tenant-selection.guard.spec.ts`, `auth.service.spec.ts` (modify/new):
  cover the session-resync and 0-membership passthrough behavior.
