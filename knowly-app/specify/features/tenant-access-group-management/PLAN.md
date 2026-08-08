# PLAN — tenant-access-group-management

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Consumes
> `knowly-api/specify/features/tenant-access-group-bulk-and-delete/PLAN.md`
> for the two new backend contracts (bulk-assign, deletion-confirmation-token
> + delete).

## Architectural decisions

- **One new page component, `TenantAccessGroupManagementPageComponent`, at
  `src/app/features/access-groups/tenant-access-group-management-page.component.ts`
  — not a variant folded into the existing (staff/global)
  `AccessGroupManagementPageComponent`.** The two screens share layout shape
  (list + selected-group detail panel) but talk to entirely different
  services, DTOs, and permissions (`MemberService`/tenant `AccessGroup`/
  `Permission` vs. `StaffUserService`/`GlobalAccessGroup`/
  `GlobalPermission`), exactly the distinction the SPEC's "Out of scope"
  section draws. Forking the component avoids threading a
  staff-vs-tenant branch through every method of an already-committed
  component; the global screen is explicitly untouched (SPEC, out of
  scope).
- **The checkbox-matrix UI for bulk-assign is a new standalone component,
  `MemberAccessGroupAssignmentComponent`
  (`src/app/features/access-groups/member-access-group-assignment.component.ts`),
  consumed from both the new tenant screen and
  `member-detail-panel.component.ts` — not duplicated, not built
  screen-local-only.** REQ-9 (bulk-assign from this new screen, member ⇒
  groups direction) and the existing single assign/unassign in
  `member-detail-panel.component.ts` (group ⇒ member direction, one row at
  a time today) are the same underlying action (`PUT` a member's set of
  group memberships) viewed from two directions. Rather than building the
  matrix once for this screen and leaving `member-detail-panel.component.ts`
  on its old one-at-a-time flow (which the SPEC's dependency section
  explicitly says "N-sequential-calls was explicitly rejected... this
  frontend feature cannot ship... until that backend endpoint exists" —
  implying member-detail-panel's own bulk case, if ever added, would need
  the same component), a single reusable component takes `tenantId`,
  `membershipId`, the full `AccessGroup[]` universe, and the member's
  currently-assigned group ids as inputs, emits a `submit` event with the
  full selected-id set, and owns nothing else (no HTTP calls of its own —
  the parent still owns the service call and the re-fetch-after-submit
  behavior REQ-10 requires). This SPEC only requires wiring it into the new
  screen; `member-detail-panel.component.ts`'s own single-assign flow is
  untouched (not asked for, not in scope) — the shared component exists so
  a future SPEC can wire it in without rebuilding the matrix, not because
  this feature changes that panel today.
- **The member roster (REQ-3: "who's in this group" / "who isn't") is
  derived from one `MemberService.list(tenantId)` call plus one
  `MemberService.getDetail(tenantId, membershipId)` call per member — the
  same N+1 shape `member-detail-panel.component.ts` already accepts for a
  single member, not a new per-group roster endpoint.** This satisfies the
  SPEC's own performance note ("a tenant-scoped member roster can be
  derived from data already fetchable per-member... if the PLAN opts to
  reuse `getMember`/`listMembers`") rather than inventing a bulk roster
  endpoint that isn't in the backend PLAN's contract. This is *not* the
  same N+1 the global screen's own doc-comment calls out and accepts for
  its "expected small size" — a tenant's member list can legitimately be
  large, so this decision is flagged as a Tier 2 call here, distinct from
  copying that precedent uncritically: mitigated by fetching all members'
  details **once per group selection** (`forkJoin`, mirroring the global
  screen's exact `forkJoin` shape) and caching the resulting
  `Map<membershipId, MemberDetail>` for the lifetime of that selection —
  switching groups without switching tenants does not require a second
  full-roster fetch if the map is keyed by membership and merely
  re-filtered by the newly selected group's id, so the cost is paid once
  per screen visit, not once per group click. If a real tenant's member
  count makes even that one-time cost too expensive, that's a backend
  roster-endpoint SPEC, out of scope here (matches SPEC's own "that shape
  is a PLAN decision, not fixed here" framing — this is the decision).
- **A new route guard, `tenantAccessGroupManagementGuard`, gates
  `/tenants/access-groups`, layered after `tenantSelectionGuard` — not
  `tenantSelectionGuard` alone.** REQ-2 requires the screen not open *and
  not issue any list/detail request* for a caller lacking
  `TENANT_ACCESS_GROUP_VIEW` — stronger than `/members`'s existing
  pattern, where `tenantSelectionGuard` alone lets the route open and the
  component's own list call 403s reactively into `NoAccessStateComponent`.
  Precedent search confirms no existing tenant-scoped route is guarded by
  anything beyond `tenantSelectionGuard` — this is the first one, mirroring
  `accessGroupManagementGuard`'s shape (a `CanActivateFn` hitting the
  permissions endpoint directly and checking one specific permission)
  rather than `staffGuard`'s any-of-several-permissions shape, since REQ-2
  names exactly one permission. It calls `GET /api/tenants/permissions`
  directly (same endpoint `PermissionsService.fetch()` wraps) instead of
  reading `PermissionsService.permissions()` synchronously, because a
  guard runs before any component (and therefore before nav-menu's
  incidental `permissionsService.fetch()` call) has necessarily populated
  it yet — same reasoning `accessGroupManagementGuard` already uses for
  the global screen. Redirects to `/select-tenant` on a missing
  permission, matching `accessGroupManagementGuard`'s existing redirect
  target choice (there is no more specific "denied" route in this app).
  This is a **novel decision with no exact precedent** (existing
  tenant-scoped routes only ever needed the "is there an active tenant"
  check) — see `DECISIONS.md` entry below.
- **`member.service.ts` gains four new methods**, not a new service — it
  is already the tenant `AccessGroup`/member HTTP boundary
  (`listAccessGroups`, `createAccessGroup`, `assignAccessGroup`,
  `unassignAccessGroup`, `generateAccessGroupUnassignmentToken` already
  live there); adding the new bulk-assign, delete, and delete-token calls
  there keeps one HTTP layer instead of splitting tenant access-group
  calls across two services (per the task's own recommendation and this
  codebase's "state lives in services" convention — no new signal-holding
  service is needed either, since the new screen's own list/selection
  state is page-local, matching the global screen's page-local
  `signal()`s rather than a new shared service).
- **`grantAccessGroupPermission` gets wired into the new screen only, no
  new service method** — `POST
  /api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions`
  already exists server-side (SPEC's Dependencies section) but
  `member.service.ts` has no client for it yet; this PLAN adds
  `grantAccessGroupPermission(tenantId, accessGroupId, permission)` to
  `member.service.ts` alongside the other four, since REQ-6 needs it and
  no existing method covers it — five new methods total, not four.

## Components and routes

- **New**: `TenantAccessGroupManagementPageComponent`
  (`features/access-groups/tenant-access-group-management-page.component.ts`)
  — top-level page: create-group form (REQ-4/5), `SharedListComponent` of
  groups (REQ-1), selected-group detail panel (REQ-3/12) containing:
  current roster with per-member unassign (REQ-8, `ConfirmDialogComponent`
  + existing `generateAccessGroupUnassignmentToken`/`unassignAccessGroup`),
  a permission-grant control (REQ-6), and, per candidate member not yet in
  the group, an entry point into `MemberAccessGroupAssignmentComponent`
  for single- or multi-group assignment (REQ-7/9), and a delete-group
  action gated behind `ConfirmDialogComponent` + the new
  deletion-confirmation-token flow (REQ-13/14/15).
- **New**: `MemberAccessGroupAssignmentComponent`
  (`features/access-groups/member-access-group-assignment.component.ts`) —
  presentational checkbox-matrix; `@Input() allGroups: AccessGroup[]`,
  `@Input() assignedGroupIds: Set<number>`, `@Output() submitted =
  EventEmitter<number[]>()` (the full desired-selection id set — parent
  decides single-call-vs-batch based on how many are checked, so the
  component itself doesn't need to know about the two different backend
  endpoints).
- **New**: `tenant-access-group-management.guard.ts`
  (`core/tenant-access-group-management.guard.ts`), exporting
  `tenantAccessGroupManagementGuard`.
- **Modified**: `app.routes.ts` — new route:
  ```ts
  {
    path: 'tenants/access-groups',
    loadComponent: () =>
      import('./features/access-groups/tenant-access-group-management-page.component')
        .then((m) => m.TenantAccessGroupManagementPageComponent),
    canActivate: [tenantSelectionGuard, tenantAccessGroupManagementGuard],
  },
  ```
  (guard order matters: `tenantSelectionGuard` establishes there *is* an
  active tenant before `tenantAccessGroupManagementGuard`'s permission
  check, which needs one, runs).
- **Modified**: `nav-menu.component.ts` — add the link under the tenant
  section, gated the same way the existing `/members` link is (checked
  against `permissionsService.has('TENANT_ACCESS_GROUP_VIEW')`, mirroring
  how the staff access-groups link is gated on
  `STAFF_PERMISSION_MANAGE` — confirmed necessary per this repo's own
  recent history, `96122fc`, adding exactly this kind of missing nav
  entry for a sibling feature).
- **Not modified**: `member-detail-panel.component.ts` — its existing
  single assign/unassign flow is left as-is (out of scope); it does not
  yet consume `MemberAccessGroupAssignmentComponent`.

## Consumed API contracts

Existing (`TenantController.java`, already wired in `member.service.ts`):

| Method | Path | Used for |
|---|---|---|
| `GET` | `/api/tenants/{tenantId}/access-groups` | REQ-1 list |
| `POST` | `/api/tenants/{tenantId}/access-groups` | REQ-4 create |
| `GET` | `/api/tenants/{tenantId}/members` | REQ-3 roster (member universe) |
| `GET` | `/api/tenants/{tenantId}/members/{membershipId}` | REQ-3 roster (per-member group membership, via `MemberDetail.accessGroups`) |
| `POST` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` | REQ-7 single assign |
| `DELETE` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}` | REQ-8 unassign |
| `POST` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}/deletion-confirmation-token` | REQ-8 unassign token |
| `POST` | `/api/tenants/{tenantId}/access-groups/{accessGroupId}/permissions` | REQ-6 grant permission (no existing client method — added below) |

New, per `knowly-api/specify/features/tenant-access-group-bulk-and-delete/PLAN.md`:

| Method | Path | Request | Response | Used for |
|---|---|---|---|---|
| `POST` | `/api/tenants/{tenantId}/members/{membershipId}/access-groups:batch` | `{ accessGroupIds: number[] }` | `204` | REQ-9/10/11 bulk assign |
| `GET` | `/api/tenants/{tenantId}/access-groups/{accessGroupId}/deletion-confirmation-token` | none | `{ word: string }` | REQ-13 delete token |
| `DELETE` | `/api/tenants/{tenantId}/access-groups/{accessGroupId}` | `{ word: string }` | `204` | REQ-13/14 delete |

Error handling for all of the above follows the app-wide convention already
in `access-group-management-page.component.ts`/`members-page.component.ts`:
`catchError` maps `err.status === 403` → `'permission-denied'` (REQ-5/11/15,
`NoAccessStateComponent`), anything else → `'network'` (REQ-16,
`ErrorStateComponent`); a `400` from the batch endpoint (REQ-10, partial
validation failure) is treated as the network/generic-error branch since it
is not a permission denial, and per REQ-10 no optimistic UI state is set —
the roster/candidate view is always re-derived from a fresh
`MemberDetail`/`list()` re-fetch after any assign/unassign/batch call
completes (success or failure), never patched in-memory.

## New `member.service.ts` methods

```ts
batchAssignAccessGroups(
  tenantId: number,
  membershipId: number,
  accessGroupIds: number[],
): Observable<void> {
  return this.http.post<void>(
    `/api/tenants/${tenantId}/members/${membershipId}/access-groups:batch`,
    { accessGroupIds },
  );
}

generateAccessGroupDeletionToken(tenantId: number, accessGroupId: number): Observable<string> {
  return this.http
    .get<{ word: string }>(
      `/api/tenants/${tenantId}/access-groups/${accessGroupId}/deletion-confirmation-token`,
    )
    .pipe(map((res) => res.word));
}

deleteAccessGroup(tenantId: number, accessGroupId: number, word: string): Observable<void> {
  return this.http.delete<void>(`/api/tenants/${tenantId}/access-groups/${accessGroupId}`, {
    body: { word },
  });
}

grantAccessGroupPermission(
  tenantId: number,
  accessGroupId: number,
  permission: Permission,
): Observable<void> {
  return this.http.post<void>(
    `/api/tenants/${tenantId}/access-groups/${accessGroupId}/permissions`,
    { permission },
  );
}
```

Note the delete-token call is `GET`, not the `POST` shape every other
`generate*Token` method in this file uses (`generateRemovalToken`,
`generateAccessGroupUnassignmentToken`, etc., all `POST {}`) — the backend
PLAN pins this specific endpoint as `GET`, so the client method follows the
contract as written rather than forcing consistency with this file's other
(`POST`-shaped) token endpoints.

## State and data

- Page-local `signal()`s on `TenantAccessGroupManagementPageComponent`,
  mirroring `AccessGroupManagementPageComponent`'s exact shape: `groups`,
  `groupsLoading`, `error`, `newGroupName`, `selectedGroup`,
  `candidatesLoading`, `memberDetails: Map<number, MemberDetail>` (this
  screen's equivalent of `candidateDetails`), `pendingUnassign`,
  `pendingDelete`, plus one `computed()` each for "current roster" and
  "assignable candidates" filtered from `memberDetails()` against
  `selectedGroup()?.id`, matching `members`/`assignable` in the reference
  component.
- No new shared/injectable state service — this screen's data is
  page-scoped and short-lived (matches `PROJECT_STATUS.md`'s existing
  precedent of page-local signals for list/detail screens like this one
  and its global counterpart; `PermissionsService` remains the only
  shared-state consumer, read-only, for per-action button gating).
- `tenantId` is read the same way `member-detail-panel.component.ts` /
  `members-page.component.ts` already do (via `ActiveTenantService`,
  confirmed as the correct source given the "staff session never gets a
  real `TenantMembership`" edge case already documented — this screen
  never needs "is there a membership," only "what's the active tenant
  id," which `ActiveTenantService` already answers correctly for a staff
  session acting inside a tenant).

## Dependencies

None. No new `package.json` entry — reuses `SharedListComponent`,
`ConfirmDialogComponent`, `NoAccessStateComponent`, `ErrorStateComponent`,
existing RxJS operators, existing `member.service.ts`/`permissions.service.ts`.

## Testing strategy

- `tenant-access-group-management.guard.spec.ts`: permission present →
  `true`; permission absent → `UrlTree` to `/select-tenant`; confirms the
  guard calls `/api/tenants/permissions` and checks
  `TENANT_ACCESS_GROUP_VIEW` specifically (not any other permission in the
  response).
- `tenant-access-group-management-page.component.spec.ts` (Red→Green per
  case): renders group list on load; permission-denied on list load →
  `NoAccessStateComponent`, no further requests fired; create form
  gated/submits/refreshes list; selecting a group loads roster via
  `list()` + per-member `getDetail()` (assert call count == 1 +
  member-count, not per-group-click); switching groups without leaving the
  screen re-filters cached `memberDetails` rather than re-fetching (assert
  `getDetail` call count does not increase on a second `selectGroup`
  within the same visit); grant-permission happy path and 403 path;
  single-assign and bulk-assign (via `MemberAccessGroupAssignmentComponent`
  submit) both re-fetch roster after success, never patch in-memory
  (REQ-10); bulk-assign 400 → generic error state, roster re-fetched
  showing only backend-confirmed state; unassign confirm-dialog round trip
  (happy path, wrong-word retry, 403); delete-group confirm-dialog round
  trip (happy path removes group from list and clears selection if it was
  selected — REQ-14; wrong-word retry; 403 leaves group in list).
- `member-access-group-assignment.component.spec.ts`: renders one checkbox
  per input group, pre-checked for ids in `assignedGroupIds`; toggling and
  submitting emits exactly the checked id set, including the single-id
  case (REQ-7's existing single-assign path can reuse this component's
  output without special-casing "exactly one").
- `member.service.spec.ts`: additions for the four new methods — correct
  method/URL/body per the table above, including the `GET` token call's
  distinct shape from this file's other `POST`-shaped token methods.
