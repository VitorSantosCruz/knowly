# SPEC — staff-user-provisioning

> The what and the why. No technical implementation details.

## Changelog / Amends

- **2026-08-02**: Amended per explicit product-owner direction to fold
  in, as a formal request-contract requirement of *this* SPEC (not left
  to PLAN.md), the combination of two sibling SPECs that were approved
  in the same session:
  - `mandatory-complete-profile` (REQ-7): every staff-creation request
    after bootstrap must supply a complete profile or the creation is
    rejected outright, with no partial/pending row ever persisted.
  - `user-role-selection-at-creation` (REQ-1–REQ-5): the creation
    request may optionally specify `role` (`STAFF`/`STAFF_ADMIN`,
    default `STAFF`), gated so only a caller who is currently
    `STAFF_ADMIN` may specify `STAFF_ADMIN`.
  New REQ-7 through REQ-10 below state the resulting contract
  explicitly. This is additive to REQ-1–REQ-6 above (unchanged) — no
  prior requirement is reversed. This amendment applies **only** to the
  normal, post-bootstrap creation path (`createStaffUser`); the
  bootstrap `STAFF_ADMIN` row continues to be created email-only with
  no profile data, per `mandatory-complete-profile` REQ-1/REQ-2 and
  `staff-bootstrap-user`'s own SPEC — CPF/RG/address/contact
  requirements are **not** reintroduced for that one exception.

## Context and motivation

Both `staff-bootstrap-user` and `staff-rbac-split` explicitly deferred
this: there is still no way for any staff account — `STAFF_ADMIN` or a
granted `STAFF` — to create *another* staff user. The bootstrap user
solves the very first login; `staff-rbac-split` lets you manage an
*existing* staff user's global permissions; neither lets you bring a new
person onto the staff roster at all. Today the only way a second staff
account can exist is another database migration, which doesn't scale
past the one bootstrap case it was built for.

Separately, tenant member provisioning (`TenantService.addMember`)
already creates a `User` row today, and that's already sufficient for a
first login: the existing passwordless login-code flow
(`specify/features/authentication/SPEC.md`) works for any `User` row
purely from its email, no password of any kind required. So unlike
staff, tenant member provisioning has no functional gap — this feature
is scoped to staff provisioning specifically.

## User stories

- As a `STAFF_ADMIN` (or a `STAFF` user granted the right permission), I
  want to create a new staff user by email so that I can bring a new
  team member onto the platform without needing a database migration.
- As a newly-provisioned staff user, I want a way to log in immediately
  after being created — not just wait for someone to separately trigger
  a login-code — so that onboarding doesn't depend on a second manual
  step.

## Requirements (EARS/GEARS)

- **REQ-1 [Event-Driven]** When an authorized staff user creates a new
  staff user by email, the system shall create exactly one `User` row
  with `GlobalRole.STAFF` (the new, permission-gated tier — never
  `STAFF_ADMIN`; see REQ-6) and no tenant membership, the same shape as
  `staff-bootstrap-user`'s row.
  **Amended 2026-08-02:** REQ-6's "never `STAFF_ADMIN`" wording is
  narrowed by REQ-9/REQ-10 below — a caller who is themselves
  `STAFF_ADMIN` may now specify `role=STAFF_ADMIN` on the request. The
  default (no `role` supplied) remains `STAFF`, unchanged.
- **REQ-2 [Unwanted Behavior]** If the given email already belongs to an
  existing `User` (staff or not), then the system shall reject the
  request rather than silently reusing or upgrading that account.
- **REQ-3 [Event-Driven]** When a new staff user is created, the system
  shall generate and email them a one-time password (the existing
  `OneTimePasswordService`/`MailService` mechanism — no new credential
  type), giving them an immediate way to log in in addition to the
  always-available login-code flow.
- **REQ-4 [Ubiquitous]** Creating a staff user shall be its own,
  independent global permission (`GlobalPermission.STAFF_USER_CREATE`)
  — distinct from `STAFF_PERMISSION_MANAGE` (managing an *existing*
  staff user's permissions) — following `staff-rbac-split`'s established
  principle that no permission implies any other.
  **Narrowed by `role-model-refinement` (2026-07-26):** despite this
  requirement, `createStaffUser` is now gated to `STAFF_ADMIN` only —
  holding `STAFF_USER_CREATE` as a plain `STAFF` no longer suffices. See
  that feature's SPEC, Decision 2.
- **REQ-5 [Event-Driven]** When a staff user is created, the system
  shall record an audit event (actor, action, outcome, the new user's
  id), per the constitution's audit requirements.
  **Amended 2026-08-02:** the audit event shall also record the assigned
  `GlobalRole` (`STAFF` or `STAFF_ADMIN`), per
  `user-role-selection-at-creation`'s observability requirement.
- **REQ-6 [Ubiquitous]** A newly-created staff user's `GlobalRole` shall
  always be `STAFF`, never `STAFF_ADMIN` — this feature provisions
  permission-gated staff only; promoting someone to `STAFF_ADMIN` is not
  addressed here (see Out of scope).
  **Amended 2026-08-02:** this default still holds when `role` is
  omitted or explicitly `STAFF` (see REQ-9). It no longer holds
  unconditionally — REQ-10 defines the one case (caller is
  `STAFF_ADMIN`, explicitly requesting `role=STAFF_ADMIN`) where the new
  row is created as `STAFF_ADMIN` instead. Promoting an *existing*
  `STAFF` user after the fact remains entirely out of scope, unchanged.
- **REQ-7 [Ubiquitous]** *(New 2026-08-02, folding in
  `mandatory-complete-profile` REQ-7.)* The staff-creation request
  payload shall require, in addition to the email address already
  required by REQ-1/REQ-2, every one of the following fields:
  - `full_name`
  - `birth_date`
  - `cpf`
  - `rg`
  - `rg_orgao_emissor`
  - a complete address: `cep`, `logradouro`, `numero` (optional),
    `complemento` (optional), `bairro`, `cidade`, `estado`, `pais`
  - at least one contact (`type` + `value`)

  This is the same field set and the same optionality of `numero`/
  `complemento` as `identity-profile-model-v2`'s existing schema and
  `mandatory-complete-profile`'s completeness definition — no new field
  is introduced here.
- **REQ-8 [Unwanted Behavior]** If a staff-creation request omits any
  field listed in REQ-7, then the system shall reject the request in
  its entirety: no `User` row, no `user_profiles` row, no address or
  contact row, and no other partial state of any kind is persisted.
  This rejection is unconditional and applies to every staff-creation
  request under this SPEC — there is no pending/incomplete state for
  any account created this way (that mechanism exists only for the
  bootstrap `STAFF_ADMIN` row, per `mandatory-complete-profile` REQ-1–
  REQ-6, and is out of scope here).
- **REQ-9 [Optional Feature]** *(New 2026-08-02, folding in
  `user-role-selection-at-creation` REQ-1/REQ-4.)* The staff-creation
  request payload shall accept an optional `role` field whose only
  valid values are `STAFF` and `STAFF_ADMIN`. Where `role` is omitted or
  explicitly `STAFF`, the new `User` row is created with
  `GlobalRole.STAFF` (REQ-6's default, unchanged).
- **REQ-10 [Complex]** *(New 2026-08-02, folding in
  `user-role-selection-at-creation` REQ-2/REQ-3.)* When a staff-creation
  request specifies `role=STAFF_ADMIN`:
  - if the caller is currently `STAFF_ADMIN`, the system shall create
    the new `User` row with `GlobalRole.STAFF_ADMIN` and record an
    audit event including the assigned role;
  - if the caller is not currently `STAFF_ADMIN` — including a `STAFF`
    caller holding `STAFF_USER_CREATE` or any other directly-granted or
    access-group permission — the system shall reject the request
    outright and create no user, mirroring
    `staff-rbac-management-operations` REQ-21's rule that only a
    `STAFF_ADMIN` may act on an admin-tier target.

  No "last admin"/floor-or-ceiling check applies to this path — that
  safeguard governs demotion/deletion only, never creation of an
  additional admin.

## Non-functional requirements

- Security: default-deny — a freshly created staff user has zero global
  permissions until explicitly granted via `staff-rbac-split`'s existing
  grant endpoints; this feature does not itself grant anything beyond
  existing.
- Security: the one-time password follows the exact same hashing/expiry
  rules already established for `User.oneTimePasswordHash`.
- Security: REQ-7/REQ-8's completeness precondition and REQ-9/REQ-10's
  role-authorization check reuse existing, already-decided rules
  (`mandatory-complete-profile`'s completeness definition;
  `staff-rbac-management-operations` REQ-21's admin-tier-caller rule) —
  neither invents a new rule.
- Observability: per REQ-5, extended to include the assigned role.

## Acceptance criteria

- [x] A `STAFF_ADMIN` can create a new staff user by email; the new user
      appears with `GlobalRole.STAFF` and no permissions.
- [x] A `STAFF` user granted `STAFF_USER_CREATE` can do the same; a
      `STAFF` user without it is rejected.
      **Narrowed by `role-model-refinement` (2026-07-26):** `createStaffUser`
      is now `STAFF_ADMIN`-only — a `STAFF` user holding `STAFF_USER_CREATE`
      is rejected too. See that feature's SPEC, Decision 2. This line is
      kept as the historical record of this feature's original acceptance
      criterion; it no longer reflects current behavior.
- [x] A `STAFF` user granted only `STAFF_PERMISSION_MANAGE` (not
      `STAFF_USER_CREATE`) cannot create a new staff user.
- [x] Attempting to create a staff user with an email that already
      exists (staff or tenant member) is rejected, not silently merged.
- [x] The new staff user receives a one-time password by email and can
      log in with it; they can also log in via the ordinary login-code
      flow without ever having received that email.
- [x] Creating a staff user emits an audit event.
- [ ] **New 2026-08-02**: a staff-creation request missing any of
      `full_name`, `birth_date`, `cpf`, `rg`, `rg_orgao_emissor`, a
      complete address, or at least one contact is rejected outright —
      no `User`/`user_profiles`/address/contact row is created.
- [ ] **New 2026-08-02**: a staff-creation request supplying every
      required field succeeds and the resulting account has a complete
      profile from the moment it exists — never a pending state.
- [ ] **New 2026-08-02**: a `STAFF_ADMIN` can create a new staff user
      with `role=STAFF_ADMIN`; the new user has `GlobalRole.STAFF_ADMIN`
      and the audit event records that role.
- [ ] **New 2026-08-02**: a `STAFF` caller (with or without
      `STAFF_USER_CREATE` or any other permission) requesting
      `role=STAFF_ADMIN` is rejected; no user is created.
- [ ] **New 2026-08-02**: a staff-creation request with `role=STAFF` or
      no `role` field behaves exactly as before (new user is `STAFF`).
- [ ] **New 2026-08-02**: creating a `STAFF_ADMIN` this way succeeds
      regardless of how many `STAFF_ADMIN`s already exist (no floor/
      ceiling check applies).

## Out of scope

- Promoting an existing `STAFF` user to `STAFF_ADMIN`, or demoting a
  `STAFF_ADMIN` — no mechanism exists for either today (only the
  `staff-bootstrap-user` migration and this feature's creation path put
  a role on a `User`); if you need this, it's a separate, later decision
  (who's authorized to mint another `STAFF_ADMIN` **after the fact** is
  a bigger question than this SPEC should silently answer — REQ-10
  above only covers choosing the role **at creation time**, not
  promotion/demotion of an existing account).
- Any change to tenant member provisioning (`addMember`) — covered by
  its own amendment in `tenancy/SPEC.md` (2026-08-02), not here.
- Deactivating/removing a staff user — this feature only adds staff,
  it doesn't yet let you take one away (tenant members already have this
  via `removeMember`'s soft-removal; staff doesn't yet, and isn't
  addressed here).
- Any UI for this — a separate, later roadmap item (staff user
  management screens).
- Reintroducing profile-completeness requirements (CPF/RG/address/
  contact) for the bootstrap `STAFF_ADMIN` row — that account remains
  email-only at creation, with its own pending-profile-completion
  mechanism, per `mandatory-complete-profile` REQ-1–REQ-6. REQ-7/REQ-8
  above apply only to `createStaffUser`, never to the bootstrap
  migration.
</content>
