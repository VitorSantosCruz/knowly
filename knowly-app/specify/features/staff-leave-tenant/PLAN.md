# PLAN — staff-leave-tenant (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Backend contract consumed from the companion
> `knowly-api/specify/features/staff-leave-tenant/PLAN.md`.

## Architectural decisions

- `ActiveTenantService` (`src/app/core/active-tenant.service.ts`) gets
  a new `leaveTenant(): Observable<void>` method, added next to
  `selectTenant()` — same service, same signal-mutation shape, no new
  service introduced (state for "is there an active tenant" already
  lives here per this app's own convention).
- `leaveTenant()` signature/behavior:
  ```ts
  leaveTenant(): Observable<void> {
    return this.http.post<void>('/api/tenants/active/clear', {}).pipe(
      tap(() => {
        this._activeTenantId.set(null);
        this._activeTenantName.set(null);
        this._activeTenantRole.set(null);
        this.locallySelected = false;
      }),
    );
  }
  ```
  The `tap` (signal clear) only runs on the HTTP success path — RxJS
  `tap` never executes on an error notification, so a failed call
  (network error or non-2xx) leaves all three signals untouched with
  zero extra code, satisfying REQ-5 without needing an explicit
  `catchError` inside the service itself. `locallySelected` is also
  reset to `false` here: it exists to protect a staff session's
  optimistically-set signals from `fetch()` nulling them out before a
  real membership row would ever exist (see the service's own doc
  comment on that field) — once the tenant is actually cleared, that
  protection must not persist into the next `fetch()` call, or a
  future genuine "no active tenant" state could be misread as the old
  optimistic-selection race.
  An empty object body (`{}`) is sent because `HttpClient.post` requires
  a body argument; the backend ignores it (no request DTO, per the
  backend PLAN) — this is not a request contract, just satisfying the
  client API's required argument.
- Nav menu gating (`nav-menu.component.ts`): a new `computed` reads
  **both** signals precisely, per this feature's own edge case
  (SPEC's staff-session-never-gets-a-membership-row point, and this
  repo's own "membership-list presence is not the same as
  active-tenant presence" bug precedent — `DECISIONS.md`/
  `PROJECT_STATUS.md` context, not re-derived from scratch here):
  ```ts
  protected readonly canLeaveTenant = computed(
    () => this.memberships().length === 0 && this.activeTenantService.activeTenantId() !== null,
  );
  ```
  `memberships().length === 0` is the existing "confirmed staff, zero
  real `TenantMembership` rows" signal already computed for
  `canSwitchTenant` (strictly narrower condition, per the SPEC's own
  REQ-3 framing: "zero" is a stronger condition than "!== 1"). This is
  **not** derived from `canSwitchTenant` itself (`!== 1`) because a
  multi-membership *regular* member (`length > 1`) would incorrectly
  pass a `!canSwitchTenant`-based check — it must be the zero-only
  condition, checked directly.
  `activeTenantService.activeTenantId() !== null` is read directly off
  the service's own public signal (not off `memberships`, which a
  staff session's active tenant never populates — see the SPEC's
  motivation section and the service's own `locallySelected` doc
  comment) — this is exactly the "membership-list presence isn't the
  same as active-tenant presence" distinction this codebase has
  already had to fix once elsewhere; it must not be reintroduced here
  by convenience.
- `NavMenuComponent.ngOnInit()` currently only calls
  `activeTenantService.list()` (into its own local `memberships`
  signal) — it never calls `activeTenantService.fetch()`, so
  `activeTenantId()` is only populated once some *other*, currently
  routed page component happens to call `fetch()` first (e.g.
  `WelcomePageComponent`, `MembersPageComponent`). Since the nav menu
  is a persistent layout component that must reflect "Leave tenant"
  correctly regardless of which page is currently routed (including
  `/welcome`, which does call `fetch()`, but a reviewer should not
  have to trace that dependency to trust this gating condition), add
  `this.activeTenantService.fetch()` alongside the existing
  `.list()` call in `ngOnInit()`. This is a small, explicit fix to
  close a "depends on some other component having already fetched"
  gap that this feature's gating condition would otherwise inherit
  silently — flagged here as its own decision rather than left
  implicit.
- New nav item added to the existing `workspaceGroup` computed, right
  after the `canSwitchTenant` item (same nav group, per SPEC REQ-2):
  ```ts
  if (this.canLeaveTenant()) {
    items.push({
      labelKey: 'nav.leaveTenant',
      testId: 'nav-leave-tenant',
      icon: 'log-out',
      onClick: () => this.onLeaveTenant(),
    });
  }
  ```
  `nav-menu.component.ts`'s `NavIconName` union (`'layout-grid' |
  'book-open' | ... | 'swap'`) and its two `@case ('swap') { <LucideArrowRightLeft .../> }`
  template switch blocks get a new `'log-out'` case mapped to
  `LucideLogOut` (confirmed exported by `@lucide/angular`), imported
  and added to the component's `imports` array alongside the other
  `Lucide*` icons — following this file's existing "one custom
  `NavIconName` string per icon, resolved via `@switch` in the
  template" pattern exactly, not a literal Lucide selector on the item
  model.
  `nav-menu.component.ts`'s current `NavMenuItem` shape is
  route-only (`routerLink: string`, required); this is the first
  workspace-group item that's an *action* rather than a navigation
  target, so `NavMenuItem.routerLink` becomes optional
  (`routerLink?: string`) and a new optional `onClick?: () => void`
  field is added. The template's existing `<a [routerLink]="item.routerLink">`
  rendering needs an `@if`/`@else` branch: render a
  `<button type="button" data-testid="{{item.testId}}" (click)="item.onClick?.()">`
  with the same `linkClass`/`iconClass` styling when `onClick` is set,
  `<a>` otherwise. This is a template-shape decision worth calling out
  explicitly since every existing workspace-group item today is a
  pure route link — read the current `overviewGroups`/`workspaceGroup`
  template rendering before implementing to keep the two branches
  visually identical (same classes, same icon slot, same focus
  behavior) rather than introducing a visually distinct control.
- Click handler (`onLeaveTenant()`), mirrors the existing generic
  mutating-call error-handling pattern already used by
  `MembersPageComponent` (`error` signal + `catchError` + `of(null)`
  sentinel + skip success side-effect on `null`), not a new pattern:
  ```ts
  protected onLeaveTenant(): void {
    this.leaveTenantError.set(null);
    this.activeTenantService
      .leaveTenant()
      .pipe(catchError(() => { this.leaveTenantError.set('network'); return of(null); }))
      .subscribe((result) => {
        if (result !== null) {
          this.router.navigateByUrl('/welcome');
        }
      });
  }
  ```
  A page-local `leaveTenantError = signal<'network' | null>(null)`
  is added to `NavMenuComponent`, with a small conditional error banner
  in the template (same visual treatment as `MembersPageComponent`'s
  existing `error()`-driven banner) — REQ-5 requires the failure to
  "surface the existing generic error-handling pattern", which in this
  app's convention is a local error signal + template banner, not a
  toast/notification service (none exists — see architecture notes).
  `Router` is newly injected into `NavMenuComponent` for this
  (`private readonly router = inject(Router)`) — not previously
  needed there.
  No confirmation dialog is added, per REQ-6/Out-of-scope — the click
  handler calls `leaveTenant()` immediately.

## Components and routes

- `NavMenuComponent` (`src/app/layout/nav-menu.component.ts`) —
  changed: new `canLeaveTenant` computed, new `onLeaveTenant()`
  handler, new `leaveTenantError` signal, new `onClick`-capable
  `NavMenuItem` branch in the template, new item pushed into
  `workspaceGroup`, `Router` injected, `activeTenantService.fetch()`
  added to `ngOnInit()`.
- No new routes. No new page component. `/welcome` (navigation target
  on success) already exists and already calls
  `activeTenantService.fetch()` in its own `ngOnInit` (confirmed above),
  so it will correctly resolve to the tenant-less state on arrival
  regardless of the nav menu's own signal state.

## Consumed API contracts

Per `knowly-api/specify/features/staff-leave-tenant/PLAN.md`:

| Method | Path | Request | Response | Status codes |
|---|---|---|---|---|
| POST | `/api/tenants/active/clear` | none | none | `200` success; `403` (`TENANT_ACCESS_DENIED`) non-staff; `401` unauthenticated; `403` (CSRF filter) if CSRF token missing/invalid |

The Angular `HttpClient` CSRF (`XSRF-TOKEN`/`X-XSRF-TOKEN`) handling is
already global (`HttpClientXsrfModule`/equivalent already configured
for every other mutating `/api/...` call in this app, e.g.
`selectTenant()`'s own `POST /api/tenants/active|` — no new CSRF
wiring needed for this call; it rides the same client-side mechanism).

## State and data

- `ActiveTenantService`'s existing three signals
  (`activeTenantId`/`activeTenantName`/`activeTenantRole`) are reused
  as-is — `leaveTenant()` nulls all three on success, mirroring what
  `selectTenant()` sets them to on entry. No new shared signal
  introduced in the service.
- `NavMenuComponent` gets one new local signal, `leaveTenantError`
  (`'network' | null`), following the same page-local error-signal
  shape already used by `MembersPageComponent`/`ArticlesPageComponent`
  etc. — not promoted to a shared service, since no other component
  needs to know about a failed leave-tenant call.

## Dependencies

None. No new `package.json` dependency — reuses `@angular/common/http`,
`rxjs` (`tap`/`catchError`/`of`, already imported patterns elsewhere),
`@angular/router`, and the existing `@lucide/angular` package (adds an
import of its already-published `LucideLogOut` icon component, the
same "one new `Lucide*` import per new nav icon" pattern every prior
nav item addition has used — not a new dependency, just a new named
import from one already installed).

## Testing strategy

Vitest, component/service tests, no new e2e framework:

- `active-tenant.service.spec.ts` (or wherever `selectTenant()` is
  currently tested): add cases for `leaveTenant()` —
  - success: `POST /api/tenants/active/clear` called, all three
    signals become `null`, `locallySelected` reset (verified
    indirectly via a subsequent `fetch()` call not resurrecting a
    stale value — mirrors the existing test style for
    `locallySelected`, if one already exists for `selectTenant()`; if
    not, add the minimal signal-value assertions only).
  - failure (mocked non-2xx / network error): signals remain at
    whatever they were set to before the call (seed them via
    `selectTenant()` or by calling the private setters through
    `selectTenant()`'s own success path in the test, not by reaching
    into private state).
- `nav-menu.component.spec.ts`:
  - zero memberships + active tenant set → "Leave tenant"
    (`[data-testid="nav-leave-tenant"]`) present (AC1).
  - any membership count ≥ 1 (regardless of active tenant/role) →
    absent (AC2).
  - zero memberships + no active tenant → absent (AC3).
  - clicking it calls `ActiveTenantService.leaveTenant()` and, on a
    mocked success, navigates to `/welcome` (AC4) — assert via a
    `Router` spy/mock, not a real navigation.
  - on a mocked failure, active-tenant signals are asserted unchanged
    and the local `leaveTenantError` banner renders, and no navigation
    occurs (AC6).
  - no confirmation dialog/modal appears in the DOM before or after
    the click (AC7) — assert absence of whatever modal/dialog
    component or ARIA role this app's existing confirmation dialogs
    use elsewhere, confirming none is triggered here.
  - after a successful leave, re-evaluate `canLeaveTenant()`
    (simulating the signal changes) and assert the nav menu's rendered
    output flips: staff-only links reappear are covered by existing
    `globalPermissionsService`-gated tests already in this file and
    are unaffected by this feature, but `nav-leave-tenant` itself must
    now be absent (AC5, narrow scope: just this item's own
    disappearance, not re-testing the whole staff/member toggle this
    file may already cover elsewhere).
