# SPEC — identity-profile-model-v2 (backend)

## Context and motivation

`identity-profile-model` (shipped, `V17__add_identity_profile_fields.sql`)
gave `User` flat `fullName`/`address`/`rg`/`cpf`/`phone` columns. The
product owner reviewed that shape and rejected it: personal data mixed
directly into the authentication table, a free-text `address` that can't
support a legally-notifiable structured address, and a single `phone`
column that can't represent "more than one way to reach this person."
The full target design — three new tables, an LGPD-minimized field set,
and a tightened per-field permission model — is confirmed and written up
in `DECISIONS.md`'s entry titled *"`identity-profile-model` retrofit:
split `users` personal-data columns into
`user_profiles`/`addresses`/`contacts`, LGPD-minimized field set,
self-service restricted to `avatar_url`"* (2026-07-28). This SPEC turns
that confirmed design into requirements; it does not re-litigate any of
it.

This is a **retrofit of already-shipped, already-running behavior**, not
new behavior layered on top — every requirement below either replaces or
narrows an existing `identity-profile-model` requirement of the same
shape. Where a requirement is unchanged from `identity-profile-model`
(e.g. "self-request always requires someone else's approval"), it is
restated here rather than cross-referenced, since this SPEC's own
acceptance criteria must be independently verifiable once the retrofit
lands and the old flat columns are gone.

This is backend-only. The already-shipped `knowly-app` frontend feature
`user-profile` (built against the old flat contract) is covered by its
own sibling frontend SPEC, `knowly-app/specify/features/user-profile-v2/
SPEC.md`, per this repo's "two SPECs, one per subproject" rule.

## User stories

- As the product owner, I want personal data (`full_name`/`cpf`/`rg`/
  `birth_date`/`avatar_url`) out of the `users` authentication table, so
  identity data and session/credential data aren't mixed.
- As the product owner, I want a structured, single current address per
  user (not free text), so it's usable to legally notify someone if they
  misuse the platform.
- As a user, I want to register more than one way to be reached (phone,
  WhatsApp, alternate email), so accountability isn't limited to a
  single channel.
- As the product owner, I want only fields that serve the platform's
  actual stated purpose (identification/accountability, or the
  explicitly-approved engagement exception for `birth_date`/
  `avatar_url`) — no `social_name`/`gender`/`nationality`.
- As a user, I want to change my own avatar without anyone else's
  approval, since it carries no identification weight — but I do NOT
  want to be able to silently change my own name/CPF/RG/birth date/
  address/contacts, even ones I could otherwise edit for someone else,
  because that data exists precisely so I can be identified.

## Requirements (EARS/GEARS)

### Data model

- **REQ-1 [Ubiquitous]** The system shall store each `User`'s identity
  fields (`full_name`, `cpf`, `rg`, `rg_orgao_emissor`, `birth_date`,
  `avatar_url`) in a new 1:1 `user_profiles` table, keyed by `user_id`,
  created eagerly (one row per `User`, populated at account creation,
  fields nullable until filled) rather than on first submission.
- **REQ-2 [Ubiquitous]** The system shall store each `User`'s current
  address as a structured record (`cep`, `logradouro`, `numero`,
  `complemento`, `bairro`, `cidade`, `estado`, `pais`) in a new 1:1
  `addresses` table, keyed by `user_id`, created only once an address is
  first submitted (not eagerly).
- **REQ-3 [Ubiquitous]** The system shall store each `User`'s
  reachability channels (phone, WhatsApp, email, other) as rows in a new
  1:n `contacts` table (`type`, `value`, `label`, `is_primary`), up to a
  maximum of 5 rows per user.
- **REQ-3a [Unwanted Behavior]** If adding a contact would exceed 5 rows
  for that user, then the system shall reject the addition.
- **REQ-3b [Complex]** Where a user has at least one `contacts` row of a
  given `type` marked `is_primary`, the system shall enforce at most one
  primary row per `(user_id, type)` pair — never one global primary
  across all types.
- **REQ-4 [Ubiquitous]** `cpf` and `rg` shall remain encrypted at rest
  (same `AttributeConverter`/blind-index mechanism `identity-profile-
  model` already established), now living on `user_profiles` instead of
  `users`; `rg_orgao_emissor` shall NOT be part of the encrypted
  envelope (it does not identify anyone on its own) but remains
  plaintext and queryable.
- **REQ-5 [Ubiquitous]** The system shall NOT introduce `social_name`,
  `gender`, or `nationality` fields anywhere in this data model
  (LGPD data-minimization — see `DECISIONS.md` for the field-by-field
  rationale).
- **REQ-6 [Ubiquitous]** `users.email` shall remain the sole login
  credential; it shall never be duplicated into a `contacts` row by any
  mechanism in this feature.

### Profile visibility

- **REQ-7 [Ubiquitous]** The system shall let any authenticated user
  retrieve their own full profile detail (`user_profiles` +
  `addresses` + `contacts`, if present).
- **REQ-8 [Unwanted Behavior]** If a user attempts to view another
  user's full profile detail without the applicable view right, then the
  system shall reject the request. (Unchanged from `identity-profile-
  model` REQ-9.)
- **REQ-9 [Optional Feature]** Where a caller holds tenant-scoped
  `PROFILE_VIEW`, `MEMBER_ADMIN` of that tenant, global-scoped
  `PROFILE_VIEW` (staff), or is `STAFF_ADMIN`, the system shall let them
  view the applicable user(s)' full profile detail. (Unchanged
  authorization surface from `identity-profile-model` REQ-10/10a/10b/
  10c.)

### Profile editing — per-field permission model

- **REQ-10 [Ubiquitous]** `avatar_url` shall be the only field in this
  data model directly self-editable by its owner, unconditionally, with
  no approval step.
- **REQ-11 [Unwanted Behavior]** If a user attempts to directly edit any
  field other than `avatar_url` on their own record — `full_name`,
  `cpf`, `rg`, `rg_orgao_emissor`, `birth_date`, any `addresses.*` field,
  or any `contacts.*` field — even if they hold a grant (tenant/global
  `PROFILE_EDIT`) that would let them edit that same field on someone
  else's record, then the system shall reject the direct edit; they must
  use the self-requested-edit flow (REQ-14) instead.
- **REQ-12 [Complex]** Where a caller is `MEMBER_ADMIN` of a tenant, the
  system shall let them directly edit any member of that tenant's
  `user_profiles`/`addresses`/`contacts` fields (including their own,
  except REQ-11's restriction still applies to `MEMBER_ADMIN` editing
  their own non-`avatar_url` fields — REQ-11 is a blanket "never self,
  regardless of holder" rule, superseding `identity-profile-model`'s old
  REQ-11 which allowed admin self-edit; see `DECISIONS.md`'s "Enforcement
  implication" note), no separate permission required for editing
  others.
- **REQ-13 [Optional Feature]** Where a caller is `STAFF_ADMIN`, or holds
  tenant-scoped `PROFILE_EDIT` (over that tenant's other members), or
  global-scoped `PROFILE_EDIT` (over any other user), the system shall
  let them directly edit that target's non-`avatar_url` fields, subject
  to REQ-11's self-exclusion for their own record.
- **REQ-14 [Event-Driven]** When a user submits a change to any field
  other than `avatar_url` on their own record — regardless of what
  direct-edit rights they hold over *other* users' records — the system
  shall create a pending profile-edit request rather than applying the
  change immediately.
- **REQ-15 [Ubiquitous]** A profile-edit request's proposed values shall
  cover `user_profiles`/`addresses` fields as flattened `proposed_*`
  columns (1:1, same shape `identity-profile-model` already used) and
  `contacts` changes as a list of add/update/remove entries (1:n, cannot
  be flattened).
- **REQ-16 [Event-Driven]** When a pending profile-edit request is
  created, the system shall notify every holder of the applicable edit
  right over the requester's record (same `Notification` reuse mechanism
  `identity-profile-model` REQ-16 already established, unchanged).
- **REQ-17 [Event-Driven]** When a holder of the applicable edit right
  approves a pending request, the system shall apply every proposed
  field/contact change atomically and mark the request resolved
  (approved).
- **REQ-18 [Event-Driven]** When a holder of the applicable edit right
  rejects a pending request, the system shall discard the proposed
  values and mark the request resolved (rejected).
- **REQ-19 [Unwanted Behavior]** If a user attempts to approve/reject a
  request they don't hold the applicable edit right over, then the
  system shall reject the action. (Unchanged from `identity-profile-
  model` REQ-19.)
- **REQ-20 [Unwanted Behavior]** If a user attempts to submit a new
  request while already having an unresolved one, then the system shall
  reject the new submission. (Unchanged from `identity-profile-model`
  REQ-20.)
- **REQ-21 [Unwanted Behavior]** If approving a request would violate
  the `cpf`/`rg` blind-index uniqueness constraint, then the system
  shall reject the approval and apply none of the request's changes.
- **REQ-22 [Unwanted Behavior]** If the resolver of a profile-edit
  request is the same user as its requester, then the system shall
  reject the resolution — self-approval is never permitted, enforced
  both in service logic and as a DB-level `CHECK` on
  `profile_edit_requests` (closing the gap `DECISIONS.md` flags: today
  this is implicit in service logic only).
- **REQ-23 [Ubiquitous]** Nobody — regardless of role or permission —
  may use any mechanism in this SPEC to change a role or permission
  grant; this feature governs `user_profiles`/`addresses`/`contacts`
  fields only. (Unchanged from `identity-profile-model` REQ-22.)

### Migration from the shipped `V17` shape

- **REQ-24 [Event-Driven]** When this feature's migration runs, the
  system shall create `user_profiles`, `addresses`, `contacts`, and
  `profile_edit_request_contacts`, and backfill `full_name`/`cpf`/`rg`
  from `users` into `user_profiles` and `phone` from `users` into one
  `contacts` row per user (type `PHONE`) — `users.email` is never
  backfilled into `contacts`.
- **REQ-25 [Unwanted Behavior]** If any `profile_edit_requests` row is
  `PENDING` at the time this feature's migration runs, then the system
  shall mark it resolved (cancelled) rather than attempt to backfill its
  unstructured `proposed_address` into the new structured `addresses`
  shape — see "Decisions / judgment calls" for why.
- **REQ-26 [Ubiquitous]** The system shall NOT migrate `users.address`'s
  existing free-text data into the new `addresses` table — confirmed by
  the product owner as having no real production value (2026-07-28,
  scoped to this pre-launch state only).
- **REQ-27 [Event-Driven]** When the new code path has been running and
  verified, a later migration shall drop `users`/`users_aud`'s
  `full_name`/`address`/`rg`/`cpf`/`phone`/`rg_blind_index`/
  `cpf_blind_index` columns — this is a direct retrofit, not a
  compatibility view or an expand/contract two-phase migration (see
  `DECISIONS.md`).

## Non-functional requirements

- Security: `cpf`/`rg` are never logged, never included in an
  `@AuditLog` `metadata` payload in plaintext, never returned to a
  caller lacking the applicable right. (Unchanged from `identity-
  profile-model`.)
- Security: the encryption key and HMAC key remain externally sourced,
  never hardcoded.
- Observability: every direct edit, request submission/approval/
  rejection, and permission-denied attempt emits `@AuditLog`.
- Consistency: reuses the existing `Notification` mechanism, no second
  in-app notification system.
- Data minimization: any future personal-data field added to this model
  must be run through the same identification-purpose test
  `DECISIONS.md` documents for this retrofit.

## Acceptance criteria

- [ ] `user_profiles`/`addresses`/`contacts` exist with the confirmed
      schema; `users` no longer carries personal-data columns once the
      final drop migration lands.
- [ ] `avatar_url` is directly self-editable with no approval step.
- [ ] Every other field — including `birth_date` — is rejected on a
      direct self-edit attempt, even by a caller who holds a grant
      letting them edit that field on someone else, and even by
      `MEMBER_ADMIN`/`STAFF_ADMIN` editing their own record.
- [ ] `MEMBER_ADMIN`/`STAFF_ADMIN`/tenant-or-global `PROFILE_EDIT`
      holders can directly edit *other* users' non-`avatar_url` fields.
- [ ] A self-submitted non-`avatar_url` change creates a pending
      request; it does not apply until approved by someone else.
- [ ] A resolver equal to the requester is rejected, DB-level and
      service-level.
- [ ] `contacts` enforces the 5-row cap and at most one primary per
      `(user_id, type)`.
- [ ] The blind-index uniqueness violation on approval rolls back the
      entire request atomically (no partial field/contact application).
- [ ] Migration backfills `full_name`/`cpf`/`rg`/`phone` correctly;
      `email` is never backfilled into `contacts`; any `PENDING` request
      at migration time is resolved (cancelled), not carried forward.
- [ ] `users.address`'s old free-text data is not present anywhere in
      the new `addresses` table after migration.
- [ ] Every relevant action is audit-logged.
- [ ] No endpoint in this feature changes a role or permission grant.
- [ ] `./mvnw spotless:apply && ./mvnw verify` green.

## Out of scope

- Frontend screens (covered by the sibling `knowly-app` SPEC,
  `user-profile-v2`).
- Multiple addresses per user (confirmed rejected — see `DECISIONS.md`).
- `social_name`/`gender`/`nationality` fields (confirmed cut).
- CPF/RG format/checksum validation.
- Automatic/scheduled deletion, expiry, or anonymization of any field.
- Chat display nickname's own data model.
- User/role/tenant-membership management.
- Rate-limiting profile-edit-request submissions.
- Where avatar image bytes are actually stored/uploaded — this SPEC
  covers `avatar_url` as a stored string field and its self-edit
  permission only; the upload mechanism behind producing that URL is a
  PLAN-level (Tier 2) decision, not a new requirement (no new user-
  facing behavior beyond "I can change my avatar").

## Decisions / judgment calls

1. **REQ-11 is a genuine behavior change from shipped
   `identity-profile-model`**, not just a schema retrofit: today
   `MEMBER_ADMIN`/`STAFF_ADMIN` can self-edit their own personal data
   directly (old REQ-11/REQ-12); after this retrofit, nobody can
   self-edit anything except `avatar_url`, regardless of role. This is
   the confirmed design in `DECISIONS.md` and is treated as settled, not
   re-derived here — flagged explicitly because it's the one requirement
   that actually removes previously-granted capability rather than only
   moving where data lives.
2. **REQ-25/REQ-26** (cancel pending requests, drop `address` data
   outright) both follow the same reasoning already confirmed by the
   product owner for `users.address`: this is a pre-launch system with
   no real production data worth preserving through a lossy structural
   change. See PLAN.md for the specific judgment call on `PENDING`
   request handling (Tier 2, resolved in PLAN, not re-asked here).
3. No other new Tier 3 conflict found — the field set, table shapes, and
   permission model are exactly as confirmed in `DECISIONS.md`.
