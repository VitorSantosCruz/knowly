# SPEC — mandatory-complete-profile

> The what and the why. No technical implementation details.

## Context and motivation

The product owner wants a hard rule: **no `User` may exist in a usable
state without a complete profile** — this applies equally to tenant
members and to staff, and explicitly includes the system's own
bootstrap/first `STAFF_ADMIN` account (created today by
`staff-bootstrap-user`'s Flyway migration with nothing but an email).
There is no exception for "the first user" in terms of *the rule itself*
— but the *mechanism* by which the rule is enforced is deliberately
different for the bootstrap account than for every other account (see
"Two distinct enforcement mechanisms" below — confirmed explicitly by
the product owner after this SPEC's first draft assumed a single
mechanism for everyone).

This is new product scope, not a gap in an already-decided design:
`identity-profile-model-v2` (`knowly-api/specify/features/
identity-profile-model-v2/SPEC.md`) deliberately made every
`user_profiles`/`addresses`/`contacts` field **optional** — the eagerly
created `user_profiles` row starts with every column nullable, and
nothing in that SPEC, in `staff-bootstrap-user`, in
`staff-user-provisioning`, or in `knowly-app`'s `onboarding-dashboard`
(a pure UI walkthrough, it collects no data) ever required completeness
before an account was usable. This feature introduces that requirement
for the first time; it reuses `identity-profile-model-v2`'s existing
tables and field set rather than inventing a second profile model.

## Definition of "complete profile" (reuses `identity-profile-model-v2`'s existing tables/fields)

A `User`'s profile is **complete** only when **all** of the following
are true. No field below is invented by this feature — every one already
exists in `identity-profile-model-v2`'s schema (`user_profiles`,
`addresses`, `contacts`).

- `user_profiles.full_name` is set (non-null/non-blank).
- `user_profiles.birth_date` is set.
- `user_profiles.cpf` is set.
- `user_profiles.rg` is set.
- `user_profiles.rg_orgao_emissor` is set. Confirmed included in the
  mandatory set, per the product owner's request to flag any technical
  reason it shouldn't be: no such reason exists —
  `ProfileFieldsDto.rgOrgaoEmissor` is already a real, collected field on
  the existing self-request/direct-edit flow (`UserProfile
  .rgOrgaoEmissor`), not a placeholder or dead column.
- An `addresses` row exists for the user with every one of its
  `NOT NULL` columns set (`cep`, `logradouro`, `bairro`, `cidade`,
  `estado`, `pais`) — `numero`/`complemento` stay optional, unchanged
  from `identity-profile-model-v2` (real addresses can lack a house
  number: "S/N").
- At least one `contacts` row exists for the user (any `type`).

`avatar_url` is explicitly **excluded** from this definition — confirmed
by the product owner: it stays exactly as `identity-profile-model-v2`
already decided (optional, self-editable, carries no identification
weight).

This feature does not change which fields exist, their validation
rules, or their encryption/blind-index handling — it only adds a
completeness check (and, for most accounts, a creation-time precondition
built from that same check) over data `identity-profile-model-v2`
already owns.

## Two distinct enforcement mechanisms — not one rule applied uniformly

**This is the central design point of this SPEC and must not be
flattened into a single mechanism during implementation.** The rule
("no incomplete profile is ever usable") is universal; *how* it's
enforced is not:

1. **The bootstrap `STAFF_ADMIN` (`staff-bootstrap-user`'s migration
   row) — and only that account** — is created with minimal data
   (email only, exactly as today) and starts in a
   **pending-profile-completion** state that fully blocks system use
   until the account's own first-login completion flow is used to fill
   in every required field. This is a one-time, single-account
   exception mechanism, not a general pattern — it exists only because a
   Flyway migration has no way to collect a full profile at deploy time.
2. **Every other `User` — any staff user created via
   `staff-user-provisioning` after bootstrap, and any tenant member
   created via `TenantService.addMember`** — has no pending state at
   all. Creation itself is the gate: the creating call must supply every
   required field (per this SPEC's completeness definition) or the
   system rejects the creation outright. There is no such thing as an
   incomplete, usable-later `MEMBER`/`STAFF` account created after
   bootstrap — either the creation call includes a complete profile and
   succeeds, or it fails and no `User` row is created at all.

Do not implement mechanism 2 as "create minimal, then block until
completed" — that was this SPEC's own first draft and was explicitly
corrected by the product owner. The only account in the entire system
that is ever created incomplete is the bootstrap `STAFF_ADMIN`.

## User stories

- As the product owner, I want it structurally impossible for a `User`
  — staff or tenant member, including the system's own bootstrap account
  — to remain usable without a complete profile, so the platform never
  accumulates unidentifiable accounts.
- As a `STAFF_ADMIN` (or permissioned `STAFF`) provisioning a new staff
  user, or a `MEMBER_ADMIN`/staff user inviting a tenant member, I want
  the creation to simply fail if I don't supply the new person's full
  profile data, so no incomplete account can ever be created after
  bootstrap.
- As the operator who deploys knowly for the first time, I want the
  bootstrap `STAFF_ADMIN` account to still be creatable with just an
  email (nothing else is available at deploy time), but to be forced to
  complete their profile on first login before anything else works, so
  even this one necessary exception never becomes a permanently
  incomplete account.

## Requirements (EARS/GEARS)

### Bootstrap account — minimal creation + pending-state gate (the one exception)

- **REQ-1 [Ubiquitous]** The `staff-bootstrap-user` migration's
  `STAFF_ADMIN` row shall continue to be created exactly as it is today
  (email only, no profile data) — this feature adds no new migration-time
  data requirement to that Flyway migration.
- **REQ-2 [Event-Driven]** When the bootstrap `STAFF_ADMIN` row is
  created, the system shall place that account in a
  **pending-profile-completion** state — the only `User` in the system
  ever created in this state.
- **REQ-3 [State-Driven]** While the bootstrap account is in the
  pending-profile-completion state, the system shall reject every
  request to every endpoint except: authentication (login/logout), the
  endpoint(s) that retrieve the caller's own profile data, and the
  endpoint(s) that let the caller submit their own missing profile
  fields — a full block, not a partial one.
- **REQ-4 [Unwanted Behavior]** If a request other than the REQ-3
  allowlist is made while the bootstrap account is pending, then the
  system shall reject it with a distinct, machine-readable signal (not a
  generic 403) so a client can distinguish "you lack permission" from
  "you must complete your profile first."
- **REQ-5 [Event-Driven]** When the bootstrap account's authentication
  session is established (its first login-code success), the system
  shall report its profile-completion state as part of that response (or
  an immediately-following own-session lookup), so a client knows before
  rendering anything else — including the existing onboarding tour —
  that it must show the profile-completion flow first.
- **REQ-6 [Event-Driven]** When the bootstrap account, while pending,
  submits values that satisfy every field in this SPEC's completeness
  definition, the system shall transition it to profile-complete and,
  from that point on, allow normal access per whatever permissions it
  holds (unrestricted, being `STAFF_ADMIN`) — no bypass and no
  default/placeholder profile data is ever substituted on its behalf
  (REQ-1 through REQ-6 apply with zero exception, including to this
  account itself).

### Every other account — creation blocked without a complete profile, no pending state

- **REQ-7 [Unwanted Behavior]** If a request to create a new staff user
  (`staff-user-provisioning`'s creation path, used for every staff
  account after bootstrap) does not include every field required by this
  SPEC's completeness definition (`full_name`, `birth_date`, `cpf`,
  `rg`, `rg_orgao_emissor`, a complete address, and at least one
  contact), then the system shall reject the creation entirely — no
  `User` row, no `user_profiles` row, no partial state of any kind is
  persisted.
- **REQ-8 [Unwanted Behavior]** If a request to add a tenant member
  (`TenantService.addMember`) does not include every field required by
  this SPEC's completeness definition, then the system shall reject the
  creation entirely, with the same all-or-nothing guarantee as REQ-7.
- **REQ-9 [Ubiquitous]** A `User` created via either of the above paths,
  once creation succeeds, shall never be in a pending-profile-completion
  state — its profile is complete from the moment the row exists, by
  construction of REQ-7/REQ-8's precondition.
- **REQ-10 [Ubiquitous]** No mechanism in this feature introduces a
  pending-profile-completion state, or any equivalent partial-access
  tier, for any account other than the single bootstrap
  `STAFF_ADMIN` row covered by REQ-1 through REQ-6.

## Non-functional requirements

- Security: this feature introduces no new personal-data field and no
  new storage location — it only adds (a) a derived completeness check
  reused as a creation-time precondition for REQ-7/REQ-8, and (b) one
  pending/complete state, scoped only to the bootstrap account, over
  `identity-profile-model-v2`'s existing, already-encrypted/blind-indexed
  columns.
- Observability: the bootstrap account's pending-to-complete transition
  emits an audit event (actor = the bootstrap account itself); a
  rejected REQ-7/REQ-8 creation attempt emits an audit event recording
  the denial and which required fields were missing, per this project's
  existing `@AuditLog` convention.
- Consistency: the completeness check must read the same tables
  `identity-profile-model-v2` already owns — this feature must not
  introduce a second, parallel notion of "profile" or a duplicated field
  set.

## Acceptance criteria

- [ ] The bootstrap `STAFF_ADMIN` migration row is created with email
      only, exactly as today, and starts pending; every
      non-allowlisted endpoint is rejected with the distinct
      pending-profile-completion signal until all required fields are
      submitted.
- [ ] Submitting `full_name`, `birth_date`, `cpf`, `rg`,
      `rg_orgao_emissor`, a complete address, and at least one contact
      for the bootstrap account transitions it to profile-complete;
      submitting all but one of these fields does not.
- [ ] A staff-user-creation request missing any required field is
      rejected outright — no `User`/`user_profiles` row is created.
- [ ] A staff-user-creation request including every required field
      succeeds and the resulting account is immediately usable, with no
      pending state at any point.
- [ ] A tenant-member-creation (`addMember`) request missing any
      required field is rejected outright — no `User`/`user_profiles`
      row is created.
- [ ] A tenant-member-creation request including every required field
      succeeds and the resulting account is immediately usable, with no
      pending state at any point.
- [ ] `avatar_url` is never checked as part of completeness, for either
      mechanism.
- [ ] No account other than the bootstrap `STAFF_ADMIN` is ever observed
      in a pending-profile-completion state.
- [ ] The bootstrap account's completion transition, and every rejected
      REQ-7/REQ-8 creation attempt, emits an audit event.

## Out of scope

- The `knowly-app` frontend screen(s) that (a) present the bootstrap
  account's mandatory completion form and gate its navigation on the
  pending state, and (b) collect the now-required full profile fields
  on the staff-creation and add-member forms — this is backend-only; per
  this repo's cross-repo SPEC-placement rule, the frontend half needs
  its own sibling SPEC (not written here) once this backend SPEC is
  approved.
- Any change to which fields exist, their validation, or their
  encryption/blind-index handling — all owned by
  `identity-profile-model-v2`, untouched here.
- Any change to `avatar_url`'s existing optional, self-editable status.
- A grace period, warning banner, or partial-access tier for any account
  other than the bootstrap exception — the product owner explicitly
  confirmed full blocking for bootstrap and hard creation-time rejection
  (no partial access, no time-boxed grace period) for everyone else.
- Automatic backfill or best-effort completion of any already-existing
  `User` row created before this feature ships — if pre-existing
  incomplete accounts (created under the old, pre-this-feature
  `addMember`/`createStaffUser` contracts) need a migration strategy,
  that's a separate decision, not addressed here.
- Rewriting `staff-user-provisioning`'s or `tenancy`'s own SPECs to
  formally add these new required request fields — this SPEC states the
  new precondition as a requirement; updating those SPECs'/DTOs' own
  documented request contracts to list the new mandatory fields is a
  PLAN-level/implementation follow-up, not re-litigated here.

## Decisions / judgment calls

1. **Corrected during drafting, not a silent assumption:** this SPEC's
   first draft assumed one "minimal creation + pending state" mechanism
   for every account, mirroring the bootstrap case. The product owner
   explicitly corrected this: only the bootstrap `STAFF_ADMIN` uses that
   mechanism; every other account is gated at creation time instead, with
   no pending state ever existing for it. The two mechanisms are kept
   clearly separated above (REQ-1–6 vs. REQ-7–10) specifically so a
   future implementer doesn't generalize the bootstrap exception into a
   system-wide pattern.
2. **Why the bootstrap account still needs REQ-2's pending state at
   all, rather than also being blocked at creation like everyone else:**
   a Flyway migration cannot prompt for or collect `full_name`/`cpf`/
   `rg`/address/contact data — the only data available at deploy time is
   the operator-supplied email (`staff-bootstrap-user`'s own SPEC,
   REQ-2). Blocking *creation* the way REQ-7/REQ-8 do for every other
   account is not physically possible for a migration with no user
   interaction step, so the pending-state mechanism is the only way this
   one account can ever come into existence at all while still
   eventually satisfying the same completeness rule.
3. **The bootstrap account's own first-completion submission is not
   routed through `identity-profile-model-v2`'s existing
   self-request/approval flow (REQ-11/REQ-14 of that SPEC), and this is
   a deliberate, flagged exception, not an oversight:** that flow
   requires approval by some *other* `STAFF_ADMIN`/permission-holder, and
   at bootstrap time no other account exists yet — requiring approval
   here would make the bootstrap account permanently unable to complete
   its own profile. REQ-6 above treats the bootstrap account's *initial*
   completion (filling still-null fields for the first time) as
   self-applied directly, with no approval step. This exception is
   scoped **only** to the bootstrap account's very first completion — it
   does not relax `identity-profile-model-v2`'s existing rule that any
   later *change* to an already-set field (by the bootstrap account or
   anyone else) still goes through the self-request/approval flow
   unchanged. Since every other account is now created already-complete
   (REQ-7/REQ-8), this exception has no equivalent case to resolve for
   non-bootstrap accounts — they never have a null field to
   self-complete in the first place.
4. **Where the bootstrap account's pending/complete state is derived
   from vs. stored** (a computed check over existing tables each time
   vs. a persisted flag updated on transition) is left to PLAN.md, per
   this file's own "implementation detail" boundary — not a product
   decision.
