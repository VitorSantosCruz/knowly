# PLAN — chat-group-membership-management (backend)

> The how. Translates the SPEC into concrete technical decisions.
> References SPEC.md. Amends `internal-team-chat`'s PLAN.md; reuses its
> `ChatConversation`/`ChatParticipant`/`ChatEligibilityService`/
> `ChatOversightConversationLoader`/`BypassTenantFilterForOversight`
> infrastructure unchanged, per the SPEC's own "Relationship to
> `internal-team-chat`'s SPEC" section.

## Architectural decisions

- **Group admin is a boolean flag on the existing `chat_participants`
  row (`is_admin`), not a new join table.** REQ-1/REQ-2/REQ-6/REQ-54
  only ever ask "is this specific (conversation, user) pair currently an
  admin" and "who are the current admins of this conversation" — both
  are answered by a single indexed column on the row that already
  represents that exact pair, with no need for a separate lifecycle,
  audit shape, or cardinality (`ChatParticipant` is already `@Audited`
  via Envers, so `is_admin` transitions are captured in `chat_
  participants_aud` for free, without a second table's history to
  reconcile against the participant table's own history). A second
  table (e.g. `chat_group_admins`) was considered and rejected: it would
  need its own uniqueness constraint mirroring `chat_participants`'
  `(conversation_id, user_id)`, its own FK-cascade-on-remove logic to
  keep it from outliving the participant row, and would let "admin but
  not a participant" become representable in the schema — a state REQ-7
  explicitly forbids ever existing (admin status cannot survive ceasing
  to be a participant). A boolean column makes that invariant
  structural: deleting the `chat_participants` row IS removing the
  admin status, not two operations that could drift apart.
- **`ChatParticipant.joinedAt` (already exists) is reused verbatim as
  REQ-54's succession tie-break ordering** — no new "seniority" column,
  per the SPEC's own explicit instruction. `id` (the row's own surrogate
  key, `BIGSERIAL`) is used as the secondary tie-break instead of
  `user_id` directly: SPEC text says "lowest user id," so the query
  orders by `joinedAt ASC, user_id ASC` explicitly (not by the
  participant row's `id`, which is unrelated to user id ordering) —
  called out here because the two could be silently conflated in a
  careless `ORDER BY` if only "the row" were considered without
  checking which column the SPEC actually names.
- **Visibility mode is a new `visibility` column on `chat_conversations`**
  (`PRIVATE` | `REQUEST_TO_JOIN` | `PUBLIC`, `NOT NULL DEFAULT 'PRIVATE'`),
  same enum-as-`VARCHAR` convention as the existing `kind` column on the
  same table. Applies only to `PEER_GROUP` rows per the SPEC's explicit
  scope; `PEER_DIRECT`/`SUPPORT` rows simply carry the default and are
  never read or written through this feature's code paths (no `CHECK`
  constraint tying `visibility` to `kind`, consistent with `title`/
  `owner_user_id`'s existing precedent on the same table of being
  meaningful only for certain `kind` values without a DB-level
  cross-column constraint).
- **Archival (REQ-43/REQ-47) is represented by a nullable
  `archived_at TIMESTAMPTZ` column on `chat_conversations`, distinct
  from soft-delete's `deleted_at`.** These are two different states with
  different visibility rules (REQ-44/REQ-45 grant staff read access to
  an *archived* group; REQ-49 explicitly says the archived-group
  staff-visibility grant does **not** extend to a *deleted* group) — a
  single "gone" flag conflating them would make REQ-49's "deletion is
  stronger than archival" requirement unrepresentable. `archived_at IS
  NOT NULL` is checked directly in `ChatConversationService` wherever
  REQ-26/REQ-37/REQ-41/REQ-46 need "is this group archived" — it is
  **not** wired into `SoftDeleteFilter`'s Hibernate `@Filter` (that
  filter's condition is fixed to `deleted_at is null` for its 13
  covered entities and is a blanket, no-exceptions default whose whole
  point is "nobody has to remember to check it"; archived groups, by
  contrast, must remain visible to specific roles per REQ-44/REQ-45,
  which requires a conscious, per-query decision — the opposite of what
  a default-on filter is for). Making `archived_at` a plain column
  checked in application code, the same way `SupportTicketStatus` is
  checked today, keeps this distinction legible instead of overloading
  one filter mechanism to mean two different things.
- **Soft-delete (REQ-49) reuses the existing, generic
  `SoftDeleteFilter`/`SoftDeleteFilterAspect`/`AllowDeletedForOversight`
  mechanism from `soft-delete-default-filter`, extended to the three
  chat entities it does not yet cover** (`ChatConversation`,
  `ChatParticipant`, `ChatMessage` — none of the 13 entities that
  feature covers were chat entities, since `chat-group-membership-
  management` did not exist yet when that migration/PLAN was written).
  This is the only correct way to add "soft-deletable" to an entity in
  this codebase per that feature's own architecture: add a
  `deleted_at TIMESTAMPTZ` column, put `@FilterDef`/`@Filter` for
  `SoftDeleteFilter.NAME` on the three entity classes (mirroring exactly
  how `Conversation`/`AccessGroup` already carry both `TenantFilter` and
  `SoftDeleteFilter` side by side), and the already-existing, already-
  registered `SoftDeleteFilterAspect` (unconditional per-`@Transactional`-
  method enable, `!within(Repository+)` pointcut, `@Order(LOWEST_PRECEDENCE)`)
  picks them up automatically — no aspect change needed, since that
  aspect calls `session.enableFilter(SoftDeleteFilter.NAME)`
  unconditionally regardless of which entities are annotated. This also
  means every *existing* `ChatConversationService`/`ChatEligibilityService`
  query against these three entities is now soft-delete-filtered by
  default with zero code change at each call site — exactly the
  "structural, not opt-in" guarantee that feature exists to provide,
  and a second reason (besides REQ-49 itself) this is the right
  mechanism rather than a bespoke `deleted_at IS NULL` predicate bolted
  onto individual repository methods the way `internal-team-chat`
  originally, and incorrectly, did for `User`.
  - REQ-49's requirement that deletion be invisible to *every* path,
    including the REQ-44/REQ-45 archived-group staff-visibility grant,
    falls out of this for free: `SoftDeleteFilter` is on by default for
    every `@Transactional` method with no annotation, so a staff
    oversight read of an archived-but-deleted group is excluded at the
    query layer before `ChatConversationService`'s application-level
    `archived_at` check ever runs — deletion wins structurally, not by
    an ordering convention in service code that a future change could
    accidentally reverse.
  - The one narrow, explicit bypass needed is for **staff running a
    dedicated future audit/investigation query** — out of this SPEC's
    scope (see SPEC's "Out of scope": "no dedicated 'view deleted
    group' feature/UI is specified here") — so `@AllowDeletedForOversight`
    is *not* used anywhere in this PLAN's implementation; it is only
    made available on the three newly-annotated entities by virtue of
    the shared aspect, exactly as it already sits unused-but-available
    on the original 13.
- **Group-admin authorization (REQ-6/REQ-9/REQ-14/REQ-24/REQ-32) is a
  plain service-method check against `chat_participants.is_admin`, not
  a new `@RequiresPermission`/`@RequiresGlobalPermission` annotation.**
  Both existing annotations gate on the *caller's tenant-membership or
  global-role* permission model; group admin is neither — it is a
  per-conversation fact with no tenant/global-role backing at all (the
  SPEC is explicit: "not derived from a user's tenant or platform
  role"). Reusing either aspect here would either force a fake tenant
  context for a staff-only group (which has none) or silently let
  `MEMBER_ADMIN`'s existing `PermissionAspect` bypass leak into group-
  admin gating, which REQ-6 explicitly forbids ("a `STAFF_ADMIN`/
  `MEMBER_ADMIN` is not a group admin by virtue of their tenant/platform
  role"). A private `requireGroupAdmin(User actor, ChatConversation
  conversation)` helper in `ChatConversationService`, re-querying
  `ChatParticipantRepository.findByConversationIdAndUserId(...).filter
  (ChatParticipant::isAdmin)` at request time, is the single place this
  check happens — mirroring how `isActiveMemberAdminOf`/`canReadSupportChannel`
  already do inline, non-annotation-based role checks in this same
  class for `internal-team-chat`'s own admin-oversight logic.
- **Deletion authorization (REQ-48's four paths) is also a plain
  service-method check, not `@RequiresPermission`** — for the same
  structural reason as above, compounded by the fact that `@RequiresPermission`
  hard-requires an *active* tenant context (`PermissionAspect.
  requireActiveMembership` throws `PermissionDeniedException` if
  `TenantContext.getActiveTenantId()` is empty), but REQ-48(c)'s
  `CHAT_GROUP_DELETE` check must be evaluated against **the target
  conversation's own tenant**, not necessarily the caller's currently-
  active tenant selection (a `MEMBER_ADMIN`/permission-holder could in
  principle reach this endpoint without that tenant being their
  currently-active one, depending on how the frontend surfaces group
  management — the SPEC does not require the caller's active tenant to
  match the group's tenant, only that they hold the permission *in that
  tenant*). `ChatConversationService.deleteConversation` therefore
  re-derives all four paths itself, in this order (first match wins,
  matching REQ-48's "any of the following" framing and existing
  precedent of trying `STAFF_ADMIN` before more specific checks):
  1. `tenantContext.isStaffAdmin()` → allowed unconditionally (path a).
  2. Conversation has a non-null tenant AND caller has an active
     `MEMBER_ADMIN` membership in that specific tenant
     (`isActiveMemberAdminOf`, already exists) → allowed (path b).
  3. Conversation has a non-null tenant AND caller has an active
     membership in that tenant holding `Permission.CHAT_GROUP_DELETE`
     (via `permissionService.hasPermission(membership, CHAT_GROUP_DELETE)`,
     re-using `PermissionService` directly rather than the aspect,
     exactly as `canReadSupportChannel` already calls
     `permissionService.hasPermission` inline for `SUPPORT_CHANNEL_VIEW`)
     → allowed (path c).
  4. Caller is a current group admin of *this specific conversation*
     (the new `requireGroupAdmin`-style check, non-throwing variant) →
     allowed (path d).
  Otherwise reject (REQ-50). This order costs nothing extra (each check
  is a cheap lookup) and lets `STAFF_ADMIN` short-circuit before any
  tenant-membership query runs, consistent with every other bypass
  check in this class.
- **REQ-54 (automatic admin succession) runs inside the same
  `@Transactional` method as the triggering removal (REQ-7/REQ-13/
  REQ-18)** — a single private `handleAdminDepartureIfNeeded(conversationId)`
  helper called at the end of `removeParticipant`/`leaveConversation`/
  `demoteViaRemoval`'s shared removal path, inside the same transaction
  boundary, never a separate scheduled/async pass. This satisfies the
  SPEC's non-functional concurrency requirement ("a group can never be
  observed, even momentarily, with participants but zero admins")
  because Postgres's default read-committed isolation, combined with
  the row-level lock a `DELETE ... WHERE conversation_id = ? AND user_id
  = ?` already takes, means no concurrent removal on the same
  conversation can interleave with the succession check without waiting
  for this transaction to commit first — no new locking primitive is
  introduced beyond relying on the same transactional boundary every
  other write in this service already uses. The helper: (1) counts
  remaining participants for the conversation; if zero, do nothing
  (REQ-20's empty-group handling takes over, not REQ-54); (2) if more
  than zero, counts remaining admins; if nonzero, do nothing; (3) if
  zero admins and ≥1 participant, `ORDER BY joined_at ASC, user_id ASC
  LIMIT 1` over remaining participants and set that row's `is_admin =
  true`.
- **Empty-group archival (REQ-43/REQ-47) is computed in the same
  transaction as the last participant's own leave** (REQ-18/REQ-20),
  inside `leaveConversation`: after deleting the caller's participant
  row, if the resulting participant count for that conversation is
  zero, check `conversation.getVisibility()` — `PRIVATE`/
  `REQUEST_TO_JOIN` sets `archived_at = now()`; `PUBLIC` leaves it
  `null` (REQ-47, group stays fully active). This is a **leave-only**
  transition — REQ-16's remove-endpoint rejection of "removal that would
  leave zero participants" (enforced as a precondition check before the
  `DELETE`, not a race-prone post-check) means archival can only ever be
  reached through `leaveConversation`, never through `removeParticipant`,
  matching the SPEC's explicit "only ever reachable via the last
  remaining participant's own leave" wording.
- **Discovery (REQ-27/REQ-28) reuses the existing `PageResponseDto<T>`
  envelope** (`br.com.conectabyte.knowly.tenancy.dto.PageResponseDto`,
  already used by `TenantService.listAllTenants`/`listDeactivatedTenants`
  and `StaffController`'s audit-trail listing) rather than inventing a
  second paginated-response shape for this feature — the SPEC's own
  non-functional requirement asks for consistency with "existing
  pagination precedents," and this is the one already-established
  generic envelope in this codebase (offset/page-based, not the cursor
  mechanism `internal-team-chat` uses for message history — messages
  and discovery are different access patterns: messages need
  stable-under-insert cursor semantics for infinite-scroll, discovery is
  a bounded, re-queryable list with no equivalent "new item at the
  boundary" problem, so reusing the simpler offset-based `Pageable`/
  `PageResponseDto` already used for the structurally similar tenant-
  listing screens is the right fit, not a second copy of `ChatCursor`).
  Query implementation: `ChatConversationRepository.findDiscoverable
  (Pageable)` returns all non-archived, non-deleted `PEER_GROUP` rows
  with `visibility IN ('REQUEST_TO_JOIN', 'PUBLIC')`; the eligibility
  filter (REQ-27's "and for which that user currently passes
  `ChatEligibilityService`'s check") and the already-joined exclusion
  (REQ-28) are applied in `ChatConversationService` after the DB page is
  fetched, not pushed into the SQL — consistent with how
  `ChatEligibilityService.listCandidates` already filters an
  in-memory-loaded list rather than building a dynamic eligibility
  predicate in JPQL, and because eligibility itself is not a column any
  query can filter on directly (it depends on the caller's own tenant
  memberships, re-derived per request, not a stored property of the
  conversation row). REQ-28's resolution: **exclude**, not mark — a
  discovery response never needs to represent "you're already in this,"
  since an already-joined group is reachable via `GET /api/chat/
  conversations` instead; marking would require the endpoint to also
  return groups a user won't act on, adding response complexity for no
  requirement this SPEC actually asks for beyond "or mark it, per
  PLAN-level decision" — exclusion is the simpler of the two explicitly
  sanctioned options.
- **`approveJoinRequest` re-derives `ChatEligibilityService.isEligible`
  a second time, at approval, not only once at submission (REQ-29/
  REQ-30/REQ-30a) — AppSec correction, pre-TASKS.md.** The initial draft
  of this PLAN called `isEligible` only inside `submitJoinRequest`, then
  treated the stored `PENDING` request as sufficient proof of
  eligibility when later approved. That is wrong for the same reason
  `internal-team-chat`'s own security posture already rejects trusting
  any earlier snapshot: the gap between submission and approval is
  unbounded (an admin may not act on a pending request for days), and a
  requester's eligibility is not immutable — a tenant membership can end
  or a staff role can change in that window. `approveJoinRequest`
  therefore: (1) loads the `ChatJoinRequest`, rejects if not `PENDING`
  (REQ-36); (2) re-derives `chatEligibilityService.isEligible(requester,
  conversation.getTenant().getId())` fresh, exactly as
  `addParticipants`/`joinPublicGroup` already do; (3) if ineligible,
  throws `ChatIneligibleParticipantException` (same type/semantics as
  REQ-11/REQ-35/REQ-40, no new exception needed) **and leaves the
  request row `PENDING`** — it is not auto-transitioned to `REJECTED`,
  since REQ-30a treats this as "cannot approve right now," a distinct
  outcome from an admin's deliberate reject decision (REQ-31), and
  auto-rejecting would destroy the admin's ability to see why the
  approval failed versus a request they chose to turn down; (4) only if
  still eligible, proceeds to create the `ChatParticipant` row and mark
  the request `APPROVED`, unchanged from the original design. This
  mirrors `sendMessage`'s existing pattern of re-deriving support
  send-rights fresh on every call rather than caching a prior grant.
- **Join requests are a new, durable `chat_join_requests` table**, one
  row per (conversation, requester, submission), not reused/overloaded
  onto `chat_participants` — REQ-29/REQ-33/REQ-34/REQ-36 need a
  `PENDING`/`APPROVED`/`REJECTED` status independent of participant
  status (a rejected request must not create a participant row, and a
  user with a decided request must still be able to submit a fresh one
  later, which `internal-team-chat`'s "Out of scope" doesn't forbid and
  this SPEC doesn't restrict either — REQ-34 only blocks a *second
  pending* request, not a new one after a prior rejection). Modeled
  after `SupportTicket`'s existing lifecycle-entity shape (own table,
  own status enum, FK to conversation and requester, `@Audited` for the
  same audit-trail reasons `internal-team-chat`'s PLAN gives for ticket
  lifecycle auditing) rather than, e.g., a status column on
  `ChatParticipant` (which would require a participant row to exist
  before approval, contradicting REQ-29/REQ-30's "create participant
  only on approval" ordering).
- **Permission**: add `CHAT_GROUP_DELETE` to the existing tenant-scoped
  `Permission` enum, exactly as the SPEC's own non-functional-requirements
  section proposes and records as its Tier 2 naming call — no
  `viewDependency()` entry (no `CHAT_GROUP_VIEW` permission exists, and
  the SPEC explicitly reasons requiring one would invent an unwanted
  dependency). No new `GlobalPermission` — REQ-44/REQ-45's archived-
  group staff visibility is a role check (`STAFF`/`STAFF_ADMIN` via
  `User.getGlobalRole()`/`TenantContext.isStaffAdmin()`), not a
  permission grant, mirroring how REQ-5a/REQ-5b's active-group look-in
  is already a role check, not a `GlobalPermission`.

## Data schema

New migration `V31__chat_group_membership_management.sql` (next
available after `V30__global_access_group_permission_soft_delete.sql`):

```sql
-- Group admin: a boolean on the existing per-(conversation, user) row.
ALTER TABLE chat_participants ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_participants_aud ADD COLUMN is_admin BOOLEAN;

-- Visibility mode + archival state, PEER_GROUP-only in practice (unenforced by CHECK,
-- consistent with title/owner_user_id's existing kind-conditional-meaning precedent).
ALTER TABLE chat_conversations ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE chat_conversations ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE chat_conversations_aud ADD COLUMN visibility VARCHAR(20);
ALTER TABLE chat_conversations_aud ADD COLUMN archived_at TIMESTAMPTZ;

-- Soft-delete, extending the existing generic SoftDeleteFilter mechanism
-- (soft-delete-default-filter) to the three chat entities it doesn't yet cover.
ALTER TABLE chat_conversations ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_conversations_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_participants ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_participants_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_messages ADD COLUMN deleted_at TIMESTAMPTZ;
-- chat_messages has no _aud table (internal-team-chat's PLAN: message content is deliberately
-- not Envers-audited) -- deleted_at is added to the base table only, consistent with that.

CREATE INDEX ix_chat_conversations_discovery
  ON chat_conversations (visibility, archived_at)
  WHERE kind = 'PEER_GROUP' AND deleted_at IS NULL;

-- Join requests: own lifecycle table, mirrors support_tickets' shape.
CREATE TABLE chat_join_requests (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  requester_user_id BIGINT NOT NULL REFERENCES users (id),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
  decided_by_user_id BIGINT REFERENCES users (id),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

-- REQ-34: at most one PENDING request per (conversation, requester) at a time.
CREATE UNIQUE INDEX ux_chat_join_requests_pending
  ON chat_join_requests (conversation_id, requester_user_id)
  WHERE status = 'PENDING';

CREATE INDEX ix_chat_join_requests_conversation ON chat_join_requests (conversation_id);

CREATE TABLE chat_join_requests_aud (
  id BIGINT NOT NULL,
  rev INTEGER NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  conversation_id BIGINT,
  requester_user_id BIGINT,
  status VARCHAR(20),
  decided_by_user_id BIGINT,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
```

`Permission.java`: add `CHAT_GROUP_DELETE` (no `viewDependency()`
entry).

`ChatConversation.java`/`ChatParticipant.java`/`ChatMessage.java`: add
`@FilterDef(name = SoftDeleteFilter.NAME, defaultCondition = "deleted_at
is null")` + `@Filter(name = SoftDeleteFilter.NAME)` (mirrors
`Conversation`/`AccessGroup`'s existing side-by-side-with-`TenantFilter`
pattern) plus a `deletedAt` field with the standard getters/setters.
`ChatConversation` gets `visibility` (new `ChatGroupVisibility` enum:
`PRIVATE`, `REQUEST_TO_JOIN`, `PUBLIC`) and `archivedAt` fields.
`ChatParticipant` gets `isAdmin` (boolean, default `false`).

## API contracts

All new endpoints live on the existing `ChatController`
(`/api/chat/**`), consistent with `internal-team-chat`'s decision that
peer conversations are not tenant-path-scoped (a staff-only group has
no tenant in its URL).

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/chat/conversations/{id}/participants` | `{userIds: number[]}` | `ChatConversationDetailDto` | 200, 400 (`CHAT_INELIGIBLE_PARTICIPANT` for any rejected id, `CHAT_PARTICIPANT_ALREADY_MEMBER` for any duplicate id — both reported per-id in the body, see below), 403 (`CHAT_ACCESS_DENIED`, not a group admin), 404, 409 (wrong kind/archived/deleted) |
| DELETE | `/api/chat/conversations/{id}/participants/{userId}` | — | `ChatConversationDetailDto` | 200, 403 (not admin), 404 (conversation or participant not found), 409 (would empty the group) |
| POST | `/api/chat/conversations/{id}/leave` | — | `204 No Content` | 204, 403 (`CHAT_ACCESS_DENIED`, not a participant), 404 |
| POST | `/api/chat/conversations/{id}/admins/{userId}` | — | `ChatConversationDetailDto` | 200, 400 (`CHAT_PARTICIPANT_ALREADY_ADMIN` no-op), 403 (caller not admin), 404 (target not a participant) |
| PUT | `/api/chat/conversations/{id}/visibility` | `{visibility: PRIVATE\|REQUEST_TO_JOIN\|PUBLIC}` | `ChatConversationDetailDto` | 200, 400 (`CHAT_VISIBILITY_UNCHANGED` no-op), 403 (not admin), 409 (archived/deleted) |
| GET | `/api/chat/discoverable-groups?page=&size=` | — | `PageResponseDto<ChatDiscoverableGroupDto>` | 200 |
| POST | `/api/chat/conversations/{id}/join-requests` | `{}` | `ChatJoinRequestDto` | 201, 400 (`CHAT_INELIGIBLE_PARTICIPANT`), 403 (`CHAT_PARTICIPANT_ALREADY_MEMBER`), 409 (`CHAT_JOIN_REQUEST_DUPLICATE`, or wrong-mode/archived/deleted) |
| GET | `/api/chat/conversations/{id}/join-requests?status=PENDING` | — | `ChatJoinRequestDto[]` | 200, 403 (not admin), 404 |
| POST | `/api/chat/conversations/{id}/join-requests/{requestId}/approve` | `{}` | `ChatJoinRequestDto` | 200, 400 (`CHAT_INELIGIBLE_PARTICIPANT` — requester no longer eligible at approval time, REQ-30a, request left `PENDING`), 403 (not admin), 409 (`CHAT_JOIN_REQUEST_ALREADY_DECIDED`) |
| POST | `/api/chat/conversations/{id}/join-requests/{requestId}/reject` | `{}` | `ChatJoinRequestDto` | 200, 403 (not admin), 409 (already decided) |
| POST | `/api/chat/conversations/{id}/join` | `{}` | `ChatConversationDetailDto` | 200, 400 (`CHAT_INELIGIBLE_PARTICIPANT`), 403 (`CHAT_PARTICIPANT_ALREADY_MEMBER`), 409 (not `PUBLIC`, or deleted) |
| DELETE | `/api/chat/conversations/{id}` | — | `204 No Content` | 204, 403 (`CHAT_ACCESS_DENIED`, none of the four paths), 404, 409 (`CHAT_CONVERSATION_ALREADY_DELETED`) |

Notes:

- `ChatConversationDetailDto` is extended (not replaced) with
  `visibility`, `archivedAt` (nullable), and `adminUserIds: List<Long>`
  — additive fields, no breaking change to existing consumers of that
  DTO from `internal-team-chat`.
- REQ-10's "reject duplicates, process the rest" batch semantics for
  add-participants: **partial success**, not all-or-nothing. The
  response body for `POST .../participants` is not the plain
  `ChatConversationDetailDto` alone but wrapped as
  `ChatAddParticipantsResultDto {conversation: ChatConversationDetailDto,
  rejected: List<ChatParticipantRejectionDto {userId, reason:
  ALREADY_PARTICIPANT|INELIGIBLE}>}`, HTTP 200 even when some ids were
  rejected (since the request as a whole succeeded for the ids it could
  process) — 400 is reserved for the case where **every** submitted id
  was rejected (nothing to add at all), matching this codebase's
  existing convention of reserving 4xx for "the request accomplished
  nothing," not "the request accomplished less than 100%." This is a
  PLAN-level resolution of REQ-10's explicitly-deferred "exact batch
  semantics" call.
- REQ-12/REQ-17/REQ-26/REQ-37/REQ-41's "wrong kind or archived/deleted"
  rejections all reuse `ChatConversationNotFoundException` (404) for
  "does not exist," and a new `ChatGroupStateConflictException` (409,
  error code `CHAT_GROUP_STATE_CONFLICT` with a `detail` field
  distinguishing `NOT_PEER_GROUP`/`ARCHIVED`/`ALREADY_DELETED`/
  `WRONG_VISIBILITY_MODE`) for every "exists but not currently
  actionable" case — one exception type with a detail enum rather than
  four separate exception classes, since all four are the same HTTP
  semantic (409, conflicting state) and `ChatExceptionHandler` already
  demonstrates the one-exception-per-distinct-*meaning* convention
  (`SupportTicketConflictException` similarly covers more than one
  underlying condition).
- `PUT .../visibility` no-op rejection (REQ-25) and `POST .../admins/
  {userId}` no-op rejection (REQ-5) both return 400 with a dedicated
  error code each (`CHAT_VISIBILITY_UNCHANGED` / `CHAT_PARTICIPANT_ALREADY_ADMIN`)
  rather than reusing a generic "bad request" — consistent with this
  controller's existing one-code-per-condition convention
  (`CHAT_INELIGIBLE_PARTICIPANT`, `CHAT_ACCESS_DENIED`, etc.).
- `ChatJoinRequestDto {id, conversationId, requesterUserId,
  requesterNickname, status, decidedAt}` — nickname resolved the same
  way every other participant-facing DTO already does
  (`nicknameOfUserId`).
- `ChatDiscoverableGroupDto {id, title, tenantId, visibility,
  participantCount}` — deliberately does not reuse
  `ChatConversationSummaryDto` (which exposes the full
  `participantUserIds` list): REQ-27 is a pre-join discovery surface,
  and exposing every current member's identity to a non-member browsing
  for groups to join is unnecessary exposure `ChatConversationSummaryDto`
  was never designed to gate — `participantCount` gives the same "is
  this group active/big enough to be worth joining" signal without it.
- All new mutating endpoints get `@AuditLog` with a distinct `action`
  string each (`chat.group.participant_add`,
  `chat.group.participant_remove`, `chat.group.leave`,
  `chat.group.admin_promote`, `chat.group.admin_succession` — written
  directly via `AuditEventWriter`, same self-invocation reasoning as
  the existing `chat.group.oversight_view`, since succession fires from
  inside another method, not from a controller entry point —
  `chat.group.visibility_change`, `chat.group.join_request_submit`,
  `chat.group.join_request_approve`, `chat.group.join_request_reject`,
  `chat.group.direct_join`, `chat.group.archive` (also
  `AuditEventWriter`-direct, non-caller-initiated per the SPEC's
  observability note), `chat.group.delete`), per the SPEC's own
  observability non-functional requirement calling out deletion,
  archival, and succession specifically.

## Reuse of existing infrastructure (explicit, per task instructions)

- `ChatEligibilityService.isEligible(User, Long tenantAnchor)` is called
  unchanged at: add-participant (per id, REQ-8), join-request submission
  (REQ-29/REQ-35), direct-join (REQ-38/REQ-40), and discovery filtering
  (REQ-27) — zero modification to that class beyond what already exists.
- `ChatOversightConversationLoader`/`BypassTenantFilterForOversight` is
  reused unchanged for loading an archived tenant group across the
  tenant filter when a `STAFF`/`STAFF_ADMIN` reader has no active tenant
  selected matching the group's tenant (REQ-44) — no new bypass
  annotation is introduced; the existing one already exists for exactly
  "staff needs to read a `ChatConversation` row outside normal tenant
  scope," which archived-group staff visibility is a straightforward
  instance of. `ChatConversationService.requireReadableConversation` is
  extended with an archived-group branch that runs only when the
  `archived_at IS NOT NULL` check (done after loading the row via the
  existing filtered-then-oversight-fallback pattern already in that
  method) passes, checking REQ-44 (`tenant != null` → any `STAFF` role,
  via `User.getGlobalRole() != null`) or REQ-45 (`tenant == null` →
  `tenantContext.isStaffAdmin()` only) — added as a new branch in the
  existing method, not a parallel method, since it shares the same
  "load once, branch on outcome" structure REQ-5a/REQ-5b's own branches
  already use there.
- `PermissionService.hasPermission` (already used inline by
  `canReadSupportChannel` for `SUPPORT_CHANNEL_VIEW`) is reused inline
  for `CHAT_GROUP_DELETE`'s check (REQ-48c), not via a new
  `@RequiresPermission`-annotated method — see "Architectural
  decisions" above for why the annotation itself doesn't fit here.
- `TenantMembershipRepository.findByUserAndTenant`/`isActiveMemberAdminOf`
  (already exists in `ChatConversationService`) is reused unchanged for
  REQ-48b's `MEMBER_ADMIN` deletion path — the exact same helper
  `internal-team-chat`'s REQ-5b oversight check already uses.
- No new Hibernate filter, no new tenant-scoping mechanism, no parallel
  `WHERE tenant_id = ?` anywhere in this feature — `ChatConversation`'s
  existing `@Filter(TenantFilter.NAME, ...)` continues to govern every
  tenant-owned row exactly as `internal-team-chat` established; this
  feature only adds the (independent, already-generic) `SoftDeleteFilter`
  pairing to the same entity.

## Dependencies

None new (backend `pom.xml` unchanged) — reuses Spring MVC, Hibernate
Envers, existing `@Filter`/`SoftDeleteFilterAspect`/`TenantFilterAspect`/
`@AuditLog`/`PageResponseDto` infrastructure.

## Package/file structure

New, in existing `br.com.conectabyte.knowly.chat`:

- `ChatGroupVisibility.java` (enum: `PRIVATE`, `REQUEST_TO_JOIN`, `PUBLIC`)
- `ChatJoinRequestStatus.java` (enum: `PENDING`, `APPROVED`, `REJECTED`)
- `ChatJoinRequest.java` (entity)
- `ChatJoinRequestRepository.java`
- `dto/AddChatParticipantsRequestDto.java`, `dto/ChatAddParticipantsResultDto.java`,
  `dto/ChatParticipantRejectionDto.java`, `dto/ChangeChatVisibilityRequestDto.java`,
  `dto/ChatJoinRequestDto.java`, `dto/ChatDiscoverableGroupDto.java`
- `exception/ChatGroupStateConflictException.java`,
  `exception/ChatDuplicateParticipantException.java`,
  `exception/ChatJoinRequestConflictException.java`,
  `exception/ChatVisibilityUnchangedException.java`,
  `exception/ChatAdminAlreadyGrantedException.java`

Modified:

- `br.com.conectabyte.knowly.chat.ChatConversation` — add `visibility`,
  `archivedAt`, `deletedAt` fields + `SoftDeleteFilter` pairing.
- `br.com.conectabyte.knowly.chat.ChatParticipant` — add `isAdmin`,
  `deletedAt` fields + `SoftDeleteFilter` pairing.
- `br.com.conectabyte.knowly.chat.ChatMessage` — add `deletedAt` field +
  `SoftDeleteFilter` pairing (no admin/visibility concept, messages are
  soft-deleted purely as a consequence of REQ-49's "its messages" wording).
- `br.com.conectabyte.knowly.chat.ChatConversationRepository` — add
  `findDiscoverable(Pageable)` (JPQL, filtered by `kind = PEER_GROUP`,
  `visibility IN (...)`, `archivedAt IS NULL` — `deletedAt IS NULL` is
  implicit via the now-attached `SoftDeleteFilter`).
- `br.com.conectabyte.knowly.chat.ChatParticipantRepository` — add
  `countByConversationId(Long)`, `findByConversationIdAndIsAdminTrue(Long)`,
  `findFirstByConversationIdOrderByJoinedAtAscUserIdAsc(Long)` (REQ-54's
  succession query — a custom `@Query` since the tie-break spans two
  columns, one of which, `user.id`, is on the associated `User`, so a
  derived-method name alone (`OrderByJoinedAtAscUserIdAsc` across a
  `@ManyToOne` path) is written as an explicit `@Query` for clarity,
  mirroring `ChatConversationRepository.findByIdRespectingFilter`'s
  existing precedent of preferring an explicit JPQL query over a long
  derived-method name once the query crosses an association).
- `br.com.conectabyte.knowly.chat.ChatEligibilityService` — unchanged.
- `br.com.conectabyte.knowly.chat.ChatConversationService` — new
  methods: `addParticipants`, `removeParticipant`, `leaveConversation`,
  `promoteToAdmin`, `changeVisibility`, `listDiscoverableGroups`,
  `submitJoinRequest`, `listJoinRequests`, `approveJoinRequest`,
  `rejectJoinRequest`, `joinPublicGroup`, `deleteConversation`; extended
  `requireReadableConversation` (archived-group branch, see above);
  private helpers `requireGroupAdmin`, `handleAdminDepartureIfNeeded`,
  `archiveIfEmptied`.
- `br.com.conectabyte.knowly.chat.ChatController` — new endpoints per
  the API contract table above.
- `br.com.conectabyte.knowly.chat.dto.ChatConversationDetailDto` — add
  `visibility`, `archivedAt`, `adminUserIds` fields (additive).
- `br.com.conectabyte.knowly.chat.exception.ChatExceptionHandler` — new
  `@ExceptionHandler` entries for the five new exception types above.
- `br.com.conectabyte.knowly.tenancy.Permission` — add
  `CHAT_GROUP_DELETE`.
- `src/main/resources/db/migration/V31__chat_group_membership_management.sql`
  (new).

## Testing strategy

- Unit tests: REQ-54's succession selection (earliest `joinedAt`, tie
  broken by lowest `user.id`, verified with a synthetic 3+ participant
  set including a deliberate `joinedAt` tie); `requireGroupAdmin`'s
  reject-non-admin/reject-non-participant branches; `deleteConversation`'s
  four-path authorization matrix in isolation (mock each dependency,
  assert each path independently grants and that the "none apply"
  case rejects); archival-vs-visibility-mode branching
  (`PRIVATE`/`REQUEST_TO_JOIN` archives, `PUBLIC` does not) as a pure
  function of participant-count-after-leave and visibility.
- Integration tests (`@SpringBootTest`, Testcontainers, extends
  `ChatControllerIntegrationTest`'s existing fixtures rather than a
  parallel setup):
  - Full REQ-1–REQ-7/REQ-54 admin-role matrix: creator becomes admin;
    promote by admin succeeds, by non-admin/against non-participant/
    against already-admin all rejected; sole-admin leave and sole-admin
    removal both trigger succession with the documented tie-break,
    verified twice against the same tied-`joinedAt` seed to prove
    determinism (mirrors the acceptance criteria's own "run twice"
    wording); multi-admin leave does not trigger succession.
  - Add/remove (REQ-8–17): admin-only gating; partial-success batch
    semantics (mixed valid/duplicate/ineligible ids in one request,
    assert the 200 body's `rejected` list and that only the valid ids
    were actually persisted); remove-to-zero rejected while leave-to-zero
    is accepted and produces archival for `PRIVATE`/`REQUEST_TO_JOIN`
    but not `PUBLIC`.
  - Visibility (REQ-22–26): admin-only change; same-value rejected;
    change against an archived/deleted group rejected; discovery/join
    behavior actually changes immediately after a mode change (no
    caching).
  - Discovery/join-request/direct-join (REQ-27–42): full eligibility-
    aware discovery pagination via `PageResponseDto`; already-participant
    exclusion; join-request submit/approve/reject full lifecycle
    including duplicate-pending rejection, decided-request re-decision
    rejection, ineligible-user rejection at submission; direct-join
    happy path and every rejection branch (already-participant,
    ineligible, wrong visibility mode, deleted).
  - **REQ-30a (AppSec-driven, mandatory): approval-time eligibility
    re-derivation.** Two scenarios, both required: (1) control — a
    request submitted and approved back-to-back with no eligibility
    change in between succeeds normally (proves the fix doesn't
    regress the happy path); (2) the actual gap — a request is
    submitted while the requester is eligible, the requester's
    eligibility is then revoked (e.g. their `TenantMembership` is
    deactivated, or, for a staff-only group, their `GlobalRole` is
    changed away from `STAFF`/`STAFF_ADMIN`), and only *then* is the
    approval attempted — asserting a 400 `CHAT_INELIGIBLE_PARTICIPANT`
    response, no `ChatParticipant` row created, and the request row
    still `PENDING` (not auto-`REJECTED`) afterward, per REQ-30a's
    exact wording.
  - Archived-group visibility (REQ-44–46): tenant group archived-history
    readable by plain `STAFF`, staff group archived-history readable
    only by `STAFF_ADMIN`, former participant with no other role
    rejected from both — extends `internal-team-chat`'s existing
    REQ-5a/REQ-5b test fixtures rather than re-seeding from scratch.
  - Deletion (REQ-48–53): all four authorization paths independently
    proven positive and each proven **not** to grant the other three
    (a `MEMBER_ADMIN` of tenant A cannot delete tenant B's group or a
    staff group; a `CHAT_GROUP_DELETE` holder cannot delete a staff
    group; a group admin's authority does not extend to a different
    group); not-found vs. wrong-kind vs. already-deleted distinguished;
    post-deletion full-path inaccessibility assertion (messaging,
    listing, discovery, join, admin actions, archived-staff-visibility)
    for the deleting user and for a `STAFF_ADMIN` alike, while a direct
    repository query (bypassing `SoftDeleteFilter` in the test via
    `@AllowDeletedForOversight`-style raw SQL/native query, matching how
    `soft-delete-default-filter`'s own tests presumably assert row
    persistence) confirms the row still physically exists.
  - Audit: every new mutating action's distinct `@AuditLog`/direct-
    `AuditEventWriter` action string is asserted written, extending
    `internal-team-chat`'s existing audit-assertion pattern.

**AppSec follow-up notes (non-blocking, for `backend-engineer` to apply
during TASKS.md/implementation, not a reason to hold this PLAN):**

- (a) **403-matrix coverage for every group-mutating endpoint, not only
  `deleteConversation`.** Today only the deletion authorization matrix
  is called out above as explicitly tested against all of its
  authorization paths and their negatives. Every other group-admin-only
  action added in this PLAN (`addParticipants`, `removeParticipant`,
  `promoteToAdmin`, `changeVisibility`, `approveJoinRequest`/
  `rejectJoinRequest`) must get the same explicit two-case negative
  coverage in its own unit/integration test: (1) caller is a current
  group admin **of a different conversation** (proves `requireGroupAdmin`
  is scoped per-conversation, not "is admin of anything"); (2) caller is
  a genuine, current **non-admin participant of the target conversation
  itself** (proves participant status alone never satisfies the
  admin-only gate). Neither case is exotic enough to skip — both are
  realistic mistakes an implementer of `requireGroupAdmin` could make
  (e.g. checking `existsByConversationIdAndUserId` instead of also
  checking `isAdmin`, or checking "is admin of *some* row" without
  scoping the lookup to the specific `conversationId` in the request
  path). `TASKS.md` should give each of these six actions its own
  explicit task/test-case pair for this matrix, not bundle it as an
  afterthought inside the action's main happy-path test.
- (b) **Reconcile this backend contract against the frontend's
  `chat-unified-ui` PLAN — see "Frontend contract reconciliation"
  below.** That frontend PLAN was written against a provisional,
  self-assumed contract (its own text says so) before this backend
  PLAN existed; several endpoint names/paths/response shapes now
  diverge and must be corrected on the frontend side before
  integration, not silently reconciled by changing the backend to match
  a guess made without this PLAN's authorization-model constraints.

## Frontend contract reconciliation

`knowly-app/specify/features/chat-unified-ui/PLAN.md` was written in
parallel, against a self-described **provisional** contract for this
feature's endpoints (its own text: "marked provisional — to confirm
against that backend PLAN.md once it exists"). Now that this backend
PLAN is the authoritative source, per this project's own cross-folder
convention (a frontend PLAN consumes the backend feature's PLAN.md
contract rather than re-deriving it), the frontend PLAN needs the
following corrections. This backend PLAN is not changed to match the
frontend's guesses — the frontend's guesses predate the actual
authorization-model decisions above (e.g. `PUT` not `PATCH` for
visibility, matching this codebase's existing `PUT`-for-full-replace-of-
a-single-field convention elsewhere; response envelopes shaped around
what each action's authorization check actually needs to report back,
e.g. the batch add-participants partial-success shape).

| Concern | Frontend PLAN currently assumes | Actual backend contract (this PLAN) | Frontend correction needed |
|---|---|---|---|
| Discoverable groups | `GET /api/chat/groups/discoverable?tenantId={id}` → `DiscoverableGroupSummary[]` (plain array, includes `viewerJoinState`) | `GET /api/chat/discoverable-groups?page=&size=` → `PageResponseDto<ChatDiscoverableGroupDto>` (paginated envelope, no `viewerJoinState` — already-joined groups are excluded server-side per REQ-28, not flagged) | Update path (`discoverable-groups`, not `groups/discoverable`), drop the `tenantId` query param (eligibility is derived server-side from the caller, not client-selected), unwrap `PageResponseDto.content` instead of expecting a bare array, and drop any `viewerJoinState === 'MEMBER'` filtering logic client-side — it's structurally impossible to receive an already-joined group from this endpoint. |
| Promote to admin | `POST /api/chat/conversations/{id}/participants/{userId}/promote` → `ConversationDetail` | `POST /api/chat/conversations/{id}/admins/{userId}` → `ChatConversationDetailDto` (same conceptual shape as `ConversationDetail`, extended with `visibility`/`archivedAt`/`adminUserIds`) | Update path to `.../admins/{userId}`; read `adminUserIds` from the response to update `GroupCapabilities.isAdmin` locally instead of any separate promote-specific field. |
| Change visibility | `PATCH /api/chat/conversations/{id}/visibility` → `ConversationSummary` | `PUT /api/chat/conversations/{id}/visibility` → `ChatConversationDetailDto` | Switch HTTP method to `PUT`; consume the detail shape, not summary (summary lacks `visibility`/`archivedAt` in this PLAN's DTO split). |
| Approve join request | `POST .../join-requests/{requestId}/approve` → `ConversationSummary`, no documented failure mode for "requester no longer eligible" | Same path → `ChatJoinRequestDto` (not a conversation shape at all), **plus a new 400 `CHAT_INELIGIBLE_PARTICIPANT` outcome (REQ-30a)** that leaves the request `PENDING` | Change expected response type to `JoinRequest`/`ChatJoinRequestDto` shape (id/status/requester info), not a conversation object — the frontend must re-fetch/patch the conversation's participant list separately if it needs the updated membership, since approval no longer echoes the whole conversation back. **Must add explicit UI handling for the new 400 case** (surface "this request is no longer approvable" rather than treating it like the existing 403/409 cases) — this is the direct frontend-side consequence of the AppSec fix in this PLAN. |
| Reject join request | `POST .../join-requests/{requestId}/reject` → `{}` | Same path → `ChatJoinRequestDto` | Consume the returned `ChatJoinRequestDto` (status `REJECTED`) instead of expecting an empty body — trivial but must not silently ignore/misparse a non-empty JSON response. |
| Remove participant | `DELETE .../participants/{userId}` → `{}`, `204` | Same path → `ChatConversationDetailDto`, `200` | Expect `200` with a body (updated conversation detail), not `204` — the frontend's current "on success, patch local state" logic for this action should use the returned detail directly instead of manually computing the post-removal participant list. |
| Leave group | `POST .../leave` → `{}`, `200` | Same path → `204 No Content` | Expect `204`, no body to parse — a response-shape assumption, not a behavior change (frontend's "drop conversation from `_conversations`" logic is unaffected either way since it doesn't need server-echoed data to do that). |
| Add participants | Not present in the frontend PLAN's contract table at all — the "add-participant flow reuses this existing picker" text does not name an endpoint | `POST /api/chat/conversations/{id}/participants` → `ChatAddParticipantsResultDto {conversation, rejected[]}` | The frontend PLAN has a genuine gap here, not just a mismatch — it must add this endpoint to its consumed-contracts table and account for the partial-success `rejected[]` array in `ChatGroupService`'s add-participant action (e.g. surface which submitted candidates were rejected and why, rather than assuming all-or-nothing success). |
| Join (`PUBLIC`, direct) | `POST .../join` → `ConversationSummary`, `200`; `403` for not-PUBLIC/ineligible, `409` for already-participant | Same path → `ChatConversationDetailDto`, `200`; `400` for ineligible, `403` for already-participant, `409` for not-PUBLIC/deleted | Swap which status code maps to which condition: already-a-participant is `403` in this backend PLAN (not `409`), ineligible is `400` (not folded into the same `403` bucket as the wrong-visibility-mode case) — the frontend's error-handling branch for this action needs three distinct cases, not two. |

Delete-group's contract already matches (`DELETE /api/chat/conversations/{id}`,
`204`, no request/response body) — no correction needed there.

