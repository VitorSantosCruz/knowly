# SPEC — staff-rbac-management-operations

> The what and the why. No technical implementation details.

## Changelog

- **2026-08-02 (promotion added)**: Product owner confirmed, after
  initial approval of this SPEC, that promotion (`STAFF`→`STAFF_ADMIN`,
  `MEMBER`→`MEMBER_ADMIN`, of an already-existing user/membership) is
  wanted in this same round after all, reversing the "Out of scope"
  line that had excluded it. A new "Promotion" requirements section
  (REQ-24–REQ-30) was added, symmetric to the existing "Demotion"
  section; the corresponding user stories, acceptance criteria, and a
  new "Decisions" entry documenting the reversal were added. Nothing
  else in this SPEC (demotion, deletion, batch permission update, access
  groups, REQ-1–REQ-23) changed.
- **2026-08-02**: Product owner correction (relayed before initial
  approval, applied immediately): the batch permission update endpoint's
  security-word requirement was originally scoped to additions only.
  Corrected to require the security word for **any** change in the
  batch — additions **or** removals — not just additions. REQ-13/14/15,
  the corresponding user story, acceptance criteria, and the "Decisions"
  section entry explaining the (now-reversed) addition-only exemption
  are all updated below; nothing else in this SPEC changed.

## Context and motivation

The product owner is reworking `knowly-app`'s member/staff management
screen (see the companion, in-parallel frontend SPEC at
`knowly-app/specify/features/staff-members-management-redesign/SPEC.md`)
and this backend SPEC covers the backend behaviors that redesign needs
and that do not exist yet:

1. **Demoting** a global `STAFF_ADMIN` to `STAFF`, or a tenant
   `MEMBER_ADMIN` to `MEMBER` — today there is no demote endpoint at
   either scope at all (confirmed by inspection of `StaffController`/
   `StaffService` and `TenantController`/`TenantService`; the
   `staff-user-provisioning` SPEC explicitly listed "promoting/demoting
   `STAFF_ADMIN`" as out of scope, flagging it as a future need — this is
   that future need, now extended to also cover `MEMBER_ADMIN`).
2. **Deleting** a staff or tenant-member user, with a floor that always
   leaves at least one `STAFF_ADMIN` and at least one `MEMBER_ADMIN` per
   tenant — today the closest existing thing,
   `TenantService.removeMember`, is a *soft deactivation*
   (`membership.active = false`), not a delete, has no such floor check,
   and has no staff-side equivalent at all (no user-delete endpoint
   exists in `StaffController`/`StaffService`).
3. **Batch-updating** a `STAFF`/`MEMBER` user's own permissions in one
   call instead of the current one-permission-per-call grant/revoke
   pair, with the existing deletion-confirmation-token mechanism
   (`deletion-confirmation-token` SPEC) required for **any** change in
   that batch — additions and removals alike (see the Changelog above:
   this was corrected from an addition-only requirement before
   approval).
4. **Access groups** already exist, at both scopes, as their own
   independent entity, decoupled from any specific user —
   `TenantService.createAccessGroup(actor, tenantId, name)` and
   `StaffService.createAccessGroup(name)` both take only a name (plus
   tenant, for the tenant-scoped one) and create a standalone
   `AccessGroup`/`GlobalAccessGroup` row; assigning a user to a group is
   a separate call (`assignAccessGroup`/`unassignAccessGroup`). So the
   "let access groups be managed independently of any user" part of the
   redesign needs **no backend change** — it's already true server-side,
   and any current UI coupling is a `knowly-app`-only concern. What *is*
   missing today is a business rule blocking `STAFF_ADMIN`/`MEMBER_ADMIN`
   from being assigned to an access group at all, which this SPEC adds.
5. **Promoting** a global `STAFF` to `STAFF_ADMIN`, or a tenant `MEMBER`
   to `MEMBER_ADMIN` — the reverse direction of (1). Originally recorded
   as out of scope for this SPEC (see the first Changelog entry below,
   for the record); the product owner confirmed on 2026-08-02 that
   promotion is wanted in this same round, so it is now included (see
   "Promotion" below). This is promoting an **already-existing**
   `STAFF`/`MEMBER` user/membership after the fact — a different
   operation from choosing the role at creation time, which
   `user-role-selection-at-creation` already covers and which this SPEC
   does not touch or redefine.

Today's one-call-per-permission grant/revoke and access-group
assign/unassign already technically allow calling them against a
`STAFF_ADMIN`/`MEMBER_ADMIN` target (both roles bypass every permission
check regardless — see `member-admin-tenant-bypass` and
`staff-rbac-split` REQ-2 — so such a grant is currently a harmless no-op,
never actually consulted, but nothing rejects the call outright). This
SPEC closes that gap explicitly, per the product owner's requirement
that these two admin tiers hold implicit, ungranted, non-listable rights
only.

## User stories

- As a `STAFF_ADMIN`, I want to demote another `STAFF_ADMIN` to `STAFF`
  (or a `MEMBER_ADMIN` to `MEMBER`, within a tenant I administer or as
  staff), so that I can right-size someone's access without deleting
  their account, while the system stops me if doing so would leave a
  tenant or the platform with zero admins of that tier.
- As a `STAFF_ADMIN` or tenant admin, I want to delete a staff/member
  user entirely, except when they're the last `STAFF_ADMIN` or the last
  `MEMBER_ADMIN` of a tenant, so accounts can be fully removed without
  ever leaving a tenant or the platform with no one who can administer
  it.
- As a `STAFF_ADMIN`/tenant admin, I want to update a `STAFF`/`MEMBER`
  user's permissions in one batch call instead of one call per
  permission, so the redesigned UI can save a whole permission-editing
  session as a single action.
- As that same admin, I want the security-word confirmation required for
  the batch as a whole whenever it changes anything — whether it adds,
  removes, or does both — so that a single save action in the redesigned
  UI always carries the same proof-of-intent guarantee, regardless of
  which direction any individual permission in it moved.
- As a `STAFF_ADMIN`/tenant admin, I want `STAFF_ADMIN`/`MEMBER_ADMIN`
  accounts to be rejected outright from any direct-permission-grant,
  direct-permission-revoke, or access-group-assignment call, so the "the
  system never tracks or lists individual grants for admin tiers" rule
  can't be silently violated by an API call the UI doesn't currently
  make but nothing stops.
- As a `STAFF_ADMIN`, I want to promote an existing `STAFF` user to
  `STAFF_ADMIN` (or, as a `STAFF_ADMIN`/tenant `MEMBER_ADMIN`, promote an
  existing `MEMBER` to `MEMBER_ADMIN` within a tenant), so that I can
  grow the set of administrators from people already onboarded, without
  having to delete and recreate their account through
  `user-role-selection-at-creation`'s creation-time role choice.
- As anyone, I should not be able to promote my own account/membership
  to an admin tier, even if I already hold enough directly-granted
  permissions to otherwise attempt it, so that becoming an admin always
  requires another admin's action, never a self-service escalation.

## Requirements (EARS/GEARS)

### Demotion

- **REQ-1 [Event-Driven]** When a `STAFF_ADMIN` (caller) requests
  demoting a target user from `GlobalRole.STAFF_ADMIN` to
  `GlobalRole.STAFF`, and at least one other `STAFF_ADMIN` account exists
  besides the target, the system shall change the target's `GlobalRole`
  to `STAFF` and record an audit event.
- **REQ-2 [Unwanted Behavior]** If a demotion request's target is the
  only remaining `GlobalRole.STAFF_ADMIN` account in the system, then the
  system shall reject the demotion with a clear, specific error and make
  no change.
- **REQ-3 [Event-Driven]** When a `STAFF_ADMIN` or a tenant's
  `MEMBER_ADMIN` (caller) requests demoting a target membership from
  `MembershipRole.MEMBER_ADMIN` to `MembershipRole.MEMBER` within a
  tenant, and at least one other active `MEMBER_ADMIN` membership exists
  in that same tenant besides the target, the system shall change the
  target membership's role to `MEMBER` and record an audit event.
- **REQ-4 [Unwanted Behavior]** If a demotion request's target is the
  only remaining active `MembershipRole.MEMBER_ADMIN` membership in that
  tenant, then the system shall reject the demotion with a clear,
  specific error and make no change.
- **REQ-5 [Unwanted Behavior]** If a caller attempts to demote their own
  account/membership, then the system shall reject the request, mirroring
  the existing self-target guard already applied to every other
  membership-mutating action in `TenantService`.
- **REQ-6 [Ubiquitous]** Demotion shall be the only way a
  `STAFF_ADMIN`/`MEMBER_ADMIN`'s elevated tier is reduced short of
  deleting the account entirely (REQ-7–REQ-10) — promotion (the reverse
  direction) is a separate operation, now specified in "Promotion"
  below (REQ-24–REQ-30); it is no longer out of scope (see the
  Changelog and "Decisions" for the 2026-08-02 reversal).

### Deletion

- **REQ-7 [Event-Driven]** When a `STAFF_ADMIN` requests deleting a staff
  user (`STAFF` or `STAFF_ADMIN`), and — if the target is a
  `STAFF_ADMIN` — at least one other `STAFF_ADMIN` account exists besides
  the target, the system shall delete the target user's account
  (following the existing deletion-confirmation-token mechanism, per
  "Non-functional requirements" below) and record an audit event.
- **REQ-8 [Unwanted Behavior]** If a staff-user deletion request's target
  is the only remaining `STAFF_ADMIN` account in the system, then the
  system shall reject the deletion with a clear, specific error and make
  no change. A `STAFF` target (never the last of any protected tier) has
  no such floor and may always be deleted.
- **REQ-9 [Event-Driven]** When a `STAFF_ADMIN` or a tenant's
  `MEMBER_ADMIN` requests deleting a tenant-member user (`MEMBER` or
  `MEMBER_ADMIN`) within a tenant, and — if the target's membership role
  is `MEMBER_ADMIN` — at least one other active `MEMBER_ADMIN` membership
  exists in that tenant besides the target, the system shall delete the
  target's membership (following the existing deletion-confirmation-token
  mechanism) and record an audit event.
- **REQ-10 [Unwanted Behavior]** If a tenant-member deletion request's
  target is the only remaining active `MEMBER_ADMIN` membership in that
  tenant, then the system shall reject the deletion with a clear,
  specific error and make no change. A `MEMBER` target (never the last of
  any protected tier) has no such floor and may always be deleted, even
  if they are the only `MEMBER` in the tenant.
- **REQ-11 [Unwanted Behavior]** If a caller attempts to delete their own
  account/membership, then the system shall reject the request, mirroring
  the existing self-target guard.

### Batch permission update (STAFF/MEMBER only)

- **REQ-12 [Ubiquitous]** The system shall provide a single endpoint, per
  scope (global `STAFF` user, tenant `MEMBER`), that accepts the full
  desired set of directly-granted permissions for that user/membership in
  one call and applies every addition and removal in one atomic
  operation, replacing the current one-call-per-permission
  grant/revoke pair for this use case.
- **REQ-13 [Event-Driven]** When a batch permission update would change
  the target's directly-granted permissions in any way — adding one or
  more permissions the target did not already directly hold, removing
  one or more the target did hold, or both in the same call — the system
  shall require a valid deletion-confirmation-token-style security word
  for that batch operation (generated via a sibling confirmation-token
  endpoint, mirroring the existing mechanism's generate-then-consume
  shape) before applying any part of the batch, and shall reject the
  entire batch if no valid word is supplied. *(Corrected 2026-08-02 —
  see Changelog: this now covers removals as well as additions, not
  additions only.)*
- **REQ-14 [Ubiquitous]** A batch permission update request that changes
  nothing (the submitted desired set is identical to the target's
  current directly-granted permissions) is a no-op and shall not require
  a security word, since there is nothing to confirm. Any batch that
  changes at least one permission in either direction falls under REQ-13
  instead — there is no partial exemption for remove-only batches.
- **REQ-15 [Event-Driven]** When a batch permission update is applied,
  the system shall record one audit event per added permission and one
  per removed permission, consistent with the granularity of today's
  single-grant/single-revoke audit events.
- **REQ-16 [Unwanted Behavior]** If a batch permission update targets a
  `STAFF_ADMIN` or `MEMBER_ADMIN`, then the system shall reject the
  request outright (see REQ-19).

### Admin tiers hold no individually-tracked grants (closes an existing gap)

- **REQ-17 [Unwanted Behavior]** If a direct-permission-grant request
  (single or batch) targets a user whose `GlobalRole` is `STAFF_ADMIN`,
  then the system shall reject the request instead of creating a
  `DirectGlobalPermissionGrant` row.
- **REQ-18 [Unwanted Behavior]** If a direct-permission-grant or
  direct-permission-revoke request (single, existing endpoints) targets a
  membership whose `MembershipRole` is `MEMBER_ADMIN`, then the system
  shall reject the request instead of creating/deleting a
  `DirectPermissionGrant` row.
- **REQ-19 [Unwanted Behavior]** If an access-group-assignment request
  (`assignAccessGroup`, either scope) targets a `STAFF_ADMIN` user or a
  `MEMBER_ADMIN` membership, then the system shall reject the request
  instead of creating the `UserGlobalAccessGroup`/`UserAccessGroup` row.

### Admin-target actions require an admin caller, not merely a granted permission

- **REQ-21 [Unwanted Behavior]** If a demotion or deletion request targets
  a `STAFF_ADMIN`, then the system shall reject the request unless the
  caller is themselves currently a `STAFF_ADMIN` — a `STAFF` caller is
  rejected regardless of any directly-granted or access-group permission
  they hold (e.g. `STAFF_USER_CREATE`, `STAFF_PERMISSION_MANAGE`), since
  those permissions govern ordinary `STAFF`/`MEMBER` management, not
  acting on another admin's account.
- **REQ-22 [Unwanted Behavior]** If a demotion or deletion request targets
  a `MEMBER_ADMIN`, then the system shall reject the request unless the
  caller is either a `STAFF_ADMIN` or that same tenant's `MEMBER_ADMIN` —
  a `MEMBER` caller is rejected regardless of any directly-granted or
  access-group permission they hold, for the same reason as REQ-21.
- **REQ-23 [Ubiquitous]** REQ-21/REQ-22 apply only when the *target* is an
  admin tier; deleting/managing a `STAFF`/`MEMBER` target continues to
  follow whatever authorization already governs that action today
  (`requireAdminOfTenantOrStaff`/granted-permission checks) — this SPEC
  does not tighten who may act on a non-admin target.

### Access groups (no backend change required — documented for completeness)

- **REQ-20 [Ubiquitous]** Access groups shall remain creatable and
  manageable (create, name, assign permissions to the group) via an
  endpoint independent of any specific user or membership — this is
  already true today (`StaffService.createAccessGroup(name)`,
  `TenantService.createAccessGroup(actor, tenantId, name)`, both taking
  no user/membership parameter) and this SPEC makes no change to that
  behavior.

### Promotion (added 2026-08-02 — see Changelog)

- **REQ-24 [Event-Driven]** When a `STAFF_ADMIN` (caller) requests
  promoting a target user from `GlobalRole.STAFF` to
  `GlobalRole.STAFF_ADMIN`, the system shall change the target's
  `GlobalRole` to `STAFF_ADMIN` and record an audit event.
- **REQ-25 [Event-Driven]** When a `STAFF_ADMIN` or a tenant's
  `MEMBER_ADMIN` (caller) requests promoting a target membership from
  `MembershipRole.MEMBER` to `MembershipRole.MEMBER_ADMIN` within a
  tenant, the system shall change the target membership's role to
  `MEMBER_ADMIN` and record an audit event.
- **REQ-26 [Ubiquitous]** Promotion shall be subject to no "last admin"
  or any other floor/ceiling check — that safeguard (REQ-2/REQ-4/REQ-8/
  REQ-10) exists only to stop the *count* of admins of a tier from
  reaching zero via demotion/deletion; promoting a user can only ever
  increase that count, so no such check applies here, mirroring
  `user-role-selection-at-creation` REQ-5/REQ-10's identical reasoning
  for admin creation.
- **REQ-27 [Unwanted Behavior]** If a promotion request targets
  `STAFF_ADMIN` and the caller is not themselves currently a
  `STAFF_ADMIN` — including a `STAFF` caller holding `STAFF_USER_CREATE`,
  `STAFF_PERMISSION_MANAGE`, or any other directly-granted or
  access-group permission — then the system shall reject the request
  outright and make no change, reusing exactly the same caller-identity
  rule as REQ-21 (demotion/deletion of a `STAFF_ADMIN` target) and
  `user-role-selection-at-creation` REQ-3 (creating a new `STAFF_ADMIN`
  directly): only a `STAFF_ADMIN` may mint or promote another
  `STAFF_ADMIN`.
- **REQ-28 [Unwanted Behavior]** If a promotion request targets
  `MEMBER_ADMIN` and the caller is neither a `STAFF_ADMIN` nor that same
  tenant's `MEMBER_ADMIN` — including a `MEMBER` caller holding any
  directly-granted or access-group permission — then the system shall
  reject the request outright and make no change, reusing exactly the
  same caller-identity rule as REQ-22 and `user-role-selection-at-creation`
  REQ-8.
- **REQ-29 [Unwanted Behavior]** If a caller attempts to promote their own
  account/membership to `STAFF_ADMIN` or `MEMBER_ADMIN`, then the system
  shall reject the request, regardless of the caller's current role or
  any permission they hold. Rationale: this mirrors the existing
  self-target guard (REQ-5/REQ-11) applied to every other sensitive,
  self-affecting action in this SPEC. It is never a live restriction on
  an already-admin caller acting on themselves in practice — REQ-27/
  REQ-28 already require the caller to *be* the matching admin tier
  before a promotion request is even considered, and an admin promoting
  themselves would be a no-op requiring no separate rule. Its actual
  effect is to close the case of a non-admin (`STAFF` or `MEMBER`)
  caller who might otherwise attempt to "self-promote" via a crafted
  request naming themselves as both caller and target: such a caller
  fails REQ-27/REQ-28 already (they are not currently an admin), so
  REQ-29 is a defense-in-depth, explicit-by-design backstop consistent
  with the rest of this SPEC's posture that every sensitive,
  self-affecting mutation states its self-target guard outright rather
  than relying solely on another requirement to imply it.
- **REQ-30 [Ubiquitous]** Promotion is the only way a `STAFF`/`MEMBER`
  user's tier is raised to `STAFF_ADMIN`/`MEMBER_ADMIN` after creation —
  it does not replace or alter `user-role-selection-at-creation`'s
  creation-time role choice, which remains a separate operation with its
  own (identical) authorization rule.

## Non-functional requirements

- Security: every mutation this SPEC adds (demote, delete, batch update,
  promote) is gated by the same authorization pattern already
  established for the equivalent existing action at that scope —
  `requireStaff`/`requireAdminOfTenantOrStaff` (tenant scope, via
  `TenantService`) and `STAFF_ADMIN`-only checks (global scope, via
  `StaffService`), including `enforceStaffCeiling` where relevant (a
  `STAFF` user, however permissioned, still can never manage a
  `STAFF`/`STAFF_ADMIN` target).
- Security: deletion (REQ-7–REQ-10) and any-change batch updates
  (REQ-13) reuse the existing `deletion-confirmation-token` mechanism
  exactly as already implemented elsewhere in this codebase — same
  generate-then-consume shape, same word format, same TTL/single-use
  semantics, same hashed-at-rest storage, same generic non-revealing
  failure response. No new confirmation mechanism is introduced.
  Promotion (REQ-24–REQ-30) does **not** require this security-word
  mechanism — it is not a destructive/irreversible action the way
  deletion or a permission-removing batch is, and no such requirement
  was requested for it.
- Security: the self-target guard (`requireNotSelfTarget`) applies to
  every new mutation the same way it already applies to
  `addMember`/`removeMember`/`grantPermission`/`revokePermission`/
  `assignAccessGroup`/`unassignAccessGroup` — including promotion
  (REQ-29).
- Observability: every demotion, deletion, batch permission add/remove,
  and promotion records an audit event (actor, action, target, outcome),
  per the constitution's audit requirements.
- Data integrity: the "at least one admin remains" floor (REQ-2/REQ-4/
  REQ-8/REQ-10) must be checked and enforced transactionally against the
  current, live count at the moment of the mutation, not a cached or
  pre-fetched count, to close any TOCTOU gap from concurrent
  demote/delete requests. This floor check does not apply to promotion
  (REQ-26), which has no such check to enforce.

## Acceptance criteria

- [ ] A `STAFF_ADMIN` can be demoted to `STAFF` when at least one other
      `STAFF_ADMIN` exists; the demotion is rejected when the target is
      the last one.
- [ ] A `MEMBER_ADMIN` can be demoted to `MEMBER` within a tenant when at
      least one other active `MEMBER_ADMIN` membership exists in that
      tenant; the demotion is rejected when the target is the last one in
      that tenant.
- [ ] Self-demotion is rejected regardless of admin-count.
- [ ] A staff user (`STAFF` or `STAFF_ADMIN`) can be deleted; deleting a
      `STAFF_ADMIN` is rejected when they're the last one; deleting a
      `STAFF` user is never blocked by any admin-count floor.
- [ ] A tenant member (`MEMBER` or `MEMBER_ADMIN`) can be deleted; deleting
      a `MEMBER_ADMIN` is rejected when they're the last active one in
      that tenant; deleting a `MEMBER` is never blocked by any
      admin-count floor, even if they're the tenant's only `MEMBER`.
- [ ] Self-deletion is rejected regardless of admin-count.
- [ ] A batch permission update endpoint exists for `STAFF`/`MEMBER`
      targets accepting the full desired permission set (or an add/remove
      diff) and applies it atomically in one call.
- [ ] A batch update that adds at least one new permission is rejected
      without a valid security word, and succeeds with one, generated via
      a sibling token-generation endpoint mirroring the existing
      mechanism.
- [ ] A batch update that only removes one or more permissions (no
      additions) is likewise rejected without a valid security word, and
      succeeds with one — the same requirement as the addition case, not
      an exemption.
- [ ] A batch update that changes nothing (submitted set identical to the
      target's current permissions) succeeds with no security word
      required, since it is a no-op.
- [ ] A batch update (single or diff form) targeting a `STAFF_ADMIN` or
      `MEMBER_ADMIN` is rejected outright.
- [ ] A direct permission grant/revoke (existing single-permission
      endpoints, either scope) targeting a `STAFF_ADMIN`/`MEMBER_ADMIN` is
      rejected outright, where today it silently succeeds as a no-op.
- [ ] An access-group assignment targeting a `STAFF_ADMIN` user or
      `MEMBER_ADMIN` membership is rejected outright, where today it
      silently succeeds as a no-op.
- [ ] Access-group creation/management continues to work independent of
      any user, unchanged from today's behavior (regression check only,
      no new behavior).
- [ ] Every demotion, deletion, and batch add/remove is audit-logged.
- [ ] A `STAFF` caller (even one holding broad granted permissions) is
      rejected when attempting to demote or delete a `STAFF_ADMIN`; a
      `MEMBER` caller is rejected when attempting to demote or delete a
      `MEMBER_ADMIN` — only another admin of the matching tier may act on
      an admin target.
- [ ] A `STAFF_ADMIN` can promote an existing `STAFF` user to
      `STAFF_ADMIN`; the promotion succeeds regardless of how many
      `STAFF_ADMIN`s already exist (no floor/ceiling check).
- [ ] A `STAFF_ADMIN` or a tenant's `MEMBER_ADMIN` can promote an existing
      `MEMBER` to `MEMBER_ADMIN` within that tenant; the promotion
      succeeds regardless of how many `MEMBER_ADMIN`s already exist in
      that tenant.
- [ ] A `STAFF` caller (with or without any directly-granted or
      access-group permission) attempting to promote any user to
      `STAFF_ADMIN` is rejected; no change is made.
- [ ] A `MEMBER` caller (with or without any directly-granted or
      access-group permission) attempting to promote any membership to
      `MEMBER_ADMIN` is rejected; no change is made.
- [ ] Self-promotion (caller names themselves as the promotion target) is
      rejected regardless of the caller's current role or permissions.
- [ ] Every promotion is audit-logged.
- [ ] A promotion request never requires a deletion-confirmation-token
      security word.

## Out of scope

- **Deactivation vs. hard delete distinction for tenant membership**:
  the existing `removeMember` soft-deactivation behavior
  (`membership.active = false`) is left completely untouched by this
  SPEC. REQ-9/REQ-10's "delete a tenant-member user" is a **new**,
  separate operation/endpoint from `removeMember` — this SPEC does not
  redefine what `removeMember` does, and does not remove or alias it.
  Whether the new delete endpoint performs a genuine hard row delete or
  a differently-flagged soft delete is a PLAN-time technical decision,
  not specified here, provided the *user-visible effect* is "no longer
  exists as an account/member" per the acceptance criteria above.
- **Reassigning a demoted/deleted `MEMBER_ADMIN`'s existing direct
  permission grants/access-group memberships**: since `MEMBER_ADMIN`
  (per REQ-18/REQ-19, and already true today via the bypass) never holds
  any actual `DirectPermissionGrant`/`UserAccessGroup` rows to begin
  with, there is nothing to migrate/reassign on demotion or deletion.
  This SPEC does not add any such migration step because none is needed.
  The same holds for promotion: a newly-promoted `STAFF_ADMIN`/
  `MEMBER_ADMIN` keeps whatever direct grants/access-group memberships
  they held as `STAFF`/`MEMBER` (they simply become moot per the
  bypass), and this SPEC does not add any step to strip or migrate them
  either.
- **UI/UX of the redesigned member management screen itself** — entirely
  `knowly-app`'s `staff-members-management-redesign` SPEC's concern; this
  SPEC covers only the backend endpoints/behaviors that screen will
  consume.
- **Rate-limiting/throttling** beyond what already applies to
  authenticated endpoints generally, and **any new confirmation
  mechanism** beyond reusing the existing `deletion-confirmation-token`
  one as-is — no new security-word scheme is introduced (and, per REQ-24
  onward, promotion introduces no security-word requirement at all).
- **Notifying the demoted/deleted/promoted user** (e.g. an email on
  demotion, deletion, or promotion) — not requested; silent
  (audit-logged only), same posture as every other tenant/staff mutation
  in this codebase today.
- **A platform-wide "last STAFF_ADMIN" floor that spans multiple tenants
  for `MEMBER_ADMIN`** — the `MEMBER_ADMIN` floor (REQ-4/REQ-10) is
  strictly per-tenant (at least one active `MEMBER_ADMIN` per tenant),
  never a cross-tenant count; only the `STAFF_ADMIN` floor (REQ-2/REQ-8)
  is platform-wide, since `STAFF_ADMIN` is a global, not tenant-scoped,
  role. Promotion has no floor check at all (REQ-26), so this
  distinction is moot for it.

## Decisions (recorded here since this SPEC pre-resolves them rather than
leaving them to PLAN time — all fall inside "implementation detail of an
approved requirement," not a Tier 3 scope question)

- **Promotion, originally excluded, was added back in on 2026-08-02 at
  the product owner's explicit request.** This SPEC's first approved
  draft listed promotion (`STAFF`→`STAFF_ADMIN`, `MEMBER`→`MEMBER_ADMIN`
  for an already-existing user/membership) under "Out of scope,"
  reasoning that only the demotion direction had been requested for that
  round. The product owner subsequently confirmed they want promotion
  included in this same round rather than deferred further, so the
  "Out of scope" line was removed and replaced with REQ-24–REQ-30 above,
  symmetric to the existing demotion requirements. This is a scope
  **addition** the product owner explicitly asked for, not a
  reinterpretation done unilaterally — recorded here per this
  repository's standing practice (see root `DECISIONS.md`) of never
  silently reversing a documented "Out of scope" line without an
  explicit, recorded instruction to do so.
- **Promotion's authorization rule is identical to, and reuses, the rule
  already established for admin-target demotion/deletion (REQ-21/
  REQ-22) and for choosing an admin role at creation
  (`user-role-selection-at-creation` REQ-3/REQ-8)** — only a
  `STAFF_ADMIN` may promote to `STAFF_ADMIN`; only a `STAFF_ADMIN` or
  that tenant's `MEMBER_ADMIN` may promote to `MEMBER_ADMIN`. No new
  authorization concept was introduced for promotion; it is the same
  "acting on/creating an admin-tier target requires an admin-tier actor
  of the matching scope" rule applied a third time.
- **Promotion has no "last admin" floor/ceiling check, by the same
  reasoning already used for admin creation
  (`user-role-selection-at-creation` REQ-5/REQ-10)**: that floor exists
  solely to prevent the admin count from reaching zero via demotion or
  deletion; promotion can only increase the count, so the check does
  not apply. This is stated explicitly (REQ-26) rather than left
  implicit, to avoid the same kind of silent-gap ambiguity that
  motivated writing REQ-5/REQ-10 explicitly in the sibling SPEC.
- **Promotion is subject to a self-target guard (REQ-29) even though, in
  practice, an admin caller promoting themselves would already be a
  no-op and a non-admin caller is already stopped by REQ-27/REQ-28.**
  This SPEC still states the guard explicitly, consistent with every
  other sensitive, self-affecting mutation in this SPEC stating its own
  self-target guard rather than relying on a reader to infer it
  transitively from a different requirement.
- **Promotion does not require the deletion-confirmation-token
  security-word mechanism.** Unlike deletion and permission-removing
  batch updates, promotion is additive and reversible (a promoted user
  can later be demoted per REQ-1/REQ-3) — it was not requested to carry
  the same proof-of-intent friction, and no such requirement is
  introduced.
- **The batch endpoint's security-word requirement covers both
  directions (REQ-13), corrected before approval (2026-08-02).** The
  first draft of this SPEC required the security word only when the
  batch added a permission, exempting remove-only batches, based on an
  initial instruction to mirror single-permission revoke's lack of an
  extra confirmation step. The product owner corrected this before
  approval: for the **batch** endpoint specifically, any change —
  addition, removal, or both — requires the security word. The
  single-permission `DELETE .../permissions/{permission}` endpoints
  (existing, untouched by this SPEC) keep their own already-established
  behavior per `deletion-confirmation-token`'s SPEC (REQ-19/25 there),
  which is unaffected by this correction; the two endpoint families are
  independent, not required to match each other's mechanics.
- **The existing single-permission grant/revoke and assign/unassign
  endpoints are not removed or deprecated by this SPEC** — REQ-12's batch
  endpoint is additive, for the specific "editing a whole permission set
  in one screen session" UI need; the single-permission endpoints remain
  available for any caller/integration that still wants one-at-a-time
  semantics (still subject to REQ-17/REQ-18's new
  `STAFF_ADMIN`/`MEMBER_ADMIN` rejection either way).
