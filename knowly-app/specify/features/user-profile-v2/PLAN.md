# PLAN — user-profile-v2 (frontend)

> The how. Translates SPEC.md into concrete technical decisions.
> References SPEC.md and, for contract/authorization semantics carried
> over verbatim, `knowly-api/specify/features/identity-profile-model-v2/
> {SPEC,PLAN}.md`. This PLAN retrofits `user-profile`'s already-shipped
> code in place — see that feature's own `PLAN.md` for the baseline
> being modified; every decision below is stated as a delta from it
> unless marked "unchanged."

## Sequencing dependency (read first)

**Blocked on `identity-profile-model-v2` (backend) reaching its task 18/
25/26 checkpoint** (DTO shapes finalized: `ProfileFieldsDto`,
`AddressDto`, `ContactDto`, `ProfileEditRequestFieldsDto`,
`ContactChangeDto`, `UserProfileDto`, `ProfileEditRequestDto`, plus the
new `POST /api/users/me/profile/avatar` endpoint). Do not start
implementation (TASKS.md) until that backend PLAN's contract is either
shipped or frozen-and-confirmed-stable by whoever owns that feature —
this mirrors `tenant-pagination-search`'s own backend-then-frontend
sequencing precedent (frontend PLAN written and reviewable in advance,
implementation gated on the real contract existing).

## Architectural decisions

- **`ProfileService` (`core/profile.service.ts`, existing) retrofitted
  in place**: `ProfileFields`/`UserProfile`/`ProfileEditRequest` types
  updated to the new shape (`address: Address` structured object,
  `contacts: Contact[]`, `birthDate`, `rgOrgaoEmissor`; `avatarUrl`
  moved out of the editable-fields shape into `UserProfile` only, since
  it's never part of a `PUT`/edit-request payload per SPEC REQ-8).
  Method signatures unchanged in shape (`getOwnProfile()`,
  `getProfile(userId)`, `directEdit(userId, fields)`,
  `submitEditRequest(fields, contactChanges)` — gains a second param,
  `listEditRequests()`, `approveEditRequest(id)`,
  `rejectEditRequest(id)`), plus one new method: `uploadAvatar(file:
  File): Observable<UserProfile>` → `POST
  /api/users/me/profile/avatar` via `FormData`, mirroring however this
  codebase's existing article-upload call (if one already exists in
  `article.service.ts`) builds its `FormData`/`HttpClient.post` call —
  reused pattern, not invented fresh. Still no signal state of its own
  (unchanged from `user-profile/PLAN.md`'s rationale: no single shared
  "current profile" multiple unrelated components need reactively).
- **`ProfileFieldsFormComponent` retrofitted, not replaced** (SPEC
  judgment call 2): inputs become `fields: ProfileFields` (now the
  richer shape), `disabled: boolean`; output `submitted =
  output<{fields: ProfileFields; contactChanges: ContactChange[]}>()`
  — the emitted contract grows to include a derived `contactChanges`
  list (see below) alongside the flat field values, since the backend's
  edit-request/direct-edit contracts both need add/update/remove
  entries for `contacts`, not just a final list.
  - **New internal sub-structure, not a second component**: an address
    fieldset (8 plain text inputs: `cep`/`logradouro`/`numero`/
    `complemento`/`bairro`/`cidade`/`estado`/`pais`) and a contacts list
    editor (repeatable rows: `type` `<select>`, `value`/`label` text
    inputs, `isPrimary` checkbox constrained to one-per-type
    client-side, remove button per row, add-row button disabled at 5
    rows per REQ-7) — both live inside this one component, per SPEC
    judgment call 2's "grows materially, not split" call.
  - **`contactChanges` derivation, Tier 2 judgment call**: the component
    tracks contacts as a plain in-memory array (`contacts =
    signal<ContactRow[]>(initialContacts)`, each row tagged with its
    original `id` if it existed on load, or `null` if newly added in
    this session) and, on submit, diffs against the array it was
    initialized with to produce `ContactChange[]` (`ADD` for rows with
    no `id`, `UPDATE` for rows with an `id` whose fields changed,
    `REMOVE` for original rows no longer present) — computed once at
    submit time, not kept as a running change-log, since the form has no
    "undo individual edits" requirement and a full diff-on-submit is
    simpler and less bug-prone than tracking incremental deltas through
    every add/edit/remove interaction.
- **New `AvatarUploadComponent`** (`shared/avatar-upload.component.ts`,
  SPEC judgment call 4) — presentational, inputs `avatarUrl:
  string | null`, output `fileSelected = output<File>()`; renders the
  current avatar (or a placeholder) plus a native `<input type="file"
  accept="image/*">`. Owns no HTTP call itself (same "presentational,
  consumer decides what to call" shape `ProfileFieldsFormComponent`
  already uses) — `OwnProfilePageComponent` is its only consumer (REQ-8
  is self-only; no other screen renders an *editable* avatar control,
  only a read-only `<img>` inside `ProfileSectionComponent`'s existing
  rendering).
- **`OwnProfilePageComponent` retrofitted in place**:
  - `hasDirectEditRight`/admin-shortcut computed **removed entirely** —
    REQ-2 makes the submit path unconditional (`submitEditRequest`
    always, for everyone), so the branching logic `user-profile/PLAN.md`
    documented (`viewerIsStaffAdmin() || memberships().some(m => m.role
    === 'ADMIN')`) has no remaining call site. This is a straightforward
    deletion, not a replacement — flagged here explicitly since it's a
    real simplification, not something to preserve "just in case."
  - New `avatarUpload = inject services for uploadAvatar` wiring:
    `AvatarUploadComponent`'s `fileSelected` output calls
    `profileService.uploadAvatar(file)` directly from this page
    component (no separate service-layer signal needed — one-shot
    action, refreshes `profile` signal with the response on success,
    per REQ-8), independent of the non-avatar form's submit/pending
    state.
  - Pending-state (REQ-3), 409-message handling (REQ-4), preserved
    entered-values-on-conflict (already existing behavior) — unchanged
    shape from `user-profile/PLAN.md`, just now applying to every
    session uniformly instead of only non-admin sessions.
- **`ProfileSectionComponent` retrofitted**:
  - Renders the new field set + a read-only avatar `<img>` (never
    editable here — REQ-8 is self-only).
  - `canEdit` composition **narrows**: `user-profile/PLAN.md`'s existing
    `canEdit` expression (`viewerIsStaffAdmin() ||
    viewerIsMemberAdminOfThisTenant() ||
    permissionsService.has('PROFILE_EDIT') ||
    globalPermissionsService.has('PROFILE_EDIT')`) gains one more
    clause, **`&& userId !== ownUserId()`** (SPEC REQ-12's "never on the
    viewer's own row," resolving `user-profile/PLAN.md`'s previously
    *accepted* self-exclusion gap — see "Deviation resolved" below).
    `ownUserId` is a new input threaded down from each host panel,
    itself sourced from one `profileService.getOwnProfile()` call made
    once per panel-open (not per-row), following the same
    "host computes, section/panel consumes" precedent
    `viewerIsStaffAdmin`/`canEdit` already established.
  - Inline edit submit now sends both `fields` and `contactChanges`
    (from `ProfileFieldsFormComponent`'s new emitted shape) to
    `profileService.directEdit(userId, fields, contactChanges)`.
  - **Deviation resolved (from `user-profile/PLAN.md`)**: that PLAN's
    "Tier 2 judgment call, self-exclusion simplified deliberately"
    entry explicitly accepted no client-side self-exclusion check as a
    "known, minor, self-only rough edge," reasoning the backend's own
    403 would surface if hit. That rough edge is now closed as a direct
    consequence of the backend's REQ-11 becoming unconditional (no admin
    bypass remains at all) — showing an edit affordance that will *always*
    403 for 100% of sessions on their own row is no longer a rare edge
    case worth accepting, so this PLAN fixes it outright rather than
    re-accepting it.
- **`ProfileEditRequestsInboxPageComponent` retrofitted**: row rendering
  extends to show the structured proposed address and a
  `proposedContactChanges` list (per-entry: action badge + type/value/
  label), reusing the same three-state (`loading`/`error`/data) shape
  and the same 409-classification approach already documented in
  `user-profile/PLAN.md` (uniqueness-conflict vs. stale, same accepted
  "if not distinguishable by body shape, both render generic conflict
  copy" fallback — unchanged, not re-derived).
- **`ProfileEditRequestsInboxPageComponent`'s known display gap
  (requester shown as `"User #{{id}}"`, no display name/email) is
  unchanged/still accepted** — nothing in `identity-profile-model-v2`
  adds a requester-display-name field to `ProfileEditRequestDto`, so
  this PLAN carries the same previously-accepted rough edge forward
  rather than re-solving it here (still flagged as a standing follow-up
  in `PROJECT_STATUS.md`, not newly introduced by this retrofit).
- **Nav entries unchanged in mechanism** ("My profile"
  unconditionally visible; edit-request inbox link gated on
  `permissionsService.has('PROFILE_EDIT') ||
  globalPermissionsService.has('PROFILE_EDIT') ||
  memberships().some(m => m.role === 'ADMIN') ||
  viewerIsStaffAdmin()`) — this gate is about *who can act on other
  people's requests*, which is untouched by the retrofit (REQ-12/13 of
  the backend SPEC, the "edit others" paths, are unchanged); only the
  self-edit paths changed, and nav gating was never about self-edit.
- **`Permission`/`GlobalPermission` frontend unions**: no change beyond
  what `user-profile` already added (`PROFILE_EDIT` on both). No new
  permission value is introduced by this retrofit.
- **No CSRF change** — same reasoning as `user-profile/PLAN.md`: every
  endpoint this feature calls, including the new `POST
  /api/users/me/profile/avatar`, is a normal authenticated endpoint, not
  added to `SecurityConfig`'s CSRF-exemption list (that's a
  `knowly-api/` file this PLAN doesn't touch, and there's no reason a
  self-authenticated multipart upload would need pre-auth CSRF
  exemption — it's not a pre-authentication endpoint).

## Components and routes

```
app.routes.ts
├── /profile (existing, unchanged route)         OwnProfilePageComponent (retrofitted)
│   ├── ProfileFieldsFormComponent (retrofitted — address fieldset + contacts editor)
│   └── AvatarUploadComponent (NEW — self-only, direct upload)
└── /profile-edit-requests (existing, unchanged route)   ProfileEditRequestsInboxPageComponent (retrofitted display)

StaffUserDetailPanelComponent (existing, additive change already shipped)
└── existing <section data-testid="profile-section">  ProfileSectionComponent (retrofitted)
    ├── read-only avatar <img>
    └── ProfileFieldsFormComponent (inline edit mode, when canEdit — now excludes own row)

MemberDetailPanelComponent (existing, additive change already shipped)
└── existing <section data-testid="profile-section">  ProfileSectionComponent (same component, retrofitted)
    ├── read-only avatar <img>
    └── ProfileFieldsFormComponent (inline edit mode, when canEdit — now excludes own row)

nav-menu.component.ts (existing, unchanged mechanism)
```

## Consumed API contracts

Per `knowly-api/specify/features/identity-profile-model-v2/PLAN.md`
(authoritative — not re-derived here, confirm against that PLAN before
implementation starts per the sequencing note above):

| Method | Path | Request | Response | Status codes handled here |
|---|---|---|---|---|
| GET | `/api/users/me/profile` | — | `UserProfileDto` | 200 |
| GET | `/api/users/{id}/profile` | — | `UserProfileDto` | 200, 403 → section-scoped no-access state |
| PUT | `/api/users/{id}/profile` | `ProfileFieldsDto` | `UserProfileDto` | 200, 403 (generic error), 409 (uniqueness → conflict message) |
| POST | `/api/users/me/profile/edit-requests` | `ProfileEditRequestFieldsDto` | `ProfileEditRequestDto` | 201, 409 (already pending → pending state) |
| POST | `/api/users/me/profile/avatar` | `multipart/form-data` | `UserProfileDto` | 200, 400 (unsupported/too large → error message) |
| GET | `/api/profile-edit-requests` | — | `ProfileEditRequestDto[]` | 200 (empty renders empty state) |
| POST | `/api/profile-edit-requests/{id}/approve` | — | — | 200 (row removed), 403/409-stale (refresh+error), 409-uniqueness (keep+message) |
| POST | `/api/profile-edit-requests/{id}/reject` | — | — | 200 (row removed), 403/409-stale (refresh+error) |

Frontend types mirror the backend DTOs field-for-field:

```ts
interface Address {
  cep: string; logradouro: string; numero: string | null;
  complemento: string | null; bairro: string; cidade: string;
  estado: string; pais: string;
}
type ContactType = 'PHONE' | 'WHATSAPP' | 'EMAIL' | 'OTHER';
interface Contact {
  id: number | null; type: ContactType; value: string;
  label: string | null; isPrimary: boolean;
}
interface ProfileFields {
  fullName: string | null; cpf: string | null; rg: string | null;
  rgOrgaoEmissor: string | null; birthDate: string | null; // ISO date
  address: Address | null; contacts: Contact[];
}
interface UserProfile extends ProfileFields {
  userId: number; email: string; avatarUrl: string | null;
}
type ContactChangeAction = 'ADD' | 'UPDATE' | 'REMOVE';
interface ContactChange {
  action: ContactChangeAction; contactId: number | null;
  type: ContactType | null; value: string | null;
  label: string | null; isPrimary: boolean | null;
}
interface ProfileEditRequest {
  id: number; requesterUserId: number; proposedFields: ProfileFields;
  proposedContactChanges: ContactChange[];
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  createdAt: string;
}
```

## State and data

- `ProfileService`: still stateless (unchanged rationale).
- `OwnProfilePageComponent`: `profile = signal<UserProfile |
  null>(null)`, `loading`, `error` (unchanged from `user-profile`);
  `hasDirectEditRight` computed **removed**; `pending = signal(false)`,
  `conflictError = signal<string[] | null>(null)` (unchanged shapes,
  now field-agnostic since REQ-2 always uses the same branch);
  `avatarError = signal<string | null>(null)` (new, REQ-9).
- `ProfileFieldsFormComponent`: new internal `contacts =
  signal<ContactRow[]>([])`-style local state for the diffable contacts
  list (see above); address fields as a plain reactive-forms group or
  signal-backed object, matching whichever pattern this component's
  existing flat fields already use (retrofit reuses the existing
  pattern, doesn't introduce a new forms approach).
- `ProfileSectionComponent`: `profile`, `profileError`, `editing`,
  `conflictError` (unchanged shapes); new `ownUserId: number | null`
  input (see `canEdit` narrowing above).
- `ProfileEditRequestsInboxPageComponent`: unchanged signal shapes,
  `requests` items now carry the richer `proposedFields`/
  `proposedContactChanges`.
- `AvatarUploadComponent`: no signal state — pure input/output.

## Dependencies

None new.

## Testing strategy

- `profile.service.spec.ts`: update every existing test for the new DTO
  shapes; new test for `uploadAvatar()` → `POST
  /api/users/me/profile/avatar` with `FormData`.
- `profile-fields-form.component.spec.ts`: update existing field-render
  assertions for the new shape (structured address inputs, no more flat
  `address`/`phone`); new cases — contacts editor renders/add/remove up
  to 5 and blocks a 6th (REQ-7); one-primary-per-type UI constraint;
  submit emits the diffed `contactChanges` correctly for a mixed
  add+update+remove scenario.
- `own-profile-page.component.spec.ts`: **rewrite** the existing
  `STAFF_ADMIN`/tenant-`ADMIN` "submits via direct PUT" cases — those
  now must assert `POST .../edit-requests` is called instead (REQ-2);
  new case for `AvatarUploadComponent`'s upload succeeding/failing
  independent of the non-avatar form's pending state.
- `avatar-upload.component.spec.ts` (new): renders current avatar or
  placeholder; selecting a file emits `fileSelected`.
- `profile-section.component.spec.ts`: update field-render assertions;
  new case — `[ownUserId]` equal to `[userId]` hides the edit toggle
  even when `[canEdit]=true`; read-only avatar rendered regardless of
  `canEdit`.
- `profile-edit-requests-inbox-page.component.spec.ts`: update row
  rendering assertions for the structured address + contact-change list.
- `staff-user-detail-panel.component.spec.ts` /
  `member-detail-panel.component.spec.ts`: update the `ownUserId` wiring
  assertion (new prop threaded through).
- No changes expected to `nav-menu.component.spec.ts` (gating logic
  unchanged) — confirm existing cases still pass unmodified.
- `app.routes.spec.ts`: no changes expected (routes unchanged).

## Deviations from this PLAN (discovered during implementation)

- **`UserProfileDto` nests the editable fields under a `fields` object, not flattened** — the
  shipped `identity-profile-model-v2` response shape is `{userId, email, fields: ProfileFieldsDto,
  avatarUrl}`, not `UserProfile extends ProfileFields` as this PLAN's "Consumed API contracts"
  block showed. `core/profile.service.ts`'s `UserProfile` interface composes `fields:
  ProfileFields` as a nested property instead. Tier 2 (frontend type shape only, no behavior
  change) — every consumer reads `profile.fields.xxx`/`profile.email`/`profile.avatarUrl`
  accordingly.
- **`directEdit(userId, fields)` never gained the second `contactChanges` parameter this PLAN
  anticipated — since resolved.** At the time this feature shipped, `PUT /api/users/{id}/profile`
  accepted `ProfileFieldsDto` as its whole body (no wrapper), and `UserProfileService#directEdit`
  always called `applyFields(target, fields, List.of())` — contact changes were hardcoded to an
  empty list for this endpoint, so there was no way for a direct edit to change contacts at all.
  `ProfileService.directEdit` stayed single-argument, and `ProfileFieldsFormComponent` gained a
  `showContacts` input (default `true`) so `ProfileSectionComponent`'s inline edit of an *other*
  user hid the contacts editor entirely (`[showContacts]="false"`) rather than showing controls
  that would silently no-op. Tier 2 (UI decision reflecting a real, confirmed backend contract
  gap) — flagged in `PROJECT_STATUS.md` as a backend follow-up candidate.
  **Follow-up closed**: `knowly-api` commit `c0a817d` changed `UserProfileController.directEdit`'s
  request body to `ProfileEditRequestFieldsDto` (`{fields, contactChanges}`, the same shape
  `submitEditRequest` already used) and made it genuinely apply contact changes. The frontend
  follow-up restored `directEdit(userId, fields, contactChanges)`'s second parameter, sending the
  `{fields, contactChanges}` wrapper body; `ProfileSectionComponent` dropped
  `[showContacts]="false"` (back to the form's default `true`) and now threads `contactChanges`
  from `ProfileFieldsFormSubmission` through to `directEdit`, mirroring `OwnProfilePageComponent`'s
  existing `submitEditRequest(fields, contactChanges)` wiring.
  `staff-user-detail-panel.component.ts`/`member-detail-panel.component.ts` needed no change —
  neither host passes `[showContacts]`, so both already inherited `ProfileSectionComponent`'s
  default.
- **`ProfileEditRequestDto.proposedFields.address` and `.contacts` are always `null` in both `GET
  /api/profile-edit-requests` and the `POST .../edit-requests` response**, even though the
  proposed address is genuinely persisted (`ProfileEditRequest.proposedCep`/etc., used internally
  by `approveEditRequest`) and contact changes are exposed separately via
  `proposedContactChanges`. Both `UserProfileController`/`ProfileEditRequestController#toDto`
  hardcode `new ProfileFieldsDto(..., null, null)` for the address/contacts positions — this is a
  backend response-mapping gap, not a frontend decision. The inbox's address block
  (`profile-edit-request-address-{id}`) is implemented per SPEC REQ-14 and will render once the
  backend is fixed, but currently never appears (`request.proposedFields.address` is always
  `null`) since the API never returns it. Not a Tier 3 item for this frontend-only feature (no
  frontend code path can work around a backend DTO-mapping bug), but flagged in `PROJECT_STATUS.md`
  as a backend bug to file, since it means REQ-14's "including the structured address" is
  currently unsatisfiable end-to-end despite the frontend code being ready for it.
- Implemented as fewer, larger commits grouped by section (foundations/service, shared form +
  avatar upload, own-profile screen, profile-section self-exclusion fix + panel wiring, inbox),
  each internally still Red-then-Green, rather than one commit per TASKS.md line-item — same
  precedent as `identity-profile-model-v2`'s own "fewer, larger commits" deviation, given how
  interdependent the type/component changes are across files.
- **`ProfileSectionComponent` moved from `implements OnChanges` to a constructor `effect()`
  scoped to `userId()` only** — a real bug caught by TDAD while wiring the new `ownUserId` input:
  `ngOnChanges` re-ran `loadProfile()` on *any* input change, including `ownUserId` arriving
  asynchronously after the initial render (from the host panel's own `getOwnProfile()` call),
  causing a second, unflushed `GET /api/users/{id}/profile` in tests and in the real app a
  redundant duplicate fetch. Tier 2 (bugfix caught during implementation, not a scope change).

## Follow-up (2026-07-30): requester identity + "any tenant" nav gate

Both items below are compliance gaps against this SPEC's own already-approved
REQ-14/REQ-19, reconfirmed and documented in `PROJECT_STATUS.md`'s
`user-profile-v2` row — not new scope, so amended here rather than a new
SPEC/PLAN cycle, same precedent as the two prior follow-ups above. Backend
side of both is `identity-profile-model-v2/PLAN.md`'s matching "Follow-up
(2026-07-30)" sections — see there for the API contract this side consumes.

- **`ProfileEditRequest` interface (`core/profile.service.ts`) gains
  `requesterName: string | null` / `requesterEmail: string | null`**,
  mapped straight through from the now-extended `ProfileEditRequestDto`
  — additive fields, no other shape change, no new HTTP call (still the
  same `listEditRequests()` → `GET /api/profile-edit-requests`).
- **`ProfileEditRequestsInboxPageComponent` renders `requesterName` when
  present, falls back to `requesterEmail`, then to the existing
  `'profileEditRequests.requester'` (`User #{id}`) string only if both are
  null** — matches the backend's documented nullable-`fullName` case
  (a requester whose `UserProfile` was eagerly created but never named)
  without introducing a new empty/error state. New i18n key
  `profileEditRequests.requesterNamed` (`en`/`pt-BR`) takes `{name}`;
  existing `profileEditRequests.requester` (`{userId}`) is kept as the
  fallback, not replaced.
- **`nav-menu.component.ts`'s `canSeeProfileEditRequests` calls a new
  `PermissionsService.hasInAnyTenant(permission: Permission):
  Signal<boolean>`** (same private-signal-+-`fetch()` shape as the rest of
  `PermissionsService` — not a new pattern), backed by
  `GET /api/tenants/permissions/any-tenant?permission=PROFILE_EDIT`. This
  replaces the removed Tier-2-accepted-gap comment and its
  active-tenant-only `this.permissionsService.has('PROFILE_EDIT')` check
  in the OR chain — `globalPermissionsService.has('PROFILE_EDIT')` and the
  `ADMIN`-membership/`viewerIsStaffAdmin()` checks are unaffected (already
  correct, not active-tenant-scoped).
  - **Fetched once alongside the existing `permissions.fetch()`/
    `globalPermissionsService.fetch()` calls at session-start** (same
    trigger point as those two, e.g. `AuthService`'s post-login/
    post-session-restore hook) rather than lazily on first nav render, so
    the nav doesn't flash the link in/out.
  - **Staff-session edge case**: a staff session with no real
    `TenantMembership` row (even after switching into a tenant) — the new
    endpoint's `findByUser(caller)` naturally returns zero memberships for
    such a session, so `hasInAnyTenant` correctly returns `false` unless
    `STAFF_ADMIN`/`STAFF`-with-`GlobalPermission` already grants it via
    the existing global-permission branch of the OR chain. No frontend
    special-casing needed — this falls out of the backend's own
    membership-based evaluation.
  - **Not gated behind `ActiveTenantService`'s active-tenant signal at
    all** — that's the whole point of this fix (REQ-19 says "anywhere,"
    not "in the active tenant"), so this check must not be short-circuited
    by "no active tenant" the way tenant-scoped routes are.

### API contract consumed (added, see backend PLAN.md for the authoritative shape)

| Method | Path                                              | Response (200)          |
|--------|---------------------------------------------------|--------------------------|
| GET    | `/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT` | `{ "granted": boolean }` |

### Implemented (2026-07-30) — deviation from this follow-up's stated signature

Both items shipped as described above, with one deliberate shape deviation:
`PermissionsService.hasInAnyTenant(permission: Permission)` returns a plain
`boolean`, not `Signal<boolean>` as originally sketched — matching the
existing `has(permission): boolean` method's shape exactly (read directly
inside `nav-menu.component.ts`'s `canSeeProfileEditRequests` computed,
which still tracks the underlying private signal correctly since the
computed's dependency tracking follows signal *reads*, not the wrapper
method's own return type). A `fetchInAnyTenant(permission: Permission):
void` companion method (same `fetch()`-then-read-`has()` pattern the rest
of `PermissionsService` already uses) populates a private
`_anyTenantGrants: Signal<Partial<Record<Permission, boolean>>>`, called
once at session-start in `nav-menu.component.ts`'s `ngOnInit`, alongside
`permissions.fetch()`/`globalPermissionsService.fetch()`, per this PLAN's
"fetched once... so the nav doesn't flash the link in/out" note. Tier 2
(method-shape consistency call, not a behavior change) — both `nav-menu
.component.spec.ts` and `permissions.service.spec.ts` cover the new
methods; `profile-edit-requests-inbox-page.component.spec.ts` covers the
`requesterName`/`requesterEmail` render + fallback chain (both-null,
name-only, email-fallback cases). `user-profile-v2` has no further
outstanding rough edges after this follow-up.
