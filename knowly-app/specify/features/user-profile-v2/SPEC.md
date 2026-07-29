# SPEC — user-profile-v2 (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

The already-shipped frontend feature `user-profile`
(`knowly-app/specify/features/user-profile/{SPEC,PLAN,TASKS}.md`, 321/321
tests green, committed) was built against `identity-profile-model`'s
**old flat contract** (`fullName`/`address` free-text string/`rg`/`cpf`/
`phone` on `GET/PUT /api/users/{id}/profile`). That backend contract has
since been redesigned — see `knowly-api/specify/features/
identity-profile-model-v2/{SPEC,PLAN}.md` and `DECISIONS.md`'s
"`identity-profile-model` retrofit" entry — into three tables
(`user_profiles`/`addresses`/`contacts`), a smaller LGPD-minimized field
set, and a materially different per-field permission model
(`avatar_url` is now the *only* directly self-editable field; everything
else, including `birth_date`, requires the self-edit-request flow, with
no exception for `MEMBER_ADMIN`/`STAFF_ADMIN` editing their own record —
a real capability removal from what today's shipped frontend currently
assumes).

The product owner confirmed (2026-07-28) this redesign proceeds and that
the already-shipped frontend must be retrofitted to match, not
deferred. This SPEC is that retrofit. It supersedes `user-profile`'s
SPEC for every requirement whose backend contract changed; requirements
whose UI behavior doesn't depend on the changed fields (e.g. the
edit-request inbox's approve/reject mechanics, the "hidden, not
shown-then-blocked" nav rule) are restated here unchanged so this SPEC
is independently complete.

This SPEC does not re-derive any backend rule — every permission name
and who-can-view/edit-whom rule is carried over verbatim from
`identity-profile-model-v2/SPEC.md`. Only UI layout, route/component
naming, and screen composition are this SPEC's own judgment calls.

**Backend contract carried over (read-only reference, not re-litigated
here — see `identity-profile-model-v2/PLAN.md` for the authoritative
DTO shapes):**

| Endpoint | Purpose |
|---|---|
| `GET /api/users/me/profile` | Caller's own full profile |
| `GET /api/users/{id}/profile` | Another user's profile, permission-gated |
| `PUT /api/users/{id}/profile` | Direct edit of non-avatar fields (admin/permission-holder-on-*other*-user only — never self) |
| `POST /api/users/me/profile/edit-requests` | Self-submitted edit request for any non-avatar field |
| `POST /api/users/me/profile/avatar` | Self, unconditional avatar upload (multipart) |
| `GET /api/profile-edit-requests` | List pending requests the caller may act on |
| `POST /api/profile-edit-requests/{id}/approve` | Approve, applies fields + contact changes |
| `POST /api/profile-edit-requests/{id}/reject` | Reject, discards proposed changes |

Fields, now: `fullName`, `cpf`, `rg`, `rgOrgaoEmissor`, `birthDate`,
`address` (structured: `cep`/`logradouro`/`numero`/`complemento`/
`bairro`/`cidade`/`estado`/`pais`), `contacts` (list of
`{type, value, label, isPrimary}`, up to 5, `type` one of `PHONE`/
`WHATSAPP`/`EMAIL`/`OTHER`) — all request-only, never direct-self-edit.
`avatarUrl` — self-edit, dedicated upload endpoint, never part of the
request/edit-request DTOs. `email` — read-only everywhere, unchanged.

**Who can directly edit whose non-avatar fields (carried over exactly):**
- `MEMBER_ADMIN` of a tenant: any *other* member of that tenant — no
  longer including self (this is the behavior change from `user-profile`).
- `STAFF_ADMIN`: any *other* user — no longer including self.
- Tenant-scoped `PROFILE_EDIT` holder (not `MEMBER_ADMIN`): any *other*
  member of that tenant, never self (unchanged from `user-profile`).
- Global-scoped `PROFILE_EDIT` holder (`STAFF`, not `STAFF_ADMIN`): any
  *other* user, never self (unchanged).
- **Anyone, including admins, editing their own non-avatar fields**:
  always submits a pending edit request (no direct-edit branch exists
  for self anymore, for anyone).
- **Anyone editing their own `avatarUrl`**: always direct, unconditional,
  no approval step, regardless of role/permission.

**Who can view whose record (unchanged from `user-profile`):** self
always; tenant-scoped `PROFILE_VIEW` holder → any member of that tenant;
global-scoped `PROFILE_VIEW` holder → any user; `MEMBER_ADMIN` → any
member of their tenant; `STAFF_ADMIN` → any user.

## User stories

- As any user, I want to change my own avatar immediately, without
  anyone else's approval.
- As any user — even a `MEMBER_ADMIN`/`STAFF_ADMIN` — I want my
  submission of any other profile field (name, CPF, RG, birth date,
  address, contacts) to become a pending request, and to understand
  clearly that this is now true for me too, not just for lower-privilege
  users.
- As a user with no pending request, I want to enter a structured
  address (CEP, street, number, etc.) instead of one free-text field.
- As a user, I want to manage a list of up to 5 contacts (phone,
  WhatsApp, email, other), mark one as primary per type, and remove ones
  I no longer use.
- As a holder of the applicable edit right, I want to approve or reject
  a pending request that now includes contact add/update/remove entries,
  not just flat field values.
- As a returning user of the already-shipped screens, I don't want to
  relearn a different app — the inbox, nav gating, section placement,
  and error-handling shapes stay the same; only the form fields and the
  self-edit-vs-request boundary change.

## Requirements (EARS/GEARS)

### Own profile — view and edit

- **REQ-1 [Ubiquitous]** The own-profile screen shall show `fullName`,
  `cpf`, `rg`, `rgOrgaoEmissor`, `birthDate`, a structured address
  (`cep`/`logradouro`/`numero`/`complemento`/`bairro`/`cidade`/`estado`/
  `pais`), and a list of up to 5 contacts (`type`/`value`/`label`/
  `isPrimary`) — all editable only via the pending-request flow — plus
  `email` (read-only) and `avatarUrl` (directly editable, see REQ-8).
- **REQ-2 [Event-Driven]** When the caller submits the own-profile
  form's non-avatar fields, the system shall always call `POST
  /api/users/me/profile/edit-requests`, regardless of the caller's
  role or permissions — there is no direct-edit branch for a caller's
  own non-avatar fields, for anyone. **This replaces `user-profile`'s
  old REQ-2/REQ-3 branching logic** (which called `PUT` directly for
  `MEMBER_ADMIN`/`STAFF_ADMIN`) with a single unconditional path.
- **REQ-3 [State-Driven]** While the caller has an unresolved
  profile-edit request pending, the own-profile screen shall show a
  visible "pending approval" state and disable resubmission of the
  non-avatar form. (Unchanged shape from `user-profile` REQ-4, still
  client-side-only per Judgment call 3 below.)
- **REQ-4 [Unwanted Behavior]** If `POST
  /api/users/me/profile/edit-requests` is rejected with 409 (an
  unresolved request already exists), then the system shall show a
  clear, non-technical "already pending" message and enter the REQ-3
  state. (Unchanged from `user-profile` REQ-5.)
- **REQ-5 [Ubiquitous]** The own-profile screen shall never render an
  input for `email`, and shall never submit it in the edit-request
  payload. (Unchanged from `user-profile` REQ-7.)
- **REQ-6 [Ubiquitous]** The contacts section of the form shall let the
  caller add, edit, and remove entries up to a maximum of 5, select a
  `type` from the four supported values, and mark at most one entry per
  `type` as primary.
- **REQ-7 [Unwanted Behavior]** If the caller attempts to add a 6th
  contact, then the system shall prevent the addition client-side with a
  clear message before ever calling the backend, matching the backend's
  own REQ-3a cap.

### Own avatar — direct, unconditional edit

- **REQ-8 [Event-Driven]** When the caller selects a new avatar image
  and confirms, the system shall call `POST
  /api/users/me/profile/avatar` (multipart) immediately — no pending
  state, no approval step, regardless of role or permission — and
  update the displayed avatar on success.
- **REQ-9 [Unwanted Behavior]** If the avatar upload is rejected (400,
  unsupported type or too large), then the system shall show a clear,
  non-technical message and leave the previous avatar displayed.

### Viewing/editing another user's profile

- **REQ-10 [Optional Feature]** Where the viewer holds an applicable
  view right over a given user, the tenant members list's/staff
  directory's existing detail panel for that user shall show a profile
  section with the new field set (`fullName`/`cpf`/`rg`/
  `rgOrgaoEmissor`/`birthDate`/structured address/contacts/`email`),
  sourced from `GET /api/users/{id}/profile`. `avatarUrl` is displayed
  here too (read-only — REQ-8 is self-only, so no other viewer can edit
  someone else's avatar).
- **REQ-11 [Unwanted Behavior]** If `GET /api/users/{id}/profile` is
  rejected with 403 for a given detail panel, then only that panel's
  profile section shall show the existing non-technical permission-
  denied state, without affecting any other section on that panel.
  (Unchanged from `user-profile` REQ-9.)
- **REQ-12 [Optional Feature]** Where the viewer additionally holds an
  applicable direct-edit right over that same *other* user (per
  `identity-profile-model-v2`'s REQ-12/13), the profile section shall
  let them edit that other user's non-avatar fields inline and call
  `PUT /api/users/{id}/profile` on submit, refreshing the section on
  success. **This inline edit affordance shall never appear when the
  viewer is looking at their own row** — even a `MEMBER_ADMIN`/
  `STAFF_ADMIN` viewing themself in the same list they manage others
  from sees the section in view-only mode, matching REQ-2's
  "no self-direct-edit for anyone" rule.
- **REQ-13 [Unwanted Behavior]** If that direct-edit call (REQ-12) is
  rejected with 409 (uniqueness conflict on `cpf`/`rg`), then the
  profile section shall show a clear, non-technical message naming the
  conflicting field(s) without exposing the other user's data.
  (Unchanged shape from `user-profile` REQ-11.)

### Edit-request approval inbox

- **REQ-14 [Ubiquitous]** The edit-request inbox shall list every
  pending request the caller may act on, sourced from `GET
  /api/profile-edit-requests`, showing per request: requester identity,
  the proposed field values (including the structured address), the
  proposed contact changes (add/update/remove, per entry), and
  submission date. (Extends `user-profile` REQ-12 with the new
  contact-changes display.)
- **REQ-15 [Event-Driven]** When the caller approves a request, the
  system shall call `POST /api/profile-edit-requests/{id}/approve` and
  remove that request from the visible list on success. (Unchanged.)
- **REQ-16 [Event-Driven]** When the caller rejects a request, the
  system shall call `POST /api/profile-edit-requests/{id}/reject` and
  remove that request from the visible list on success. (Unchanged.)
- **REQ-17 [Unwanted Behavior]** If an approve call is rejected with 409
  (a `cpf`/`rg` uniqueness conflict), then the system shall show a
  clear, non-technical message naming the conflicting field(s), leave
  the request visible and still pending, and not remove it from the
  list. (Unchanged shape from `user-profile` REQ-15.)
- **REQ-18 [Unwanted Behavior]** If an approve or reject call is
  rejected with 403 or a non-conflict 409 (already resolved/cancelled by
  someone else, or by the migration per `identity-profile-model-v2`
  REQ-25), then the system shall show the existing non-technical
  error/permission-denied state and refresh the list so the stale
  request no longer appears. (Unchanged shape from `user-profile`
  REQ-16.)
- **REQ-19 [Unwanted Behavior]** If the caller holds no applicable edit
  right anywhere, then the inbox's entry point (nav link and/or route)
  shall not be shown. (Unchanged from `user-profile` REQ-17.)
- **REQ-20 [Ubiquitous]** The edit-request inbox shall show an explicit
  empty state when the list is empty, distinct from loading or error.
  (Unchanged from `user-profile` REQ-18.)

## Non-functional requirements

- Design: follows "Ink & Signal," reuses existing shared components — no
  new component library, no new dependency. (Unchanged.)
- Accessibility: the own-profile form (including the new structured
  address fields and contacts list add/remove/edit controls), the
  inline edit form on another user's profile section, the avatar upload
  control, and the inbox's approve/reject actions are all fully
  keyboard-operable with clear focus states. (Extends `user-profile`'s
  bar to the new contacts/address UI.)
- Security: this SPEC is never the real authorization boundary — every
  underlying call is independently re-enforced server-side. (Unchanged.)
- Security: `cpf`/`rg` values are never logged to the browser console
  and never included in any client-side error message beyond "this
  field conflicts." (Unchanged.)
- No pagination on the edit-request inbox. (Unchanged.)
- No CPF/RG format/checksum validation or masked input; no client-side
  address (CEP) lookup/autofill — plain text inputs for every structured
  address field, matching `identity-profile-model-v2`'s own "out of
  scope: CPF/RG format/checksum validation" and this feature's own
  scope discipline (a CEP-to-address autofill integration would be new
  third-party API surface, out of scope here).

## Acceptance criteria

- [ ] A user can view their own profile with the new field set;
      `email` read-only, `avatarUrl` directly editable, everything else
      request-only.
- [ ] Submitting the own-profile non-avatar form always creates a
      pending request, for every role including `MEMBER_ADMIN`/
      `STAFF_ADMIN` — no session ever sees an immediate-apply path for
      those fields anymore.
- [ ] Avatar upload applies immediately for every session, with no
      pending state.
- [ ] A second non-avatar submission while one is pending is prevented
      client-side and shows the "already pending" message on 409.
- [ ] The contacts UI enforces the 5-entry cap client-side and supports
      per-type primary selection.
- [ ] A viewer with an applicable view right sees the new profile
      section (including structured address, contacts, read-only
      avatar) on the relevant detail panel; without it, sees a
      section-scoped permission-denied state.
- [ ] A viewer with an applicable edit right over an *other* user can
      edit that user's non-avatar fields inline; the same viewer never
      sees an edit affordance on their own row in that same list.
- [ ] The edit-request inbox lists every pending request including its
      contact changes; approve/reject work as before; a uniqueness
      conflict on approve keeps the row and shows a clear message; a
      stale/403 result refreshes the list.
- [ ] A caller with no applicable edit right anywhere does not see the
      inbox's nav link/route.
- [ ] An empty inbox shows the distinct empty state.
- [ ] `email` is never rendered as editable or submitted in any payload.
- [ ] `npm run format:check && npm test && npm run build` all pass.

## Out of scope

- Everything already out of scope for `user-profile` and unaffected by
  the field-set change: chat display nickname, role/permission
  management UI, a generic in-app notification inbox, audit-trail
  viewing, CPF/RG format/checksum validation, bulk approve/reject,
  pagination of the inbox, any new backend endpoint beyond what
  `identity-profile-model-v2` already defines.
- **CEP-to-address autofill/lookup** (a real third-party integration,
  not requested, not part of `identity-profile-model-v2`'s own scope
  either).
- **Multiple addresses per user** — backend confirms a single current
  address only; no UI for "add a second address" exists or is implied.
- **Avatar image cropping/editing UI** — a raw file picker/upload only;
  no client-side crop/resize tool.
- **`social_name`/`gender`/`nationality` fields** — confirmed cut from
  the data model entirely; no UI renders them.

## Judgment calls (Tier 2 — frontend-only; flag any of these before
PLAN.md work starts if they should instead be reconsidered)

1. **Route/component structure carried over unchanged from
   `user-profile`** (`/profile` own-profile, `/profile-edit-requests`
   inbox, `ProfileSectionComponent` embedded in the two existing detail
   panels) — the already-shipped structural decisions (SPEC judgment
   calls 1-3 in `user-profile/SPEC.md`) are about screen composition,
   not about the changed field set/permission model, so they're kept as
   the least-disruptive retrofit rather than re-litigated.
2. **`ProfileFieldsFormComponent` is retrofitted in place, not
   replaced**, to render the new field set (structured address block,
   contacts list editor) instead of the old flat `address`/`phone`
   inputs — still the single shared form used by both the own-profile
   screen and the other-user inline edit, per the same reuse rationale
   `user-profile`'s judgment call 5 already established. The component
   grows materially (address sub-fields + a repeatable contacts list),
   accepted as proportionate rather than splitting it, since "same
   fields, same validation, same conflict-handling copy" still holds
   for the *new* field set just as it did for the old one.
3. **REQ-3's pending-state detection stays client-side-only, reactive to
   a 409** — same accepted rough edge `user-profile`'s judgment call 4
   already documented (no backend "do I have a pending request" read
   endpoint exists in `identity-profile-model-v2` either); not
   re-litigated, since nothing about the retrofit closes this gap.
4. **Avatar upload gets its own small, separate UI affordance** (e.g. an
   "change avatar" button/file input near the profile header) rather
   than being folded into `ProfileFieldsFormComponent`'s submit flow —
   because REQ-2/REQ-8 are now two structurally different submission
   paths (always-request vs. always-direct-multipart) for the *same*
   screen, conflating them into one form/one submit button would make
   that split invisible to the user exactly where clarity matters most
   (this is the field that behaves differently from every other field on
   the same screen).
5. **The other-user detail-panel inline edit affordance (REQ-12) is
   hidden, not merely disabled, when the viewer is looking at their own
   row** — chosen over `user-profile`'s previous behavior (which allowed
   an admin's own row to show the edit affordance) because REQ-2 removed
   that capability entirely at the backend; showing a disabled/
   would-be-rejected control for the viewer's own row would be confusing
   given every other row in the same list *does* show it. Determining
   "is this the viewer's own row" reuses the same own-`userId` value
   `OwnProfilePageComponent` already reads off `GET
   /api/users/me/profile`, fetched once and compared against each row's
   `userId` (already present on `MemberDto`/`MemberDetailDto`/staff
   directory entries per `user-profile`'s already-resolved deviation).
