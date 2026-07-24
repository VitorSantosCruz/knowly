# PLAN — Select tenant

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- `ActiveTenantService` (existing, `core/active-tenant.service.ts`)
  gains `list()` (plain `GET /api/tenants/memberships`, no signal
  mutation — used by the guard and this screen without disturbing
  `fetch()`'s existing state) and `selectTenant(tenantId, tenantName)`
  (`POST /api/tenants/active`, then updates the active-tenant signals
  locally so callers don't need a redundant refetch).
- `tenantSelectionGuard` (`core/tenant-selection.guard.ts`, new): a
  `CanActivateFn` that calls `list()`; if some membership is already
  active, or there's at most one membership, allows navigation;
  otherwise redirects to `/select-tenant` (REQ-1). Applied to every
  tenant-scoped route (`dashboard`, `members`, `conversations`,
  `articles`) in `app.routes.ts`.
- `SelectTenantPageComponent` (new): lists memberships from `list()`;
  clicking one calls `selectTenant()` then `router.navigateByUrl('/dashboard')`.
  No permission/access-denied handling needed — every membership
  returned is one the user already holds.

## Consumed API contracts

Both already exist in `knowly`'s `tenancy` feature:

- `GET /api/tenants/memberships` → `200
  Array<{ tenantId, tenantName, role, active }>`
- `POST /api/tenants/active` (`{ tenantId }`) → `200`

## Package/file structure

- `core/active-tenant.service.ts`: add `list()`, `selectTenant()`.
- `core/tenant-selection.guard.ts` (+ `.spec.ts`)
- `features/select-tenant/select-tenant-page.component.ts` (+ `.spec.ts`)
- `app.routes.ts`: register `/select-tenant`; add `canActivate:
  [tenantSelectionGuard]` to every tenant-scoped route.

## Testing strategy

- `ActiveTenantService`: `list()` doesn't mutate the active-tenant
  signals; `selectTenant()` posts the choice and updates them.
- `tenantSelectionGuard`: allows navigation when a membership is
  already active or there's only one; redirects to `/select-tenant`
  when multiple exist and none is active.
- `SelectTenantPageComponent`: lists memberships; selecting one posts
  the choice and navigates to `/dashboard`.
