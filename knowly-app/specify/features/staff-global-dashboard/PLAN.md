# PLAN — staff-global-dashboard (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **`/dashboard` becomes a thin wrapper switching on
  `ActiveTenantService`, same shape as `UserManagementPageComponent`**
  (`features/user-management/user-management-page.component.ts`): a new
  `DashboardWrapperPageComponent`
  (`features/dashboard/dashboard-wrapper-page.component.ts`) replaces the
  direct `DashboardPageComponent` mapping on the `/dashboard` route,
  waits for `activeTenantService.activeTenantResolved()`, then renders
  `DashboardPageComponent` unchanged when `activeTenantId()` is non-null
  and a new `GlobalDashboardPageComponent` when it's null (REQ-1, REQ-2,
  REQ-7). `tenantSelectionGuard` stays on the route unchanged — same
  reasoning `user-management-screens`' PLAN already gives: it already
  lets a 0-membership/staff session through, and this feature doesn't
  touch that guard's separate job (blocking a pending multi-membership
  selection). No new "resolved" signal is needed on `ActiveTenantService`
  — `activeTenantResolved` already exists from `user-management-screens`
  and is reused as-is.
- **`GlobalDashboardPageComponent`
  (`features/dashboard/global-dashboard-page.component.ts`) owns one
  page-level fetch to `GET /api/staff/metrics/global`**, not four
  independent per-tile fetches — unlike the tenant-scoped dashboard's
  five tiles (each backed by its own timeseries/point-in-time endpoint),
  all four global numbers come back from a single endpoint call, and
  REQ-5's 403 handling is explicitly page-level ("the global metrics
  view shall show ... `app-no-access-state`"), not per-tile. The
  component owns `metrics: Signal<GlobalMetricsDto | null>` and
  `error: Signal<'network' | 'permission-denied' | null>`, following the
  exact same `catchError` → classify-by-`err.status === 403` →
  `NoAccessStateComponent`/`ErrorStateComponent` pattern already used by
  `StaffDirectoryPageComponent`/`StaffUserDetailPanelComponent` — no new
  error-handling shape.
- **`metric-tile.component.ts` gains an additive, backward-compatible
  "pre-fetched value" mode** (Tier 2 — no exact precedent for a shared
  fetch-owning component also needing a presentational mode; written
  down here and in `DECISIONS.md`). Today `url`/`valueSelector`/
  `sparklineSelector`/`period` are all `input.required<...>()` and the
  component always self-fetches via `createMetricFetcher`. Three
  changes, all additive:
  - `url`, `valueSelector`, `period` become optional
    (`input<T | undefined>(undefined)`); the component's constructor
    `effect()` only creates/loads a `fetcher` when `url()` is defined —
    every existing call site (the five tenant-scoped tiles) keeps
    passing all three, so their behavior is byte-for-byte unchanged.
  - New `value = input<number | undefined>(undefined)` and
    `loading = input<boolean>(false)`: when `value()` is defined, the
    tile renders `label()` + `value()` directly, skips its own fetch
    entirely, and shows no sparkline chart/table (matching REQ-3's "no
    sparkline, point-in-time counts only" requirement) — loading/error
    states become the parent's responsibility in this mode (the parent
    already owns them per the decision above), so the tile itself shows
    nothing extra while `loading()` is true beyond what the parent
    already gates.
  - New `disabled = input(false)`: renders a visibly muted "coming soon"
    label instead of a value, no fetch attempted regardless of any other
    input — used for the fifth support-tickets tile (REQ-4).
  - *Why extend rather than fork a second tile component:* identical
    reasoning to `dashboard-analytics`'s own PLAN for
    `createMetricFetcher` — a second near-identical presentational
    component would violate this app's "one shape, don't duplicate"
    convention and double the surface kept in sync with the card's
    visual styling. The self-fetching mode is untouched, so this is
    additive, not a breaking change to the five existing tenant tiles.
- **`GlobalDashboardPageComponent` renders five `app-metric-tile`s**:
  four in pre-fetched `[value]` mode (`metrics().tenantCount`,
  `metrics().newTenantsThisMonth`, `metrics().articlesReadTotal`,
  `metrics().staffCount`) and one `[disabled]="true"` tile for support
  tickets (REQ-3, REQ-4).
- **Welcome-screen link is a fifth conditional quick-link card in the
  existing `@if (tenantName() && (...))` grid's sibling branch** — since
  the existing grid is gated on `tenantName()` being truthy (the
  tenant-member case) and this new card is the mirror-image
  staff-outside-tenant case, it needs its own `@if` block (not another
  clause added to the existing tenant-gated one), consistent with
  REQ-8/REQ-9's independent gating (`DASHBOARD_VIEW_GLOBAL` or
  `STAFF_ADMIN`, not any of the three existing per-tenant permissions).
  `WelcomePageComponent` gains
  `showGlobalDashboard = computed(() => !tenantName() &&
  (globalPermissionsService.has('DASHBOARD_VIEW_GLOBAL') ||
  viewerIsStaffAdmin()))`, injecting `GlobalPermissionsService` (not
  currently injected there) and calling its existing `.fetch()` in
  `ngOnInit` alongside the two calls already there — matches
  `nav-menu.component.ts`'s existing "always fetch global permissions on
  a page that might need them" pattern rather than assuming the sidebar
  has already populated the signal by the time `/welcome` mounts (the
  two components mount independently; `WelcomePageComponent` cannot
  assume `NavMenuComponent`'s `ngOnInit` ran first).
- **`viewerIsStaffAdmin` inference is duplicated as a local `computed()`
  in `WelcomePageComponent` and `GlobalDashboardPageComponent`**, same
  `ALL_GLOBAL_PERMISSIONS.every(p => globalPermissionsService.has(p))`
  shape already established and documented as a known Tier 2 tradeoff in
  `user-management-screens`' PLAN (`StaffDirectoryPageComponent`'s own
  `viewerIsStaffAdmin`) — not extracted into a shared helper/service
  method here, matching that PLAN's precedent of page-local
  `computed()` over injected service signals (`nav-menu.component.ts`'s
  `canSwitchTenant` is the same shape). Three near-identical
  implementations of this exact expression now exist
  (`StaffDirectoryPageComponent`, `WelcomePageComponent`,
  `GlobalDashboardPageComponent`); flagged here as an accepted,
  consistent repetition of a already-precedented pattern, not a new one
  — extracting it to `GlobalPermissionsService.isStaffAdmin()` would be
  a reasonable future cleanup but is out of scope for this SPEC to
  decide unilaterally (touches a service outside this feature's SPEC).
- **Audit-trail section is a new, independent `ngOnChanges`-driven
  sub-load inside `StaffUserDetailPanelComponent`**, mirroring
  `loadDetail`/`loadAccessGroups`'s existing shape exactly: its own
  `auditTrail = signal<AuditEvent[] | null>(null)` and
  `auditTrailError = signal<DetailError>(null)`, its own
  `loadAuditTrail()` private method called from `ngOnChanges()` alongside
  the two existing calls, its own `catchError` → 403-classify block. This
  keeps the failure section-scoped per REQ-12 (a 403 from the audit-trail
  endpoint only sets `auditTrailError`, never the panel's other two
  sections' independent state) without introducing a new shared
  "multi-section error" abstraction — the existing per-section pattern
  already does exactly this by construction (each section has its own
  independent signal pair).
- **New `AuditEvent` type + `getAuditTrail` method added to
  `StaffUserService`** (not a new service) — same reasoning
  `user-management-screens`' PLAN already gives for keeping
  `/api/staff/users/**` sub-resource calls on `StaffUserService`: this is
  one more `GET /api/staff/users/{userId}/...` endpoint on the same
  resource family, not a new domain.
- **Audit-trail rows render as a plain `<table>`, not `p-table`/any
  library component** — matches this app's post-PrimeNG-removal
  convention (hand-rolled Tailwind, see `DECISIONS.md`) and
  `top-articles-table.component.ts`'s existing plain-table shape; no
  sorting/filtering is required by the SPEC (REQ-10/REQ-14 render
  exactly what the backend returns, in the order the backend returns
  it), so no interactive table behavior is needed beyond static markup.
- **Nav link gating (REQ-6)**: `nav-menu.component.ts`'s
  `overviewGroups` computed currently gates the dashboard link on
  `permissionsService.has('DASHBOARD_VIEW')` only (tenant-scoped
  permission). This becomes
  `permissionsService.has('DASHBOARD_VIEW') ||
  globalPermissionsService.has('DASHBOARD_VIEW_GLOBAL')` — same
  "one link, one route, two possible reasons to show it" shape already
  used for the `members` nav entry (`TENANT_MEMBER_MANAGE` OR
  `STAFF_USER_VIEW`). `STAFF_ADMIN` satisfies the second clause too
  (`ownPermissions()` returns every value for `STAFF_ADMIN`, as already
  documented in `user-management-screens`' PLAN), so no separate
  `STAFF_ADMIN` clause is needed. Since this link already only appears
  under `nav.category.overview` (unconditionally rendered once *any*
  qualifying permission is held, tenant or global), no second nav
  category/entry is introduced — one link continues to serve both
  contexts, matching REQ-1/REQ-2's "same route, two contexts" framing at
  the nav layer too.
- **`GlobalPermission` frontend union gains two values**:
  `DASHBOARD_VIEW_GLOBAL` and `AUDIT_TRAIL_VIEW`, appended to
  `core/global-permission.ts`'s `GlobalPermission` type and
  `ALL_GLOBAL_PERMISSIONS` array. Verified current state first (see
  below) — neither exists yet; `user-management-screens`' PLAN
  deliberately left them out as "not referenced by that feature." Adding
  them here grows `ALL_GLOBAL_PERMISSIONS`, which is also the array
  `viewerIsStaffAdmin`'s inference walks everywhere it's used — this is
  intentional and correct (a plain `STAFF` user would need genuinely all
  permissions, including these two new ones, to be misclassified as
  admin; the existing accepted Tier 2 tradeoff already covers this
  exact growth pattern, not a new risk).
- **No CSRF change** — `/api/staff/metrics/global` and
  `/api/staff/users/{userId}/audit-trail` are both `GET` (no CSRF
  applies to reads at all under `SecurityConfig`'s
  `csrf().ignoringRequestMatchers` design — CSRF protection targets
  state-changing methods) and neither is added to that exemption list.
  Nothing to do here, same conclusion `user-management-screens`' PLAN
  already reached for `/api/staff/**`.

## Components and routes

```
DashboardWrapperPageComponent (NEW, route `/dashboard`, replaces direct
                                DashboardPageComponent mapping)
├── DashboardPageComponent      (unchanged, reused as-is — tenant-scoped)
└── GlobalDashboardPageComponent (NEW — staff, no active tenant)
    └── MetricTileComponent x5   (existing component, extended — 4x [value] mode, 1x [disabled])

WelcomePageComponent (existing, additive change)
└── new quick-link card to `/dashboard`, gated on DASHBOARD_VIEW_GLOBAL/STAFF_ADMIN

StaffUserDetailPanelComponent (existing, additive change)
└── new <section data-testid="staff-audit-trail"> (audit trail table)

nav-menu.component.ts (existing, additive change)
└── `members`-style dual-gate on the existing `nav.dashboard` entry
```

- `DashboardWrapperPageComponent`
  (`features/dashboard/dashboard-wrapper-page.component.ts`): loading
  state, then `DashboardPageComponent` or `GlobalDashboardPageComponent`
  depending on `ActiveTenantService.activeTenantId()`, same structure as
  `UserManagementPageComponent`.
- `DashboardPageComponent`: **unchanged**, reused as-is.
- `GlobalDashboardPageComponent`
  (`features/dashboard/global-dashboard-page.component.ts`): page-level
  fetch/loading/error state, renders 5 `app-metric-tile`s on success.
- `MetricTileComponent`: extended (not replaced) — see "Architectural
  decisions" above.
- `StaffUserDetailPanelComponent`: gains a fourth section
  (`data-testid="staff-audit-trail"`), independent load/error state.

## Consumed API contracts

Per `knowly-api/specify/features/global-staff-dashboard-metrics/PLAN.md`
and `knowly-api/specify/features/staff-audit-trail-view/PLAN.md`,
confirmed directly against those PLANs (both backend features already
shipped, per `PROJECT_STATUS.md`):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/staff/metrics/global` | — | `{ tenantCount, newTenantsThisMonth, articlesReadTotal, staffCount }` (all `number`) | 200 (holds `DASHBOARD_VIEW_GLOBAL` or `STAFF_ADMIN`) / 403 |
| GET | `/api/staff/users/{userId}/audit-trail` | — | `[{ occurredAt, action, resourceType, resourceId, tenantId: string \| null, outcome, metadata }]`, reverse-chronological, capped at 500 rows | 200 (holds `AUDIT_TRAIL_VIEW` or `STAFF_ADMIN`) / 403 / 404 (`{userId}` not found) |

`tenantId: null` renders as the literal string "global" in the table per
REQ-10; `metadata` is fetched but not rendered by this screen (SPEC lists
only occurred-at/action/resource type/resource id/tenant id/outcome as
the displayed columns — `metadata` is out of scope for display, not
dropped from the type, since the backend DTO includes it and a future
feature may want it).

A 404 from the audit-trail endpoint (nonexistent `{userId}`) is not
expected to occur from this UI, since `userId` always comes from an
already-loaded `StaffUserSummary` row in the same session
(`StaffDirectoryPageComponent`'s own list) — if it somehow does (e.g. a
stale detail panel left open across a concurrent staff-user deletion,
which this codebase has no delete-staff-user feature to even trigger
today), it falls into the existing generic `'network'` error branch
(same as any other unclassified non-403 status), consistent with how
`staff-user-detail-panel`'s existing sections already handle
unclassified errors.

## State and data

- `ActiveTenantService`: unchanged, `activeTenantResolved` reused as-is
  (added by `user-management-screens`).
- `GlobalPermissionsService`: unchanged, reused; `GlobalPermission` union
  gains `DASHBOARD_VIEW_GLOBAL` and `AUDIT_TRAIL_VIEW`.
- `GlobalDashboardPageComponent`: `metrics = signal<GlobalMetricsDto |
  null>(null)`, `loading = signal(true)`, `error = signal<'network' |
  'permission-denied' | null>(null)` — same shape as
  `StaffDirectoryPageComponent`.
- `MetricTileComponent`: no new state beyond the additive inputs
  described above; still no local service-level state, purely
  input-driven.
- `WelcomePageComponent`: new `showGlobalDashboard = computed(...)` (see
  above), no new signal — reads existing/newly-injected service signals.
- `StaffUserDetailPanelComponent`: `auditTrail = signal<AuditEvent[] |
  null>(null)`, `auditTrailError = signal<DetailError>(null)` — added
  alongside the existing `detail`/`availableAccessGroups`/`error`
  signals, independent of them.
- `StaffUserService`: gains `getAuditTrail(userId: number):
  Observable<AuditEvent[]>` calling
  `GET /api/staff/users/${userId}/audit-trail` — no local signal state,
  same stateless-wrapper shape as every other method on this service.
  New exported `AuditEvent` interface colocated in
  `core/staff-user.service.ts`, next to the other DTO-mirroring types
  already there (`StaffUserSummary`, `GlobalAccessGroup`,
  `StaffUserDetail`).
- `nav-menu.component.ts`: no new state, existing
  `globalPermissionsService`/`permissionsService` signals reused in the
  `overviewGroups` computed's existing dashboard-link condition.

## Dependencies

None new.

## Testing strategy

- `global-permission.ts`: no dedicated spec file exists today (a plain
  type/array); covered transitively by every consumer test below.
- `metric-tile.component.spec.ts`: new cases for the additive inputs —
  `[value]` mode renders the given number with no HTTP call and no
  sparkline chart/table; `[disabled]` mode renders the "coming soon"
  label with no HTTP call; existing five self-fetching cases (loading/
  success/network-error/permission-denied/sparkline-table) untouched and
  still passing, proving the extension is backward-compatible.
- `global-dashboard-page.component.spec.ts` (new): renders 5 tiles (4
  populated + 1 disabled) after a successful
  `GET /api/staff/metrics/global`; a 403 renders `app-no-access-state`
  at the page level (not per-tile); a non-403 error renders
  `app-error-state`.
- `dashboard-wrapper-page.component.spec.ts` (new): loading state while
  `!activeTenantService.activeTenantResolved()`; renders
  `DashboardPageComponent` when `activeTenantId()` is non-null;
  `GlobalDashboardPageComponent` when it's null; switching from null to
  non-null (REQ-7) swaps the rendered child.
- `app.routes` / existing `dashboard-page.component.spec.ts`: updated
  only to the extent the route now points at
  `DashboardWrapperPageComponent`; `dashboard-page.component.spec.ts`
  itself is otherwise untouched (component unchanged).
- `welcome-page.component.spec.ts`: new cases — the global-dashboard
  quick-link card appears when staff-outside-tenant and
  `DASHBOARD_VIEW_GLOBAL`/`STAFF_ADMIN`-shaped permissions are held;
  absent when neither is held; absent whenever `tenantName()` is set
  (tenant-member case, unchanged existing behavior); existing
  tenant-branded link cases untouched.
- `staff-user-detail-panel.component.spec.ts`: new cases — the
  audit-trail section renders rows (timestamp, action, resource type/id,
  tenant id or "global", outcome) reverse-chronological as returned; a
  403 from the audit-trail call renders `app-no-access-state` inside
  that section only, while direct-permissions/access-groups/effective-
  permissions sections still render normally (asserted by mocking the
  audit-trail call to fail while the other two succeed); zero events
  renders a distinct "no audit history" message, not an empty table.
- `staff-user.service.spec.ts`: new case — `getAuditTrail(userId)` calls
  `GET /api/staff/users/{userId}/audit-trail`.
- `nav-menu.component.spec.ts`: new case — the `dashboard` nav entry
  appears for `DASHBOARD_VIEW_GLOBAL` alone and for a `STAFF_ADMIN`-shaped
  ("all permissions") response even without `DASHBOARD_VIEW` (tenant
  permission); existing `DASHBOARD_VIEW`-only case unchanged.
