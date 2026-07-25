# PLAN — Tenant creation (staff)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- No backend/API change — `POST /api/tenants` already exists and is
  staff-enforced server-side (tenancy REQ-10). This feature is
  frontend-only.
- No explicit "isStaff" flag exists anywhere in the API today. The
  frontend already infers staff-ness by whether `GET /api/tenants`
  (`listAllTenants()`) succeeds — `select-tenant-page.component.ts` only
  calls it as a fallback when the user has zero memberships, and treats
  a failure (403) as "show nothing". This feature reuses that same
  signal rather than inventing a new backend concept: a new
  `staffGuard` route guard calls `listAndTenantsService.listAllTenants()`
  and allows navigation only on success.
- New standalone component `TenantCreatePageComponent` in
  `src/app/features/tenant-create/`, following the existing page-form
  conventions used by `members-page.component.ts` (reactive form + inline
  field errors + submit-error banner).

## Components and routes

- New route `/tenants/new` → `TenantCreatePageComponent`, guarded by
  new `staffGuard` (`src/app/core/staff.guard.ts`).
- `select-tenant-page.component.ts`: when the staff fallback branch
  (listAllTenants succeeds) is reached, render a "create tenant" link to
  `/tenants/new`, alongside the existing tenant list/empty state.
- `app.routes.ts`: add the new route entry.

## Consumed API contracts

- `POST /api/tenants` — body `{ name: string, adminEmail: string }`,
  204/200 empty body on success. Errors: 400 (validation), 403
  (non-staff caller — defense in depth, guard already blocks this in
  normal usage), 409 (e.g. admin email already in a conflicting state,
  per backend `TenantService`). All non-2xx are treated uniformly by the
  form as "show inline error, keep field values."
- `GET /api/tenants` (existing `listAllTenants()`) — reused unmodified
  by the new `staffGuard` as the staff-detection signal.

## State and data

- `ActiveTenantService` gains `createTenant(name, adminEmail): Observable<void>`
  wrapping the POST above — same pattern as `selectTenant`.
- `TenantCreatePageComponent` follows `members-page.component.ts`'s
  existing form convention: plain signals bound to inputs (no
  `@angular/forms` module), a template `(submit)` handler doing minimal
  presence/email-shape validation before calling the service, an
  `errorMessage` signal for the inline banner on failure, and a
  `submitting` signal to disable the button mid-request.
- On success, navigate to `/select-tenant` (REQ-4).

## Dependencies

- None new — Angular reactive forms and HttpClient are already in use
  elsewhere (`members-page.component.ts`).

## Testing strategy

- `staff.guard.spec.ts`: allows navigation when `listAllTenants()`
  succeeds, redirects to `/select-tenant` when it errors (mirrors
  `tenant-selection.guard.spec.ts`'s style).
- `active-tenant.service.spec.ts`: add a case for `createTenant()`
  posting to `/api/tenants` with the right body.
- `tenant-create-page.component.spec.ts`:
  - renders the form; submit disabled/blocked with empty fields
    (REQ-1, client-side validation before hitting the API).
  - successful submit calls the service and navigates to
    `/select-tenant` (REQ-4).
  - service error surfaces an inline error message and preserves
    entered values (REQ-5).
- `select-tenant-page.component.spec.ts`: add a case asserting the
  "create tenant" link appears only when `listAllTenants()` succeeds
  (staff path), not when the user already has memberships.
