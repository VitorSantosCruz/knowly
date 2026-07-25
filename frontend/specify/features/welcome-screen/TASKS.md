# TASKS — welcome-screen

- [x] 1. `AuthService.checkSession()` + tests.
- [x] 2. `rootRedirectGuard` + `RootRedirectPlaceholderComponent`, wired
      into the `''` route + tests.
- [x] 3. `tenantSelectionGuard`: 0-membership passthrough (amends
      `select-tenant` SPEC REQ-5) + tests.
- [x] 4. `WelcomePageComponent` (staff/tenant greeting, dashboard link,
      tour trigger) + tests.
- [x] 5. Move tour target ids to `NavMenuComponent`; drop tour trigger
      and tour-id from `DashboardPageComponent` + tests.
- [x] 6. Repoint post-login/tenant-selection navigation to `/welcome`
      (`LoginPageComponent`, `SelectTenantPageComponent`) + tests.
- [x] 7. Create-tenant button restyle on `/select-tenant`
      (moved to a proper button next to the title).
- [x] 8. `npm run format:check && npm test && npm run build` green.
- [x] 9. Update `PROJECT_STATUS.md` and commit.
