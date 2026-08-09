# SPEC — chat-group-membership-management (backend)

> The what and the why. No technical implementation details.

## Context and motivation

`internal-team-chat` (already implemented, 97/97 tasks, see
`knowly-api/specify/features/internal-team-chat/SPEC.md`/`PLAN.md`)
shipped peer-to-peer group conversations (`ChatConversation` with
`kind = PEER_GROUP`), but the **only** way to set a group's
participants is at creation time, via
`POST /api/chat/conversations`'s `participantUserIds` array. There is
no way today to change a group's membership after it exists, no way to
discover a group you are not already in, no way to join one without
already being added, no per-group administrative role, and no way to
delete a group.

The confirmed product need (frontend flow): a user clicks "Criar
grupo," names it, and lands inside a group that starts with only
themself as a participant (and, per the decisions below, is
automatically that group's first admin). From inside that group,
participants and group admins need the capabilities specified below.

This SPEC is an **amendment** to `internal-team-chat`: it reuses that
feature's data model (`ChatConversation`/`ChatParticipant`) and its
`ChatEligibilityService` (the tenant-anchor eligibility rule from
REQ-3/REQ-4/REQ-5 of that SPEC) unchanged, and adds the
membership-mutation, discovery/join, per-group-admin, and deletion
capability that was missing. It does not reopen, and is not to be read
as silently reopening, any decision already made in
`internal-team-chat`'s SPEC — see "Relationship to `internal-team-chat`'s
SPEC" below for what was checked.

**This SPEC applies to `PEER_GROUP` conversations only.** `PEER_DIRECT`
(1:1) conversations have exactly two fixed participants for the
lifetime of the conversation in `internal-team-chat`'s model and are
untouched by this SPEC. `SUPPORT` channels have their own,
entirely different membership shape (owning member + at most one
currently-assigned staff user, governed by ticket lifecycle, not by
`chat_participants` mutation) and are also untouched by this SPEC.
Visibility/discovery/join-request/admin/deletion concepts introduced
here likewise apply to `PEER_GROUP` only.

**Status: CLOSED, no open Tier 3 questions remain. Ready for PLAN.md.**
This revision incorporates every product-owner decision resolving the
previous drafts' Tier 3 questions: group-admin authorization for add/
remove/approve-reject (former questions 1, 5), whether to introduce an
admin concept at all (former question 2), post-creation visibility
change (former question 4), the empty-group/archival rule including the
new tenant-group/staff-group distinction (former question 3), the new
group-deletion capability, and — the last open item, from the
immediately preceding round — automatic admin succession when a
group's only admin leaves or is removed (REQ-54 below).

## Relationship to `internal-team-chat`'s SPEC (read before implementing)

- `internal-team-chat`'s "Out of scope" section does **not** contain
  any line excluding post-creation participant management, group
  discovery, join requests, a per-group admin role, or group deletion.
  The absence of a stated boundary is treated here as an *unaddressed
  gap*, not as an implicit "no" — per this project's own rule, silence
  is not a scope boundary. This SPEC is therefore a net-new amendment,
  not a Tier 3 reversal of an existing "Out of scope" line.
- `internal-team-chat`'s REQ-3/REQ-4/REQ-5 `ChatEligibilityService` is
  reused **verbatim, unmodified** — this SPEC does not alter who is
  eligible to be a participant of a member-only or staff-only group,
  only *when* and *how* that eligibility check can be invoked (at
  creation, via add, via join request, or via direct public join).
- **The "tenant group" vs. "staff group" distinction the product owner
  asked for is not a new concept to model — it already exists,
  shipped, in `internal-team-chat`.** That SPEC's REQ-5 defines exactly
  two kinds of `PEER_GROUP`, distinguished by `tenant_id` nullability:
  a **member-only group** (`tenant_id` set — this SPEC's "grupo de
  tenant") and a **staff-only group** (`tenant_id IS NULL` — this
  SPEC's "grupo de staff"), and `ChatEligibilityService` already
  enforces who can be a participant of each. Investigation confirms no
  new enum, column, or table is needed to represent "is this a tenant
  group or a staff group" — this SPEC's archival-access rule (see
  "Empty-group handling" below) is defined directly in terms of this
  existing `tenant_id IS NULL` distinction, reusing it rather than
  introducing a parallel "group scope" concept. This resolves the
  coordinator's request to investigate before inventing new modeling:
  there was already a precedent, and it is a complete match.
- `internal-team-chat`'s REQ-5a/REQ-5b admin "look-in" (read-only,
  creates no participant row, `STAFF_ADMIN` global / `MEMBER_ADMIN`
  scoped to tenants they administer) is **unchanged** and remains
  strictly a read path over *active* (non-archived, non-deleted)
  groups. The new archived-group access rule below (staff-only reads of
  an archived group's history) is a **distinct, new** rule, specified
  fresh in this SPEC, not an extension of REQ-5a/REQ-5b's wording —
  notably it grants plain `STAFF` (not just `STAFF_ADMIN`) read access
  to an archived tenant group's history, which is broader than
  REQ-5a's `STAFF_ADMIN`-only look-in for *active* groups. This
  intentional difference (archived tenant-group history is
  staff-team-visible, not just admin-visible) is called out explicitly
  because it does not fall out of REQ-5a/REQ-5b by extension — it is a
  new grant, confirmed by the product owner's decision below, not an
  AI-inferred generalization of the existing rule.
- **Group admin (new in this SPEC) is explicitly a distinct concept
  from `STAFF_ADMIN`/`MEMBER_ADMIN`** (tenant/platform-level roles).
  Group admin is scoped to exactly one `PEER_GROUP` conversation and
  has no bearing on, and is not derived from, a user's tenant or
  platform role.
- **Participant tenure is already tracked** — `internal-team-chat`'s
  `chat_participants` table has a `joined_at` column on every
  participant row (used there only for display/audit purposes). This
  SPEC's automatic admin-succession rule (REQ-54) reuses that existing
  column as its tie-break ordering rather than introducing a new
  "seniority" concept.

## User stories

- As a participant of a group I just created, I want to automatically
  be its admin, so I can manage it without an extra setup step.
- As a group admin, I want to add or remove participants, promote
  another participant to admin, approve/reject join requests, change
  the group's visibility, and delete the group — the actions this SPEC
  reserves for admins.
- As a participant of an existing group, I want to add further eligible
  peers, any number of times.
- As a participant of a group, I want to leave it voluntarily.
- As a participant of a group whose only admin just left or was
  removed, I want the group to automatically get a new admin, so the
  group doesn't become permanently unmanageable just because no one
  remembered to promote someone first.
- As the creator of a group, I want to choose whether it's private,
  request-to-join, or fully public, and to change that later as the
  group's needs change.
- As an eligible user browsing for groups to join, I want to see
  `REQUEST_TO_JOIN`/`PUBLIC` groups I qualify for and either request to
  join or join immediately, depending on the mode.
- As a staff user, I want to be able to look into the history of an
  archived tenant group (one that emptied out) for accountability
  purposes, and as a `STAFF_ADMIN`, the same for an archived staff
  group.
- As a `STAFF_ADMIN`, `MEMBER_ADMIN`, a tenant permission holder, or a
  group admin, I want to permanently delete a group when it's no longer
  needed, with its history retained for audit rather than physically
  erased.

## Requirements (EARS/GEARS)

### Group admin role

- **REQ-1 [Event-Driven]** When a `PEER_GROUP` conversation is created,
  the system shall record its creator as that group's first admin, a
  role tracked per (conversation, user) — distinct from, and never
  derived from, `STAFF_ADMIN`/`MEMBER_ADMIN` tenant/platform roles.
- **REQ-2 [Event-Driven]** When a current group admin promotes another
  current participant of the same group to admin, the system shall
  grant that participant the group-admin role for that group; a group
  may have more than one admin simultaneously.
- **REQ-3 [Unwanted Behavior]** If a caller who is not a current admin
  of the target group attempts to promote another participant to
  admin, then the system shall reject the request.
- **REQ-4 [Unwanted Behavior]** If the user being promoted is not a
  current participant of the group, then the system shall reject the
  promotion.
- **REQ-5 [Unwanted Behavior]** If the user being promoted is already a
  group admin, then the system shall reject the promotion as a no-op
  rather than silently succeeding twice.
- **REQ-6 [Ubiquitous]** The system shall require every action reserved
  for a group admin elsewhere in this SPEC (add participant, remove
  participant, approve/reject join request, change visibility, delete
  the group) to be authorized by checking the caller's current
  group-admin status for that specific conversation, re-derived at
  request time — never cached, never inferred from tenant/platform role
  (a `STAFF_ADMIN`/`MEMBER_ADMIN` is not a group admin by virtue of
  their tenant/platform role; see "Deleting a group" for the separate,
  explicit paths those roles get for deletion specifically).
- **REQ-7 [Event-Driven]** When a group admin leaves the group (REQ-18)
  or is removed from it (REQ-13), the system shall remove their admin
  status along with their participant status — admin status does not
  survive after ceasing to be a participant, and, if this leaves the
  group with zero admins and at least one remaining participant, the
  system shall additionally apply REQ-54's automatic succession.
- **REQ-54 [Event-Driven]** When removing a participant's admin status
  (REQ-7) leaves a `PEER_GROUP` conversation with **zero admins** and
  **at least one remaining participant**, the system shall
  automatically promote exactly one of the remaining participants to
  admin, selected as follows: the remaining participant with the
  **earliest `joined_at`** (i.e. the longest-tenured current
  participant); if two or more remaining participants share the exact
  same `joined_at` value, the system shall break the tie by the
  **lowest user id** among them, guaranteeing a single, deterministic
  successor in every case. This keeps every non-empty `PEER_GROUP`
  conversation continuously manageable via the group-admin path,
  without requiring the departing/removed admin to have promoted anyone
  first.

### Adding participants

- **REQ-8 [Event-Driven]** When a current group admin submits one or
  more user identifiers to add to an existing `PEER_GROUP`
  conversation, the system shall, for each identifier, re-derive that
  user's eligibility via the existing `ChatEligibilityService` against
  that conversation's tenant anchor before adding them as a
  participant (a newly-added participant is not an admin by default —
  see REQ-2 for how admin status is separately granted).
- **REQ-9 [Unwanted Behavior]** If a caller who is not a current group
  admin attempts to add a participant, then the system shall reject the
  request — adding participants is a group-admin-only action.
- **REQ-10 [Unwanted Behavior]** If any submitted user identifier is
  already a current participant, then the system shall reject that
  identifier without duplicating the row, while still processing the
  remaining, non-duplicate identifiers in the same request — exact
  all-or-nothing vs. partial-success batch semantics are a PLAN-level
  decision.
- **REQ-11 [Unwanted Behavior]** If any submitted user identifier fails
  `ChatEligibilityService`'s check, then the system shall reject that
  identifier with the same ineligibility semantics used at
  conversation-creation time.
- **REQ-12 [Unwanted Behavior]** If the target conversation does not
  exist, is not a `PEER_GROUP`, or is archived/deleted (see below),
  then the system shall reject the request (not-found for a
  non-existent conversation; a distinct rejection for wrong-kind or
  archived/deleted, per PLAN-level error-code decisions).

### Removing participants

- **REQ-13 [Event-Driven]** When a current group admin requests removal
  of a specific current participant, the system shall delete that
  user's participant row, immediately revoking their read/write access
  — including, if the removed user was also an admin, their admin
  status (and triggering REQ-54's succession if that empties the
  group's admin set).
- **REQ-14 [Unwanted Behavior]** If the caller is not a current group
  admin, then the system shall reject the removal request —
  group-admin-only, same resolution as REQ-9.
- **REQ-15 [Unwanted Behavior]** If the specified user is not a current
  participant, then the system shall reject the request rather than
  succeeding on a no-op.
- **REQ-16 [Unwanted Behavior]** If removing the specified participant
  would leave the conversation with zero participants, then the system
  shall reject the removal — emptying a group is only ever reachable
  via the last remaining participant's own **leave** (REQ-19), which
  has its own explicit empty-group handling, not via a third-party
  removal.
- **REQ-17 [Unwanted Behavior]** If the target conversation does not
  exist, is not a `PEER_GROUP`, or is archived/deleted, then the system
  shall reject the request, same as REQ-12.

### Leaving a group

- **REQ-18 [Event-Driven]** When a current participant requests to
  leave a `PEER_GROUP` conversation, the system shall delete that
  caller's own participant row (and admin status, if any — REQ-7,
  triggering REQ-54's succession if applicable), regardless of
  group-admin status — leaving is always self-service, never gated by
  the group-admin requirement that governs add/remove.
- **REQ-19 [Unwanted Behavior]** If the caller is not currently a
  participant, then the system shall reject the leave request.
- **REQ-20 [State-Driven]** While the caller is the **last remaining
  participant**, the system shall allow them to leave exactly like any
  other participant; what happens to the now-empty group is governed
  entirely by "Empty-group handling" below (visibility-mode-dependent:
  archive, or stay available). REQ-54's succession does not apply in
  this specific case — there is no remaining participant left to
  promote once the last one leaves.
- **REQ-21 [Unwanted Behavior]** If the target conversation does not
  exist or is not a `PEER_GROUP`, then the system shall reject the
  request. (Leaving an already-archived/deleted group is moot — see
  "Empty-group handling"/"Deleting a group": an archived group has zero
  participants by definition, so there is no one left to leave it, and
  a deleted group is inaccessible to everyone.)

### Group visibility

- **REQ-22 [Ubiquitous]** The system shall record exactly one
  visibility mode per `PEER_GROUP` conversation — `PRIVATE`,
  `REQUEST_TO_JOIN`, or `PUBLIC` — set at creation, defaulting to
  `PRIVATE` when not explicitly chosen.
- **REQ-23 [Event-Driven]** When a current group admin changes a
  group's visibility mode, the system shall update it to the requested
  mode, taking effect immediately for discovery (REQ-27) and join
  (REQ-29/REQ-38) behavior.
- **REQ-24 [Unwanted Behavior]** If a caller who is not a current group
  admin attempts to change visibility, then the system shall reject the
  request.
- **REQ-25 [Unwanted Behavior]** If the requested visibility mode is
  identical to the group's current mode, then the system shall reject
  the request as a no-op change rather than silently succeeding (so a
  client can distinguish "nothing changed" from a real transition, e.g.
  for audit-log clarity).
- **REQ-26 [Unwanted Behavior]** If the target group is archived or
  deleted, then the system shall reject a visibility-change request —
  there is nothing to change visibility on for a group with no
  participants left to admin it (archived) or one that no longer
  exists as a usable entity (deleted).

### Discovery of `REQUEST_TO_JOIN`/`PUBLIC` groups

- **REQ-27 [Ubiquitous]** The system shall allow any authenticated user
  to retrieve a list of active (non-archived, non-deleted) `PEER_GROUP`
  conversations whose visibility is `REQUEST_TO_JOIN` or `PUBLIC` and
  for which that user currently passes `ChatEligibilityService`'s
  eligibility check against the conversation's tenant anchor. `PRIVATE`
  groups, and archived/deleted groups of any visibility, are never
  returned by this discovery capability.
- **REQ-28 [Unwanted Behavior]** If a user who is already a participant
  of a given `REQUEST_TO_JOIN`/`PUBLIC` group queries discovery, then
  the system shall exclude that group from their results (or mark it
  clearly as already-joined, per PLAN-level UX decision).

### Requesting to join a `REQUEST_TO_JOIN` group

- **REQ-29 [Event-Driven]** When an eligible, non-participant user
  submits a join request for an active `REQUEST_TO_JOIN` group, the
  system shall create a pending join-request record, re-deriving
  eligibility at request time.
- **REQ-30 [Event-Driven]** When a current group admin approves a
  pending join request, the system shall re-derive the requester's
  eligibility via `ChatEligibilityService` **at approval time** —
  never trusting the eligibility already re-derived once at submission
  time (REQ-29) as still current — and, only if the requester still
  qualifies, add them as a participant (not an admin) and mark the
  request approved.
- **REQ-30a [Unwanted Behavior]** If, at approval time, the requester no
  longer passes `ChatEligibilityService`'s check (e.g. they lost the
  tenant membership or staff capacity that made them eligible when they
  originally submitted the request, in the time between submission and
  approval), then the system shall reject the approval — with the same
  ineligibility semantics used elsewhere (REQ-11/REQ-35/REQ-40) — rather
  than silently adding a now-ineligible user as a participant. This
  approval-time re-check is independent of, and does not replace,
  REQ-29's submission-time check: a request can be validly created and
  later become unapprovable purely because time passed and the
  requester's eligibility changed in the interim, which is exactly the
  gap this requirement closes. The request itself is left `PENDING`
  (not auto-rejected) so a group admin can see why the approval failed;
  an admin who wants the stale request off the pending list uses the
  existing reject action (REQ-31) explicitly.
- **REQ-31 [Event-Driven]** When a current group admin rejects a
  pending join request, the system shall mark it rejected; the
  requester does not become a participant.
- **REQ-32 [Unwanted Behavior]** If a caller who is not a current group
  admin attempts to approve or reject a join request, then the system
  shall reject the action — approval authority is group-admin-only.
- **REQ-33 [Unwanted Behavior]** If a user who is already a participant
  submits a join request, then the system shall reject it outright, no
  pending record created.
- **REQ-34 [Unwanted Behavior]** If a user submits a join request while
  they already have a pending request for that same group, then the
  system shall reject the duplicate.
- **REQ-35 [Unwanted Behavior]** If a user who fails
  `ChatEligibilityService`'s check submits a join request, then the
  system shall reject it with the same ineligibility semantics used
  elsewhere.
- **REQ-36 [Unwanted Behavior]** If an approve/reject action targets a
  request already decided, then the system shall reject the action.
- **REQ-37 [Unwanted Behavior]** If a join request targets a group that
  is not `REQUEST_TO_JOIN` at request time, or is archived/deleted,
  then the system shall reject the submission.

### Joining a `PUBLIC` group directly

- **REQ-38 [Event-Driven]** When an eligible, non-participant user
  requests to join an active `PUBLIC` group, the system shall
  immediately add them as a participant (not an admin), no approval
  step, re-deriving eligibility at join time.
- **REQ-39 [Unwanted Behavior]** If a user who is already a participant
  attempts to join directly, then the system shall reject the request
  as a no-op.
- **REQ-40 [Unwanted Behavior]** If a user who fails
  `ChatEligibilityService`'s check attempts to join directly, then the
  system shall reject the request.
- **REQ-41 [Unwanted Behavior]** If a direct-join request targets a
  group that is not `PUBLIC` at request time, or is deleted, then the
  system shall reject it (note: unlike the other join/admin paths, a
  `PUBLIC` group never archives on going empty — see below — so
  "archived `PUBLIC` group" cannot occur; deleted still applies).
- **REQ-42 [Unwanted Behavior]** If either the join-request or the
  direct-join target conversation does not exist or is not a
  `PEER_GROUP`, then the system shall reject the request accordingly
  (not-found vs. wrong-kind).

### Empty-group handling (by group type and visibility mode)

The rule for what happens when a group's last participant leaves
depends on both the group's **visibility mode** and, for archival
access, on whether it is a **tenant group** (`tenant_id` set) or a
**staff group** (`tenant_id IS NULL`), reusing `internal-team-chat`'s
existing member-only/staff-only distinction (see "Relationship to
`internal-team-chat`'s SPEC" above). Note this section covers the
*last participant* case (zero participants remain); REQ-54 above
separately covers the *last admin, but not last participant* case
(zero admins, but at least one participant remains).

- **REQ-43 [Complex]** Where a group's visibility is `PRIVATE` or
  `REQUEST_TO_JOIN`, when its last remaining participant leaves, the
  system shall transition that group to an **archived** state:
  removed from all discovery results (already implied by REQ-27's
  active-only scope), no longer joinable by any path (join-request or
  direct-join both rejected per REQ-37/REQ-41), but with its message
  and participant *history* retained, not deleted.
- **REQ-44 [State-Driven]** While a group is archived and is a
  **tenant group**, the system shall allow any user holding the
  `STAFF` role (not only `STAFF_ADMIN`) to view its retained history —
  a deliberately broader grant than the active-group `STAFF_ADMIN`-only
  look-in (`internal-team-chat`'s REQ-5a), confirmed explicitly for
  archived tenant groups by the product owner.
- **REQ-45 [State-Driven]** While a group is archived and is a **staff
  group**, the system shall restrict its retained-history view access
  to `STAFF_ADMIN` only — mirroring the *active*-group staff-only-group
  oversight scope (no broadening for staff groups, unlike REQ-44's
  tenant-group broadening).
- **REQ-46 [Unwanted Behavior]** If any user other than the roles named
  in REQ-44/REQ-45 (for the group's respective type) attempts to view
  an archived group's history, then the system shall reject the
  request — this includes a former participant of that now-archived
  group, who has no residual access once they left.
- **REQ-47 [Complex]** Where a group's visibility is `PUBLIC`, when its
  last remaining participant leaves, the system shall leave the group
  fully active (not archived): still returned by discovery (REQ-27,
  subject to normal eligibility filtering) and still directly joinable
  (REQ-38) by any eligible user, exactly as if it had never emptied.

### Deleting a group

Distinct from archival (REQ-43): archival happens automatically when a
`PRIVATE`/`REQUEST_TO_JOIN` group empties out and preserves the group as
a staff-visible artifact; deletion is a deliberate, permanent action
that can be taken on a group at any time, with participants or without,
by one of four authorized parties.

- **REQ-48 [Ubiquitous]** The system shall allow any of the following
  callers to permanently delete a `PEER_GROUP` conversation, regardless
  of its current participant count, visibility mode, or archived state:
  (a) a `STAFF_ADMIN`, unconditionally, no additional permission
  required; (b) a `MEMBER_ADMIN`, unconditionally **for a tenant group
  belonging to a tenant they currently administer** (scoped exactly
  like `internal-team-chat`'s REQ-5b — a `MEMBER_ADMIN`'s authority is
  inherently tenant-scoped and therefore does not, and cannot, extend
  to a staff group, which has no tenant); (c) any user currently
  holding a new tenant-scoped permission dedicated to this action (see
  Non-functional Requirements for its proposed name), for a tenant
  group in a tenant where they hold that permission (this path
  structurally does not apply to staff groups, which are not
  tenant-owned data a tenant permission grant can reach); (d) a current
  group admin of that specific group (REQ-1/REQ-2/REQ-54), for either a
  tenant or a staff group.
- **REQ-49 [Event-Driven]** When a group is deleted by any of REQ-48's
  authorized parties, the system shall mark the conversation, its
  participant rows, and its messages as soft-deleted — retained in
  storage for audit purposes, consistent with this codebase's existing
  audit-logging posture, but no longer reachable through any normal
  read/write path (messaging, listing, discovery, join, admin actions)
  for any user, including the deleting user, other former participants,
  and the staff-visibility grants in REQ-44/REQ-45 (which apply to
  *archived*, not *deleted*, groups — deletion is a stronger, final
  state that archival's staff-visibility carve-out does not extend to).
- **REQ-50 [Unwanted Behavior]** If a caller who does not qualify under
  any of REQ-48's four paths attempts to delete a group, then the
  system shall reject the request.
- **REQ-51 [Unwanted Behavior]** If the target conversation does not
  exist, then the system shall reject the request with a not-found
  response.
- **REQ-52 [Unwanted Behavior]** If the target conversation is not a
  `PEER_GROUP`, then the system shall reject the request — this
  capability never deletes a `PEER_DIRECT` conversation or a `SUPPORT`
  channel, both out of scope for this entire SPEC.
- **REQ-53 [Unwanted Behavior]** If the target conversation is already
  soft-deleted, then the system shall reject a repeat deletion request
  rather than treating it as a no-op success.

## Non-functional requirements

- Security: every eligibility re-derivation (add, join-request,
  direct-join) and every group-admin/deletion-authorization check must
  be re-derived from the caller's actual current state at request
  time — never cached, never trusted from client input or a stale
  discovery-list snapshot — matching the posture `internal-team-chat`
  already established for its own admin-oversight checks.
- Security: `STAFF_ADMIN`/`MEMBER_ADMIN` **active-group** oversight
  look-in (`internal-team-chat`'s REQ-5a/REQ-5b) grants read-only
  visibility and never participant/admin status; this SPEC's
  archived-group staff-visibility (REQ-44/REQ-45) is a separate,
  explicitly new grant, not an extension of REQ-5a/REQ-5b, and must be
  implemented as its own distinct check (do not reuse or broaden the
  REQ-5a/REQ-5b code path to cover archived groups, since REQ-44
  intentionally grants plain `STAFF` — not just `STAFF_ADMIN` — access
  that active groups never grant).
- Security/permissions: this SPEC needs one new tenant-scoped
  permission to satisfy REQ-48(c) — a dedicated "delete chat groups"
  grant, following this codebase's existing `Permission` enum naming
  convention (`<DOMAIN>_<ACTION>`, e.g. `ARTICLE_DELETE`,
  `TENANT_MEMBER_DELETE`, `SUPPORT_CHANNEL_VIEW`). Proposed name:
  **`CHAT_GROUP_DELETE`**, added to `Permission.java` alongside the
  existing entries. Like `TENANT_MEMBER_MANAGE`/`CONVERSATION_USE`, it
  has no `viewDependency()` — there is no `CHAT_GROUP_VIEW` permission
  today (group messaging/visibility is deliberately ungated per
  `internal-team-chat`'s REQ-1), so requiring one as a prerequisite
  would invent a dependency this feature doesn't otherwise need. This
  permission, like every tenant-scoped `Permission`, is inherently
  scoped to tenant groups only (a permission grant lives within a
  tenant's grant model) and structurally cannot reach a staff group —
  consistent with REQ-48(c)'s wording above. This is a Tier 2 naming
  judgment call (no existing "delete a chat thing" permission to copy
  verbatim), recorded here per this codebase's own convention for
  documenting such calls, not silently invented without a trail.
- Security: soft-deleted (REQ-49) and archived (REQ-43) data must
  remain subject to the same tenant-isolation `@Filter` a tenant group
  already goes through — a `STAFF`/`STAFF_ADMIN` reading archived
  tenant-group history still does so through the same
  cross-tenant-oversight mechanism `internal-team-chat`'s PLAN
  established (an explicit, narrow bypass annotation re-verified at
  read time), never a blanket filter-disable.
- Concurrency: REQ-54's succession must be computed transactionally
  with the admin-removing event that triggers it (REQ-7/REQ-13/REQ-18)
  so that a group can never be observed, even momentarily, in a state
  with participants but zero admins — the exact locking/transaction
  mechanism is a PLAN-level decision, but the invariant itself
  ("nonzero participants implies at least one admin, always") is a
  SPEC-level guarantee, not an eventually-consistent one.
- Observability: promote-to-admin, automatic admin succession
  (REQ-54), add, remove, leave, visibility change, join-request
  submit/approve/reject, direct-join, archive (automatic), and delete
  are all reasonable candidates for this codebase's existing
  `@AuditLog` pattern, consistent with `internal-team-chat` PLAN's
  precedent of auditing group-related state changes — deletion,
  archival, and automatic succession in particular should be audited
  given their permanence/broadened-access/non-caller-initiated nature;
  exact event naming is a PLAN-level decision.
- Performance: discovery (REQ-27) should be paginated consistently with
  this codebase's existing pagination precedents, not returned as an
  unbounded list — exact mechanism is a PLAN-level decision.

## Acceptance criteria

- [ ] Creating a group makes its creator that group's first admin.
- [ ] An existing admin can promote another current participant to
      admin; a group can have more than one admin at once.
- [ ] Promoting a non-participant, promoting an already-admin
      participant, or promoting by a non-admin caller is rejected in
      each case.
- [ ] A group admin can add one or more eligible participants after
      creation, more than once; a non-admin participant cannot.
- [ ] Adding re-runs `ChatEligibilityService`; rejects an already-
      participant identifier without duplicating it; rejects an
      ineligible identifier.
- [ ] A group admin can remove another current participant, revoking
      their access immediately (and their admin status, if they had
      one); a non-admin cannot.
- [ ] Removing the last remaining participant via the remove endpoint
      is rejected; the same user can still leave voluntarily.
- [ ] Any current participant (admin or not) can leave; leaving removes
      admin status too, if held.
- [ ] **When a group's sole admin leaves while other participants
      remain, the longest-tenured (earliest `joined_at`) remaining
      participant is automatically promoted to admin**, verified with a
      group of 3+ participants where the sole admin leaves.
- [ ] **The same scenario, but two or more remaining participants share
      an identical `joined_at`, resolves deterministically to the one
      with the lowest user id** — run twice against the same seeded
      data to confirm the outcome is not random.
- [ ] **The same automatic-succession behavior also fires when the sole
      admin is removed by another admin (REQ-13's path), not only when
      they voluntarily leave.**
- [ ] A group with multiple admins, one of whom leaves, does **not**
      trigger succession (at least one admin already remains).
- [ ] The last participant of a `PRIVATE`/`REQUEST_TO_JOIN` group
      leaving archives that group (no succession fires — zero
      participants remain, not just zero admins): it disappears from
      discovery, is no longer joinable by any path, but its history is
      retained.
- [ ] An archived **tenant** group's history is viewable by any `STAFF`
      user; an archived **staff** group's history is viewable only by
      `STAFF_ADMIN`; a former participant with no other qualifying role
      cannot view either after the group archived.
- [ ] The last participant of a `PUBLIC` group leaving does **not**
      archive it — it stays discoverable and directly joinable.
- [ ] A group admin can change visibility at any time; a non-admin
      cannot; changing to the same current value is rejected; changing
      visibility on an archived or deleted group is rejected.
- [ ] Discovery returns only active, eligible, non-`PRIVATE` groups the
      caller isn't already in (or marks already-joined ones distinctly).
- [ ] An eligible non-participant can submit a join request to an
      active `REQUEST_TO_JOIN` group; a group admin can approve
      (creates participant, non-admin) or reject (no participant
      created) it; a non-admin cannot approve/reject.
- [ ] Duplicate join requests, join requests by existing participants,
      by ineligible users, or against a non-`REQUEST_TO_JOIN`/archived/
      deleted group are all rejected; deciding an already-decided
      request is rejected.
- [ ] **A requester who was eligible at submission time but has become
      ineligible by approval time (e.g. their tenant membership ended in
      the interim) is rejected at approval, not silently added as a
      participant** — verified with a request approved successfully
      immediately after submission (control) versus the same scenario
      with an eligibility-revoking change injected between submission
      and approval (REQ-30a).
- [ ] An eligible non-participant joins a `PUBLIC` group immediately via
      direct join, no approval step; direct-join as an existing
      participant, by an ineligible user, or against a non-`PUBLIC` or
      deleted group is rejected.
- [ ] A `STAFF_ADMIN` can delete any group (tenant or staff), with or
      without participants, without holding any specific permission.
- [ ] A `MEMBER_ADMIN` can delete a tenant group belonging to a tenant
      they administer, but cannot delete a staff group or a tenant
      group of a tenant they don't administer.
- [ ] A user holding the new `CHAT_GROUP_DELETE` tenant permission can
      delete a tenant group in that tenant; holding it grants no
      authority over a staff group.
- [ ] A current group admin can delete their own group (tenant or
      staff), even without any tenant/platform role.
- [ ] A caller who qualifies under none of the four deletion paths is
      rejected.
- [ ] Deleting a non-existent conversation returns not-found; deleting
      a non-`PEER_GROUP` conversation, or a conversation already
      deleted, is rejected.
- [ ] After deletion, the conversation, its participants, and its
      messages are inaccessible through every normal path (messaging,
      listing, discovery, join, admin actions, and the archived-group
      staff-visibility grants) for every user, while remaining present
      in storage for audit purposes.

## Out of scope

- Renaming a group or any group-metadata mutation beyond participant
  membership, admin role, visibility mode, and deletion.
- Demoting a group admin back to a plain participant (this SPEC
  specifies promotion, REQ-2, and automatic succession, REQ-54, but not
  voluntary/manual demotion of a still-participating admin) — if
  needed, that's a follow-up, not silently folded in here.
- Manual/on-demand re-election of an admin when a group already has at
  least one — REQ-54 fires only when the admin count reaches exactly
  zero with participants remaining; it is not a general "vote a new
  admin in" mechanism.
- Any change to `PEER_DIRECT` (1:1) conversations or to `SUPPORT`
  channel membership (governed entirely by ticket assignment, per
  `internal-team-chat`).
- Any change to `ChatEligibilityService`'s eligibility rule itself.
- Bulk/batch operations beyond "add one or more in a single request" —
  no bulk-remove, no bulk approve/reject-all-pending endpoint.
- Un-archiving a group, or any self-service path back from archived to
  active (e.g. by re-adding a participant) — not specified here; if
  ever wanted, that's a fresh decision given the deliberate "no
  mandatory-transfer, no automatic rescue" stance already taken for
  archival in this SPEC (REQ-54's automatic rescue applies only to the
  admin-less-but-not-empty case, never to a fully-emptied, archived
  group).
- Restoring or accessing a *deleted* (as opposed to *archived*) group's
  content for investigative purposes beyond what's already retained at
  the storage layer — no dedicated "view deleted group" feature/UI is
  specified here; if staff ever need that, it's a separate feature.
- Notifications (in-app, email, or otherwise) for any event in this
  SPEC (promotion, automatic succession, add/remove/leave, visibility
  change, join-request submission/approval/rejection, direct join,
  archival, deletion) — any future need is a separate feature,
  consistent with `internal-team-chat`'s own "no push notifications"
  scope line.
- Real-time delivery of any of this SPEC's events to already-open
  clients — same transport-mechanism-is-a-PLAN-decision stance
  `internal-team-chat` already took for messages.
- Full-text or filtered search over discoverable groups beyond the
  visibility+eligibility gate itself.
- Any cap on group size (number of participants) or on the number of
  admins a group may have.

## Tier 3 — status

**None outstanding.** All product-owner decisions needed to write a
PLAN.md are recorded above: group-admin authorization for add/remove/
approve-reject, the admin-concept itself, post-creation visibility
change, the tenant-group/staff-group-aware empty-group/archival rule,
group deletion's four authorization paths and the new
`CHAT_GROUP_DELETE` permission proposal, and automatic admin succession
(REQ-54, with earliest-`joined_at`-then-lowest-user-id as the confirmed
tie-break rule). This SPEC is ready for `PLAN.md`.

**Amendment (AppSec review of PLAN.md, pre-TASKS.md gate): REQ-30a
added.** The initial PLAN draft's `approveJoinRequest` only re-derived
eligibility once, at submission time (REQ-29), leaving a gap where a
requester who became ineligible during the pending window (tenant
membership ended, etc.) could still be silently added as a participant
on approval — inconsistent with this SPEC's and `internal-team-chat`'s
established "always re-derive at the moment of the state-changing
action, never trust an earlier snapshot" posture, which every other
eligibility-gated action in this SPEC (REQ-8/REQ-11 add, REQ-38/REQ-40
direct-join) already follows. REQ-30a closes this gap; it is a Medium-
severity fix to a design gap the SPEC's original acceptance criteria
didn't explicitly test for, not a scope change — no new capability is
introduced, no existing requirement is reversed.
