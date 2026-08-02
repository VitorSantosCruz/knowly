# PLAN — staff-members-management-redesign

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Consumes backend contracts from
> `knowly-api/specify/features/staff-rbac-management-operations/PLAN.md`
> (demote/promote/hard-delete/batch-permission endpoints),
> `knowly-api/specify/features/user-role-selection-at-creation/PLAN.md`
> (creation `role` field — informs display only, creation flow is out of
> this SPEC's scope), `knowly-api/specify/features/
> permission-granularity-model/PLAN.md` (the real `GlobalPermission`/
> `Permission` enum values the translation map must cover), and
> `knowly-api/specify/features/deletion-confirmation-token/SPEC.md` (the
> security-phrase mechanism, already consumed today via
> `ConfirmDialogComponent`).

## Gap resolved: promotion UI (amendment, 2026-08-02)

SPEC.md now specifies promotion (REQ-7b through REQ-7e), confirmed by the
product owner: a `STAFF` target's detail view gets a "promover a
`STAFF_ADMIN`" action, a `MEMBER` target's detail view gets a "promover a
`MEMBER_ADMIN`" action, gated by REQ-7a's visibility rule (reused, not
reinvented), never disabled on a last-admin basis (REQ-7d — promotion has
no floor check server-side either, per
`staff-rbac-management-operations/PLAN.md`'s "Promotion never calls
either lock/count path"). This is implemented alongside the demote button
in "Detail screen reorganization" below, consuming the promote endpoints
already in the "Consumed API contracts" table
(`POST .../{userId}/promote`, `POST .../{membershipId}/promote`) — no new
backend work needed.

## Architectural decisions

- **New shared component `SharedListComponent`/`app-shared-list`**
  (`knowly-app/src/app/shared/shared-list/shared-list.component.ts`),
  generic over a row type `T`, implementing the full Ink & Signal
  translation in SPEC.md verbatim (header/count, optional toolbar,
  checkbox column, sortable column headers, status pill slot, row-actions
  slot, loading/empty/no-results/error states, responsive collapse,
  a11y). Standalone, `ChangeDetectionStrategy.OnPush`. *Why a generic
  component over per-screen duplication:* REQ-1/REQ-2 explicitly require
  one shared component reused by staff directory, tenant members, and
  (future, noted but out of scope) tenant list — three screens with
  different row shapes (staff user, tenant member, tenant) but an
  identical structural contract, which is exactly what a generic
  `<T>` component + column-definition input is for, matching this
  codebase's existing "configurable, not screen-specific" component
  precedent (`ProfileFieldsFormComponent`'s field-array input shape).
- **Columns and row-actions are configured via plain input objects, not
  `ng-content`/content-projection.** `columns: input.required<SharedListColumn<T>[]>()`
  where `SharedListColumn<T> = { key: string; headerKey: string;
  sortable?: boolean; render: (row: T) => string | { pillKey: string;
  colorClass: string } }` (a discriminated union so status-pill columns
  render via the shared badge pattern and plain-text columns render via
  a string, per SPEC's status-column spec). Row actions are
  `rowActions: input<SharedListRowAction<T>[]>([])` where each action is
  `{ icon: Type<unknown>; labelKey: string; variant: 'secondary' |
  'danger'; disabled?: (row: T) => boolean; disabledReasonKey?: (row: T)
  => string | null; onClick: (row: T) => void }`. *Why input objects over
  projected templates (Tier 2, no exact precedent in this codebase):*
  this codebase has no existing generic "table with projected cell
  templates" component to mirror, and `TicketStatusBadgeComponent`/
  `buttonClass()`'s existing shape is "map + render function," not
  Angular `TemplateRef` projection — staying consistent with that data-
  driven pattern (versus introducing `ng-template`/`ContentChild`
  querying, a heavier pattern this app has never used) keeps the
  component's contract simple to test with Vitest (assert on the
  `columns`/`rowActions` arrays and rendered DOM, no `TemplateRef`
  mocking) and keeps sort/search/pagination logic entirely inside the
  shared component rather than re-derived per consumer.
- **Selection, sorting, search, and pagination state live inside
  `SharedListComponent` itself, not in the parent** — `selectedIds =
  signal<Set<string | number>>()`, `sortState = signal<{key: string; dir:
  'asc'|'desc'} | null>()`, `searchTerm = signal('')`, `page =
  signal(1)`. The component emits `selectionChange`/`sortChange` outputs
  for parents that need to react (e.g. enabling a bulk-action toolbar
  button), but owns the raw UI state itself. *Why:* per the frontend
  architecture rule ("state lives in services as signals, not
  components") this is the one explicit exception the rule itself
  implies — that guidance targets *shared/cross-screen* state
  (auth, permissions, active tenant); pure UI-interaction state scoped to
  one component instance (which row is selected in *this* table render)
  has no cross-component consumer and doesn't belong in a service, matching
  existing precedent (`ConfirmDialogComponent`'s own `word`/`typed`/
  `loading` signals are component-local, not service-owned, for the same
  reason).
- **Data fetching/pagination against the backend stays the parent's
  responsibility** — `SharedListComponent` never calls `HttpClient`
  itself; it receives `rows: input<T[]>()`, `loading: input<boolean>()`,
  `error: input<'network' | 'permission-denied' | null>()`, and (for
  server-side pagination) `totalCount: input<number>()` +
  `pageChange: output<number>()`. *Why:* keeps the existing
  `StaffUserService`/tenant member service ownership of HTTP calls
  intact (per the "API calls via services" convention) and lets each
  screen decide client-side vs. server-side pagination without the
  shared component needing to know which backend endpoint it's fed by.
  This SPEC/PLAN does not add server-side pagination to any endpoint
  that doesn't already have it — `StaffUserService.list`/tenant members
  list are unpaginated today (confirmed by inspection) and stay that way;
  `SharedListComponent` paginates client-side over the full fetched set
  in that case (out-of-scope note in SPEC: "pagination of any list beyond
  what's already specified" is not addressed here — the component
  supports both modes but this feature only exercises client-side).
- **`StaffDirectoryPageComponent`/`MembersPageComponent` are rewritten
  to consume `SharedListComponent`**, each supplying its own
  `columns`/`rowActions` config and its existing `StaffUserService`/
  tenant-member-service calls for data — no change to either service's
  HTTP surface beyond the new endpoints below. Row identity column
  (avatar + name/email), a role-badge status column (`STAFF_ADMIN`/
  `STAFF` or `MEMBER_ADMIN`/`MEMBER`, per SPEC's canonical color map),
  and edit/delete row-actions (edit → navigates to the existing detail
  route; delete → opens the shared delete flow, see below).
- **Detail screen reorganization**
  (`StaffUserDetailPanelComponent`/`MemberDetailPanelComponent`):
  - "Editar perfil" button moves into a new header/top region rendered
    before any audit-trail or permission content (REQ-28) — a `<header
    class="mb-6 flex items-center justify-between">` wrapping the
    existing title (`detail.email`) and a new `buttonClass('secondary')`
    "Editar perfil" button, replacing wherever `ProfileSectionComponent`'s
    edit trigger currently lives at the bottom.
  - For a `STAFF_ADMIN`/`MEMBER_ADMIN` target: the entire permission-
    checkbox section (REQ-3) is removed from the template (not just
    hidden) and replaced with a single "Demover para STAFF/MEMBER"
    button, shown only per REQ-7a's viewer-role gate, disabled with an
    explanatory `title`/`aria-describedby` per REQ-5/REQ-6's last-admin
    rule, driven directly and reliably by the detail endpoint's new
    `isLastAdminOfType: boolean` field (see "Consumed API contracts" —
    the earlier graceful-degradation fallback, attempt-then-surface-409,
    is superseded now that the backend PLAN exposes this field; the
    button is disabled *before* any click, never by trying and failing).
  - For a `STAFF` target: a "Promover a STAFF_ADMIN" button
    (`buttonClass('secondary')`) is shown next to (not replacing) the
    switches-batch permission UI, gated by REQ-7a/REQ-7c's viewer-role
    rule, never disabled on a last-admin basis (REQ-7d — no `isLastAdmin`
    check applies to promote). For a `MEMBER` target: same shape,
    "Promover a MEMBER_ADMIN". Confirmed via the existing
    `ConfirmDialogComponent` (a plain confirm, no security-phrase gate —
    REQ-18's security-phrase requirement is scoped to the permission
    batch-save flow only, and neither SPEC nor the backend PLAN's promote
    endpoint requires a deletion-confirmation token). On confirm, calls
    `POST .../promote` (staff) or `POST .../{membershipId}/promote`
    (tenant member) and refreshes the detail view (REQ-7e).
  - For `STAFF`/`MEMBER`: the checkbox section is replaced by the new
    switches-batch UI (below), with the promote button described above
    rendered alongside it.
  - Delete action moves into this same header/action area (REQ-8),
    reusing the existing `ConfirmDialogComponent` wired to the new
    hard-delete + token endpoints, disabled per REQ-9/REQ-10 the same
    `isLastAdminOfType`-flag way as demote (for `STAFF`/`MEMBER` targets,
    `isLastAdminOfType` is always `false` per the backend PLAN's
    definition, so delete is never disabled on this basis for them, per
    REQ-11).
- **Switches-batch permission editing** (`STAFF`/`MEMBER` targets only):
  new local signal `pendingPermissions = signal<Set<GlobalPermission |
  Permission>>()` seeded from `detail.directPermissions` on load/refresh.
  Toggling a switch only mutates this local set (REQ-16) — no HTTP call
  per toggle. A `hasChanges = computed(() =>
  !setsEqual(pendingPermissions(), initialPermissions()))` drives REQ-19
  ("Save" hidden/disabled when `hasChanges()` is `false`). Clicking
  "Save" opens the existing `ConfirmDialogComponent` (REQ-17/REQ-18 — one
  confirmation for the whole batch, using the security-phrase mechanism
  unconditionally whenever `hasChanges()` is true, since REQ-18 already
  covers "at least one grant, one revoke, or both" — i.e. any non-empty
  diff); on confirm, submits the **full current `pendingPermissions()`
  set** to the new batch endpoint (matches the backend PLAN's
  full-replacement-set contract, not a diff) with the confirmed word,
  then re-fetches detail on success and resets `pendingPermissions`.
- **Human-readable permission names**: new `permissionLabels` Transloco
  key namespace, `permissions.<ENUM_VALUE>`, added under both
  `staffDirectory.*`-adjacent and a new shared `permissions.*` root key
  in `en.json`/`pt-BR.json` (shared root, not duplicated per screen,
  since both `Permission` (tenant-scope) and `GlobalPermission`
  (staff-scope) enum members are disjoint string sets and can share one
  flat namespace without collision — confirmed by inspection of both
  enums in `permission-granularity-model/PLAN.md`). A new pure function
  `translatePermissionLabel(value: string, transloco: TranslocoService):
  string` in `shared/permission-labels.ts` looks up `permissions.<value>`
  and falls back to the raw `value` string when the key is missing
  (REQ-14) — implemented via Transloco's own "key not found returns the
  key itself" default behavior (already relied on elsewhere in this
  codebase for similar fallbacks), not a custom try/catch.
- **Audit-trail translation**: new `shared/audit-trail-labels.ts` with a
  `Record<string, string>` keyed by `"<action>"` or `"<action>:<outcome>"`
  (outcome-specific entries win over the bare-action entry when present),
  populated by inventorying every `AuditService`/`@AuditLog` action-string
  literal in `knowly-api/src/main/java` (REQ-27a) — this inventory is a
  TASKS-time step (grep + enumerate), not guessed here; the PLAN commits
  to the *mechanism* (map + fallback), TASKS.md gets an explicit "grep
  knowly-api for every audit action literal and add a translation
  key for each" task so the map's completeness is verified against real
  code, not sample UI data. Timestamps: new pure function
  `formatAuditTimestamp(iso: string): string` using
  `Intl.DateTimeFormat` with the browser's local timezone (implicit,
  no explicit `timeZone` option) and `dd/MM/yyyy HH:mm:ss`-shaped output
  (REQ-25) — no new date library; `Intl` is already available and this
  codebase has no existing date-formatting dependency to reuse instead.
- **Access groups get their own screen**
  (`AccessGroupManagementPageComponent`,
  `knowly-app/src/app/features/access-groups/`), routed at a new path
  `/staff/access-groups` (guarded — see routing below), consuming
  `SharedListComponent` for the group list (columns: name, member count)
  and a detail/expand view for members. Existing group-creation/
  membership-assignment calls move here from
  `StaffUserDetailPanelComponent`, which loses its inline "create group"
  affordance (REQ-24). Assignment UI only offers `STAFF`/`MEMBER` users
  as candidates (REQ-23) — the assignable-user picker filters
  `StaffUserService.list()`'s results by `globalRole !== 'STAFF_ADMIN'`
  client-side (no new backend filter needed; the existing list endpoint
  already returns `globalRole` per `StaffUserSummary`).
- **Tenant-scope access groups**: SPEC's REQ-20-24 is written generically
  ("access group") but the SPEC's "Amends prior SPECs" section only
  cites `user-management-screens` REQ-9 (global/staff scope). Tenant-
  scoped access groups (if they exist as a parallel concept — not
  confirmed in this SPEC) are **not** touched by this PLAN; only the
  global/staff access-group screen is built. If tenant-scoped access
  groups need the same "own screen" treatment, that's a separate SPEC
  amendment, not assumed here.
- **New route + guard**: `/staff/access-groups` added to the staff-scope
  route group, carrying the same permission-specific guard already
  protecting the staff directory route (checked against
  `GlobalPermission.STAFF_ACCESS_GROUP_...`-shaped permission via `GET
  /api/staff/permissions`, mirroring `staffGuard`'s existing fixed
  pattern — never inferring guard state from an unrelated call). Exact
  `GlobalPermission` constant to gate on is whatever the backend PLAN's
  access-group endpoints are already gated by today (unchanged by this
  feature — access-group create/list endpoints already exist and are
  already permission-gated per `staff-rbac-split`; this PLAN adds no new
  backend gate, only a new frontend route pointing at existing calls).

## Components and routes

- New: `shared/shared-list/shared-list.component.ts` (+ `.spec.ts`)
- New: `shared/permission-labels.ts`, `shared/audit-trail-labels.ts`,
  `shared/audit-timestamp.ts` (+ specs)
- New: `features/access-groups/access-group-management-page.component.ts`
  (+ `.spec.ts`), routed at `/staff/access-groups`
- Modify: `features/user-management/staff-directory-page.component.ts`
  (consumes `SharedListComponent`)
- Modify: `features/user-management/staff-user-detail-panel.component.ts`
  (header reorg, admin-tier demote/delete-only view, switches-batch for
  non-admin, group-creation removed)
- Modify: `features/members/members-page.component.ts` (consumes
  `SharedListComponent`)
- Modify: `features/members/member-detail-panel.component.ts` (same
  reorg as staff detail panel, tenant-scoped equivalents)
- Modify: `app.routes.ts` (new `/staff/access-groups` route + guard)
- Modify: `core/staff-user.service.ts` (new methods, see API contracts)
- New/modify: tenant-scope equivalent of `StaffUserService` (existing
  member service, name TBD at TASKS time by inspecting
  `MembersPageComponent`'s current data source — not read in this PLAN
  pass; the SPEC's own "Out of scope" line defers exact service wiring
  beyond consuming the new endpoints).

## Consumed API contracts

From `staff-rbac-management-operations/PLAN.md` (exact shapes, not
re-derived):

| Method | Path | Used by |
|---|---|---|
| POST | `/api/staff/users/{userId}/demote` | admin-tier detail panel, demote button |
| POST | `/api/staff/users/{userId}/promote` | `STAFF`-target detail panel, promote button (REQ-7b/7e) |
| POST | `/api/staff/users/{userId}/deletion-confirmation-token` | delete flow token fetch |
| DELETE | `/api/staff/users/{userId}` | delete flow confirm |
| PUT | `/api/staff/users/{userId}/permissions/batch` | switches-batch save |
| POST | `/api/staff/users/{userId}/permissions/batch/deletion-confirmation-token` | switches-batch confirm token fetch |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/demote` | admin-tier member detail, demote button |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/promote` | `MEMBER`-target member detail, promote button (REQ-7b/7e) |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/hard-delete/deletion-confirmation-token` | member delete flow |
| DELETE | `/api/tenants/{tenantId}/members/{membershipId}/hard-delete` | member delete flow |
| PUT | `/api/tenants/{tenantId}/members/{membershipId}/permissions/batch` | member switches-batch save |
| POST | `/api/tenants/{tenantId}/members/{membershipId}/permissions/batch/deletion-confirmation-token` | member switches-batch confirm token |

**Gap resolved (amendment, 2026-08-02): detail DTO now carries
`isLastAdminOfType`.** `staff-rbac-management-operations/PLAN.md` has
been amended to add a computed `isLastAdminOfType: boolean` field to
`StaffUserDetailDto`/`MemberDetailDto` (see that PLAN's own amendment for
the exact computation — a read-only, non-locking reuse of the same
`countByGlobalRoleIn`/tenant-scoped count query the mutation endpoints
already use, `false` for a `STAFF`/`MEMBER` target by definition since
the last-admin rule only ever applies to admin-tier roles). This
frontend PLAN consumes that field directly:
`StaffUserService`/tenant-member-service's existing `getDetail`-shaped
method return types gain the field (no new HTTP call), and the demote/
delete buttons in "Detail screen reorganization" above read it straight
from the already-fetched detail response. The previously-planned
graceful-degradation fallback (attempt the action, surface the raw 409
message as the only way a user learns the restriction) is dropped — it
is no longer needed since the field is available at TASKS time, not a
follow-up.

## State and data

- `SharedListComponent`: component-local signals (selection, sort,
  search, page) per "Architectural decisions" above.
- `StaffUserDetailPanelComponent`/`MemberDetailPanelComponent`:
  component-local `pendingPermissions` signal for the switches batch
  (not service state — scoped to one open detail view, discarded on
  navigation, matching `ConfirmDialogComponent`'s existing local-signal
  precedent).
- No new shared/cross-screen service-level signal state is introduced —
  `PermissionsService`/`GlobalPermissionsService`/`ActiveTenantService`
  are unaffected; this feature only adds new one-shot HTTP methods to
  the existing `StaffUserService` (and tenant-member equivalent), which
  matches those services' existing shape (methods returning
  `Observable<T>`, no new state signal on the service itself — the
  existing services already work this way, e.g. `grantPermission`/
  `revokePermission`).

## AppSec review note (2026-08-02, appsec gate before TASKS.md)

Reviewed against `staff-rbac-management-operations/PLAN.md` and this
repo's frontend security posture. No blocking issues found; two points
made explicit here so TASKS.md doesn't have to re-derive them:

- **CSRF**: none of the new endpoints in "Consumed API contracts"
  (`/api/staff/**`, `/api/tenants/{tenantId}/members/**`) are listed in
  `SecurityConfig`'s `ignoringRequestMatchers` — they require the
  `X-XSRF-TOKEN` header. The frontend already attaches it automatically
  via `provideHttpClient(withXsrfConfiguration(...))` in `app.config.ts`,
  so none of this feature's new `StaffUserService`/tenant-member-service
  methods need special CSRF handling; this PLAN adds no exception and
  none should be added.
- **XSS**: every value this feature renders (identity cell name/email,
  role/status pill text, permission labels, audit-trail action phrases)
  is bound through Angular's default interpolation (`{{ }}`) inside
  `SharedListComponent`'s `render()` string return and the detail
  panels' templates — no `[innerHTML]`/`bypassSecurityTrust*` call is
  introduced anywhere in this PLAN. `permission-labels.ts` and
  `audit-trail-labels.ts` are static, code-owned `Record<string, string>`
  maps keyed by backend enum/action values (not backend-supplied HTML),
  so Transloco's lookup surface carries no arbitrary-HTML-interpolation
  risk. Tenant/user-supplied strings (names, emails) flow through
  unchanged from existing services and were already rendered via
  interpolation pre-redesign — this PLAN doesn't change that binding
  mechanism, only the surrounding layout.
- **Security-phrase reuse**: confirmed `ConfirmDialogComponent` fetches a
  fresh token on every open transition (`resetState()` +
  `requestToken()`) and again on any `retryToken` bump — no caching or
  cross-operation reuse of a confirmation word, consistent with the
  six independent call sites this PLAN wires it to (demote/promote/
  delete/batch-save, staff and tenant-member variants).

Cleared for TASKS.md.

## Dependencies

None new. Reuses `@lucide/angular` icons already in the dependency tree
(`LucideChevronsUpDown`, `LucideChevronUp`, `LucideChevronDown`,
`LucideInbox`, `LucideSquarePen`, `LucideTrash2` — all need explicit
per-component import per this codebase's tree-shaking convention, none
require a `package.json` change since `@lucide/angular` ships every icon
as an importable standalone component already).

## Testing strategy (Vitest)

- `shared-list.component.spec.ts`: renders columns/rows per config;
  checkbox selection (individual + select-all + indeterminate); sort
  toggle cycles `none → asc → desc → none` and emits `sortChange`;
  search filters rows (client-side mode) and emits nothing extra since
  filtering is local; empty/no-results/error/loading states render per
  input; row-action `disabled`/`title` rendering; responsive
  `hidden sm:table-cell` class presence on non-essential columns.
- `permission-labels.spec.ts` / `audit-trail-labels.spec.ts`: known-value
  translation, fallback-to-raw-value for an unknown enum/action string
  (REQ-14/REQ-27).
- `audit-timestamp.spec.ts`: known ISO input → expected local-format
  output (mock `Intl`/timezone if needed for determinism).
- `staff-user-detail-panel.component.spec.ts` (extend existing):
  admin-tier target shows no checkboxes, only demote (+ delete in header)
  gated by REQ-7a's viewer-role condition, disabled per
  `isLastAdminOfType` with no extra HTTP call needed to know that;
  non-admin (`STAFF`) target shows switches plus a "promover a
  STAFF_ADMIN" button gated by REQ-7c and never disabled, confirms via
  `ConfirmDialogComponent` and calls the promote endpoint on confirm;
  Save hidden with zero pending changes, batch confirm dialog triggered
  on Save with changes, submits full pending set on confirm; "Editar
  perfil" rendered in the top header region (assert DOM order/position,
  not just presence).
- `member-detail-panel.component.spec.ts` (extend existing): same cases,
  tenant-scoped equivalents.
- `access-group-management-page.component.spec.ts` (new): create group,
  list groups, assign/unassign member, `STAFF_ADMIN`/`MEMBER_ADMIN` never
  offered as assignable (REQ-23).
- `staff-directory-page.component.spec.ts` / `members-page.component.spec.ts`
  (extend existing): now assert `app-shared-list` is rendered with the
  expected `columns`/`rowActions` config instead of the old bare
  `<table>` assertions.
