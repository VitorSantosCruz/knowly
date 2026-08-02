# TASKS — navigation-menu

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.

- [x] 1. `core/global-permission.ts` (`GlobalPermission` type +
      `ALL_GLOBAL_PERMISSIONS`, mirrors `permission.ts`).
- [x] 2. `core/global-permissions.service.ts` (mirrors
      `permissions.service.ts`, calls `GET /api/staff/permissions`).
      Unit test: `has()` false before `fetch()`, true/false correctly
      after, per PLAN.md's testing strategy.
- [x] 3. Fix `core/staff.guard.ts` (REQ-6): use
      `GlobalPermissionsService#fetch()` + `.has('TENANT_CREATE')`
      instead of the `GET /api/tenants` success heuristic. Test: a
      `STAFF` user granted only `TENANT_CREATE` (not
      `TENANT_ACT_AS_ANY`) is now allowed through — the regression this
      SPEC exists to fix.
- [x] 4. Fix `select-tenant-page.component.ts`'s "Create tenant" link
      (same bug, second instance): replace the `isStaff` signal (set
      from `listAllTenants()` success) with
      `GlobalPermissionsService#has('TENANT_CREATE')`. Test updated
      accordingly.
- [x] 5. `layout/nav-menu.component.ts`: renders `Dashboard`/
      `Conversations`/`Articles`/`Members` links gated by
      `PermissionsService` (only when there's an active tenant) and
      `Create tenant` gated by `GlobalPermissionsService` — REQ-1/2/3/5.
      Test: each link's visibility per permission combination,
      including the "no active tenant → no tenant-scoped links, but
      global ones still evaluated" case.
- [x] 6. Add "Switch tenant" link (REQ-4): visible when
      `ActiveTenantService#listOwnMemberships()`/`list()` returns more
      than one membership, navigates to `/select-tenant`. Test per
      PLAN.md.
- [x] 7. Wire `<app-nav-menu />` into `app-shell.component.ts` alongside
      the existing corner cluster (not replacing it).
- [x] 8. Run `npm run format:check && npm test && npm run build` and
      confirm everything is green.
- [x] 9. Update `PROJECT_STATUS.md` (this repo's feature table + "Next
      up") and the backend's `PROJECT_STATUS.md` (mark roadmap item 4
      done, point "Next up" at item 5 — user management screens), then
      commit both repos.

### Added 2026-08-01 — REQ-7 through REQ-11

- [x] 10. `nav-menu.component.spec.ts`: add regression test — logo
      (`brand-wordmark`) renders for a logged-in `MEMBER` session with
      zero tenant permissions and zero memberships-derived state (REQ-7).
      Confirms no code change needed per PLAN.md's finding; test should
      already pass green with no production change (documents the fix
      already landed via `cca348a`).
- [x] 11. `avatar-menu.component.spec.ts`: add regression test — logout
      entry renders for the same zero-tenant-permission `MEMBER` session
      (REQ-8). Same "already green" expectation as task 10.
- [x] 12. `nav-menu.component.spec.ts`: add regression test — "switch
      tenant" item shown for a `MEMBER` with `>1` memberships regardless
      of tenant permission level (REQ-9). Expected already green, no
      production change.
- [x] 13. `nav-menu.component.spec.ts`: add the real regression test for
      REQ-11 (RED first) — a 0-membership session with an empty
      `GlobalPermission` list must not render the "switch tenant" item.
      Confirm this fails against the current `canSwitchTenant`
      (`memberships().length !== 1` alone), then fix
      `nav-menu.component.ts`: the `=== 0` branch resolves to
      `globalPermissionsService.has('TENANT_ACT_AS_ANY')` instead of an
      unconditional `true`; the `> 1` branch is untouched. Confirm GREEN.
- [x] 14. `nav-menu.component.spec.ts`: add test — "switch tenant" item
      shown for a 0-membership session holding `TENANT_ACT_AS_ANY` (REQ-10
      first clause) — should already be GREEN off task 13's fix, no
      further production change.
- [ ] ~~15. `nav-menu.component.spec.ts`: add test documenting the
      accepted, flagged gap...~~ **Superseded, do not implement as
      written** — the backend field this task deferred on
      (`staff-rbac-split` REQ-9) has since landed; task 20 below closes
      the length-1 ambiguity for real instead of documenting it as an
      accepted limitation. Struck through rather than deleted, per SDD
      practice of not rewriting history.
- [x] 16. Run
      `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green.
- [x] 17. Update `PROJECT_STATUS.md`'s `navigation-menu` row: note REQ-7
      through REQ-11 closed except the flagged `STAFF`-with-one-membership
      gap (pending a backend `OwnGlobalPermissionsDto` field addition —
      file this as a concrete backend follow-up, not just a prose
      mention, mirroring how other rows in this file record open backend
      follow-ups), then commit.

### Added 2026-08-01 (clarification) — REQ-12/REQ-13, and closing the flagged `isStaffAccount` gap

- [x] 18. `nav-menu.component.spec.ts`: add regression tests — "Create
      tenant" and "leave tenant" never appear for a `MEMBER`/
      `MEMBER_ADMIN` session regardless of tenant permission level
      (REQ-12). Expected already GREEN against current
      `nav-menu.component.ts` (no production change); documents the
      already-correct behavior per PLAN.md's finding.
- [x] 19. `nav-menu.component.spec.ts`: add regression tests — a `STAFF`
      session holding `TENANT_CREATE` sees "Create tenant" while
      `activeTenantId()` is `null`, loses it the moment `activeTenantId()`
      becomes non-null (entering a tenant session), and regains it once
      `activeTenantId()` returns to `null` (REQ-13). Expected already
      GREEN, no production change.
- [x] 20. Confirm the exact field name for the boolean added to
      `GET /api/staff/permissions`'s response against
      `knowly-api/specify/features/staff-rbac-split/PLAN.md`/the actual
      `OwnGlobalPermissionsDto` once implemented there (this PLAN assumes
      `isStaffAccount`) before writing the interface below — if it lands
      under whatever name.
- [x] 21. `core/global-permissions.service.ts` (+ `.spec.ts`, RED first):
      add `isStaffAccount` to `OwnGlobalPermissionsResponse`, a new
      `_isStaffAccount` signal + `isStaffAccount = ...asReadonly()`,
      populated by `fetch()` alongside `_permissions`. Test:
      `isStaffAccount()` false before `fetch()`, true/false correctly
      after, per a mocked response.
- [x] 22. `nav-menu.component.ts` (+ `.spec.ts`, RED first): closes the
      length-1 gap — `canSwitchTenant` and `canLeaveTenant` gain an
      explicit `length === 1 && globalPermissionsService.isStaffAccount()`
      branch (in addition to their existing `length > 1`/`length === 0`
      branches respectively), per PLAN.md's exact boolean shape. Tests:
      a length-1 session with `isStaffAccount: true` now sees both
      "switch tenant" and "leave tenant"; the same length-1 session with
      `isStaffAccount: false` (plain member) still sees neither
      (unchanged regression).
- [x] 23. Run
      `npm run format:check && npm test && npm run build && npm run lint`
      and confirm everything is green.
- [x] 24. Update `PROJECT_STATUS.md`'s `navigation-menu` row: mark REQ-12/
      REQ-13 closed, and mark the previously-flagged
      `STAFF`-with-one-membership gap (item from task 17) as resolved by
      `staff-rbac-split`'s `isStaffAccount` field landing — remove that
      backend follow-up entry rather than leaving it open, then commit.
