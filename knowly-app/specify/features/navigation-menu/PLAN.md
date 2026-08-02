# PLAN — navigation-menu

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- New `core/global-permission.ts`: `GlobalPermission` type + `ALL_GLOBAL_PERMISSIONS`,
  mirroring `core/permission.ts`'s shape exactly, values matching the
  backend's `GlobalPermission` enum (`TENANT_CREATE`, `TENANT_ACT_AS_ANY`,
  `TENANT_MEMBER_MANAGE_ANY`, `TENANT_ACCESS_GROUP_MANAGE_ANY`,
  `TENANT_PERMISSION_GRANT_MANAGE_ANY`, `STAFF_PERMISSION_MANAGE`,
  `STAFF_USER_CREATE`).
- New `core/global-permissions.service.ts`: mirrors
  `core/permissions.service.ts` exactly (`fetch()`/`has()`/cached
  `signal`), calling `GET /api/staff/permissions` instead of
  `GET /api/tenants/permissions`.
- **Bug fix (REQ-6)**: `core/staff.guard.ts` no longer infers "can create
  a tenant" from `GET /api/tenants` succeeding. It calls
  `GlobalPermissionsService#fetch()` then checks
  `.has('TENANT_CREATE')` — the actual permission `/tenants/new`
  requires, decoupled from whatever `TENANT_ACT_AS_ANY` (list-all-tenants)
  requires. Guard becomes async the same way it already is today (still
  returns an `Observable<boolean | UrlTree>`), just backed by a different
  call.
- Same bug, second instance: `select-tenant-page.component.ts`'s
  `isStaff` signal (used to show/hide the "Create tenant" link) is
  replaced with `GlobalPermissionsService#has('TENANT_CREATE')`, fetched
  the same way, instead of piggybacking on `listAllTenants()` success.
  `listAllTenants()` itself is unchanged (it's still the correct call
  for the "0 memberships → show all tenants" fallback per `select-tenant`
  REQ-5/REQ-6, which is about `TENANT_ACT_AS_ANY`, not `TENANT_CREATE`).
- New `layout/nav-menu.component.ts`, rendered inside `app-shell.component.ts`
  alongside the existing fixed corner cluster (not replacing it — help/
  language/theme/logout stay where they are; this is the missing
  section-to-section navigation).
  - On construction, calls `PermissionsService#fetch()` (tenant
    permissions) if there's an active tenant session, and
    `GlobalPermissionsService#fetch()` if the session is staff (same
    `GET /api/staff/permissions` call staff.guard now uses — the service
    caches after first fetch, so both call sites share one HTTP call per
    session).
  - Renders a `RouterLink` per section per SPEC REQ-2/REQ-3's permission
    mapping, using `@if` per link exactly like `select-tenant-page.component.ts`
    already does for its "Create tenant" link — no new conditional-rendering
    pattern.
  - "Switch tenant" link (REQ-4): shown whenever
    `ActiveTenantService#listOwnMemberships()` (existing call, used by
    `select-tenant-page` already) returns more than one membership;
    navigates to `/select-tenant` — the existing screen, reused as-is
    (SPEC explicitly requires this, not a new switcher UI).
- No "is this a staff session" detection is needed at all:
  `GET /api/staff/permissions` (`StaffController#ownPermissions`) is
  safe to call for *any* authenticated user — a plain tenant member has
  zero global grants, so it just returns an empty list, not a 403 or an
  error. The menu therefore always calls
  `GlobalPermissionsService#fetch()` unconditionally, and calls
  `PermissionsService#fetch()` (`GET /api/tenants/permissions`) only
  when `ActiveTenantService#activeTenantId()` is set (that endpoint 403s
  with no active tenant). Each staff link naturally stays hidden for a
  non-staff user since `has()` is false for every `GlobalPermission`;
  no new session-type flag is introduced.

## Data schema

None — frontend-only, no new backend calls beyond the already-existing
`GET /api/staff/permissions` (added by the backend's `staff-rbac-split`).

## API contracts (consumed, not introduced)

| Method | Path | Used for |
|---|---|---|
| GET | `/api/tenants/permissions` | Tenant-scoped menu filtering (existing `PermissionsService`) |
| GET | `/api/staff/permissions` | Staff menu filtering + `staffGuard`/create-tenant-link fix (new `GlobalPermissionsService`); response also carries `isStaffAccount: boolean` (assumed name, per `staff-rbac-split` REQ-9 — confirm exact name against that feature's PLAN.md/DTO before implementing) used to resolve the length-1-membership staff/member ambiguity (REQ-10/12/13) |
| GET | `/api/tenants/memberships` | "Switch tenant" link visibility (existing, via `ActiveTenantService`) |

## Dependencies

None new.

## Package/file structure

- `src/app/core/global-permission.ts` (new)
- `src/app/core/global-permissions.service.ts` (new)
- `src/app/core/staff.guard.ts` (modify: use `GlobalPermissionsService`)
- `src/app/features/select-tenant/select-tenant-page.component.ts` (modify: replace `isStaff` heuristic)
- `src/app/layout/nav-menu.component.ts` (new)
- `src/app/layout/app-shell.component.ts` (modify: render `<app-nav-menu />`)

## Added 2026-08-01 — REQ-7 through REQ-11 (member logo/logout, staff tenant-list gating)

- **REQ-7/REQ-8 finding: already structurally satisfied, no new component
  needed.** The "minimal pre-tenant welcome layout with no logo/logout"
  premise this bug was originally reported against no longer matches
  `app-shell.component.ts` — `cca348a` (2026-08-01, same day as this SPEC
  delta, "replace standalone logout button with avatar menu") and its
  preceding commits already made `App`'s only template `<app-shell />`,
  and `AppShellComponent` renders the full sidebar (`<app-nav-menu />`,
  which always renders the logo once `authService.isLoggedIn()`) and
  header (`<app-avatar-menu />`, gated the same way) for every route
  except the single hardcoded `/login` bare route — regardless of
  `ActiveTenantService#activeTenantId()`, membership count, or tenant
  permission level. There is no second, minimal layout for any
  authenticated, non-`/login` route today. This PLAN therefore adds
  **no new layout/component** for REQ-7/8 — only regression tests
  (`nav-menu.component.spec.ts`/`avatar-menu.component.spec.ts`) pinning
  this already-correct behavior for a zero-tenant-permission `MEMBER`,
  so a future change can't reintroduce the permission-gated wrapper this
  bug originally reported.
- **REQ-9 finding: already satisfied by `canSwitchTenant`'s existing
  `memberships().length !== 1` check** (a `MEMBER` with `>1` memberships
  always gets `true` here regardless of any tenant permission, since
  membership count is the only input) — again, only a regression test is
  added, no logic change.
- **REQ-10/REQ-11 bug (real fix)**: `canSwitchTenant` today is purely
  `this.memberships().length !== 1`, so a `STAFF` session (which the
  existing code already treats `memberships().length === 0` as a proxy
  for, per `canLeaveTenant`'s established comment) unconditionally gets
  `true` — it never checks `GlobalPermission.TENANT_ACT_AS_ANY`, which is
  the permission `select-tenant-page.component.ts`'s own fallback listing
  (`listAllTenants()`) already requires server-side. This is exactly
  REQ-11's bug: a `STAFF` user with neither `TENANT_ACT_AS_ANY` nor any
  real membership currently still sees the item. Fix: split the
  zero-membership case out of the flat `!== 1` check —
  `memberships().length === 0` now resolves to
  `globalPermissionsService.has('TENANT_ACT_AS_ANY')` instead of an
  unconditional `true`. The `> 1` case is untouched (still unconditionally
  `true`, satisfies REQ-9 and REQ-10's "member of at least one tenant"
  clause for that count identically for a `MEMBER` or a `STAFF` account
  that happens to hold a real membership).
  `TENANT_ACT_AS_ANY` is the correct permission to check, not a new one —
  it is already the exact permission `GET /api/tenants` (`listAllTenants`)
  requires server-side (see `select-tenant`'s existing `isFallback()`
  branch and `DECISIONS.md`'s "Staff can act as any tenant without
  holding a membership"), so this reuses the same signal already backing
  the full-listing screen the item links to, rather than inventing a
  second "can list tenants" concept.
- **`select-tenant-page.component.ts` needs no change for REQ-10's two
  differentiated behaviors.** Its existing `ngOnInit()` branch already
  does exactly what REQ-10 asks: `memberships().length > 0` renders those
  memberships only (enter-your-own-tenant(s), the second clause's
  behavior) and never attempts `listAllTenants()`; only the
  `length === 0` case falls back to `listAllTenants()` (the first
  clause's full-listing behavior, itself gated server-side by
  `TENANT_ACT_AS_ANY`, returning a 403 today if the caller lacks it — that
  403 case is unreachable through the menu once the nav-menu fix above
  ships, since the item itself won't render). No PLAN-level change here.
- **Known accepted gap, flagged rather than silently decided (Tier 3):**
  REQ-10's second clause ("is a member of at least one tenant") is
  written to apply specifically to a `STAFF` user, and per this
  requirement a `STAFF` account with *exactly one* real membership must
  still see the item (to enter that one tenant + leave back to staff
  area) — but a plain `MEMBER` with exactly one membership must *not*
  (the existing, correct "already home" behavior, unchanged by this
  PLAN). The frontend has no signal to distinguish these two
  `length === 1` cases: `GET /api/staff/permissions` returns an empty
  list for both a plain `MEMBER` and a `STAFF` account holding zero
  global grants (per `DECISIONS.md`'s "Staff can act as any tenant
  without holding a membership," the canonical staff shape is *zero*
  real memberships in the first place — a `STAFF` account additionally
  holding one is a real but atypical state, not something any existing
  DTO surfaces a role/flag for). This PLAN does **not** invent a
  heuristic for this edge (e.g. guessing off unrelated signals, the exact
  anti-pattern `staffGuard`'s original bug was) — the `length === 1` case
  stays resolved as `false` (matching the `MEMBER` default), which never
  violates REQ-11 (never wrongly shows the item) and never regresses
  REQ-9, but does mean REQ-10's `STAFF`-with-exactly-one-membership case
  is not fully covered by this PLAN. **Flagged for backend coordination**,
  not decided here: closing this gap needs a small, additive field (e.g.
  a `role`/`isStaff` boolean) on `OwnGlobalPermissionsDto`
  (`GET /api/staff/permissions`), which is a `knowly-api` contract change
  requiring its own backend SPEC/PLAN entry, not something this frontend
  PLAN can unilaterally add. Until that lands, this is a documented,
  narrow, safe-by-default limitation, not a silent scope decision.

## Testing strategy

- Unit tests (Vitest, mirrors `permissions.service.spec.ts`'s existing
  pattern): `GlobalPermissionsService#has` before/after `fetch()`.
- `staff.guard.spec.ts` (modify/extend): a `STAFF` user granted only
  `TENANT_CREATE` (mocked `GET /api/staff/permissions` response) is
  allowed through; one with neither permission is redirected — this is
  the regression test for REQ-6's bug fix.
- `select-tenant-page.component.spec.ts` (modify): "Create tenant" link
  visibility now driven by the mocked global-permissions response, not
  by whether `listAllTenants()` succeeds.
- `nav-menu.component.spec.ts` (new): each link's visibility per
  permission combination (REQ-2/REQ-3/REQ-5); "switch tenant" link
  presence per membership count (REQ-4).

## Added 2026-08-01 (clarification) — REQ-12/REQ-13, and closing the flagged `isStaffAccount` gap

- **REQ-12 (member never sees "Create tenant"/"leave tenant") is already
  structurally satisfied by the current `nav-menu.component.ts`, no
  production change needed for the common case.** `workspaceGroup()`'s
  "Create tenant" item requires `globalPermissionsService.has('TENANT_CREATE')`
  — a plain `MEMBER`/`MEMBER_ADMIN` always gets an empty
  `GET /api/staff/permissions` response, so `has()` is false regardless
  of tenant role. Its "leave tenant" item requires
  `this.memberships().length === 0` — any real membership (the only way
  a `MEMBER`/`MEMBER_ADMIN` session exists) makes that `false`. This PLAN
  adds only regression tests pinning this (`nav-menu.component.spec.ts`),
  not a code change, for the ordinary case.
- **REQ-13 (staff "Create tenant" hidden while acting inside a tenant) is
  already satisfied for the typical case too.** The "Create tenant" item
  already requires `this.activeTenantService.activeTenantId() === null`
  in addition to `TENANT_CREATE` — a `STAFF` user who has entered a
  tenant session has a non-null `activeTenantId()`, so the item
  disappears the moment they act as that tenant, and reappears once
  `activeTenantId()` returns to `null` (leave-tenant/session end). Only a
  regression test is added.
- **The one case neither of the above covers, and where the previously
  "accepted gap" actually bites**: a `STAFF` user who atypically holds a
  *real* `TenantMembership` (not just an "acting as" session) with
  exactly one membership. Today `canSwitchTenant`/`canLeaveTenant` treat
  `memberships().length === 1` identically to a plain `MEMBER` with one
  membership (both resolve every workspace action to hidden), because
  the frontend has had no signal distinguishing "this length-1 account
  is staff" from "this length-1 account is a plain member" — this is
  exactly the gap the prior PLAN pass flagged and deferred pending a
  backend field. That field now exists (`staff-rbac-split` REQ-9): **this
  PLAN replaces the deferred/accepted-gap section above with a concrete
  fix using it.**
  - **Field name**: the backend `staff-rbac-split` SPEC (REQ-9) commits
    to "a boolean field indicating whether the calling account is a
    staff account at all" on `GET /api/staff/permissions`'s response,
    but neither its SPEC.md nor its PLAN.md name the field yet (`PLAN.md`
    hasn't been updated past REQ-9's addition as of this pass). **This
    PLAN assumes the field is named `isStaffAccount`** (matches this
    codebase's existing `is*`-boolean convention, e.g.
    `ActiveTenantService`'s `active`/`role` shapes) **and flags this as
    an assumption to confirm against the backend PLAN.md/DTO before
    implementation** — if the backend lands a different name (e.g.
    `staffAccount`, `isStaff`), the frontend task below updates the
    interface/response-parsing to match, no other decision here changes.
  - `GlobalPermissionsService`'s response interface and public surface
    gain `isStaffAccount`: `OwnGlobalPermissionsResponse` gets an
    `isStaffAccount: boolean` field; a new `_isStaffAccount` signal +
    `isStaffAccount = this._isStaffAccount.asReadonly()` is added,
    populated by `fetch()` alongside `_permissions`, mirroring the
    existing `permissions` signal's shape exactly (no new pattern).
  - `nav-menu.component.ts`'s `canSwitchTenant`/`canLeaveTenant` gain an
    explicit `length === 1` branch instead of folding it into `!== 1`/
    `=== 0`:
    - `canSwitchTenant`: `true` when `length > 1`, or when `length === 1`
      **and** `globalPermissionsService.isStaffAccount()` (the
      previously-unreachable "staff with exactly one real membership"
      case), `false` otherwise (unchanged for the plain-member `=== 1`
      case, still "already home").
    - `canLeaveTenant`: `true` when `activeTenantId() !== null` **and**
      (`length === 0` **or** (`length === 1` **and** `isStaffAccount()`)) —
      widens the existing `length === 0` clause to also cover a staff
      account's atypical single real membership, without touching the
      `length === 0` (canonical staff, per `DECISIONS.md`) or `length > 1`
      (never eligible to "leave" today, unchanged, out of this PLAN's
      scope) shapes.
  - This closes the gap using the same signal source
    (`GlobalPermissionsService`) already backing every other staff-only
    decision in this component (`TENANT_CREATE`, `viewerIsStaffAdmin`) —
    no new service, no new fetch call, `isStaffAccount` rides the
    existing `fetch()`/response the component already calls in
    `ngOnInit()`.
  - REQ-12's guarantee is not weakened by this: `isStaffAccount()` is
    `false` for every `MEMBER`/`MEMBER_ADMIN` by REQ-9's own definition,
    so the new `length === 1` branches above never fire for a plain
    member — they only ever add visibility for the genuine staff edge
    case, never remove it for a member.

### Added 2026-08-01

- `nav-menu.component.spec.ts` (extend): logo (`brand-wordmark`) renders
  for a logged-in `MEMBER` with zero tenant permissions and zero
  `GlobalPermission`s (REQ-7 regression); "switch tenant" item shown for
  a `MEMBER` with `>1` memberships regardless of tenant permission level
  (REQ-9 regression); "switch tenant" item hidden for a `STAFF`-shaped
  0-membership session with no `GlobalPermission`s at all (REQ-11, the
  actual bug fix); shown for a 0-membership session holding
  `TENANT_ACT_AS_ANY` (REQ-10 first clause); shown for a session with
  exactly one real membership and no `TENANT_ACT_AS_ANY` — accepted as
  ambiguous with the plain-`MEMBER` case per PLAN.md's flagged gap, so
  this case is asserted as hidden (documenting the accepted limitation as
  a real, intentional test, not leaving it uncovered).
- `avatar-menu.component.spec.ts` (extend): logout entry present for a
  logged-in `MEMBER` with zero tenant permissions (REQ-8 regression).
- No new test file for `select-tenant-page.component.ts` — its existing
  `ngOnInit()` branch already covers REQ-10's split without a code
  change, per PLAN.md above; existing coverage stands.

### Added 2026-08-01 (clarification) — REQ-12/REQ-13 and the `isStaffAccount` fix

- `global-permissions.service.spec.ts` (extend): `isStaffAccount()` false
  before `fetch()`; true/false correctly after, mirroring `has()`'s
  existing test shape.
- `nav-menu.component.spec.ts` (extend):
  - Regression, no code change expected: "Create tenant"/"leave tenant"
    absent for a `MEMBER`/`MEMBER_ADMIN` session regardless of tenant
    permission level (REQ-12).
  - Regression, no code change expected: "Create tenant" present for a
    `STAFF` session holding `TENANT_CREATE` while `activeTenantId()` is
    `null`, and absent once `activeTenantId()` is set (REQ-13), then
    present again once it returns to `null`.
  - Real fix, test-first (RED then GREEN): a session with exactly one
    real membership and `isStaffAccount: true` now sees "switch tenant"
    and "leave tenant" (previously hidden, the closed gap) — while the
    same length-1 session with `isStaffAccount: false` (plain member)
    still sees neither, unchanged.
