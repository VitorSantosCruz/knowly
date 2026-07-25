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
| GET | `/api/staff/permissions` | Staff menu filtering + `staffGuard`/create-tenant-link fix (new `GlobalPermissionsService`) |
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
