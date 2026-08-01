# TASKS — staff-leave-tenant (frontend)

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each task should be small enough for a single implementation iteration.
> Backend contract already implemented per
> `knowly-api/specify/features/staff-leave-tenant/PLAN.md` (coordinate
> with backend TASKS.md if `POST /api/tenants/active/clear` is not yet
> merged before starting task 2).

- [ ] 1. Write `active-tenant.service.spec.ts` cases for `leaveTenant()`
      success: mock `POST /api/tenants/active/clear` to succeed; assert
      `activeTenantId`/`activeTenantName`/`activeTenantRole` all become
      `null`, and that `locallySelected` is reset (verified indirectly
      via a subsequent `fetch()` not resurrecting a stale value) (Red —
      `leaveTenant()` does not exist yet).
- [ ] 2. Implement the minimum code for task 1's test to pass (Green):
      add `leaveTenant(): Observable<void>` to `ActiveTenantService`
      (`src/app/core/active-tenant.service.ts`), posting to
      `/api/tenants/active/clear` with an empty body and, in a `tap`,
      nulling the three signals and resetting `locallySelected = false`.
- [ ] 3. Write the `active-tenant.service.spec.ts` case for `leaveTenant()`
      failure: seed the three signals via a successful `selectTenant()`
      call first, mock `POST /api/tenants/active/clear` to fail (non-2xx
      or network error), call `leaveTenant()`, and assert all three
      signals remain unchanged (Red only if `tap`'s error-skip behavior
      is broken by an implementation deviation — otherwise confirms the
      existing task 2 implementation already satisfies this; write it
      regardless as a regression guard).
- [ ] 4. Confirm task 3 passes with no code change (Green) — RxJS `tap`
      never runs on an error notification, so the task 2 implementation
      already satisfies this without an explicit `catchError` in the
      service.
- [ ] 5. Write the `nav-menu.component.spec.ts` case for AC1: zero
      memberships + active tenant set → `[data-testid="nav-leave-tenant"]`
      present (Red — `canLeaveTenant`/the nav item do not exist yet).
- [ ] 6. Implement the minimum code for task 5's test to pass (Green):
      add the `canLeaveTenant` computed signal to `NavMenuComponent`
      (`memberships().length === 0 && activeTenantService.activeTenantId()
      !== null`), and push the new nav item
      (`labelKey: 'nav.leaveTenant'`, `testId: 'nav-leave-tenant'`,
      `icon: 'log-out'`, `onClick: () => this.onLeaveTenant()`) into
      `workspaceGroup` right after the `canSwitchTenant` item when
      `canLeaveTenant()` is true. Add a stub `onLeaveTenant()` method
      (full behavior implemented in task 12) so the component compiles.
- [ ] 7. Extend `NavMenuItem`'s type (`routerLink?: string`, new
      `onClick?: () => void`), add the `'log-out'` case to the
      `NavIconName` union, import `LucideLogOut` from `@lucide/angular`
      into the component's `imports` array, and add the `'log-out'` case
      to both `@switch` template blocks mapping to `LucideLogOut`. Add
      the template `@if`/`@else` branch rendering a `<button
      type="button" data-testid="{{item.testId}}"
      (click)="item.onClick?.()">` with the same `linkClass`/`iconClass`
      styling as the existing `<a [routerLink]="item.routerLink">` when
      `item.onClick` is set. Re-run task 5's test to confirm it still
      passes with the real template branch (not just the pushed item).
- [ ] 8. Write the `nav-menu.component.spec.ts` case for AC2: any
      membership count ≥ 1 (regardless of active tenant/role) →
      `nav-leave-tenant` absent (Red only if `canLeaveTenant`'s
      zero-only condition is wrong — otherwise a regression-lock;
      confirm Green against task 6's implementation with no further
      code change).
- [ ] 9. Write the `nav-menu.component.spec.ts` case for AC3: zero
      memberships + no active tenant → `nav-leave-tenant` absent (Red
      only if `canLeaveTenant`'s active-tenant-id condition is missing —
      otherwise a regression-lock; confirm Green against task 6's
      implementation with no further code change).
- [ ] 10. Write the `nav-menu.component.spec.ts` case: `NavMenuComponent`
      is constructed with `ActiveTenantService.fetch()` never having
      been called elsewhere first, and asserts `fetch()` is invoked
      during `ngOnInit()` (via a spy) so `activeTenantId()` is populated
      independently of routed-page fetches (Red — `ngOnInit()` currently
      only calls `.list()`).
- [ ] 11. Implement the minimum code for task 10's test to pass (Green):
      add `this.activeTenantService.fetch()` alongside the existing
      `.list()` call in `NavMenuComponent.ngOnInit()`.
- [ ] 12. Write the `nav-menu.component.spec.ts` case for AC4: clicking
      `nav-leave-tenant` calls `ActiveTenantService.leaveTenant()` and,
      on a mocked success, navigates to `/welcome` (assert via a
      `Router` spy/mock, not a real navigation) (Red — `onLeaveTenant()`
      is still a stub from task 6).
- [ ] 13. Implement the minimum code for task 12's test to pass (Green):
      inject `Router` into `NavMenuComponent`
      (`private readonly router = inject(Router)`), add a
      `leaveTenantError = signal<'network' | null>(null)` field, and
      implement `onLeaveTenant()` per the PLAN: reset
      `leaveTenantError` to `null`, call `leaveTenant()` piped through
      `catchError(() => { this.leaveTenantError.set('network'); return
      of(null); })`, and on a non-`null` result call
      `this.router.navigateByUrl('/welcome')`.
- [ ] 14. Write the `nav-menu.component.spec.ts` case for AC6: on a
      mocked `leaveTenant()` failure, assert the active-tenant signals
      are unchanged, the local error banner renders (same visual
      treatment as `MembersPageComponent`'s `error()`-driven banner),
      and no navigation occurs (Red — the error banner template does
      not exist yet).
- [ ] 15. Implement the minimum code for task 14's test to pass (Green):
      add the `leaveTenantError()`-driven conditional error banner to
      `NavMenuComponent`'s template, mirroring
      `MembersPageComponent`'s existing banner markup/styling.
- [ ] 16. Write the `nav-menu.component.spec.ts` case for AC7 (no
      confirmation dialog): before and after clicking
      `nav-leave-tenant` (both success and failure paths), assert
      absence of whatever modal/dialog component or ARIA role this
      app's existing confirmation dialogs use elsewhere (Red only if a
      confirmation step is accidentally introduced — otherwise a
      regression-lock; confirm Green against the task 13/15
      implementation with no further code change, since REQ-6
      deliberately adds none).
- [ ] 17. Write the `nav-menu.component.spec.ts` case for AC5: after a
      successful leave (simulating the signal changes —
      `canLeaveTenant()` now false), assert `nav-leave-tenant` itself is
      absent from the rendered output (narrow scope: just this item's
      own disappearance, not re-testing the whole staff/member nav
      toggle already covered elsewhere in this file) (Red only if
      `canLeaveTenant` doesn't react to the signal change — otherwise a
      regression-lock; confirm Green against task 6's implementation
      with no further code change, since `computed` already reacts to
      signal changes).
- [ ] 18. Run `npm run format:check && npm test && npm run build && npm
      run lint` and confirm everything is green (including resolving
      any lint/compiler warnings introduced in touched files, per this
      subproject's standing rule).
- [ ] 19. Update `PLAN.md` if any decision changed during implementation.
- [ ] 20. Commit the completed, verified feature (Conventional Commits).
