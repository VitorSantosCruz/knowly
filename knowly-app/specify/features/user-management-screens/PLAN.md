# PLAN — User management screens

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md.

## Architectural decisions

- **Same route, one wrapper component switching on the existing
  `ActiveTenantService` signal** — `/members` keeps its current path
  (no rename: no reason to break the existing nav link/tests/bookmarks)
  but now points to a new `UserManagementPageComponent`
  (`features/user-management/user-management-page.component.ts`) instead
  of `MembersPageComponent` directly. It renders `MembersPageComponent`
  unchanged when `ActiveTenantService.activeTenantId()` is non-null and a
  new `StaffDirectoryPageComponent` when it's null — the same signal
  `welcome-page`/`nav-menu` already use to distinguish "inside a tenant"
  from "staff, no tenant" (REQ-1, REQ-2, REQ-3). `tenantSelectionGuard`
  stays on the route unchanged (it already lets a 0-membership staff
  session through; this feature doesn't change that guard's job of
  blocking a *pending multi-membership selection*, which is orthogonal).
- **New `ActiveTenantService.activeTenantResolved: Signal<boolean>`**,
  set `true` at the end of `fetch()`'s existing subscribe callback (was:
  fire-and-forget `void`, no way for a caller to know the first fetch
  landed). Why: `UserManagementPageComponent` must not decide "tenant
  view vs staff view" before the fetch resolves — `activeTenantId()`
  starts `null` and briefly *is* null both while loading and when
  genuinely staff-with-no-tenant, so branching on it alone during the
  loading window would flash the staff directory before flipping to the
  tenant view. This is a same-shape, additive extension of the existing
  service (still "private signal + public `.asReadonly()`"), not a new
  pattern (Tier 2 — flagged here since no other screen needed a "has the
  first fetch happened" flag yet).
- **No separate route guard for the staff-directory half of REQ-13** —
  only the nav link is gated (see below). `/members` already has no
  `TENANT_MEMBER_MANAGE`-specific guard for the tenant view either (a
  member without that permission hitting `/members` today gets
  `MembersPageComponent`'s existing 403 → `NoAccessStateComponent` path,
  not a redirect); the staff view follows the identical precedent —
  direct navigation without `STAFF_USER_VIEW`/`STAFF_ADMIN` hits
  `StaffDirectoryPageComponent`'s own list call, 403s, and shows the same
  shared no-access state (REQ-12). Consistent handling beats introducing
  a second enforcement style (`staffGuard`'s redirect-on-deny) for what
  is, underneath, the same "hidden entry point, 403-safety-netted body"
  rule the tenant view already follows.
- **REQ-10's "STAFF/STAFF_ADMIN row" ceiling collapses to a single
  viewer-level flag** — the staff directory (REQ-4) only ever lists
  `STAFF`/`STAFF_ADMIN` rows by construction (`GET /api/staff/users`
  never returns anything else), so "hide management actions against a
  `STAFF`/`STAFF_ADMIN` row" is equivalent to "hide management actions
  entirely, for every row, whenever the viewer is `STAFF` and not
  `STAFF_ADMIN`" — there is no row in this screen a non-admin `STAFF`
  viewer is ever allowed to manage. One computed `viewerIsStaffAdmin`
  flag gates create/grant/revoke/assign/unassign uniformly instead of a
  per-row role comparison that would always evaluate the same way.
- **`viewerIsStaffAdmin` is inferred from `GlobalPermissionsService`, no
  new backend endpoint** — the frontend has no signal carrying the
  viewer's own `GlobalRole` today, and adding one is out of scope (no new
  backend endpoint per this SPEC's own "Out of scope"). `StaffController#ownPermissions`
  already special-cases `STAFF_ADMIN`: it returns literally *every*
  `GlobalPermission` value unconditionally, regardless of grants, while a
  plain `STAFF` only ever gets their actually-granted subset. So
  `viewerIsStaffAdmin := ALL_GLOBAL_PERMISSIONS.every(p =>
  globalPermissionsService.has(p))` (checked against the frontend's own
  known subset, not requiring backend/frontend enum parity) is a safe,
  reusable inference. **Known limitation, accepted per this SPEC's own
  security NFR** ("UI-only filtering... never the actual authorization
  boundary"): a plain `STAFF` user who happens to have been granted every
  frontend-known `GlobalPermission` individually would be misclassified
  as an admin client-side; REQ-12's 403 fallback (backend's
  `enforceStaffCeiling` checks the real `GlobalRole`, unconditionally)
  is what actually stops them, exactly as the NFR requires. This is a
  Tier 2 judgment call — no existing precedent for "infer a role
  client-side from a permission-set shape" — written down here rather
  than silently coded.
- **`GlobalPermission` frontend union type gains `STAFF_USER_VIEW`**
  (`core/global-permission.ts`), the one new backend enum value this
  feature actually reads (nav gating, REQ-13). The three other new
  backend values from unrelated already-shipped work
  (`PROFILE_VIEW`/`PROFILE_EDIT`/`DASHBOARD_VIEW_GLOBAL`) are
  deliberately *not* added — nothing in this SPEC references them, and
  adding unused literals would misrepresent what this feature touches.
- **Nav link visibility (REQ-13)**: `nav-menu.component.ts`'s existing
  single `nav.members` entry (currently gated on
  `permissionsService.has('TENANT_MEMBER_MANAGE')` only) becomes
  `permissionsService.has('TENANT_MEMBER_MANAGE') ||
  globalPermissionsService.has('STAFF_USER_VIEW')`. One link, one route,
  two possible reasons to show it — matches the SPEC's "one screen"
  framing exactly; `STAFF_ADMIN` satisfies the second clause too, since
  `ownPermissions()` returns all values for `STAFF_ADMIN` as noted above,
  so no separate `STAFF_ADMIN`-specific clause is needed.
- **New `StaffUserService` (`core/staff-user.service.ts`)**, same shape
  as `MemberService`: thin wrapper, one method per `/api/staff/users*`
  and `/api/staff/access-groups*` endpoint this screen calls.
  `GET /api/staff/permissions` (own-permissions) stays owned by the
  existing `GlobalPermissionsService` — not duplicated here.
- **Detail view as a client-side panel, not a second route** — same
  reasoning `user-management`'s own PLAN already gives for
  `MemberDetailPanelComponent`: one cohesive screen, no stated
  deep-linking requirement. `StaffUserDetailPanelComponent` mirrors
  `MemberDetailPanelComponent`'s structure (direct permissions checkbox
  list, access groups assign/unassign, effective permissions read-only)
  with the `viewerIsStaffAdmin` ceiling flag threaded in to disable its
  controls.
- **Error handling reuses `NoAccessStateComponent`/`ErrorStateComponent`
  exactly as `MembersPageComponent` does** (REQ-12) — same
  `'network' | 'permission-denied' | null` error-state union, same
  catch-and-classify-by-`err.status === 403` pattern, no new shared
  component.
- **No CSRF change** — `/api/staff/**` is not in `SecurityConfig`'s
  `ignoringRequestMatchers` list (verified by reading it) and does not
  need to be added: it's already an authenticated, session-cookie-based
  endpoint, and the app-wide `withXsrfConfiguration` in `app.config.ts`
  attaches the `X-XSRF-TOKEN` header to every mutating call automatically
  — the same mechanism every other non-exempt POST/DELETE in this app
  already relies on. Nothing to do here.

## Emergent decisions

- `StaffUserService.list(email?)` uses Angular's `HttpParams` to attach
  the optional `email` query param rather than string-concatenating the
  URL — same standard approach, no behavior difference, just avoids
  hand-rolling encoding.
- Since `UserManagementPageComponent` calls `ActiveTenantService.fetch()`
  itself (to compute `activeTenantResolved`) and `MembersPageComponent`
  (unchanged, reused as-is) *also* calls `fetch()` in its own `ngOnInit`,
  mounting the tenant view triggers a second, redundant
  `GET /api/tenants/memberships` call. This is accepted as-is per PLAN's
  explicit "`MembersPageComponent`: unchanged, reused as-is" — not
  worth changing a component this SPEC declares out of scope to touch,
  and the endpoint is cheap/idempotent. Noted here since it's a
  small, real behavioral consequence of the wrapper design that the
  PLAN didn't call out explicitly.
- No dedicated route guard was added for the staff-directory view, per
  PLAN's own "No separate route guard" decision — implemented exactly
  as specified, confirmed here rather than treated as an open question.

## Components and routes

- `UserManagementPageComponent` (route `/members`, replaces the direct
  `MembersPageComponent` mapping): loading state, then
  `MembersPageComponent` or `StaffDirectoryPageComponent` depending on
  `ActiveTenantService.activeTenantId()`.
- `MembersPageComponent`: **unchanged**, reused as-is.
- `StaffDirectoryPageComponent`
  (`features/user-management/staff-directory-page.component.ts`): staff
  list + email search input + "create staff user" form (hidden unless
  `viewerIsStaffAdmin` or `globalPermissionsService.has('STAFF_USER_CREATE')`,
  further disabled by nothing else — creation isn't row-targeted, so
  REQ-10's per-row ceiling doesn't apply to it, only the plain permission
  check does) + detail panel toggle, mirroring `MembersPageComponent`'s
  structure.
- `StaffUserDetailPanelComponent`
  (`features/user-management/staff-user-detail-panel.component.ts`):
  direct permissions, global access groups, effective permissions as
  distinct sections (REQ-7); permission toggles and access-group
  assign/unassign disabled when `!viewerIsStaffAdmin` (REQ-10) and always
  available when `viewerIsStaffAdmin` (REQ-11), mirroring
  `MemberDetailPanelComponent`'s re-fetch-on-toggle pattern (no
  optimistic updates, same reasoning as `user-management`'s PLAN).

## Consumed API contracts

Per `knowly-api/specify/features/staff-rbac-split/PLAN.md` and
`knowly-api/specify/features/staff-user-listing/PLAN.md`, controller
verified directly at
`knowly-api/src/main/java/br/com/conectabyte/knowly/tenancy/StaffController.java`:

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| GET | `/api/staff/users` | — | `[{ id, email, globalRole: 'STAFF'\|'STAFF_ADMIN' }]` | 200 / 403 (no `STAFF_USER_VIEW`, not `STAFF_ADMIN`) |
| GET | `/api/staff/users?email=<substring>` | query `email` | same as above, filtered | 200 |
| POST | `/api/staff/users` | `{ email }` | `{ userId, email, directPermissions: [], accessGroups: [], effectivePermissions: [] }` | 201 / 403 (no `STAFF_USER_CREATE`, ceiling n/a — target is always fresh `STAFF`) |
| GET | `/api/staff/users/{userId}/permissions` | — | `{ userId, email, directPermissions, accessGroups: [{id,name}], effectivePermissions }` | 200 / 403 |
| POST | `/api/staff/users/{userId}/permissions` | `{ permission }` | — | 200 / 403 |
| DELETE | `/api/staff/users/{userId}/permissions/{permission}` | — | — | 200 / 403 |
| GET | `/api/staff/access-groups` | — | `[{ id, name }]` | 200 / 403 |
| POST | `/api/staff/access-groups` | `{ name }` | `{ id, name }` | 200 / 403 |
| POST | `/api/staff/access-groups/{id}/permissions` | `{ permission }` | — | 200 / 403 |
| POST | `/api/staff/users/{userId}/access-groups/{id}` | — | — | 200 / 403 |
| DELETE | `/api/staff/users/{userId}/access-groups/{id}` | — | — | 200 / 403 |

Already owned by `GlobalPermissionsService`, not duplicated in
`StaffUserService`:

| Method | Path | Response |
|---|---|---|
| GET | `/api/staff/permissions` | `{ permissions: GlobalPermission[] }` (own permissions; all values if `STAFF_ADMIN`) |

Note: `StaffUserDetailDto`'s field is `userId` (not `membershipId`) — the
detail panel/service types use `userId` to avoid silently drifting from
the actual DTO shape.

## State and data

- `ActiveTenantService`: adds `activeTenantResolved: Signal<boolean>`
  (see above); `activeTenantId`/`activeTenantName` unchanged.
- `GlobalPermissionsService`: unchanged, reused; `GlobalPermission` union
  gains `STAFF_USER_VIEW`.
- `StaffUserService`: no local signal state — same stateless-wrapper
  shape as `MemberService`; state lives in the consuming components.
- `UserManagementPageComponent`: no local error state of its own — it's
  a pure switch, errors belong to whichever child view is active.
- `StaffDirectoryPageComponent`: signals for `staffUsers`,
  `accessGroups`, `searchTerm`, `selectedUserId`, `loading`, `error`
  (`'network' | 'permission-denied' | null`) — same shape as
  `MembersPageComponent`.
- `viewerIsStaffAdmin`: a `computed()` in `StaffDirectoryPageComponent`
  (and passed as an `@Input` to `StaffUserDetailPanelComponent`) reading
  `GlobalPermissionsService.permissions()` — not a new service, this is
  page-local derived state, matching how `canSwitchTenant` in
  `nav-menu.component.ts` is a local `computed()` over injected service
  signals rather than its own service.
- `GlobalRole` frontend type (`'STAFF' | 'STAFF_ADMIN'`), added next to
  `StaffUserSummaryDto`'s frontend equivalent in `staff-user.service.ts`
  — small, stable literal union, same reasoning `user-management`'s PLAN
  already gives for hand-writing the `Permission` union instead of a
  shared-contract-generation step.

## Dependencies

None new.

## Testing strategy

- `active-tenant.service.spec.ts`: `activeTenantResolved` is `false`
  before `fetch()`, `true` after (including the "preserved value, no
  active membership found" branch already tested there).
- `staff-user.service.spec.ts`: one `HttpTestingController` assertion per
  endpoint in the contract table above.
- `user-management-page.component.spec.ts`: renders nothing conclusive
  while `!activeTenantResolved()`; renders `MembersPageComponent` when
  `activeTenantId()` is non-null; renders `StaffDirectoryPageComponent`
  when it's null.
- `staff-directory-page.component.spec.ts`: renders the staff list on
  load; entering a search term calls `GET /api/staff/users?email=` and
  refreshes the list; submitting create calls `StaffUserService.create`
  and refreshes; a 403 on list/create renders `NoAccessStateComponent`;
  the create form/button is absent or disabled when
  `viewerIsStaffAdmin()` is `false` and the viewer lacks
  `STAFF_USER_CREATE`.
- `staff-user-detail-panel.component.spec.ts`: renders direct
  permissions/access groups/effective permissions as distinct sections;
  toggling a permission calls grant/revoke and re-fetches; assigning/
  unassigning an access group updates the shown state; every
  management control is `disabled`/absent when `viewerIsStaffAdmin` is
  `false`, present and enabled when `true`; a 403 from any action (a
  grant/revoke succeeding client-side-eligibility-wise but rejected
  server-side) renders `NoAccessStateComponent` (REQ-12).
- `nav-menu.component.spec.ts`: the `members` nav entry appears for
  `TENANT_MEMBER_MANAGE` (existing case, unchanged), for `STAFF_USER_VIEW`
  alone (new), and for `STAFF_ADMIN` (via the "all permissions" response,
  new); absent for neither.
