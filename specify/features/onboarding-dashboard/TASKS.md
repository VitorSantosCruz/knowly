# TASKS — Onboarding and metrics dashboard

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 0. Prerequisite (tracked in the `knowly` backend repo, not here)

- [ ] 0. Backend: add `Permission.DASHBOARD_VIEW`, the four
      `/api/tenants/metrics/*` endpoints, and the two
      `/api/users/me/onboarding-*` endpoints listed in PLAN.md's Consumed
      API Contracts, each behind `@RequiresPermission`/`@AuditLog` per
      the tenancy feature's established conventions. Its own
      SPEC/PLAN/TASKS cycle in `knowly/specify/features/`.

## 1. Routing and post-login navigation (REQ-6)

- [x] 1. Test: `DashboardPageComponent` renders at route `/dashboard`
      (Red).
- [x] 2. Add the route + a placeholder `DashboardPageComponent` (Green).
- [x] 3. Test: `LoginPageComponent`'s `'loggedIn'` step navigates to
      `/dashboard` instead of rendering the old placeholder (Red).
- [x] 4. Implement the navigation, remove the placeholder markup (Green).
- [x] 5. Test: a `401` response from any `/api/**` call redirects to
      `/login` (Red).
- [x] 6. Implement `AuthHttpInterceptor`, register it in `app.config.ts`
      (Green).

## 2. Onboarding status (REQ-3)

- [x] 7. Test: `OnboardingService.fetch()` calls
      `GET /api/users/me/onboarding-status` and sets `completed`
      accordingly (Red).
- [x] 8. Implement `OnboardingService` (Green).
- [x] 9. Test: `OnboardingService.markComplete()` calls
      `POST /api/users/me/onboarding-complete` (Red).
- [x] 10. Implement `markComplete()` (Green).

## 3. Tour engine (REQ-1, REQ-2, REQ-4, REQ-5)

- [x] 11. Test: `TourService.start()` sets `active = true`,
      `stepIndex = 0` (Red).
- [x] 12. Implement `TourService` with its fixed `steps` array (Green).
- [x] 13. Test: `next()`/`back()` move `stepIndex` within bounds; `next()`
      on the last step finishes the tour (`active = false`) and calls
      `OnboardingService.markComplete()` (Red).
- [x] 14. Implement `next()`/`back()`/`skip()` (`skip()` also calls
      `markComplete()`, per REQ-3's "finishes or skips") (Green).
- [x] 15. Test: `TourOverlayComponent` renders the current step's
      title/body, positioned against the element matching its
      `data-tour-id` (Red).
- [x] 16. Implement `TourOverlayComponent` (Green).
- [x] 17. Test: while active, Tab/Shift+Tab cycle focus only within the
      overlay's controls (focus trap), and Escape calls `skip()` (Red).
- [x] 18. Implement the focus trap + keyboard handling (Green).
- [x] 19. Test: `DashboardPageComponent` calls `TourService.start()`
      automatically when `OnboardingService.completed()` resolves to
      `false`, and does not when it resolves to `true` (REQ-1/REQ-6)
      (Red).
- [x] 20. Implement the auto-start wiring (Green).
- [x] 21. Test: `HelpMenuComponent`'s "Restart tour" calls
      `TourService.start()` regardless of `OnboardingService.completed()`
      (REQ-4) (Red).
- [x] 22. Implement `HelpMenuComponent` + wire it into `AppShellComponent`
      (Green).

## 4. Dashboard widgets (REQ-7, REQ-8, REQ-9, REQ-10)

- [x] 23. Test: `ArticleCountCardComponent` fetches
      `GET /api/tenants/metrics/articles` and renders the count on
      success (Red).
- [x] 24. Implement `ArticleCountCardComponent` (Green).
- [x] 25. Test: same component shows a loading state before the response
      arrives (Red).
- [x] 26. Implement the loading state (Green).
- [x] 27. Test: on a network/5xx error, the component shows
      `ErrorStateComponent` with the response's trace id (REQ-9) (Red).
- [x] 28. Implement the error path + `ErrorStateComponent` (Green).
- [x] 29. Test: on `403 PERMISSION_DENIED`, the component shows
      `NoAccessStateComponent`, not the error state (REQ-10) (Red).
- [x] 30. Implement the permission-denied path + `NoAccessStateComponent`
      (Green).
- [x] 31. Repeat tasks 23–30 for `ArticleUsageListComponent`
      (`GET /api/tenants/metrics/articles/usage`, rendered most-used
      first per REQ-7).
- [x] 32. Repeat tasks 23–30 for `ConversationsCardComponent`
      (`GET /api/tenants/metrics/conversations`).
- [x] 33. Repeat tasks 23–30 for `MessagesCardComponent`
      (`GET /api/tenants/metrics/messages`).
- [x] 34. Test: `DashboardPageComponent` composes all four widgets and
      includes a link to the (future) articles screen (REQ-11) (Red).
- [x] 35. Implement the composition + link (Green).

## 5. i18n and design

- [x] 36. Add tour-step and dashboard-label keys to
      `public/i18n/en.json` / `pt-BR.json`.
- [x] 37. Apply the established design-system standard (slate/indigo
      palette, spacing, card/button states) to the tour overlay, help
      menu, and dashboard widgets.

## 6. Final verification

- [x] 38. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 39. Update `PLAN.md`'s "Emergent decisions" section if anything
      changed during implementation.
- [x] 40. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
      what's now verified by tests.
