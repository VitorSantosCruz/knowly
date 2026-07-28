# SPEC — user-profile (frontend)

> The what and the why. No technical implementation details.

## Context and motivation

`identity-profile-model` (backend, fully implemented and shipped —
`knowly-api/specify/features/identity-profile-model/{SPEC,PLAN,TASKS}.md`)
gives every `User` real personal-data fields (`fullName`, `address`,
`rg`, `cpf`, `phone`) plus a permission model governing who may view/edit
them, and a self-service edit-request flow for users with no direct-edit
right over their own record. Nothing in `knowly-app` consumes any of
this yet — `PROJECT_STATUS.md` item 6's note flags it explicitly:
"profile view/edit UI (belongs to item 13's not-yet-built frontend
half)." This SPEC is that frontend half.

This SPEC does not re-derive any backend rule. Every permission name,
who-can-view/edit-whom rule, and the request/approval flow are carried
over verbatim from `identity-profile-model/SPEC.md` (REQ-8 through
REQ-22) and `PLAN.md` (endpoint contracts, DTO shapes) — see "Backend
contract carried over" below for the exact mapping. Only the UI layout,
route/component naming, and screen composition are this SPEC's own
judgment calls (see "Judgment calls" section).

**Backend contract carried over (read-only reference, not re-litigated
here):**

| Endpoint | Purpose | Backend REQ |
|---|---|---|
| `GET /api/users/me/profile` | Caller's own full profile | REQ-8 |
| `GET /api/users/{id}/profile` | Another user's profile, permission-gated | REQ-9/10/10a/10b/10c |
| `PUT /api/users/{id}/profile` | Direct edit (admin/permission-holder-on-other) | REQ-11/12/13/13a/14/14a |
| `POST /api/users/me/profile/edit-requests` | Self-submitted edit request (no direct-edit right) | REQ-15, 409 on REQ-20 |
| `GET /api/profile-edit-requests` | List pending requests the caller may act on | — |
| `POST /api/profile-edit-requests/{id}/approve` | Approve, applies fields | REQ-17, 409 on REQ-21 |
| `POST /api/profile-edit-requests/{id}/reject` | Reject, discards fields | REQ-18 |

Fields: `fullName`, `address`, `rg`, `cpf`, `phone` are editable;
`email` is read-only everywhere in this feature (backend never accepts
it in `ProfileFieldsDto`).

**Who can directly edit whose record (carried over exactly, not
reinterpreted):**
- `MEMBER_ADMIN` of a tenant: any member of that tenant, including self
  (REQ-11).
- `STAFF_ADMIN`: any user, including self (REQ-12).
- Tenant-scoped `PROFILE_EDIT` holder (not acting as `MEMBER_ADMIN`):
  any *other* member of that tenant, never self (REQ-13/13a).
- Global-scoped `PROFILE_EDIT` holder (`STAFF`, not `STAFF_ADMIN`): any
  *other* user, never self (REQ-14/14a).
- Anyone else editing their own record: submits a pending edit request
  instead (REQ-15), requiring approval from a holder of the applicable
  edit right (REQ-16).

**Who can view whose record (carried over exactly):** self always
(REQ-8); tenant-scoped `PROFILE_VIEW` holder → any member of that
tenant (REQ-10); global-scoped `PROFILE_VIEW` holder → any user
(REQ-10a); `MEMBER_ADMIN` → any member of their tenant (REQ-10b);
`STAFF_ADMIN` → any user (REQ-10c).

## User stories

- As any user, I want to view and edit my own profile (name, address,
  RG, CPF, phone), so I can keep my personal data accurate.
- As a `MEMBER_ADMIN`/`STAFF_ADMIN`, I want my own profile edits to
  apply immediately, since I already have that admin power over my own
  record.
- As a plain member/staff user with no direct-edit right, I want my
  profile submission to become a pending request rather than silently
  failing, and I want to see that it's awaiting approval so I know why
  my change hasn't taken effect yet.
- As that same user, I want to be prevented from submitting a second
  request while one is already pending, with a clear reason why, rather
  than a confusing generic error.
- As a `MEMBER_ADMIN`, `STAFF_ADMIN`, or a holder of `PROFILE_VIEW`
  (tenant or global), I want to view another person's profile detail
  from the screen where I already manage/look at that person, without
  navigating to a whole separate app section.
- As a holder of the applicable edit right, I want a single place to see
  every pending profile-edit request I can act on, and approve or reject
  each one.
- As that same approver, I want a clean, understandable message if
  approving a request would conflict with another user's already-unique
  data (e.g. someone else already has that CPF), instead of a raw error.

## Requirements (EARS/GEARS)

### Own profile — view and edit

- **REQ-1 [Ubiquitous]** The system shall provide an own-profile screen
  showing `fullName`, `address`, `rg`, `cpf`, `phone` (editable) and
  `email` (read-only), sourced from `GET /api/users/me/profile`.
- **REQ-2 [Event-Driven]** When the caller holds a direct-edit right
  over their own record (per `identity-profile-model` REQ-11/12 —
  `MEMBER_ADMIN` or `STAFF_ADMIN`) and submits the own-profile form, the
  system shall call `PUT /api/users/{id}/profile` with their own id and
  apply the change immediately on success.
- **REQ-3 [Event-Driven]** When the caller holds no direct-edit right
  over their own record and submits the own-profile form, the system
  shall call `POST /api/users/me/profile/edit-requests` instead of the
  direct-edit endpoint.
- **REQ-4 [State-Driven]** While the caller has an unresolved
  profile-edit request pending, the own-profile screen shall show a
  visible "pending approval" state and disable resubmission of the form.
- **REQ-5 [Unwanted Behavior]** If `POST
  /api/users/me/profile/edit-requests` is rejected with 409 (an
  unresolved request already exists, per REQ-20), then the system shall
  show a clear, non-technical message explaining a request is already
  pending, and enter the same state REQ-4 describes.
- **REQ-6 [Unwanted Behavior]** If a direct-edit `PUT` call (REQ-2) is
  rejected with 409 (a uniqueness conflict on `rg`/`cpf`/`phone`/
  `address`), then the system shall show a clear, non-technical message
  naming the conflicting field(s) without exposing the other user's
  data, and leave the form's entered values intact for correction.
- **REQ-7 [Ubiquitous]** The own-profile screen shall never render an
  input for `email`, and shall never submit it in either the direct-edit
  or edit-request payload.

### Viewing another user's profile

- **REQ-8 [Optional Feature]** Where the viewer holds an applicable
  view right over a given user (tenant-scoped `PROFILE_VIEW`,
  global-scoped `PROFILE_VIEW`, `MEMBER_ADMIN` of that user's tenant, or
  `STAFF_ADMIN` — per `identity-profile-model` REQ-10/10a/10b/10c), the
  tenant members list's/staff directory's existing detail panel for
  that user shall gain a profile section showing `fullName`, `address`,
  `rg`, `cpf`, `phone`, `email`, sourced from
  `GET /api/users/{id}/profile`.
- **REQ-9 [Unwanted Behavior]** If `GET /api/users/{id}/profile` is
  rejected with 403 for a given detail panel, then only that panel's
  profile section shall show the existing non-technical permission-
  denied state, without affecting any other section already rendering
  on that panel (permissions/access-groups/audit-trail, per the existing
  section-scoped-failure pattern established by `staff-global-dashboard`
  REQ-12).
- **REQ-10 [Optional Feature]** Where the viewer additionally holds an
  applicable direct-edit right over that same user (per
  `identity-profile-model` REQ-11/12/13/14), the profile section shall
  also let them edit the fields inline and call
  `PUT /api/users/{id}/profile` on submit, refreshing the section on
  success.
- **REQ-11 [Unwanted Behavior]** If that direct-edit call (REQ-10) is
  rejected with 409, then the profile section shall show the same
  conflict message REQ-6 describes.

### Edit-request approval inbox

- **REQ-12 [Ubiquitous]** The system shall provide an edit-request
  inbox screen listing every pending profile-edit request the caller
  may act on, sourced from `GET /api/profile-edit-requests`, showing per
  request: requester identity, the proposed field values, and submission
  date.
- **REQ-13 [Event-Driven]** When the caller approves a request from the
  inbox, the system shall call `POST
  /api/profile-edit-requests/{id}/approve` and remove that request from
  the visible list on success.
- **REQ-14 [Event-Driven]** When the caller rejects a request from the
  inbox, the system shall call `POST
  /api/profile-edit-requests/{id}/reject` and remove that request from
  the visible list on success.
- **REQ-15 [Unwanted Behavior]** If an approve call is rejected with 409
  (a uniqueness conflict on the proposed `rg`/`cpf`/`phone`/`address`,
  per `identity-profile-model` REQ-21), then the system shall show a
  clear, non-technical message naming the conflicting field(s), leave
  the request visible and still pending in the inbox (it was not
  resolved), and not remove it from the list.
- **REQ-16 [Unwanted Behavior]** If an approve or reject call is
  rejected with 403 (the caller no longer holds the applicable right by
  the time of action, per REQ-19) or 409 for a reason other than REQ-15
  (already resolved by someone else), then the system shall show the
  existing non-technical error/permission-denied state and refresh the
  list so the stale request no longer appears.
- **REQ-17 [Unwanted Behavior]** If the caller holds no applicable edit
  right anywhere (never any `PROFILE_EDIT`/admin role over anyone), then
  the inbox's entry point (nav link and/or route) shall not be shown,
  consistent with this project's established "hidden, not
  shown-then-blocked" nav rule.
- **REQ-18 [Ubiquitous]** The edit-request inbox shall show an explicit
  empty state ("no pending requests") when the list is empty, distinct
  from a loading or error state.

## Non-functional requirements

- Design: follows the established "Ink & Signal" design system, reuses
  existing shared components (`error-state.component.ts`,
  `no-access-state.component.ts`, the existing detail-panel section
  pattern with `data-testid`-tagged `<section>`s) — no new component
  library, no new dependency.
- Accessibility: the own-profile form, the inline edit form on another
  user's profile section, and the inbox's approve/reject actions are
  fully keyboard-operable with clear focus states, matching
  `user-management`'s existing accessibility bar.
- Security: REQ-9/REQ-17's hiding/error-state behavior is UI-only —
  every underlying call is independently re-enforced server-side
  (`identity-profile-model`'s own authorization); this SPEC is never the
  real authorization boundary, matching the principle already
  established in `navigation-menu`/`user-management-screens`/
  `staff-global-dashboard`.
- Security: `rg`/`cpf` values are never logged to the browser console
  and never included in any client-side error message beyond "this
  field conflicts" (REQ-6/REQ-11/REQ-15 never echo the *other* user's
  conflicting value).
- No pagination on the edit-request inbox — matches
  `identity-profile-model`'s own endpoint contract (`GET
  /api/profile-edit-requests` returns a plain list, no envelope); if the
  volume of pending requests ever needs pagination, that's a separate,
  later SPEC on both sides.

## Acceptance criteria

- [ ] A user can view their own profile (`fullName`/`address`/`rg`/
      `cpf`/`phone` editable, `email` read-only).
- [ ] A `MEMBER_ADMIN`/`STAFF_ADMIN` editing their own profile sees the
      change applied immediately.
- [ ] A user with no direct-edit right submitting the own-profile form
      instead creates a pending edit request, and the screen shows a
      "pending approval" state with resubmission disabled.
- [ ] Submitting a second request while one is pending is prevented
      client-side (REQ-4) and, if attempted anyway, shows a clear
      "already pending" message on 409 rather than a raw error.
- [ ] A direct-edit uniqueness conflict (409) shows a clear message
      naming the conflicting field(s) without exposing another user's
      data, and preserves the form's entered values.
- [ ] A viewer with an applicable view right sees a profile section on
      the relevant user's existing detail panel (staff directory or
      tenant members); a viewer without it sees a permission-denied
      state scoped to only that section.
- [ ] A viewer who also holds a direct-edit right over that user can
      edit the profile section's fields inline and see the update
      reflected on success.
- [ ] The edit-request inbox lists every pending request the caller may
      act on (requester, proposed fields, submission date); approving or
      rejecting removes it from the visible list on success.
- [ ] An approval that conflicts with another user's unique data (409)
      shows a clear message and leaves the request still pending in the
      inbox.
- [ ] A stale approve/reject attempt (already resolved, or right revoked
      since page load) shows the existing error/permission-denied state
      and the list refreshes to drop it.
- [ ] A caller with no applicable edit right anywhere does not see the
      inbox's nav link/route at all.
- [ ] An empty inbox shows an explicit "no pending requests" state.
- [ ] `email` is never rendered as an editable field or submitted in any
      payload from this feature.
- [ ] `npm run format:check && npm test && npm run build` all pass.

## Out of scope

- **Chat display nickname** (`PROJECT_STATUS.md` item 14) — a distinct
  data field/screen, not touched here.
- **Role/permission management UI** — already covered by
  `user-management-screens`; this feature never renders a role/
  permission toggle, matching `identity-profile-model`'s own REQ-22
  ("nobody may use any mechanism in this feature to change a role or
  permission grant").
- **Tenant company-record fields** (`cnpj`/`razaoSocial`/`nomeFantasia`/
  `inscricaoEstadual`) — `identity-profile-model`'s own PLAN notes these
  are exposed, if at all, through the existing tenant-detail contract,
  not a new one; no tenant-record screen/edit UI is part of this
  feature.
- **A generic in-app notification inbox/bell** — no such frontend
  feature exists yet in `knowly-app` (verified: no
  `notification`-related component exists today). This SPEC's
  edit-request inbox (REQ-12) is a dedicated list screen consuming
  `GET /api/profile-edit-requests` directly; it does not build or
  surface the generic `Notification`/`GET /api/notifications`-style
  "list my unresolved notifications" mechanism `tenant-membership-
  acceptance` introduced. Building that generic inbox (which would also
  need to eventually cover membership-invitation notifications) is a
  separate, not-yet-scoped feature.
- **Audit-trail viewing of profile changes** — already covered by
  `staff-global-dashboard`'s existing audit-trail section on
  `StaffUserDetailPanelComponent`; this feature does not add a
  profile-specific audit view.
- **CPF/RG format/checksum validation or input masking** — matches
  `identity-profile-model`'s own "Out of scope" line (no format/checksum
  validation); this feature renders plain text inputs, no masked-input
  component.
- **Bulk approve/reject** in the inbox — one request acted on at a time,
  matching this repo's existing "no bulk actions" precedent
  (`user-management`/`user-management-screens`).
- **Pagination of the edit-request inbox** — matches the backend
  endpoint's own unpaginated contract; see "Non-functional requirements."
- **Any new backend endpoint or contract change** — this feature
  consumes `identity-profile-model`'s endpoints exactly as documented in
  its `PLAN.md`; no backend SPEC accompanies this frontend SPEC.
- **Extending audit-trail or profile viewing into the tenant-scoped
  `MembersPageComponent`'s existing member rows beyond what REQ-8
  already covers** — REQ-8 is written to cover both the staff directory
  and tenant members detail panels identically (both already have a
  per-person detail panel per `user-management`/`user-management-
  screens`), so no further extension is needed or implied.

## Judgment calls (Tier 2 — frontend-only, made without asking per this
task's explicit instruction; flag any of these before PLAN.md work
starts if they should instead be reconsidered)

1. **No new standalone route for "my profile."** Following
   `user-management-screens`'/`staff-global-dashboard`'s precedent of
   extending existing screens rather than adding parallel routes, the
   own-profile screen (REQ-1–7) is reachable as a new `/profile` route
   (a genuinely new destination — nothing existing today shows "my own
   data"), but viewing/editing *another* user's profile (REQ-8–11) is
   *not* a new route: it's a new section on the existing
   `StaffUserDetailPanelComponent` (staff directory) and on the existing
   member-detail panel in `MembersPageComponent` (tenant members) — same
   "extend the existing detail panel" precedent `staff-global-dashboard`
   set for its audit-trail section, applied to both existing detail
   panels rather than inventing one shared new panel component.
2. **`/profile` is a new top-level route, not folded into
   `/welcome` or the nav's existing user menu.** Chosen because a
   profile screen is substantial (5 editable fields, pending-state UI,
   conflict handling) — mirroring `dashboard`/`members`'s own weight as
   dedicated routes rather than being squeezed into a menu dropdown. A
   nav entry ("My profile") is added to the existing top header/user
   area, visible to every authenticated user regardless of tenant
   context (own-profile viewing is universal per REQ-8 of the backend
   SPEC — unlike every other nav item in this app, it does not disappear
   inside/outside a tenant, since it's about the caller's own identity,
   not tenant-scoped data).
3. **The edit-request inbox is a new, separate route
   (`/profile-edit-requests`, nav-gated per REQ-17)** rather than a tab
   inside `/profile` or folded into the staff directory / members
   screen. Chosen because its audience (anyone holding *any* applicable
   edit right — tenant `PROFILE_EDIT`, global `PROFILE_EDIT`,
   `MEMBER_ADMIN`, `STAFF_ADMIN`) doesn't map cleanly onto either
   existing screen's own context-switching rule (`/members` already
   branches structurally on tenant-vs-staff context; overloading it
   further with a third, orthogonal "pending requests" concern would
   fight that existing branch rather than extend it). A dedicated route
   keeps the "one screen, two contexts" pattern intact for `/members`
   and avoids conflating "manage this tenant's members" with "approve
   edits to someone's personal data," which are different capabilities
   even when the same person holds both.
4. **Own-profile pending state (REQ-4) is derived client-side by
   attempting the submission and reacting to the 409, not by a
   dedicated "do I have a pending request" GET on page load.** No
   backend endpoint exists for "does the current user have a pending
   request" as a standalone check (`GET /api/profile-edit-requests` only
   lists requests the caller may *approve*, not their own submitted
   one) — so REQ-4's pending state is set the first time REQ-5's 409 is
   observed in the current session and persists until the user
   navigates away/reloads (matching this repo's general precedent of
   not inventing new backend surface area for a frontend-only SPEC).
   This means a returning user whose request is still pending from a
   *previous* session will not see the pending banner until they submit
   again and get the 409 — flagged as an accepted, minor rough edge
   given no read endpoint exists for this state; a future backend
   addition (e.g. an "own pending request" field on
   `GET /api/users/me/profile`) could close this gap later without
   changing this SPEC's other behavior.
5. **Inline edit on another user's profile section (REQ-10) reuses the
   same form component as the own-profile screen** rather than building
   a second form — same fields, same validation, same conflict-handling
   copy, parameterized only by target user id and whether direct-edit
   vs. edit-request submission applies (own-profile screen only ever
   uses REQ-2/3's branch; the detail-panel section only ever uses direct
   edit, since REQ-8/10 already gate on the viewer holding a direct-edit
   right — a viewer with only view rights, no edit rights, over another
   user never sees an edit affordance, and the self-request flow is
   exclusively an own-profile concept, never applicable to editing
   someone else's record).
