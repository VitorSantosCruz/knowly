# PLAN — user-profile (frontend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md and, for contract/authorization semantics carried
> over verbatim, `knowly-api/specify/features/identity-profile-model/
> {SPEC,PLAN}.md`.

## Architectural decisions

- **New `ProfileService`** (`core/profile.service.ts`), same stateless
  HTTP-wrapper shape as `StaffUserService`/`MemberService` (no signal
  state of its own — every consumer owns its own signals, matching
  `StaffUserDetailPanelComponent`'s pattern rather than
  `PermissionsService`'s pattern, since there is no single shared
  "current profile" that multiple unrelated components need to read
  reactively): `getOwnProfile()`, `getProfile(userId)`,
  `directEdit(userId, fields)`, `submitEditRequest(fields)`,
  `listEditRequests()`, `approveEditRequest(id)`,
  `rejectEditRequest(id)`. Types `ProfileFields`, `UserProfile`,
  `ProfileEditRequest`, `ProfileEditRequestStatus` mirror
  `identity-profile-model/PLAN.md`'s `ProfileFieldsDto`/`UserProfileDto`/
  `ProfileEditRequestDto` field-for-field.

- **`ProfileFieldsFormComponent`** (`shared/profile-fields-form.component.ts`),
  a new presentational, reusable form — inputs `fields:
  ProfileFields`, `disabled: boolean`; output `submitted =
  output<ProfileFields>()`. Renders `fullName`/`address`/`rg`/`cpf`/
  `phone` as plain text inputs (no masking, per SPEC's explicit
  out-of-scope), never renders or emits `email` (REQ-7). This is the
  single form implementation consumed by both the own-profile screen
  and the detail-panel inline edit (SPEC judgment call 5) — it owns no
  HTTP call itself, only emits the submitted values; each consumer
  decides which endpoint to call and how to react to 403/409, exactly
  like `member-detail-panel.component.ts`'s existing inline forms own
  their own submit handlers today. *Why a new shared component rather
  than inlining the form twice*: SPEC judgment call 5 explicitly
  requires "same fields, same validation, same conflict-handling copy"
  reuse; duplicating markup would risk drift between the two screens
  the first time either changes.

- **`OwnProfilePageComponent`** (`features/profile/own-profile-page.component.ts`,
  route `/profile`, **no `canActivate` guard** — REQ-8/SPEC judgment
  call 2 make this route universal to any authenticated session
  regardless of tenant context, unlike every other tenant-scoped route
  in `app.routes.ts`; there is no existing generic "must be logged in"
  guard in this codebase today (every other route relies on
  `tenantSelectionGuard`/`staffGuard`, both of which encode a
  *tenant*/*staff* precondition this screen deliberately has none of),
  so adding one here would be inventing new route-guard scope beyond
  this SPEC — an unauthenticated visit 401s on the first `GET
  /api/users/me/profile` call exactly like any other unauthenticated
  API call elsewhere in this app, degrading to the existing generic
  network-error UI, not a security gap since nothing renders without
  that call succeeding).
  - Own-profile direct-edit-right inference (needed to pick REQ-2 vs
    REQ-3's branch **before** submitting, not after a 403): computed
    locally as `viewerIsStaffAdmin() || memberships().some(m => m.role
    === 'ADMIN')`, fetched via `GlobalPermissionsService`/
    `ActiveTenantService.list()` in `ngOnInit`, mirroring
    `nav-menu.component.ts`'s own `viewerIsStaffAdmin`/`memberships`
    fetch shape exactly (third occurrence of the page-local
    `viewerIsStaffAdmin` `computed()` duplication already accepted as a
    known tradeoff in `staff-global-dashboard`'s PLAN — not extracted
    here either, same reasoning). **Tier 2 judgment call**: a
    tenant/global `PROFILE_EDIT` holder is deliberately *excluded* from
    this computed (REQ-13a/14a explicitly forbid that grant from
    covering self), so `permissionsService.has('PROFILE_EDIT')`/
    `globalPermissionsService.has('PROFILE_EDIT')` are never consulted
    here — only the two admin-role shortcuts (REQ-11/REQ-12) grant
    unconditional self-edit.
  - The caller's own `userId` (needed for the `PUT
    /api/users/{id}/profile` call) is read directly off the
    already-fetched `GET /api/users/me/profile` response's `userId`
    field — no separate "who am I" plumbing/service is added.
  - Pending-state (REQ-4) is a plain `pending = signal(false)`, set
    only when a 409 is observed on `POST
    .../edit-requests` in the current session (SPEC judgment call 4,
    carried over verbatim — no dedicated "do I have a pending request"
    read exists on the backend, so this is not re-litigated here).
  - Direct-edit 409 (REQ-6) and edit-request 409 (REQ-5) are
    distinguished by which endpoint was called, each mapped to its own
    non-technical message; the form's entered values are preserved on
    a REQ-6 conflict by not re-fetching/resetting the `fields` signal
    the form is bound to.

- **`ProfileSectionComponent`** (`features/user-management/profile-section.component.ts`,
  new, shared between both existing detail panels) — a new `<section
  data-testid="profile-section">` embedded as an additive change into
  `StaffUserDetailPanelComponent` (after the existing audit-trail
  section) and `MemberDetailPanelComponent` (after the existing
  effective-permissions section), mirroring the audit-trail section's
  `ngOnChanges`-driven, independently-erroring sub-load shape from
  `staff-global-dashboard`'s PLAN exactly (own `profile =
  signal<UserProfile | null>(null)`, own `profileError =
  signal<DetailError>(null)`, own `catchError` → 403-classify block,
  loaded from the host's existing `ngOnChanges()` alongside its other
  section loads — a 403 here never touches the panel's other,
  already-independent sections, per REQ-9).
  - Inputs: `userId: number` (target), `canEdit: boolean` — **the host
    panel computes and passes `canEdit`**, not `ProfileSectionComponent`
    itself, following the exact precedent already set by
    `StaffUserDetailPanelComponent`'s own `viewerIsStaffAdmin` input
    (host-computed, section/panel-consumed).
  - `canEdit` composition, **Tier 2 judgment call, self-exclusion
    simplified deliberately**: `viewerIsStaffAdmin() ||
    viewerIsMemberAdminOfThisTenant() ||
    permissionsService.has('PROFILE_EDIT') ||
    globalPermissionsService.has('PROFILE_EDIT')` — with **no
    client-side self-exclusion check** for the last two clauses (unlike
    the own-profile screen, which excludes them entirely). Reasoning:
    REQ-13a/14a's self-exclusion only matters in the rare case a
    `PROFILE_EDIT` holder (not admin) opens their *own* row in the
    staff directory or members list; adding that check would require
    plumbing a new "what is my own userId" signal into both host pages
    solely for this edge case. Given the NFR's explicit "this SPEC is
    never the real authorization boundary" principle and that the
    existing `reportError`/409-message pattern already handles a
    rejected submission gracefully (the same UX REQ-11 already
    specifies for *any* conflict), this is accepted as a known, minor,
    self-only rough edge — the backend's REQ-13a/14a rejection (403)
    surfaces via the section's existing error-reporting path if it is
    ever hit, not a silent failure. `viewerIsMemberAdminOfThisTenant()`
    is computed only where a `tenantId` is meaningful (new optional
    `tenantId` input, populated by `MembersPageComponent` via a lookup
    into `ActiveTenantService.list()`'s own membership role field for
    the active tenant; left `false`/unset in the staff-directory
    context, which has no tenant).
  - Inline edit mode: toggled by a local `editing = signal(false)`
    button, gated on `canEdit()`; renders `ProfileFieldsFormComponent`
    when editing; submit calls `profileService.directEdit(userId,
    fields)`, reloads the section on success (REQ-10), reuses the same
    409-conflict message as the own-profile screen (REQ-11/REQ-6, same
    copy — a new shared `PROFILE_CONFLICT_MESSAGE`-style i18n key, not
    duplicated text).

- **`ProfileEditRequestsInboxPageComponent`**
  (`features/profile-edit-requests/profile-edit-requests-inbox-page.component.ts`,
  route `/profile-edit-requests`, **no `canActivate` guard** — same
  reasoning already established for `staffGuard`'s existence *not*
  being needed here: `GET /api/profile-edit-requests` never 403s for a
  caller holding no applicable right, it simply returns an empty list
  (confirmed against `identity-profile-model/PLAN.md`'s contract table)
  — mirroring exactly why `/api/staff/permissions` (which `staffGuard`
  itself calls) is safe to call unconditionally. An unauthorized direct
  visit sees only the empty-state UI, never another user's data, so no
  route-level check is added beyond the nav-link hiding REQ-17 already
  requires).
  - Lists every row from `GET /api/profile-edit-requests` with
    `requester identity` rendered as `"User #{{requesterUserId}}"`.
    **Tier 2 judgment call, flagged explicitly**:
    `ProfileEditRequestDto` (per `identity-profile-model/PLAN.md`,
    already shipped, not re-derivable here) carries only
    `requesterUserId`, no requester email/display name, even though
    REQ-12 asks for "requester identity." Resolving this by adding a
    lookup (e.g. fetching every possible tenant's member list or the
    staff directory to cross-reference an id → email) is out of this
    frontend feature's reasonable scope (rows can originate from
    different tenants or from staff with no tenant at all, so no single
    existing list call could resolve every row correctly) and adding a
    `requesterEmail` field to the backend DTO is a backend contract
    change this SPEC's own "Out of scope" section rules out
    ("no backend SPEC accompanies this frontend SPEC"). Accepted as a
    known UX rough edge for this iteration, the same way SPEC's own
    judgment call 4 accepts a known gap rather than expanding scope;
    worth flagging in `PROJECT_STATUS.md` as a follow-up once a small
    backend addition is scoped.
  - Approve/reject: `POST .../approve` / `POST .../reject`; success
    removes the row (REQ-13/14); a 409 classified as "already pending
    elsewhere" (REQ-15, distinguished from a stale 409 by response body
    shape — `identity-profile-model`'s `ProfileFieldConflictException`
    vs. its "already resolved" 409 path; if the two 409 reasons are not
    distinguishable by body shape at implementation time, both render
    the same generic conflict copy and the row's list-removal behavior
    still follows REQ-15/REQ-16 exactly: REQ-15 never removes, REQ-16
    always refreshes) shows the conflict message and leaves the row
    (REQ-15); a 403 or non-uniqueness 409 (REQ-16) shows the existing
    error/permission-denied state and re-fetches the list so the stale
    row drops out.
  - Empty state (REQ-18) rendered distinctly from loading/error, same
    three-state shape (`loading`/`error`/data) already used by every
    other list page in this app (e.g. `MembersPageComponent`).

- **Nav entries added to `nav-menu.component.ts`** (the only nav
  mechanism this codebase has — verified no separate header/user-menu
  component exists today, correcting SPEC judgment call 2's "top
  header/user area" phrasing to this app's actual single sidebar nav):
  - **"My profile"**: a new, unconditionally-rendered nav group (not
    permission-gated at all, since REQ-1/SPEC judgment call 2 make this
    universal to every authenticated session) — added as its own
    `accountGroup` computed similarly shaped to `workspaceGroup` but
    with no conditional branches, always present once
    `authService.isLoggedIn()` is true (same top-level gate every other
    nav content already has).
  - **Edit-request inbox link**: added to `overviewGroups`'s existing
    array-building logic as a new conditional item, gated on
    `permissionsService.has('PROFILE_EDIT') ||
    globalPermissionsService.has('PROFILE_EDIT') ||
    memberships().some(m => m.role === 'ADMIN') ||
    viewerIsStaffAdmin()` — same "one link, multiple possible reasons to
    show it" shape already used for `members`/`dashboard`'s nav
    entries. **Tier 2 judgment call**: this check only reflects the
    *currently active* tenant's `PROFILE_EDIT` grant (via
    `permissionsService`, which is itself tenant-scoped), not every
    tenant the caller might hold that permission in — accepted as
    consistent with the "hidden, not shown-then-blocked" nav rule
    erring toward hiding (a false negative here is a minor
    inconvenience, not a security issue, since the backend's own list
    endpoint is the real authorization boundary per the NFR) — the
    same shape `members`'s nav gating already uses today (also only
    checks the active tenant's permission).

- **`Permission`/`GlobalPermission` frontend unions gain `PROFILE_EDIT`**
  (`core/permission.ts`'s `Permission` union +
  `ALL_PERMISSIONS`; `core/global-permission.ts`'s `GlobalPermission`
  union + `ALL_GLOBAL_PERMISSIONS`) — both already exist on the backend
  per `identity-profile-model/PLAN.md`, simply not yet mirrored on the
  frontend. `PROFILE_VIEW` is **not** added to either union: no
  frontend code path in this PLAN needs to check "does the viewer hold
  `PROFILE_VIEW`" client-side (REQ-8/9's viewing gate is purely
  reactive to the `GET`'s 200/403, never predicted client-side, see
  `ProfileSectionComponent` above) — adding an unused enum value would
  be dead weight. If a future feature needs to predict `PROFILE_VIEW`
  client-side, it can add it then, matching this codebase's existing
  precedent of adding permission-union values only when a consumer
  needs them (`staff-global-dashboard`'s PLAN made the identical call
  for its own two new values).

- **No CSRF change** — every new/changed endpoint this feature calls
  (`GET`/`PUT`/`POST` under `/api/users/**/profile`,
  `/api/profile-edit-requests/**`) already exists per
  `identity-profile-model`'s shipped backend; none of them are added to
  `SecurityConfig`'s CSRF-exemption list by this PLAN (out of this
  subproject's control regardless — that list lives in `knowly-api/`),
  and this frontend feature does not touch `SecurityConfig`.

## Components and routes

```
app.routes.ts
├── /profile (NEW)                          OwnProfilePageComponent, no guard
│   └── ProfileFieldsFormComponent (shared, reused below)
└── /profile-edit-requests (NEW)             ProfileEditRequestsInboxPageComponent, no guard

StaffUserDetailPanelComponent (existing, additive change)
└── new <section data-testid="profile-section">  ProfileSectionComponent
    └── ProfileFieldsFormComponent (inline edit mode, when canEdit)

MemberDetailPanelComponent (existing, additive change)
└── new <section data-testid="profile-section">  ProfileSectionComponent (same component, reused)
    └── ProfileFieldsFormComponent (inline edit mode, when canEdit)

nav-menu.component.ts (existing, additive change)
├── new always-visible `accountGroup` → "My profile" (/profile)
└── new conditional item on `overviewGroups` → edit-request inbox (/profile-edit-requests)
```

## Consumed API contracts

Per `knowly-api/specify/features/identity-profile-model/PLAN.md`
(already shipped, confirmed directly against that PLAN, not
re-derived):

| Method | Path | Request | Response | Status codes handled here |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | — | `UserProfileDto` | 200 |
| GET | `/api/users/{id}/profile` | — | `UserProfileDto` | 200, 403 (REQ-9 → section-scoped no-access state) |
| PUT | `/api/users/{id}/profile` | `ProfileFieldsDto` | `UserProfileDto` | 200, 403 (generic error), 409 (uniqueness → REQ-6/11 conflict message) |
| POST | `/api/users/me/profile/edit-requests` | `ProfileFieldsDto` | `ProfileEditRequestDto` | 201, 409 (REQ-20 → REQ-5 "already pending" + pending state) |
| GET | `/api/profile-edit-requests` | — | `ProfileEditRequestDto[]` | 200 (empty array renders REQ-18's empty state) |
| POST | `/api/profile-edit-requests/{id}/approve` | — | — | 200 (REQ-13, row removed), 403/409-stale (REQ-16), 409-uniqueness (REQ-15) |
| POST | `/api/profile-edit-requests/{id}/reject` | — | — | 200 (REQ-14, row removed), 403/409-stale (REQ-16) |

`ProfileFieldsDto`/`UserProfileDto`/`ProfileEditRequestDto` shapes are
carried over verbatim from that PLAN (see this task's own instructions
for the exact field lists) — mirrored on the frontend as `ProfileFields`,
`UserProfile` (extends `ProfileFields` with `userId`, `email`),
`ProfileEditRequest` (`id`, `requesterUserId`, `proposedFields:
ProfileFields`, `status`, `createdAt`).

## State and data

- `ProfileService`: no signal state (stateless HTTP wrapper, see
  above).
- `OwnProfilePageComponent`: `profile = signal<UserProfile |
  null>(null)`, `loading = signal(true)`, `error = signal<'network' |
  null>(null)` (no permission-denied case — REQ-8 makes this
  unconditionally viewable), `pending = signal(false)`, `conflictError
  = signal<string[] | null>(null)` (field names, REQ-6), `hasDirectEditRight
  = computed(...)` (see above).
- `ProfileSectionComponent`: `profile = signal<UserProfile |
  null>(null)`, `profileError = signal<DetailError>(null)`, `editing =
  signal(false)`, `conflictError = signal<string[] | null>(null)` —
  added to each host panel alongside its existing independent
  per-section signals, none shared.
- `ProfileEditRequestsInboxPageComponent`: `requests =
  signal<ProfileEditRequest[]>([])`, `loading = signal(true)`, `error =
  signal<'network' | null>(null)`, per-row `conflictMessage:
  Record<number, string>`-style transient state for REQ-15 (kept as a
  small local `Map`/signal, not a shared service — scoped to this one
  page).
- `nav-menu.component.ts`: no new signals; reuses its existing
  `memberships`/`permissionsService`/`globalPermissionsService` signals
  in two new computed branches.
- `core/permission.ts` / `core/global-permission.ts`: gain
  `PROFILE_EDIT` (see above).

## Dependencies

None new.

## Deviations from this PLAN (discovered during implementation)

- **`ProfileSectionComponent` was not wired into `MemberDetailPanelComponent`
  in the first iteration (task 29 in TASKS.md was deferred).** This PLAN
  assumed `MemberDetail`/`Member` carried enough identity to resolve the
  target `userId` `GET /api/users/{id}/profile` needs. Verified at the
  time against the shipped backend (`knowly-api`'s `MemberDto`/
  `MemberDetailDto` records): neither exposed anything but
  `membershipId` — no `userId` field existed anywhere in the
  tenant-members contract, and `membershipId` is a distinct identifier
  (the `TenantMembership` row's own id), not interchangeable with the
  target user's id. **This has since been resolved**: the backend now
  exposes `userId` on both `MemberDto`/`MemberDetailDto` (commit
  `c6e56b2`), and task 29 is now implemented — `MemberDetailPanelComponent`
  renders `ProfileSectionComponent` with a host-computed `canEdit`
  (`viewerIsMemberAdminOfThisTenant() || permissionsService.has('PROFILE_EDIT')`),
  with `viewerIsMemberAdminOfThisTenant` sourced from a new
  `ActiveTenantService#activeTenantRole` signal (the viewer's own role
  within the active tenant, following the same "preserve prior value
  when no active membership is found" rule as `activeTenantId`/
  `activeTenantName`) rather than adding a second, redundant
  `/api/tenants/memberships` call inside `MembersPageComponent`.
  REQ-8/REQ-9/REQ-10/REQ-11 are now satisfied for both the
  staff-directory panel and `MembersPageComponent`'s member-detail
  panel.

## Testing strategy

- `profile.service.spec.ts` (new): each method calls the correct
  method/path per the contract table above.
- `profile-fields-form.component.spec.ts` (new): renders the five
  editable fields, never renders/emits `email`; emits `submitted` with
  the entered values on submit; `[disabled]` suppresses submission.
- `own-profile-page.component.spec.ts` (new): loads and renders
  `GET /api/users/me/profile`'s fields with `email` read-only;
  `STAFF_ADMIN`/tenant-`MEMBER_ADMIN` sessions submit via `PUT` and see
  the change applied immediately; a plain session submits via `POST
  .../edit-requests` and enters the pending state (REQ-4); a second
  submission attempt while pending is blocked client-side; a 409 on
  the edit-request call shows the "already pending" message and sets
  the same pending state (REQ-5); a 409 on the direct-edit `PUT` shows
  the conflict message naming the field(s) and leaves the form's
  entered values intact (REQ-6).
- `profile-section.component.spec.ts` (new): renders profile fields
  from `GET /api/users/{id}/profile`; a 403 renders
  `app-no-access-state` scoped to only this section while a sibling
  section (mocked to succeed) still renders normally, mirroring the
  audit-trail section's existing test shape; `[canEdit]=true` reveals
  the inline edit toggle and a submitted edit calls `PUT` and refreshes
  the section; a 409 on that call shows the conflict message
  (REQ-11); `[canEdit]=false` never renders the edit toggle.
- `staff-user-detail-panel.component.spec.ts` /
  `member-detail-panel.component.spec.ts`: new case each — the new
  profile section renders inside the existing panel alongside the
  panel's other, untouched sections.
- `profile-edit-requests-inbox-page.component.spec.ts` (new): renders
  every pending request (requester id, proposed fields, submission
  date); approve/reject remove the row on success; a 409 on approve
  (REQ-15) keeps the row and shows the conflict message; a 403/stale
  409 (REQ-16) refreshes the list and shows the existing
  error/permission-denied state; zero requests renders the distinct
  empty state (REQ-18).
- `nav-menu.component.spec.ts`: new cases — "My profile" link always
  renders once logged in, regardless of tenant/permission state; the
  edit-request inbox link appears for a tenant/global `PROFILE_EDIT`
  holder, a tenant `ADMIN` membership, and a `STAFF_ADMIN`-shaped
  session, and is absent for a session with none of those.
- `app.routes.spec.ts` (or equivalent routing test, if one exists
  today): `/profile` and `/profile-edit-requests` resolve to their new
  components with no guard blocking an otherwise-authenticated session.
