# PLAN — User management

## Architectural decisions

- New `ActiveTenantService` (`core/active-tenant.service.ts`): wraps
  `GET /api/tenants/memberships`, exposes the currently active tenant's
  id/name as a signal. This didn't exist before this feature — no prior
  screen needed to know the numeric active tenant id (the backend scopes
  everything by session; the frontend never had to ask). Every
  tenant-scoped call this feature makes (`/api/tenants/{tenantId}/...`)
  needs it. Backed by the `active: boolean` field the tenancy backend
  now returns per membership (added alongside this feature, see
  `knowly/specify/features/tenancy/PLAN.md`).
- `MemberService` (`core/member.service.ts`): wraps every
  `/api/tenants/{tenantId}/members*` and `/access-groups*` endpoint.
- One route `/members` (`MembersPageComponent`), listing members, with a
  detail view as a client-side toggle/panel rather than a second route —
  same reasoning as the login feature's step-based wizard: this is one
  cohesive screen, not independently linkable pages (no deep-linking
  requirement stated).
- Permission toggles in the detail view are optimistic-free: each toggle
  disables itself, calls the grant/revoke endpoint, and re-fetches the
  member detail on response (simplicity over an optimistic-update
  layer — this screen isn't performance-sensitive enough to justify the
  extra state-reconciliation complexity).

## Emergent decisions

<Filled in during implementation, if anything changes from the plan
above.>

## Components and routes

- `MembersPageComponent` (route `/members`): member list + inline
  "add member" form + access-group management panel.
- `MemberDetailPanelComponent`: shown when a member row is selected;
  direct permissions (checkboxes per `Permission`), access groups
  (assign/unassign), effective permissions (read-only list).
- `PermissionDeniedStateComponent`: shared presentational component for
  REQ-7, reused instead of each action hand-rolling its own 403 message.

## Consumed API contracts

Per `knowly/specify/features/tenancy/PLAN.md`, all under
`/api/tenants/{tenantId}/...`, `tenantId` resolved via
`ActiveTenantService`:

- `GET /api/tenants/memberships` → `[{ tenantId, tenantName, role, active }]`
- `GET /api/tenants/{tenantId}/members` → `[{ membershipId, email, role }]`
- `POST /api/tenants/{tenantId}/members` `{ email, role }` → `200`
- `DELETE /api/tenants/{tenantId}/members/{membershipId}` → `200`
- `GET /api/tenants/{tenantId}/members/{membershipId}` →
  `{ membershipId, email, role, directPermissions, accessGroups, effectivePermissions }`
- `POST/DELETE /api/tenants/{tenantId}/members/{membershipId}/permissions[/{permission}]`
- `GET/POST /api/tenants/{tenantId}/access-groups` → `[{ id, name }]`
- `POST /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions`
- `POST/DELETE /api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}`

All CSRF-exempt already (`/api/tenants/**`, per the backend's
`SecurityConfig`).

## State and data

- `ActiveTenantService`: `activeTenantId: Signal<number | null>`,
  `activeTenantName: Signal<string | null>`, fetched once per app
  session (same pattern as `OnboardingService`).
- `MembersPageComponent`: signals for `members`, `accessGroups`,
  `selectedMembershipId`, `loading`, `error` (`'network' | 'permission-denied' | null`).
- The `Permission` union type is a hand-written TypeScript literal union
  mirroring the backend enum (`TENANT_MEMBER_MANAGE`, `ARTICLE_VIEW`,
  `ARTICLE_CREATE`, `ARTICLE_EDIT`, `ARTICLE_DELETE`) — small and stable
  enough not to warrant a shared-contract-generation step.

## Dependencies

None new.

## Testing strategy

- `active-tenant.service.spec.ts`: fetch, exposes the active tenant's
  id/name from the marked entry.
- `member.service.spec.ts`: one HTTP assertion per endpoint via
  `HttpTestingController`.
- `members-page.component.spec.ts`: renders the list; add-member refreshes
  it; remove-member removes the row; opening a member shows the detail
  panel; a 403 on any action shows `PermissionDeniedStateComponent`.
- `member-detail-panel.component.spec.ts`: toggling a permission calls
  grant/revoke and re-fetches; assigning/unassigning an access group
  calls the right endpoint.
