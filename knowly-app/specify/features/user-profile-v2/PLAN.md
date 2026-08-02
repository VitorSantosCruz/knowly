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

## Amendment — REQ-21/22/23 masked input (2026-08-02)

- **New `InputMaskDirective`** (`shared/input-mask.directive.ts`, standalone,
  selector `[appInputMask]`) is the masking mechanism, not a `ControlValueAccessor`/
  Reactive Forms adapter and not a pipe. *Why*: `ProfileFieldsFormComponent`'s
  existing fields are bound the "plain signal" way this codebase already uses
  everywhere (`[value]="localFields().cpf"` + `(input)="onFieldChange('cpf', $any($event.target).value)"`),
  not `ngModel`/`formControlName` — there is no `NgControl` for a CVA to attach to,
  and a display-only pipe can't intercept keystrokes to reformat as the user types.
  A small host-listening directive that sits directly on the existing `<input>` is
  the shape that fits this binding style without introducing Reactive Forms into
  this component (a real, separate Tier 2 call this PLAN is *not* making — unlike
  `tenant-creation`'s form, see `DECISIONS.md`, this component's fields stay flat
  enough that the plain-signal convention remains the right default here).
  - **Contract**: `[appInputMask]="'cpf' | 'rg' | 'cep' | 'phone'"` input. The
    directive listens to the native `input` event (`HostListener`), derives the
    digits-only (or otherwise mask-relevant) raw value from `event.target.value`,
    computes the masked display string for the given pattern (CPF
    `000.000.000-00`, CEP `00000-000`, phone `(00) 00000-0000`/`(00) 0000-0000`
    depending on digit count, `rg` left as free-format digits/dashes per
    existing backend normalization — no rigid mask beyond grouping, since RG
    formats vary by issuing state and the SPEC doesn't fix one), writes the
    masked string back into the host element's `.value` (with a caret-position
    fix-up so typing/deleting mid-string doesn't jump the cursor to the end),
    and emits the **unmasked** (digits-only) value via a `(appInputMaskChange)`
    output — the component's existing `(input)` handler is replaced with
    `(appInputMaskChange)="onFieldChange('cpf', $event)"` for masked fields only;
    every other field (`fullName`, `rgOrgaoEmissor`, `birthDate`, non-phone
    contacts, non-`cep` address fields) is untouched.
  - **Submitted value stays unmasked, by construction, not by a strip-before-submit
    step**: because the directive's output already carries the digits-only value,
    `localFields()`/`contacts()` — the same signals `onSubmit` already reads
    verbatim — never contain masked characters in the first place. REQ-22 is
    satisfied without adding any "strip mask" logic at submit time.
  - **`rg` needs no masking pattern of its own beyond REQ-21's literal field list**:
    re-reading REQ-21, only `cpf`, `cep`, and phone-type contacts get a fixed
    punctuation mask; `rg` is listed in REQ-21 as a plain field with no format
    given (Brazilian RG has no single national format) — so `[appInputMask]="'rg'"`
    is **not** applied; `rg`'s `<input>` is unchanged from before this amendment.
    Flagged here explicitly since REQ-21's wording groups `rg` with the other
    three at a glance; this PLAN reads it as scoped to the fields the SPEC gives
    a concrete pattern for.
  - **Contact rows**: `[appInputMask]` is applied conditionally in the template
    based on `row.type` (`'PHONE' | 'WHATSAPP'` → `'phone'`, else no directive) —
    since `type` can change via the `<select>`, the directive re-evaluates its
    `mask` input reactively (Angular re-binds `[appInputMask]` whenever the
    template expression's value changes, no manual re-subscription needed) and
    simply stops reformatting once `type` moves to `EMAIL`/`OTHER`.
  - **No DOM/selector change**: the directive attaches to the same `<input>`
    elements with their existing `data-testid`s — it adds behavior via
    `HostListener`, not a wrapping element, so every existing test that queries
    `[data-testid="profile-field-cpf"]` etc. keeps working unmodified in shape;
    only the *value* asserted after simulating keystrokes changes for masked
    fields.
  - **Accessibility (SPEC's non-functional requirement)**: caret position is
    explicitly preserved by computing the caret offset delta (masked length
    before vs. after the edit) and calling `setSelectionRange` after writing the
    new value back, rather than always parking the caret at the end — this is
    the one piece of real complexity in the directive and is called out as its
    own TDAD task below rather than assumed to fall out "for free."
- **Lives in `knowly-app/src/app/shared/`**, alongside `ProfileFieldsFormComponent`
  itself — not `core/`, since it has no service/HTTP concern, matching this
  folder's existing "presentational, reusable, no side effects" convention.
- **No new dependency** — hand-rolled directive, ~80–120 lines, no third-party
  input-mask library. Confirmed against `package.json`: nothing added.

### Implemented (2026-08-02) — one deviation found during TDAD (Tier 2, not a scope change)

Shipped exactly as described above (`InputMaskDirective`, `HostListener`-based,
`cpf`/`cep`/`phone` only, `rg` untouched), with one implementation-detail fix
discovered while writing `profile-fields-form.component.spec.ts`'s wiring
tests (task 37): binding a masked `<input>`'s `[value]` straight to the
underlying (unmasked, per REQ-22) signal — e.g. `[value]="localFields().cpf"`
— fights the directive on every keystroke. Sequence: the directive writes the
masked string into `element.value` synchronously inside its own `input`
handler, then emits the unmasked digits via `(appInputMaskChange)`, which the
component's handler stores back into the plain signal (unmasked, by design);
on the very next change-detection pass the *component's own* `[value]`
binding re-reads that now-unmasked signal and overwrites the directive's
masked DOM value right back to plain digits — so nothing ever stayed
formatted. Fixed by exporting a small `formatMaskedValue(mask, rawValue):
string` helper alongside the directive (same `formatByMask` internals the
directive itself uses) and using it in the template's `[value]` binding for
each of the three masked inputs — e.g. `[value]="formatMaskedValue('cpf',
localFields().cpf ?? '')"` — so the externally-driven display formats
identically to what the directive itself would produce, and the two bindings
agree on every change-detection pass instead of fighting. `rg`/`rgOrgaoEmissor`
and all non-phone contact/address inputs are unaffected (still bound directly
to the raw signal, no `formatMaskedValue` call). No test/selector/DOM shape
changed as a result — only the source of the bound expression's value.

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

## Amendment — country-agnostic identity/address model (2026-08-02)

> Delta only. Covers SPEC.md's third 2026-08-02 amendment: `cpf` →
> `taxId`, restructured country-agnostic `address`, new `countryCode`
> selector, country-driven labels/masks, and a phone/WhatsApp DDI
> selector composing E.164 client-side. **Blocked on
> `identity-profile-model-v2`'s matching backend amendment (that
> feature's TASKS.md task 37, DTO shapes frozen) — same sequencing rule
> as this PLAN's own top-of-file dependency note.**

### Architectural decisions

- **`core/profile.service.ts` types retrofitted in place**: `Address`'s
  eight Brazil-only fields replaced by `addressLine1`, `addressLine2:
  string | null`, `city`, `stateRegion: string | null`, `postalCode`,
  `countryCode`. `ProfileFields.cpf` renamed `taxId`; new
  `ProfileFields.countryCode: string | null`. `Contact.value` unchanged
  in type (still `string`) — E.164-ness is a content convention, not a
  type-level distinction, so no new field/type is added to `Contact`
  itself (per backend SPEC's REQ-3c, no `contacts.countryCode` DTO
  field).
- **New `CountryFieldConfig` lookup table** (`shared/country-field-config.ts`,
  plain exported const object, no new dependency) — the mechanism REQ-1a/
  REQ-21 require: a `Record<string, CountryLabels>`-shaped map plus a
  fixed `DEFAULT` fallback entry, keyed by ISO 3166-1 alpha-2 country
  code. Each entry supplies: `taxIdLabel: string`, `postalCodeLabel:
  string`, `addressLine1Label`/`cityLabel`/`stateRegionLabel` (default to
  generic "Address line 1"/"City"/"State/Region" for every country not
  overridden — most countries need no override here, only the tax-id/
  postal-code labels realistically vary enough to matter per SPEC's own
  user story), and optional `taxIdMask`/`postalCodeMask`/`phoneMask`
  strings consumed by `InputMaskDirective`'s existing mask-pattern
  mechanism (extended, not replaced — see below).
  - **Concrete entries, per the task's own "2-3 examples" ask**:
    - `BR`: `taxIdLabel: 'CPF'`, `postalCodeLabel: 'CEP'`, masks
      `'000.000.000-00'` / `'00000-000'` (unchanged from the existing
      `InputMaskDirective` patterns — this amendment doesn't change
      Brazil's own behavior, only makes it one entry in a table instead
      of the only behavior).
    - `US`: `taxIdLabel: 'SSN'`, `postalCodeLabel: 'ZIP Code'`,
      `stateRegionLabel: 'State'`, mask `taxIdMask: '000-00-0000'`
      (standard SSN grouping), `postalCodeMask: '00000'` (5-digit ZIP —
      deliberately not the ZIP+4 extended format, since SPEC doesn't ask
      for country-specific *validation*, just a reasonable display mask,
      and 5-digit is the common case).
    - `GB`: `taxIdLabel: 'NINO'`, `postalCodeLabel: 'Postcode'`,
      `stateRegionLabel: 'County'` (optional field, per SPEC REQ-2a —
      the UK genuinely has no mandatory state/region, matching the
      backend's own `stateRegion` nullability), no `taxIdMask`/
      `postalCodeMask` defined (UK postcodes/NINOs have irregular,
      non-fixed-length shapes that don't reduce to one simple digit
      mask — falls back to the plain-unmasked-input behavior REQ-21
      explicitly allows for exactly this case).
    - `DEFAULT`: `taxIdLabel: 'Tax ID'`, `postalCodeLabel: 'Postal
      Code'`, `addressLine1Label: 'Address line 1'`, no masks — the
      REQ-1a-mandated fallback for every `countryCode` not explicitly
      listed (this table intentionally does not attempt full ISO-3166
      coverage, per SPEC's own "Out of scope" line on this exact point).
  - **Why a plain lookup object, not a service/signal**: this is static
    reference data, not application state — no HTTP call backs it, no
    component needs to react to it changing at runtime (it doesn't
    change), so it doesn't fit `PermissionsService`-style
    signal-plus-`fetch()` shape; a directly-imported const is the
    correct level of ceremony, same rationale this codebase already
    applies to any other static config-like lookup.
- **`InputMaskDirective` extended with a `country` input, not
  replaced**: `[appInputMask]="'taxId' | 'postalCode' | 'phone'"`
  (renamed from `'cpf' | 'cep' | 'phone'` — `'rg'` already dropped by
  the prior amendment) gains a sibling `[appInputMaskCountry]="countryCode()"`
  input; the directive's `HostListener` looks up the mask pattern for
  the given `(mask, country)` pair via `CountryFieldConfig` instead of
  a single hardcoded Brazilian pattern per mask type. **Where no mask is
  defined for the given country** (e.g. `GB`), the directive becomes a
  no-op passthrough (writes the raw value back unchanged, emits it
  unchanged via `(appInputMaskChange)`) — this is the "plain, unmasked
  text input" behavior REQ-21 requires for a country with no known mask,
  achieved by the directive doing nothing rather than the template
  conditionally omitting the directive (simpler: one binding shape,
  always present, behavior varies by config rather than by
  template-level conditional logic).
  - `formatMaskedValue(mask, country, rawValue)` (the existing exported
    helper, per this PLAN's prior "Implemented" deviation note) gains
    the same `country` parameter, used identically in both the
    directive and `ProfileFieldsFormComponent`'s template `[value]`
    bindings — no divergence between the two call sites is introduced
    by this amendment, same agreement invariant the prior deviation
    fix already established.
- **`ProfileFieldsFormComponent` gains a `countryCode` `<select>`**
  (new form control, populated from `CountryFieldConfig`'s keys plus
  a "not specified" empty option, since `countryCode` is nullable per
  backend REQ-1b) whose value drives: (a) the `taxId`/`postalCode`
  labels (via `CountryFieldConfig` lookup, rendered directly in the
  template next to each field, not hardcoded "CPF"/"CEP" strings
  anymore), (b) the mask inputs above, and (c) the default
  `AddressDto.countryCode` value for newly-entered addresses (per the
  backend's Judgment-call-6 resolution — the frontend does **not**
  duplicate a second, independent address-country selector, resolving
  SPEC's Judgment call 9 in favor of **one shared control**: simplest
  UI, matches the backend's "defaults, doesn't force" write-time
  behavior, and nothing in SPEC requires exposing the "different
  tax-residence-vs-address-country" edge case as a user-facing feature
  yet — if a future SPEC amendment wants two independent selectors,
  that's new UI scope, not assumed here).
  - Address fieldset's 8 old inputs replaced by 6 new ones
    (`addressLine1`, `addressLine2`, `city`, `stateRegion`,
    `postalCode`, no separate address-level country selector per
    the above).
- **Phone/WhatsApp DDI selector (REQ-6a, resolving SPEC Judgment call
  10) — decision: a plain text prefix input, not a searchable
  flag+code dropdown.** New `PhoneDdiInputComponent`
  (`shared/phone-ddi-input.component.ts`) renders two native inputs side
  by side: a short text input for the DDI (pre-filled from
  `CountryFieldConfig`'s entry for the row's `type`-linked country
  context — falls back to the profile's own `countryCode` as a
  starting guess, user-editable, not locked) and the existing
  national-number input; on any change it emits the composed
  `+<ddi><number>` string (digits-only concatenation, `IdentityFieldNormalizer`-
  equivalent stripping applied client-side before composing) via a
  single `(valueChange)` output carrying the full E.164 string, and
  accepts an existing E.164 `value` as input, splitting it back via a
  small `splitE164(value): {ddi: string; number: string}` helper (a
  fixed max-3-digit DDI heuristic — E.164 doesn't self-delimit DDI
  length from the string alone; this component does not attempt a full
  ITU calling-code table, it defaults to a simple "first 1–3 digits,
  configurable via a short known-DDI-length map seeded with `BR: 2`,
  `US: 1`, `GB: 2`" — **flagged as an approximation, not a complete
  solution**, since a fully correct DDI-length disambiguation needs the
  same ITU calling-code table a searchable-dropdown approach would also
  need; this PLAN accepts the approximation to avoid a new dependency,
  per Judgment call 10's framing that this exact choice is open).
  *Why not a searchable flag+dropdown*: that shape typically wants a
  bundled country-list-with-flags asset/library (e.g.
  `country-flag-icons`, `libphonenumber-js` for its own metadata) — a
  new dependency this PLAN does not introduce without flagging it
  first (see Dependencies below); a plain two-input composition needs
  none.
- **Contact row's DDI/number split display** reuses
  `PhoneDdiInputComponent` only when `row.type` is `PHONE`/`WHATSAPP`
  (same conditional-rendering precedent `[appInputMask]`'s per-row
  `type` binding already established for the prior amendment) — for
  `EMAIL`/`OTHER` rows the plain `value` text input is unchanged.

### Components and routes (delta)

```
shared/country-field-config.ts        (NEW — plain data, no component)
shared/phone-ddi-input.component.ts    (NEW — presentational)
shared/input-mask.directive.ts         (MODIFIED — country-aware)
shared/profile-fields-form.component.ts (MODIFIED — countryCode select,
                                          restructured address fieldset,
                                          PhoneDdiInputComponent per
                                          PHONE/WHATSAPP row)
```

No route changes, no new page components.

### Consumed API contracts (delta)

Per `identity-profile-model-v2/PLAN.md`'s matching amendment (task 37 —
confirm frozen before implementing here):

```ts
interface Address {
  addressLine1: string; addressLine2: string | null; city: string;
  stateRegion: string | null; postalCode: string; countryCode: string;
}
interface ProfileFields {
  fullName: string | null; taxId: string | null; countryCode: string | null;
  address: Address | null; contacts: Contact[];
}
```
`Contact`/`ContactChange`/`ProfileEditRequest` shapes otherwise
unchanged (E.164 lives inside the existing `value: string`, no new
field).

### State and data (delta)

- `ProfileFieldsFormComponent`: new local `countryCode = signal<string
  | null>(...)` driving the `CountryFieldConfig` lookups (computed
  `activeCountryConfig = computed(() =>
  COUNTRY_FIELD_CONFIG[this.countryCode() ?? ''] ?? COUNTRY_FIELD_CONFIG['DEFAULT'])`).
- `PhoneDdiInputComponent`: no signal state of its own — pure
  input/output, same shape as `AvatarUploadComponent`.

### Dependencies

None new — `CountryFieldConfig` is a hand-rolled const, `PhoneDdiInputComponent`
is a hand-rolled pair of native inputs, no ITU calling-code library, no
flag-icon asset package. **If the DDI-length approximation proves too
inaccurate in practice (e.g. real users routinely need 3-digit DDIs this
PLAN's seed map doesn't cover), reaching for `libphonenumber-js` (or
similar) would be a genuine new Tier 3 dependency decision — flagged
here as the likely next request, not pre-approved by this amendment.**

### Testing strategy (delta)

- `country-field-config.spec.ts` (new): `BR`/`US`/`GB`/unknown-code
  fallback-to-`DEFAULT` lookups return the expected label/mask shape.
- `input-mask.directive.spec.ts`: extend existing cases with a
  `country` input — `BR` + `taxId` still masks as before (regression);
  `GB` + `postalCode` (no mask defined) passes the raw value through
  unmodified, no masking applied.
- `phone-ddi-input.component.spec.ts` (new): renders DDI + number
  inputs; composes a valid E.164 string on change; splits an existing
  E.164 value back into DDI/number on init for `BR`/`US`-shaped inputs.
- `profile-fields-form.component.spec.ts`: update every existing
  `cpf`/`cep`-labeled assertion to `taxId`/`postalCode`; new cases —
  selecting a different `countryCode` updates the `taxId`/`postalCode`
  labels and mask behavior live without reload (SPEC acceptance
  criterion); the address fieldset renders exactly the 6 new fields,
  no old 8-field shape remains; a `PHONE`/`WHATSAPP` contact row shows
  `PhoneDdiInputComponent`, an `EMAIL`/`OTHER` row does not.
- No new i18n keys beyond whatever `CountryFieldConfig`'s labels
  literally are (plain English strings per the concrete entries above
  — if this app's existing i18n convention requires translated labels
  rather than literal strings, that's a Tier 2 follow-up flagged for
  whoever implements this, not resolved here, since it depends on
  reading the current i18n setup for `ProfileFieldsFormComponent`'s
  other labels first).

### AppSec note

No new PII surface — `countryCode`/address restructuring are the same
sensitivity class as the fields they replace (per backend SPEC's own
"no new sensitivity" privacy note); this frontend amendment introduces
no new backend call, no new stored credential, no new CORS surface.
**Mandatory `appsec` pass still applies at the backend-amendment level
(see that PLAN's AppSec gate) — nothing additional required here purely
for the frontend delta, but flag this amendment for the same appsec
review pass as a bundle, not a separate one, per this repo's "appsec
gate before TASKS.md" standing rule.**

### Deviations from this PLAN (discovered during implementation)

- **Proceeded without waiting for `identity-profile-model-v2`'s backend
  amendment to land in this checkout** (this PLAN's own top-of-section
  sequencing gate) — the backend implementation was in progress
  concurrently, in the same repo, when this frontend amendment was
  implemented; its DTOs (`ProfileFieldsDto`/`AddressDto`/
  `MandatoryProfileFieldsDto`) were not yet renamed at that point. Per
  explicit direction, this frontend work used the already-applied
  migration files (`V26__remove_rg_and_birth_date_fields.sql`/
  `V27__country_agnostic_identity_address.sql`) and the backend PLAN's
  amendment text as the frozen ground truth for field names/shapes
  instead of reading the (still-in-progress) Java DTOs directly. No
  file conflicts resulted (disjoint subprojects); the two efforts'
  target shapes agree (`taxId`, `countryCode`,
  `addressLine1`/`addressLine2`/`city`/`stateRegion`/`postalCode`).
- **`InputMaskDirective`'s per-country mask table is nested `Map`s, not
  `Record`s** (`shared/input-mask.directive.ts`'s `MASK_TABLE`,
  `country-field-config.ts`'s `COUNTRY_FIELD_CONFIG`/
  `DDI_LENGTH_BY_COUNTRY`/`DEFAULT_DDI_BY_COUNTRY`) — a `Record` with a
  dynamic string-key lookup (`obj[key]`) trips this repo's ESLint
  `security/detect-object-injection` rule (added when ESLint security
  rules landed in CI, per `knowly-app/CLAUDE.md`); `Map.get(key)` isn't
  a property-access expression the rule flags, so this sidesteps the
  warning without a `// eslint-disable` comment or any behavior change.
  Tier 2 (implementation-detail data-structure choice, not a scope
  change).
- **`formatGrouped` (`input-mask.directive.ts`) rewritten to iterate via
  `.entries()`/`.at()` instead of indexing plain arrays with a loop
  variable** — same ESLint rule, same reasoning, applied to array
  indexing rather than object-key indexing.
- **`ProfileFieldsFormComponent`'s `countryCode` `<select>` binds
  `[selected]` per-`<option>`, not `[value]` on the `<select>` itself**
  — a real bug caught by TDAD: a plain `[value]` property binding on
  the host `<select>` silently failed to select the matching `<option>`
  in tests, because Angular evaluates a parent element's own property
  bindings before its `@for`-generated child `<option>`s exist in the
  DOM on the very first change-detection pass (so `select.value = 'BR'`
  runs against a `<select>` with no `<option value="BR">` in it yet,
  and the browser silently no-ops the assignment). Fixed by moving the
  "which one is selected" decision onto each `<option>`'s own
  `[selected]="code === localFields().countryCode"` binding instead,
  which doesn't depend on element-creation ordering.
- **`PhoneDdiInputComponent` guards its resync effect against its own
  echoed emission** (`shared/phone-ddi-input.component.ts`'s
  `lastEmitted` field) — a real bug caught by TDAD, the same class of
  self-fighting-binding issue this PLAN's prior "Implemented (2026-08-02)"
  deviation note already documented for `InputMaskDirective`'s
  `[value]`/`formatMaskedValue` fix. Composing a DDI/number pair and
  emitting the result, which the parent stores and hands straight back
  as this component's `value` input on the next change-detection pass,
  would otherwise re-split that round-tripped E.164 string using
  `ddiLengthFor(countryCode)`'s fixed-length heuristic on every
  keystroke — silently overwriting a manually-typed DDI whose length
  doesn't match that heuristic's guess (e.g. a 1-digit DDI in a country
  seeded for 2). Fixed by tracking the component's own last-emitted
  value and skipping the resync when the incoming `value` matches it
  (i.e. it's an echo of our own emission, not a genuinely external
  change — initial load, an existing contact swapped in, etc. still
  resync normally).
- **`phone-ddi-input.component.spec.ts` was not written as a separate
  top-level spec file** — its behavior (render DDI + number inputs,
  compose on change, split an existing E.164 value on init) is covered
  by `profile-fields-form.component.spec.ts`'s "phone/WhatsApp contact
  rows" and "submitting a mix of..." cases instead, since
  `ProfileFieldsFormComponent` is this component's only real consumer
  and the shared spec already exercises it end-to-end (including the
  round-trip-through-the-parent scenario the `lastEmitted` fix above
  addresses, which a component-only spec wouldn't naturally cover).
  Tier 2 (test-organization call, not a coverage gap — flagged here
  since TASKS.md's task 46 literally names the file).
- **`tenant-creation`'s already-shipped `TenantCreatePageComponent`/
  `ActiveTenantService` were also updated**, not originally listed in
  this amendment's TASKS.md — the first admin's mandatory-profile
  section mirrors `MandatoryProfileFieldsDto`'s shape one-for-one (per
  that backend DTO's own doc comment, "reused as-is by staff creation,
  `addMember`, and the bootstrap completion endpoint"), so the same
  `taxId`/`countryCode`/6-field-address rename applies there too or the
  app doesn't build/type-check. `CreateTenantProfile`'s address and the
  company's own `CreateTenantAddress` now share field *names*
  (`city`/`postalCode`) for the first time (previously disambiguated by
  using Portuguese names for the user's address) — `AddressFieldsComponent`
  gained a new `idPrefix` input (default `''`, preserving every other
  call site's existing `data-testid`s unchanged) so the two address
  blocks' rendered `data-testid`s stay unique on the same page
  (`address-field-postalCode` vs. `address-field-user-postalCode`).
  `bootstrap-profile-completion`'s `CompleteProfilePageComponent` needed
  no further change beyond its already-shared `EMPTY_FIELDS` constant,
  since it composes `ProfileFieldsFormComponent` directly and inherits
  the new field set automatically, per that feature's own PLAN.md.
